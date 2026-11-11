package com.solar.sales.notification;

import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Mail is configured with app.mail.* (MAIL_HOST, MAIL_USERNAME, ...) rather than spring.mail.*, so an unset host
 * cleanly falls back to logging instead of failing to connect.
 */
@Configuration
@EnableConfigurationProperties(CompanyProperties.class)
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    EmailSender emailSender(@Value("${app.mail.host:}") String host,
                            @Value("${app.mail.port:587}") int port,
                            @Value("${app.mail.username:}") String username,
                            @Value("${app.mail.password:}") String password,
                            @Value("${app.mail.starttls:true}") boolean starttls,
                            @Value("${app.mail.from:}") String from,
                            CompanyProperties company) throws UnsupportedEncodingException {
        if (host.isBlank()) {
            log.warn("Customer emails are only logged: set MAIL_HOST (and MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM) "
                    + "to send them (see README)");
            return new LoggingEmailSender();
        }
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host.trim());
        mail.setPort(port);
        if (!username.isBlank()) {
            mail.setUsername(username.trim());
            mail.setPassword(password);
        }
        Properties props = mail.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        String fromAddress = from.isBlank() ? username.trim() : from.trim();
        return new SmtpEmailSender(mail, new InternetAddress(fromAddress, company.name(), "UTF-8"));
    }

    /** Emails are sent off the request thread; tests switch this to synchronous (app.notifications.async=false). */
    @Bean
    TaskExecutor notificationExecutor(@Value("${app.notifications.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("notify-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(4);
        return executor;
    }
}
