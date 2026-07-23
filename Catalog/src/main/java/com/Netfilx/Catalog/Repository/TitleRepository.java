package com.Netfilx.Catalog.Repository;

import com.Netfilx.Catalog.Entity.Title;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface TitleRepository extends JpaRepository<Title, UUID> {
    List<Title> findByType(String type); // e.g. "MOVIE" or "SERIES"

    @Query("SELECT t FROM Title t JOIN t.genres g WHERE g.name = :genreName")
    List<Title> findByGenreName(String genreName);
}