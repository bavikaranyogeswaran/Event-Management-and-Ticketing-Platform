package com.ticketing.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.config.AppProperties;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.user.User;
import com.ticketing.user.UserStatus;
import com.ticketing.user.UserRepository;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OutboxJobService outbox;
    private final UserSessionService userSessionService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AppProperties props;

    PasswordResetService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService, OutboxJobService outbox,
            UserSessionService userSessionService, IdGenerator idGenerator, Clock clock, AppProperties props) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.outbox = outbox;
        this.userSessionService = userSessionService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.props = props;
    }

    /** Always succeeds from the caller's view, whether or not the email exists, to avoid revealing accounts. */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .ifPresent(this::issueResetToken);
    }

    private void issueResetToken(User user) {
        String rawToken = tokenService.generateRawToken();
        Instant now = Instant.now(clock);

        AuthToken token = new AuthToken(idGenerator.newId(), user.getId(), tokenService.hash(rawToken),
                AuthTokenPurpose.PASSWORD_RESET, now.plus(props.auth().passwordResetTtl()));
        authTokenRepository.save(token);

        String link = props.baseUrl() + "/reset-password?token=" + rawToken;
        outbox.enqueue(JobTypes.EMAIL, JobTypes.passwordResetKey(token.getId()),
                new PasswordResetJob(user.getEmail(), user.getDisplayName(), link));

        if (props.email().logLinks()) {
            log.info("Password reset link for {}: {}", user.getEmail(), link);
        }
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        AuthToken token = authTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, AuthErrorCodes.TOKEN_INVALID,
                        "This reset link is not valid."));

        Instant now = Instant.now(clock);
        if (token.getPurpose() != AuthTokenPurpose.PASSWORD_RESET || !token.isUsable(now)) {
            throw new ApiException(HttpStatus.GONE, AuthErrorCodes.TOKEN_EXPIRED,
                    "This reset link has expired or was already used.");
        }

        token.markUsed(now);
        User user = userRepository.findById(token.getUserId()).orElseThrow(ResourceNotFoundException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // force everyone using the old password to log in again
        userSessionService.invalidateAll(user.getEmail());
    }
}
