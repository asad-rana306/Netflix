package com.Netfilx.Catalog.Repository;

import com.Netfilx.Catalog.Entity.Title;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleRepository extends JpaRepository<Title, UUID> {

    @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "25"))
    Slice<Title> findAllBy(Pageable pageable);

    @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "25"))
    Slice<Title> findByType(String type, Pageable pageable);

    @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "25"))
    Slice<Title> findByGenres_Name(String genreName, Pageable pageable);

    // ✅ Loads single Title and its Genres in 1 single JOIN query
    @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "25"))
    @EntityGraph(attributePaths = {"genres"})
    Optional<Title> findById(UUID id);

    // ✅ Uses native ILIKE for direct GIN index matching without per-row LOWER() calls
    @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "25"))
    @Query("""
        SELECT t FROM Title t
        WHERE (:query IS NULL OR :query = '' OR
               t.title ILIKE CONCAT('%', :query, '%') OR
               t.description ILIKE CONCAT('%', :query, '%') OR
               EXISTS (SELECT 1 FROM t.genres g WHERE g.name ILIKE CONCAT('%', :query, '%')))
          AND (:type IS NULL OR :type = '' OR UPPER(t.type) = UPPER(:type))
          AND (:genre IS NULL OR :genre = '' OR EXISTS (SELECT 1 FROM t.genres g WHERE g.name ILIKE CONCAT('%', :genre, '%')))
    """)
    Slice<Title> searchTitles(
            @Param("query") String query,
            @Param("type") String type,
            @Param("genre") String genre,
            Pageable pageable
    );
}