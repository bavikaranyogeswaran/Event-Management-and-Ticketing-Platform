package com.ticketing.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.event.TicketTypeCommand;
import com.ticketing.file.FakeObjectStorage;
import com.ticketing.file.FileAsset;
import com.ticketing.file.FileAssetRepository;
import com.ticketing.file.FilePurpose;
import com.ticketing.file.FileStatus;
import com.ticketing.file.TestObjectStorageConfig;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJob;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.notification.OutboxStatus;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TestObjectStorageConfig.class)
class CsvExportDispatcherTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired CsvExportDispatcher dispatcher;
    @Autowired OutboxJobRepository jobs;
    @Autowired FileAssetRepository fileAssets;
    @Autowired FakeObjectStorage storage;
    @Autowired UserRepository userRepository;
    @Autowired OrganizerProfileRepository organizerProfileRepository;
    @Autowired EventService eventService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void successPathMarksSentAndUploadsAndMakesFileReady() {
        // organizer + profile + draft event (no attendees needed; empty CSV is valid)
        User user = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "csvorg@example.com", "hash", "OrgUser"));
        user.addRole(Role.ORGANIZER);
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), user.getId(), "Org", null, null));

        Instant now = Instant.now();
        var event = eventService.createDraft(profile.getId(), new EventDraftCommand(CONCERTS, "Csv Event", "desc",
                EventType.PHYSICAL, "Venue", "Addr", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 50));
        eventService.addTicketType(event.getId(), profile.getId(), new TicketTypeCommand("GA", null,
                new BigDecimal("500.00"), 10, 2, now, now.plus(28, ChronoUnit.DAYS)));

        UUID fileId = UUID.randomUUID();
        String publicId = "exports/" + fileId;
        UUID ownerId = user.getId();

        FileAsset asset = new FileAsset(fileId, ownerId, null, FilePurpose.EXPORT, publicId, "text/csv", 0L);
        fileAssets.saveAndFlush(asset);

        String payload = buildPayload(fileId, publicId, event.getId(), profile.getId(), ownerId);
        OutboxJob job = new OutboxJob(UUID.randomUUID(), JobTypes.EXPORT,
                JobTypes.exportKey(fileId), payload, now);
        job.markPublishing();
        jobs.saveAndFlush(job);

        dispatcher.deliver(job.getId());

        OutboxJob done = jobs.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(done.getSentAt()).isNotNull();

        FileAsset ready = fileAssets.findById(fileId).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(FileStatus.READY);

        assertThat(storage.exists(publicId)).isTrue();
    }

    @Test
    void unknownJobIsRejectedForDeadLettering() {
        assertThatThrownBy(() -> dispatcher.deliver(UUID.randomUUID()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private String buildPayload(UUID fileId, String publicId, UUID eventId, UUID organizerId, UUID ownerUserId) {
        return objectMapper.writeValueAsString(Map.of(
                "fileId", fileId.toString(),
                "publicId", publicId,
                "eventId", eventId.toString(),
                "organizerId", organizerId.toString(),
                "ownerUserId", ownerUserId.toString()));
    }
}
