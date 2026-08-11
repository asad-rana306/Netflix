package com.Netfilx.Catalog.Services;

import com.Netfilx.Catalog.DTO.Response.PaginatedResponse;
import com.Netfilx.Catalog.DTO.Response.TitleResponse;
import com.Netfilx.Catalog.DTO.Response.TitleRowResponse;
import com.Netfilx.Catalog.DTO.Response.TitleSummaryDto;
import com.Netfilx.Catalog.Entity.Genre;
import com.Netfilx.Catalog.Entity.Title;
import com.Netfilx.Catalog.Repository.TitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final TitleRepository titleRepository;

    // ==========================================
    // READ OPERATIONS (MULTI-TIER CACHED)
    // ==========================================

    @Cacheable(value = "homeFeed", key = "#preferredGenre != null ? #preferredGenre : 'default'", cacheManager = "caffeineCacheManager")
    @Transactional(readOnly = true)
    public List<TitleRowResponse> getHomeFeed(String preferredGenre) {
        List<TitleRowResponse> feed = new ArrayList<>();

        List<TitleSummaryDto> trending = titleRepository.findAllBy(PageRequest.of(0, 10, Sort.by("createdAt").descending()))
                .map(this::mapToSummaryDto).getContent();
        feed.add(new TitleRowResponse("Trending Now", trending));

        String activeGenre = (preferredGenre != null && !preferredGenre.isBlank()) ? preferredGenre : "Action";
        List<TitleSummaryDto> recommended = titleRepository.findByGenres_Name(activeGenre, PageRequest.of(0, 15))
                .map(this::mapToSummaryDto).getContent();
        feed.add(new TitleRowResponse("Recommended for You (" + activeGenre + ")", recommended));

        List<TitleSummaryDto> action = titleRepository.findByGenres_Name("Action", PageRequest.of(0, 15))
                .map(this::mapToSummaryDto).getContent();
        feed.add(new TitleRowResponse("Action & Adventure", action));

        List<TitleSummaryDto> comedies = titleRepository.findByGenres_Name("Comedy", PageRequest.of(0, 15))
                .map(this::mapToSummaryDto).getContent();
        feed.add(new TitleRowResponse("Top Comedies", comedies));

        return feed;
    }

    @Cacheable(value = "titleDetails", key = "#id", cacheManager = "redisCacheManager")
    @Transactional(readOnly = true)
    public TitleResponse getTitleById(UUID id) {
        Title title = titleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Title not found with ID: " + id));
        return mapToDto(title);
    }

    @Cacheable(value = "searchResults", key = "{ 'all', #page, #size }", cacheManager = "redisCacheManager", unless = "#result.content.isEmpty()")
    @Transactional(readOnly = true)
    public PaginatedResponse<TitleSummaryDto> getAllTitles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return mapToPaginatedResponse(titleRepository.findAllBy(pageable));
    }

    @Cacheable(value = "searchResults", key = "{ 'type', #type, #page, #size }", cacheManager = "redisCacheManager", unless = "#result.content.isEmpty()")
    @Transactional(readOnly = true)
    public PaginatedResponse<TitleSummaryDto> getTitlesByType(String type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return mapToPaginatedResponse(titleRepository.findByType(type.toUpperCase(), pageable));
    }

    @Cacheable(value = "searchResults", key = "{ 'genre', #genre, #page, #size }", cacheManager = "redisCacheManager", unless = "#result.content.isEmpty()")
    @Transactional(readOnly = true)
    public PaginatedResponse<TitleSummaryDto> getTitlesByGenre(String genre, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return mapToPaginatedResponse(titleRepository.findByGenres_Name(genre, pageable));
    }

    @Cacheable(value = "searchResults", key = "{ 'search', #query, #type, #genre, #page, #size }", cacheManager = "redisCacheManager", unless = "#result.content.isEmpty()")
    @Transactional(readOnly = true)
    public PaginatedResponse<TitleSummaryDto> searchTitles(String query, String type, String genre, int page, int size) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanType = (type != null && !type.isBlank()) ? type.trim() : null;
        String cleanGenre = (genre != null && !genre.isBlank()) ? genre.trim() : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("title").ascending());
        return mapToPaginatedResponse(titleRepository.searchTitles(cleanQuery, cleanType, cleanGenre, pageable));
    }

    // ==========================================
    // WRITE OPERATIONS (STRICT CACHE INVALIDATION)
    // ==========================================

    @Caching(evict = {
            @CacheEvict(value = "titleDetails", key = "#id", cacheManager = "redisCacheManager"),
            @CacheEvict(value = "homeFeed", allEntries = true, cacheManager = "caffeineCacheManager"),
            @CacheEvict(value = "searchResults", allEntries = true, cacheManager = "redisCacheManager")
    })
    @Transactional
    public TitleResponse updateTitleDescription(UUID id, String newDescription) {
        Title title = titleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Title not found with ID: " + id));
        title.setDescription(newDescription);
        titleRepository.save(title);
        return mapToDto(title);
    }

    @Caching(evict = {
            @CacheEvict(value = "titleDetails", key = "#id", cacheManager = "redisCacheManager"),
            @CacheEvict(value = "homeFeed", allEntries = true, cacheManager = "caffeineCacheManager"),
            @CacheEvict(value = "searchResults", allEntries = true, cacheManager = "redisCacheManager")
    })
    @Transactional
    public void deleteTitle(UUID id) {
        titleRepository.deleteById(id);
    }

    // ==========================================
    // MAPPERS
    // ==========================================

    private TitleSummaryDto mapToSummaryDto(Title title) {
        return new TitleSummaryDto(
                title.getId(), title.getTitle(), title.getType(), title.getMaturityRating(),
                title.getThumbnailUrl(), title.getReleaseYear(),
                title.getGenres().stream().map(Genre::getName).collect(Collectors.toSet()),
                title.getHlsMasterUrl()
        );
    }

    private TitleResponse mapToDto(Title title) {
        return new TitleResponse(
                title.getId(), title.getTitle(), title.getDescription(), title.getType(),
                title.getMaturityRating(), title.getThumbnailUrl(), title.getHlsMasterUrl(),
                title.getReleaseYear(), title.getGenres().stream().map(Genre::getName).collect(Collectors.toSet()),
                title.getPreviewUrl()
        );
    }

    // Safely converts Spring Data's Slice into a Serializable JSON response
    private PaginatedResponse<TitleSummaryDto> mapToPaginatedResponse(Slice<Title> titleSlice) {
        List<TitleSummaryDto> content = titleSlice.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();
        return new PaginatedResponse<>(content, titleSlice.hasNext(), titleSlice.getNumber());
    }
    // ==========================================
    // WRITE OPERATIONS (S3 ASSET UPDATES)
    // ==========================================

    @Caching(evict = {
            @CacheEvict(value = "titleDetails", key = "#id", cacheManager = "redisCacheManager"),
            @CacheEvict(value = "homeFeed", allEntries = true, cacheManager = "caffeineCacheManager"),
            @CacheEvict(value = "searchResults", allEntries = true, cacheManager = "redisCacheManager")
    })
    @Transactional
    public TitleResponse updateMovieThumbnailKey(UUID id, String s3Key) {
        Title title = titleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Title not found with ID: " + id));

        title.setThumbnailUrl(s3Key);
        titleRepository.save(title);
        return mapToDto(title);
    }

    @Caching(evict = {
            @CacheEvict(value = "titleDetails", key = "#id", cacheManager = "redisCacheManager"),
            @CacheEvict(value = "homeFeed", allEntries = true, cacheManager = "caffeineCacheManager"),
            @CacheEvict(value = "searchResults", allEntries = true, cacheManager = "redisCacheManager")
    })
    @Transactional
    public TitleResponse updateMovieVideoKey(UUID id, String s3Key) {
        Title title = titleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Title not found with ID: " + id));

        title.setHlsMasterUrl(s3Key);
        titleRepository.save(title);
        return mapToDto(title);
    }
}