package com.ticketing.file;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TestObjectStorageConfig.class)
class FileUploadServiceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    FileUploadService uploadService;
    @Autowired
    FileAssetRepository files;
    @Autowired
    UserRepository users;
    @Autowired
    OrganizerProfileRepository organizerProfiles;
    @Autowired
    EventService eventService;
    @Autowired
    FakeObjectStorage storage;

    private UUID userId;

    @BeforeEach
    void setUp() {
        storage.reset();
        userId = user("Uploader");
    }

    private UploadTicket requestProfile() {
        return uploadService.requestUpload(userId, FilePurpose.PROFILE_IMAGE, null, "image/png", 1000);
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    private UUID ownedEvent(UUID ownerUserId) {
        UUID profileId = organizerProfiles.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), ownerUserId, "Org", null, null)).getId();
        Instant now = Instant.now();
        return eventService.createDraft(profileId, new EventDraftCommand(
                CONCERTS, "Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    @Test
    void aProfileImageRequestCreatesPendingMetadataAndSignsTheUpload() {
        UploadTicket ticket = uploadService.requestUpload(userId, FilePurpose.PROFILE_IMAGE, null, "image/png", 1000);

        assertThat(ticket.upload().signature()).isNotBlank();
        FileAsset asset = files.findById(ticket.fileId()).orElseThrow();
        assertThat(asset.getStatus()).isEqualTo(FileStatus.PENDING);
        assertThat(asset.getPurpose()).isEqualTo(FilePurpose.PROFILE_IMAGE);
        assertThat(asset.getOwnerUserId()).isEqualTo(userId);
        assertThat(asset.getEventId()).isNull();
        assertThat(asset.getPublicId()).startsWith("avatars/").isEqualTo(ticket.upload().publicId());
    }

    @Test
    void aBannerRequestForAnOwnedEventIsAccepted() {
        UUID eventId = ownedEvent(userId);

        UploadTicket ticket = uploadService.requestUpload(
                userId, FilePurpose.EVENT_BANNER, eventId, "image/jpeg", 2000);

        FileAsset asset = files.findById(ticket.fileId()).orElseThrow();
        assertThat(asset.getPurpose()).isEqualTo(FilePurpose.EVENT_BANNER);
        assertThat(asset.getEventId()).isEqualTo(eventId);
        assertThat(asset.getPublicId()).startsWith("banners/");
    }

    @Test
    void aBannerRequestForAnEventYouDoNotOwnIsRejected() {
        UUID othersEvent = ownedEvent(user("Owner"));

        assertThatThrownBy(() -> uploadService.requestUpload(
                userId, FilePurpose.EVENT_BANNER, othersEvent, "image/png", 1000))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBannerRequestWithoutAnEventIsRejected() {
        assertThatThrownBy(() -> uploadService.requestUpload(
                userId, FilePurpose.EVENT_BANNER, null, "image/png", 1000))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void anUnsupportedTypeIsRejected() {
        assertThatThrownBy(() -> uploadService.requestUpload(
                userId, FilePurpose.PROFILE_IMAGE, null, "image/gif", 1000))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aFileOverTheLimitIsRejected() {
        long overProfileCap = 3L * 1024 * 1024; // the profile cap is 2MB

        assertThatThrownBy(() -> uploadService.requestUpload(
                userId, FilePurpose.PROFILE_IMAGE, null, "image/png", overProfileCap))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void anExportCannotBeRequestedByAClient() {
        assertThatThrownBy(() -> uploadService.requestUpload(
                userId, FilePurpose.EXPORT, null, "image/png", 1000))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void completingAVerifiedUploadMarksItReady() {
        UploadTicket ticket = requestProfile();
        storage.simulateUpload(ticket.upload().publicId(), "image/png", 500);

        CompletedUpload completed = uploadService.complete(userId, ticket.fileId());

        assertThat(completed.asset().getStatus()).isEqualTo(FileStatus.READY);
        assertThat(completed.url()).isNotBlank();
        assertThat(files.findById(ticket.fileId()).orElseThrow().isReady()).isTrue();
    }

    @Test
    void completeStoresTheProvidersTypeAndSizeNotTheDeclaredOnes() {
        UploadTicket ticket = requestProfile(); // declared image/png, 1000
        storage.simulateUpload(ticket.upload().publicId(), "image/webp", 2000);

        uploadService.complete(userId, ticket.fileId());

        FileAsset asset = files.findById(ticket.fileId()).orElseThrow();
        assertThat(asset.getMime()).isEqualTo("image/webp");
        assertThat(asset.getSizeBytes()).isEqualTo(2000);
    }

    @Test
    void completeRejectsAFileMissingFromTheProvider() {
        UploadTicket ticket = requestProfile(); // the upload is never simulated

        assertThatThrownBy(() -> uploadService.complete(userId, ticket.fileId()))
                .isInstanceOf(ApiException.class);
        assertThat(files.findById(ticket.fileId()).orElseThrow().getStatus()).isEqualTo(FileStatus.PENDING);
    }

    @Test
    void completeRejectsAnUploadThatTurnedOutTooLarge() {
        UploadTicket ticket = requestProfile();
        storage.simulateUpload(ticket.upload().publicId(), "image/png", 3L * 1024 * 1024); // over the 2MB cap

        assertThatThrownBy(() -> uploadService.complete(userId, ticket.fileId()))
                .isInstanceOf(ApiException.class);
        assertThat(files.findById(ticket.fileId()).orElseThrow().getStatus()).isEqualTo(FileStatus.PENDING);
    }

    @Test
    void completeRejectsAnUploadOfTheWrongType() {
        UploadTicket ticket = requestProfile();
        storage.simulateUpload(ticket.upload().publicId(), "image/gif", 500);

        assertThatThrownBy(() -> uploadService.complete(userId, ticket.fileId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void completeIsIdempotent() {
        UploadTicket ticket = requestProfile();
        storage.simulateUpload(ticket.upload().publicId(), "image/png", 500);
        uploadService.complete(userId, ticket.fileId());

        CompletedUpload second = uploadService.complete(userId, ticket.fileId());

        assertThat(second.asset().getStatus()).isEqualTo(FileStatus.READY);
    }

    @Test
    void completeIsScopedToTheOwner() {
        UploadTicket ticket = requestProfile();
        storage.simulateUpload(ticket.upload().publicId(), "image/png", 500);

        assertThatThrownBy(() -> uploadService.complete(user("Someone Else"), ticket.fileId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
