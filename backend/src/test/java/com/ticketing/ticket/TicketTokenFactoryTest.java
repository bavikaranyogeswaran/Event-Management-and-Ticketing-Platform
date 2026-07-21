package com.ticketing.ticket;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.shared.config.AppProperties;
import com.ticketing.shared.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTokenFactoryTest {

    private static final UUID TICKET = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_TICKET = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final TokenService tokenService = new TokenService();

    private TicketTokenFactory factoryWith(String secret) {
        AppProperties properties = new AppProperties(null, null, null, null, null,
                new AppProperties.Ticket("TCK", 2, 4, secret, 320, 2), null, null, null);
        return new TicketTokenFactory(properties, tokenService);
    }

    @Test
    void theSameTicketAlwaysDerivesTheSameToken() {
        TicketTokenFactory factory = factoryWith("a-secret");
        assertThat(factory.rawToken(TICKET)).isEqualTo(factory.rawToken(TICKET));
    }

    @Test
    void aRebuiltFactoryStillDerivesTheSameToken() {
        // this is the whole point: a QR can be re-rendered long after the ticket was issued
        assertThat(factoryWith("a-secret").rawToken(TICKET))
                .isEqualTo(factoryWith("a-secret").rawToken(TICKET));
    }

    @Test
    void differentTicketsGetDifferentTokens() {
        TicketTokenFactory factory = factoryWith("a-secret");
        assertThat(factory.rawToken(TICKET)).isNotEqualTo(factory.rawToken(OTHER_TICKET));
    }

    @Test
    void tokensCannotBeDerivedWithoutTheRightSecret() {
        assertThat(factoryWith("a-secret").rawToken(TICKET))
                .isNotEqualTo(factoryWith("another-secret").rawToken(TICKET));
    }

    @Test
    void storedHashMatchesWhatAScannedTokenWouldHashTo() {
        TicketTokenFactory factory = factoryWith("a-secret");
        String scanned = factory.rawToken(TICKET);
        assertThat(factory.tokenHash(TICKET)).isEqualTo(tokenService.hash(scanned));
    }

    @Test
    void storedHashIsNotTheTokenItself() {
        TicketTokenFactory factory = factoryWith("a-secret");
        assertThat(factory.tokenHash(TICKET)).isNotEqualTo(factory.rawToken(TICKET));
    }

    @Test
    void tokenIsSafeToPutInAQrCode() {
        assertThat(factoryWith("a-secret").rawToken(TICKET)).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void aMissingSecretStopsTheApplicationStarting() {
        assertThatThrownBy(() -> factoryWith("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ticket.token-secret");
    }
}
