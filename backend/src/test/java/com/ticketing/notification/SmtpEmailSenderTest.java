package com.ticketing.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ticketing.shared.config.AppProperties;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against an in-memory SMTP server, so the adapter is exercised without touching real mail. */
class SmtpEmailSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private AppProperties propertiesWith(String from) {
        return new AppProperties(null, null, null,
                new AppProperties.Email(false, "smtp", from), null, null, null, null);
    }

    private EmailSender senderAt(int port) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(port);
        return new SmtpEmailSender(mailSender, propertiesWith("no-reply@ticketing.local"));
    }

    @Test
    void theMessageIsDeliveredWithItsSubjectSenderAndBody() throws Exception {
        senderAt(greenMail.getSmtp().getPort())
                .send(new EmailMessage("asha@example.com", "Your tickets", "Enjoy the show."));

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Your tickets");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("no-reply@ticketing.local");
        assertThat(GreenMailUtil.getBody(received[0])).contains("Enjoy the show.");
    }

    @Test
    void aDeliveryFailureIsRaisedSoThePipelineCanRetry() {
        // nothing listens on port 1, so the send cannot connect
        EmailSender sender = senderAt(1);

        assertThatThrownBy(() -> sender.send(new EmailMessage("a@b.c", "Subject", "Body")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a@b.c");
    }
}
