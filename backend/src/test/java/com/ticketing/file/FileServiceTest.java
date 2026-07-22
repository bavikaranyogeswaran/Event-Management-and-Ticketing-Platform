package com.ticketing.file;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TestObjectStorageConfig.class)
class FileServiceTest extends AbstractIntegrationTest {

    @Autowired
    FileService fileService;
    @Autowired
    FileUploadService uploadService;
    @Autowired
    UserRepository users;
    @Autowired
    FakeObjectStorage storage;

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

    private UUID readyBanner(UUID owner) {
        UploadTicket ticket = uploadService.requestUpload(owner, FilePurpose.EVENT_BANNER, "image/jpeg", 2000);
        storage.simulateUpload(ticket.upload().publicId(), "image/jpeg", 1500);
        uploadService.complete(owner, ticket.fileId());
        return ticket.fileId();
    }

    @Test
    void confirmAcceptsAReadyBannerOwnedByTheUser() {
        UUID fileId = readyBanner(userId);

        assertThatCode(() -> fileService.confirmEventBanner(userId, fileId)).doesNotThrowAnyException();
    }

    @Test
    void confirmRejectsAPendingBanner() {
        UploadTicket ticket = uploadService.requestUpload(userId, FilePurpose.EVENT_BANNER, "image/jpeg", 2000);

        assertThatThrownBy(() -> fileService.confirmEventBanner(userId, ticket.fileId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void confirmRejectsAProfileImage() {
        UploadTicket ticket = uploadService.requestUpload(userId, FilePurpose.PROFILE_IMAGE, "image/png", 500);
        storage.simulateUpload(ticket.upload().publicId(), "image/png", 500);
        uploadService.complete(userId, ticket.fileId());

        assertThatThrownBy(() -> fileService.confirmEventBanner(userId, ticket.fileId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void confirmRejectsAnotherUsersFile() {
        UUID fileId = readyBanner(userId);

        assertThatThrownBy(() -> fileService.confirmEventBanner(user("Intruder"), fileId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void imageUrlIsPresentForAReadyImage() {
        UUID fileId = readyBanner(userId);

        assertThat(fileService.imageUrl(fileId)).isPresent();
    }

    @Test
    void imageUrlIsEmptyForAPendingUploadOrNoFile() {
        UploadTicket pending = uploadService.requestUpload(userId, FilePurpose.EVENT_BANNER, "image/jpeg", 2000);

        assertThat(fileService.imageUrl(pending.fileId())).isEmpty();
        assertThat(fileService.imageUrl(null)).isEmpty();
    }
}
