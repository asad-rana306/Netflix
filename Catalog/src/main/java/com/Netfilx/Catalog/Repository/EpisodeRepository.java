package com.Netfilx.Catalog.Repository;

import com.Netfilx.Catalog.Entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
    List<Episode> findByTitleIdAndSeasonNumberOrderByEpisodeNumberAsc(UUID titleId, Integer seasonNumber);
}
