package com.Netfilx.Catalog.Controller;

import com.Netfilx.Catalog.DTO.Response.PaginatedResponse;
import com.Netfilx.Catalog.DTO.Response.TitleResponse;
import com.Netfilx.Catalog.DTO.Response.TitleRowResponse;
import com.Netfilx.Catalog.DTO.Response.TitleSummaryDto;
import com.Netfilx.Catalog.Services.CatalogService;
import com.Netfilx.Catalog.Services.S3Service;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final DataSource dataSource;
    private final S3Service s3Service;

    // --- PUBLIC / USER READ ENDPOINTS ---

    @GetMapping("/home")
    public ResponseEntity<List<TitleRowResponse>> getHomeFeed(
            @RequestParam(required = false, defaultValue = "Action") String preferredGenre) {
        return ResponseEntity.ok(catalogService.getHomeFeed(preferredGenre));
    }

    @GetMapping("/titles")
    public ResponseEntity<PaginatedResponse<TitleSummaryDto>> getTitles(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(catalogService.getTitlesByType(type, page, size));
        }
        if (genre != null && !genre.isBlank()) {
            return ResponseEntity.ok(catalogService.getTitlesByGenre(genre, page, size));
        }
        return ResponseEntity.ok(catalogService.getAllTitles(page, size));
    }

    @GetMapping("/titles/{id}")
    public ResponseEntity<TitleResponse> getTitleById(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogService.getTitleById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<TitleSummaryDto>> searchTitles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(catalogService.searchTitles(q, type, genre, page, size));
    }

    // --- ADMIN / WRITE ENDPOINTS (TRIGGERS CACHE INVALIDATION) ---

    @PutMapping("/admin/titles/{id}")
    public ResponseEntity<TitleResponse> updateTitle(
            @PathVariable UUID id,
            @RequestParam String newDescription) {
        return ResponseEntity.ok(catalogService.updateTitleDescription(id, newDescription));
    }

    @DeleteMapping("/admin/titles/{id}")
    public ResponseEntity<Void> deleteTitle(@PathVariable UUID id) {
        catalogService.deleteTitle(id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/movies/{movieId}/upload")
    public ResponseEntity<Map<String, String>> uploadMovieAsset(
            @PathVariable UUID movieId,
            @RequestParam("assetType") String assetType, // "thumbnails" or "movies"
            @RequestParam("file") MultipartFile file) throws IOException {

        String folder = assetType.equalsIgnoreCase("video") ? "movies" : "thumbnails";
        String s3Key = s3Service.uploadFile(folder, file.getOriginalFilename(), file);

        // Save reference key to Catalog database
        if ("video".equalsIgnoreCase(assetType)) {
            catalogService.updateMovieVideoKey(movieId, s3Key);
        } else {
            catalogService.updateMovieThumbnailKey(movieId, s3Key);
        }

        return ResponseEntity.ok(Map.of(
                "movieId", String.valueOf(movieId),
                "assetType", assetType,
                "s3Key", s3Key
        ));
    }
    @PostMapping("/titles/{titleId}/upload")
    public ResponseEntity<TitleResponse> uploadTitleAsset(
            @PathVariable UUID titleId,
            @RequestParam("assetType") String assetType, // "thumbnail" or "video"
            @RequestParam("file") MultipartFile file) throws IOException {

        String folder = "video".equalsIgnoreCase(assetType) ? "movies" : "thumbnails";
        String s3Key = s3Service.uploadFile(folder, file.getOriginalFilename(), file);

        TitleResponse updatedTitle;
        if ("video".equalsIgnoreCase(assetType)) {
            updatedTitle = catalogService.updateMovieVideoKey(titleId, s3Key);
        } else {
            updatedTitle = catalogService.updateMovieThumbnailKey(titleId, s3Key);
        }

        return ResponseEntity.ok(updatedTitle);
    }
}