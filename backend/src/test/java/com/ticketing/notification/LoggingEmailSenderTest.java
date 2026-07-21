package com.ticketing.notification;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.ticketing.shared.config.AppProperties;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the default sender never writes a token to the log unless a dev opts in. */
class LoggingEmailSenderTest {

    private static final EmailMessage WITH_TOKEN = new EmailMessage(
            "asha@example.com", "Verify your email",
            "Confirm here: https://app/verify?token=SECRET-TOKEN-VALUE");

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private EmailSender sender(boolean logLinks) {
        return new LoggingEmailSender(new AppProperties(null, null, null,
                new AppProperties.Email(logLinks, "log", "no-reply@ticketing.local"), null, null, null, null));
    }

    private String loggedLine() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    void theRecipientAndSubjectAreAlwaysLogged() {
        sender(false).send(WITH_TOKEN);

        assertThat(loggedLine())
                .contains("asha@example.com")
                .contains("Verify your email");
    }

    @Test
    void theBodyIsHiddenByDefaultSoTokensDoNotLeak() {
        sender(false).send(WITH_TOKEN);

        assertThat(loggedLine())
                .doesNotContain("SECRET-TOKEN-VALUE")
                .contains("body hidden");
    }

    @Test
    void theBodyIsRevealedOnlyWhenTheDevOptsIn() {
        sender(true).send(WITH_TOKEN);

        assertThat(loggedLine()).contains("SECRET-TOKEN-VALUE");
    }

    @Test
    void sendingNeverThrows() {
        // the pipeline treats a throw as a delivery failure, so the logging sender must not throw
        List.of(sender(false), sender(true)).forEach(s -> s.send(WITH_TOKEN));
    }
}
