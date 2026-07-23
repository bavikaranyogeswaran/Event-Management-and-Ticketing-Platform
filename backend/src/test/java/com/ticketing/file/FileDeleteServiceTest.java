package com.ticketing.file;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TestObjectStorageConfig.class)
class FileDeleteServiceTest extends AbstractIntegrationTest {

    @Autowired FileDeleteService deleteService;
    @Autowired FileAssetRepository files;
    @Autowired OutboxJobRepository jobs;
    @Autowired UserRepository users;
    @Autowired FakeObjectStorage storage;

    private UUID userId;

    @BeforeEach
    void setUp() {
        storage.reset();
        userId = user("Owner");
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    private UUID readyAsset(UUID owner, FilePurpose purpose) {
        FileAsset asset = new FileAsset(UUID.randomUUID(), owner, null, purpose,
                "avatars/" + UUID.randomUUID(), "image/png", 800);
        asset.markReady("image/png", 800);
        return files.saveAndFlush(asset).getId();
    }

    @Test
    void deleteMarksTheAssetDeletedAndEnqueuesADestroyJob() {
        UUID fileId = readyAsset(userId, FilePurpose.PROFILE_IMAGE);
        String publicId = files.findById(fileId).orElseThrow().getPublicId();

        deleteService.delete(userId, fileId);

        FileAsset reloaded = files.findById(fileId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(jobs.findByJobKey(JobTypes.fileDeleteKey(fileId))).isPresent();
    }

    @Test
    void deleteIsIdempotentForAlreadyDeletedAssets() {
        UUID fileId = readyAsset(userId, FilePurpose.PROFILE_IMAGE);
        deleteService.delete(userId, fileId);

        assertThatCode(() -> deleteService.delete(userId, fileId)).doesNotThrowAnyException();
        // only one job was enqueued for this file
        assertThat(jobs.findByJobKey(JobTypes.fileDeleteKey(fileId))).isPresent();
    }

    @Test
    void deleteRejectsAnotherUsersFile() {
        UUID fileId = readyAsset(userId, FilePurpose.PROFILE_IMAGE);
        UUID other = user("Other");

        assertThatThrownBy(() -> deleteService.delete(other, fileId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(files.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.READY);
    }

    @Test
    void deletePendingAssetsIsAllowed() {
        FileAsset pending = new FileAsset(UUID.randomUUID(), userId, null, FilePurpose.PROFILE_IMAGE,
                "avatars/" + UUID.randomUUID(), "image/png", 800);
        UUID fileId = files.saveAndFlush(pending).getId();

        deleteService.delete(userId, fileId);

        assertThat(files.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(jobs.findByJobKey(JobTypes.fileDeleteKey(fileId))).isPresent();
    }
}
