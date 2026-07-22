package com.ticketing.file;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class FileAssetPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    FileAssetRepository files;
    @Autowired
    UserRepository users;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = users.saveAndFlush(new User(UUID.randomUUID(),
                "owner." + UUID.randomUUID() + "@example.com", "hash", "Owner")).getId();
    }

    private FileAsset pending(String publicId) {
        return new FileAsset(UUID.randomUUID(), ownerId, null, FilePurpose.PROFILE_IMAGE,
                publicId, "image/png", 1024);
    }

    @Test
    void savesAndLoadsAPendingAsset() {
        FileAsset saved = files.saveAndFlush(pending("avatars/" + UUID.randomUUID()));

        FileAsset loaded = files.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(loaded.getPurpose()).isEqualTo(FilePurpose.PROFILE_IMAGE);
        assertThat(loaded.getStatus()).isEqualTo(FileStatus.PENDING);
        assertThat(loaded.getMime()).isEqualTo("image/png");
        assertThat(loaded.getSizeBytes()).isEqualTo(1024);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getDeletedAt()).isNull();
    }

    @Test
    void markReadyStoresTheVerifiedTypeAndSize() {
        FileAsset asset = files.saveAndFlush(pending("avatars/" + UUID.randomUUID()));

        asset.markReady("image/webp", 2048);
        files.saveAndFlush(asset);

        FileAsset loaded = files.findById(asset.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(FileStatus.READY);
        assertThat(loaded.getMime()).isEqualTo("image/webp");
        assertThat(loaded.getSizeBytes()).isEqualTo(2048);
    }

    @Test
    void markDeletedRecordsTheStatusAndTime() {
        FileAsset asset = files.saveAndFlush(pending("avatars/" + UUID.randomUUID()));

        asset.markDeleted(Instant.now());
        files.saveAndFlush(asset);

        FileAsset loaded = files.findById(asset.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(loaded.getDeletedAt()).isNotNull();
    }

    @Test
    void publicIdIsUnique() {
        String publicId = "banners/" + UUID.randomUUID();
        files.saveAndFlush(pending(publicId));

        assertThatThrownBy(() -> files.saveAndFlush(pending(publicId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByPublicIdReturnsTheAsset() {
        String publicId = "banners/" + UUID.randomUUID();
        FileAsset saved = files.saveAndFlush(pending(publicId));

        assertThat(files.findByPublicId(publicId)).get()
                .extracting(FileAsset::getId).isEqualTo(saved.getId());
    }

    @Test
    void findByIdAndOwnerIsScopedToTheOwner() {
        FileAsset saved = files.saveAndFlush(pending("avatars/" + UUID.randomUUID()));

        assertThat(files.findByIdAndOwnerUserId(saved.getId(), ownerId)).isPresent();
        assertThat(files.findByIdAndOwnerUserId(saved.getId(), UUID.randomUUID())).isEmpty();
    }
}
