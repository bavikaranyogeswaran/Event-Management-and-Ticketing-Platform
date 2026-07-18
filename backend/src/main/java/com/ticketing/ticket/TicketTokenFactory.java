package com.ticketing.ticket;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;
import com.ticketing.shared.security.TokenService;

/**
 * Produces a ticket's validation token by deriving it from the ticket id and a server-side secret,
 * so the token can be recomputed whenever a QR needs rendering while only its hash is ever stored.
 * A database dump alone is not enough to forge a ticket — the secret lives outside the database.
 */
@Component
class TicketTokenFactory {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final TokenService tokenService;

    TicketTokenFactory(AppProperties properties, TokenService tokenService) {
        String secret = properties.ticket().tokenSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.ticket.token-secret is not set; tickets cannot be issued without it "
                            + "(set APP_TICKET_TOKEN_SECRET in .env)");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.tokenService = tokenService;
    }

    /** The value carried inside the QR code; never logged and never returned by an API. */
    String rawToken(UUID ticketId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(ticketId.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(ALGORITHM + " unavailable", e);
        }
    }

    /** What the tickets table stores, and what check-in compares a scanned token against. */
    String tokenHash(UUID ticketId) {
        return tokenService.hash(rawToken(ticketId));
    }
}
