package com.ticketing.file;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Import(TestObjectStorageConfig.class)
class FileOrphanSweeperTest extends AbstractIntegrationTest {

    @Autowired FileOrphanSweeper sweeper;
    @Autowired FileAssetRepository files;
    @Autowired OutboxJobRepository jobs;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeObjectStorage storage;

    private UUID userId;

    @BeforeEach
    void setUp() {
        storage.reset();
        userId = users.saveAndFlush(new User(UUID.randomUUID(),
                "owner." + UUID.randomUUID() + "@example.com", "hash", "Owner")).getId();
    }

    private UUID pendingAsset() {
        FileAsset asset = new FileAsset(UUID.randomUUID(), userId, null, FilePurpose.PROFILE_IMAGE,
                "avatars/" + UUID.randomUUID(), "image/png", 800);
        return files.saveAndFlush(asset).getId();
    }

    @Test
    void orphanedPendingAssetsAreMarkedDeletedAndEnqueued() {
        UUID fileId = pendingAsset();
        // simulate the asset being created 2 hours ago, past the default 1-hour TTL
        jdbc.update("UPDATE file_assets SET created_at = now() - interval '2 hours' WHERE id = ?", fileId);

        int deleted = sweeper.sweepOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(files.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(jobs.findByJobKey(JobTypes.fileDeleteKey(fileId))).isPresent();
    }

    @Test
    void recentPendingAssetsAreLeftAlone() {
        UUID fileId = pendingAsset(); // created_at defaults to now()

        int deleted = sweeper.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(files.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.PENDING);
    }

    @Test
    void readyAssetsAreIgnoredEvenIfOld() {
        FileAsset ready = new FileAsset(UUID.randomUUID(), userId, null, FilePurpose.EVENT_BANNER,
                "banners/" + UUID.randomUUID(), "image/jpeg", 2000);
        ready.markReady("image/jpeg", 2000);
        UUID fileId = files.saveAndFlush(ready).getId();
        jdbc.update("UPDATE file_assets SET created_at = now() - interval '2 hours' WHERE id = ?", fileId);

        int deleted = sweeper.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(files.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.READY);
    }

    @Test
    void alreadyDeletedAssetsAreSkipped() {
        UUID fileId = pendingAsset();
        jdbc.update("UPDATE file_assets SET created_at = now() - interval '2 hours' WHERE id = ?", fileId);
        // manually mark deleted before the sweep
        jdbc.update("UPDATE file_assets SET status = 'DELETED', deleted_at = now() WHERE id = ?", fileId);

        int deleted = sweeper.sweepOnce();

        assertThat(deleted).isZero();
    }
}
