package com.ticketing.file;

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
        String profileFolder) {
}
