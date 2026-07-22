package com.ticketing.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.file.FileAsset;
import com.ticketing.file.FileAssetRepository;
import com.ticketing.file.FilePurpose;
import com.ticketing.file.TestObjectStorageConfig;
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
class EventBannerTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    EventService eventService;
    @Autowired
    EventRepository events;
    @Autowired
    FileAssetRepository files;
    @Autowired
    UserRepository users;
    @Autowired
    OrganizerProfileRepository organizerProfiles;

    private UUID organizerUserId;
    private UUID organizerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        organizerUserId = user("Organizer");
        organizerId = organizerProfiles.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();
        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    private UUID readyBanner(UUID owner) {
        FileAsset asset = new FileAsset(UUID.randomUUID(), owner, null, FilePurpose.EVENT_BANNER,
                "banners/" + UUID.randomUUID(), "image/jpeg", 1500);
        asset.markReady("image/jpeg", 1500);
        return files.saveAndFlush(asset).getId();
    }

    @Test
    void setBannerAttachesAReadyBannerAndExposesItsUrl() {
        UUID fileId = readyBanner(organizerUserId);

        eventService.setBanner(eventId, organizerId, organizerUserId, fileId);

        assertThat(events.findById(eventId).orElseThrow().getBannerFileId()).isEqualTo(fileId);
        assertThat(eventService.bannerUrl(fileId)).isNotNull();
    }

    @Test
    void setBannerRejectsAnEventTheCallerDoesNotOwn() {
        UUID fileId = readyBanner(organizerUserId);
        UUID otherProfile = organizerProfiles.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), user("Other"), "Other", null, null)).getId();

        assertThatThrownBy(() -> eventService.setBanner(eventId, otherProfile, organizerUserId, fileId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setBannerRejectsAFileThatIsNotAReadyBanner() {
        FileAsset pending = new FileAsset(UUID.randomUUID(), organizerUserId, null, FilePurpose.EVENT_BANNER,
                "banners/" + UUID.randomUUID(), "image/jpeg", 1500); // still PENDING
        UUID fileId = files.saveAndFlush(pending).getId();

        assertThatThrownBy(() -> eventService.setBanner(eventId, organizerId, organizerUserId, fileId))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void clearBannerRemovesTheReference() {
        UUID fileId = readyBanner(organizerUserId);
        eventService.setBanner(eventId, organizerId, organizerUserId, fileId);

        eventService.clearBanner(eventId, organizerId);

        assertThat(events.findById(eventId).orElseThrow().getBannerFileId()).isNull();
    }

    @Test
    void bannerUrlIsNullWhenThereIsNoBanner() {
        assertThat(eventService.bannerUrl(null)).isNull();
    }
}
