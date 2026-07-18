package com.ticketing.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.security.TokenService;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

@Service
public class EmailVerificationService {

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final Clock clock;

    EmailVerificationService(AuthTokenRepository authTokenRepository, UserRepository userRepository,
            TokenService tokenService, AuditService auditService, Clock clock) {
        this.authTokenRepository = authTokenRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void verify(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, AuthErrorCodes.TOKEN_INVALID,
                        "This verification link is not valid."));

        Instant now = Instant.now(clock);
        if (token.getPurpose() != AuthTokenPurpose.EMAIL_VERIFICATION || !token.isUsable(now)) {
            throw new ApiException(HttpStatus.GONE, AuthErrorCodes.TOKEN_EXPIRED,
                    "This verification link has expired or was already used.");
        }

        token.markUsed(now);
        User user = userRepository.findById(token.getUserId()).orElseThrow(ResourceNotFoundException::new);
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
        }
        auditService.record(AuditActions.EMAIL_VERIFIED, user.getId(), null);
    }
}
