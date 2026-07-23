package com.Netfilx.Catalog.Controller;

import com.Netfilx.Catalog.DTO.Response.TitleResponse;
import com.Netfilx.Catalog.Services.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    // GET /api/v1/catalog/titles
    @GetMapping("/titles")
    public ResponseEntity<List<TitleResponse>> getTitles(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre) {

        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(catalogService.getTitlesByType(type));
        }
        if (genre != null && !genre.isBlank()) {
            return ResponseEntity.ok(catalogService.getTitlesByGenre(genre));
        }
        return ResponseEntity.ok(catalogService.getAllTitles());
    }

    // GET /api/v1/catalog/titles/{id}
    @GetMapping("/titles/{id}")
    public ResponseEntity<TitleResponse> getTitleById(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogService.getTitleById(id));
    }
}