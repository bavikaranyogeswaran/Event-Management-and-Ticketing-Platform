package com.ticketing.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/**
 * Delivers over SMTP (Gmail by default). Active only when app.email.transport=smtp, so the app
 * runs without mail credentials otherwise. A send failure propagates so the pipeline can retry.
 */
@Component
@ConditionalOnProperty(name = "app.email.transport", havingValue = "smtp")
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.email().from();
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            // let the pipeline see the failure and schedule a retry rather than swallowing it
            throw new IllegalStateException("Could not send email to " + message.to(), e);
        }
    }
}
