package com.ticketing.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.config.AppProperties;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.user.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OutboxJobService outbox;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AppProperties props;

    RegistrationService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService, OutboxJobService outbox,
            IdGenerator idGenerator, Clock clock, AppProperties props) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.outbox = outbox;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.props = props;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, AuthErrorCodes.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists.");
        }

        User user = new User(idGenerator.newId(), normalizedEmail,
                passwordEncoder.encode(rawPassword), displayName.trim());
        user.addRole(Role.ATTENDEE);
        userRepository.save(user);

        issueVerificationEmail(user);
        return user;
    }

    private void issueVerificationEmail(User user) {
        String rawToken = tokenService.generateRawToken();
        Instant now = Instant.now(clock);

        AuthToken token = new AuthToken(idGenerator.newId(), user.getId(), tokenService.hash(rawToken),
                AuthTokenPurpose.EMAIL_VERIFICATION, now.plus(props.auth().emailVerificationTtl()));
        authTokenRepository.save(token);

        String link = props.baseUrl() + "/verify-email?token=" + rawToken;
        outbox.enqueue(JobTypes.EMAIL, JobTypes.emailVerificationKey(user.getId()),
                new EmailVerificationJob(user.getEmail(), user.getDisplayName(), link));

        if (props.email().logLinks()) {
            log.info("Email verification link for {}: {}", user.getEmail(), link);
        }
    }
}
