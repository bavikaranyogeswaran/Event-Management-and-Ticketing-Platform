package com.ticketing.organizer;

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
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TestObjectStorageConfig.class)
class OrganizerLogoTest extends AbstractIntegrationTest {

    @Autowired
    OrganizerProfileService profiles;
    @Autowired
    FileAssetRepository files;
    @Autowired
    UserRepository users;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = user("Organizer");
        profiles.create(userId, "My Org", null, null);
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    private UUID readyImage(UUID owner, FilePurpose purpose) {
        FileAsset asset = new FileAsset(UUID.randomUUID(), owner, null, purpose,
                "avatars/" + UUID.randomUUID(), "image/png", 800);
        asset.markReady("image/png", 800);
        return files.saveAndFlush(asset).getId();
    }

    @Test
    void setLogoAttachesAReadyImageAndExposesItsUrl() {
        UUID fileId = readyImage(userId, FilePurpose.PROFILE_IMAGE);

        profiles.setLogo(userId, fileId);

        assertThat(profiles.getByUser(userId).getImageFileId()).isEqualTo(fileId);
        assertThat(profiles.logoUrl(fileId)).isNotNull();
    }

    @Test
    void setLogoRejectsABannerFile() {
        UUID banner = readyImage(userId, FilePurpose.EVENT_BANNER);

        assertThatThrownBy(() -> profiles.setLogo(userId, banner)).isInstanceOf(ApiException.class);
    }

    @Test
    void setLogoRejectsAnotherUsersImage() {
        UUID othersImage = readyImage(user("Other"), FilePurpose.PROFILE_IMAGE);

        assertThatThrownBy(() -> profiles.setLogo(userId, othersImage))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearLogoRemovesTheReference() {
        UUID fileId = readyImage(userId, FilePurpose.PROFILE_IMAGE);
        profiles.setLogo(userId, fileId);

        profiles.clearLogo(userId);

        assertThat(profiles.getByUser(userId).getImageFileId()).isNull();
    }

    @Test
    void logoUrlIsNullWhenThereIsNone() {
        assertThat(profiles.logoUrl(null)).isNull();
    }
}
