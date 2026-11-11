package com.solar.sales.notification;

import com.solar.sales.dto.NotificationResponse;
import com.solar.sales.dto.PageResponse;
import com.solar.sales.entity.Notification;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Renders, sends and records the customer emails of the sale workflow. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SolarSaleRepository sales;
    private final NotificationRepository notifications;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;

    public NotificationService(SolarSaleRepository sales, NotificationRepository notifications, EmailSender emailSender,
                               EmailTemplates templates, @Qualifier("notificationExecutor") TaskExecutor executor,
                               PlatformTransactionManager transactionManager) {
        this.sales = sales;
        this.notifications = notifications;
        this.emailSender = emailSender;
        this.templates = templates;
        this.executor = executor;
        // REQUIRES_NEW: the after-commit listener may run on the request thread, where the finished sale
        // transaction is still bound; joining it would silently drop these writes.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Workflow trigger: runs after the sale change commits, so a rolled-back change never emails anyone. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSaleEvent(SaleNotificationEvent event) {
        executor.execute(() -> {
            try {
                deliver(event.saleId(), event.type(), event.triggeredBy());
            } catch (RuntimeException e) {
                log.error("Could not process {} email for sale {}", event.type(), event.saleId(), e);
            }
        });
    }

    /** Admin action: send (or re-send) the invoice now and return the result. */
    public NotificationResponse sendNow(Long saleId, NotificationType type, String triggeredBy) {
        return NotificationResponse.from(deliver(saleId, type, triggeredBy));
    }

    public NotificationResponse resend(Long notificationId, String triggeredBy) {
        Notification original = notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + notificationId + " not found"));
        return sendNow(original.getSaleId(), original.getType(), triggeredBy);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> forSale(Long saleId) {
        return notifications.findBySaleIdOrderByCreatedAtDescIdDesc(saleId).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> recent(int page, int size) {
        return PageResponse.of(notifications.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100))), NotificationResponse::from);
    }

    private Notification deliver(Long saleId, NotificationType type, String triggeredBy) {
        // render inside a transaction (lazy-safe), send outside it, then record the outcome
        Prepared prepared = tx.execute(status -> {
            SolarSale sale = sales.findById(saleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale " + saleId + " not found"));
            EmailMessage message = templates.render(sale, type);
            return new Prepared(notifications.save(new Notification(sale, type, message.subject(), triggeredBy)), message);
        });
        Notification notification = prepared.notification();
        EmailMessage message = prepared.message();

        if (message.to() == null || message.to().isBlank()) {
            notification.markSkipped("The customer has no email address");
        } else if (!emailSender.isConfigured()) {
            sendQuietly(message);
            notification.markSkipped("Email sending is not configured (set MAIL_HOST); the email was only logged");
        } else {
            try {
                emailSender.send(message);
                notification.markSent();
            } catch (Exception e) {
                log.warn("Sending {} email for {} to {} failed: {}", type, notification.getInvoiceNumber(),
                        message.to(), e.getMessage());
                notification.markFailed(rootMessage(e));
            }
        }
        return tx.execute(status -> notifications.save(notification));
    }

    private void sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (Exception ignored) {
            // the logging sender never fails
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private record Prepared(Notification notification, EmailMessage message) {
    }
}
