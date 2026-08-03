package com.Netfilx.Catalog.Controller;

import com.Netfilx.Catalog.DTO.Response.PaginatedResponse;
import com.Netfilx.Catalog.DTO.Response.TitleResponse;
import com.Netfilx.Catalog.DTO.Response.TitleRowResponse;
import com.Netfilx.Catalog.DTO.Response.TitleSummaryDto;
import com.Netfilx.Catalog.Services.CatalogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final DataSource dataSource;

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
}