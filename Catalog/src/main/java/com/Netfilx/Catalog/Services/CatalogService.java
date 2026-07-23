package com.Netfilx.Catalog.Services;

import com.Netfilx.Catalog.DTO.Response.TitleResponse;
import com.Netfilx.Catalog.Entity.Genre;
import com.Netfilx.Catalog.Entity.Title;
import com.Netfilx.Catalog.Repository.TitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final TitleRepository titleRepository;

    @Transactional(readOnly = true)
    public List<TitleResponse> getAllTitles() {
        return titleRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TitleResponse> getTitlesByType(String type) {
        return titleRepository.findByType(type.toUpperCase()).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TitleResponse> getTitlesByGenre(String genre) {
        return titleRepository.findByGenreName(genre).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TitleResponse getTitleById(UUID id) {
        Title title = titleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Title not found with ID: " + id));
        return mapToDto(title);
    }

    private TitleResponse mapToDto(Title title) {
        return new TitleResponse(
                title.getId(),
                title.getTitle(),
                title.getDescription(),
                title.getType(),
                title.getMaturityRating(),
                title.getThumbnailUrl(),
                title.getHlsMasterUrl(),
                title.getReleaseYear(),
                title.getGenres().stream().map(Genre::getName).collect(Collectors.toSet())
        );
    }
}