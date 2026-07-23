package com.ticketing.file;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Upload policy: which image types are allowed, how large each kind may be, and where each lives. */
@ConfigurationProperties(prefix = "app.files")
public record FileProperties(
        List<String> allowedMimeTypes,
        DataSize bannerMaxSize,
        DataSize profileMaxSize,
        String bannerFolder,
        String profileFolder,
        Duration orphanTtl,           // how long a PENDING upload may sit before it is treated as abandoned
        Duration orphanSweepInterval,  // how often the orphan sweep runs
        int orphanBatchSize,           // max assets to delete per sweep run
        Duration exportDownloadTtl) {  // how long a signed export download URL remains valid
}
