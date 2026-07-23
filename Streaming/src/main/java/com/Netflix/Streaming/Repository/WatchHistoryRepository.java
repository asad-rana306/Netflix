package com.Netflix.Streaming.Repository;

import com.Netflix.Streaming.Entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, UUID> {

    Optional<WatchHistory> findByProfileIdAndTitleIdAndEpisodeId(UUID profileId, Long titleId, Long episodeId);

    Optional<WatchHistory> findByProfileIdAndTitleIdAndEpisodeIdIsNull(UUID profileId, Long titleId);

    // Fetch items that are partially watched (Continue Watching row)
    List<WatchHistory> findByProfileIdAndIsCompletedFalseOrderByLastWatchedAtDesc(UUID profileId);
}