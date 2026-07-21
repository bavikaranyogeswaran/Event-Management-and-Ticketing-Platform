package com.ticketing.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/**
 * The default sender: writes the email to the log instead of delivering it. Bodies can hold a
 * verification link or reset token, so they are hidden unless the dev explicitly opts in.
 */
@Component
@ConditionalOnProperty(name = "app.email.transport", havingValue = "log", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean revealBodies;

    LoggingEmailSender(AppProperties properties) {
        this.revealBodies = properties.email().logLinks();
    }

    @Override
    public void send(EmailMessage message) {
        if (revealBodies) {
            log.info("EMAIL to {} | {} | {}", message.to(), message.subject(), message.body());
        } else {
            // never log the body by default: it may contain a single-use token (NFR 3.6)
            log.info("EMAIL to {} | {} | (body hidden; set app.email.log-links=true to reveal)",
                    message.to(), message.subject());
        }
    }
}
