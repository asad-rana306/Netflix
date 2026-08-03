package com.Netflix.Streaming.Repository;

import com.Netflix.Streaming.Entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, UUID> {

    // ⚡ CHANGED to findFirstBy to safely handle edge-case duplicate matches
    Optional<WatchHistory> findFirstByProfileIdAndTitleIdAndEpisodeId(UUID profileId, UUID titleId, UUID episodeId);

    Optional<WatchHistory> findFirstByProfileIdAndTitleIdAndEpisodeIdIsNull(UUID profileId, UUID titleId);

    // Fetch items that are partially watched (Continue Watching row)
    List<WatchHistory> findByProfileIdAndIsCompletedFalseOrderByLastWatchedAtDesc(UUID profileId);
}