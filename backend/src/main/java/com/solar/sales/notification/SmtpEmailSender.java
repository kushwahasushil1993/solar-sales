package com.solar.sales.notification;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    SmtpEmailSender(JavaMailSender mailSender, InternetAddress from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(EmailMessage message) throws Exception {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.text(), message.html());
        mailSender.send(mime);
    }
}
