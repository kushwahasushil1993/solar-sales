package com.solar.sales.task;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.task.TaskDtos.Inbox;
import com.solar.sales.task.TaskDtos.InboxItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications (the bell / Notifications tab). */
@Service
public class InboxService {

    private static final int PAGE = 50;

    private final InboxRepository inbox;
    private final AppUserRepository users;

    public InboxService(InboxRepository inbox, AppUserRepository users) {
        this.inbox = inbox;
        this.users = users;
    }

    @Transactional
    public void notifyUser(Long recipientId, InboxType type, String title, String message, SolarSale sale, String actor) {
        inbox.save(new InboxNotification(recipientId, type, title, message, sale == null ? null : sale.getId(), actor));
    }

    /** One notification per active admin. */
    @Transactional
    public void notifyAdmins(InboxType type, String title, String message, SolarSale sale, String actor) {
        for (AppUser admin : users.findByRoleAndEnabledTrue(Role.ADMIN)) {
            notifyUser(admin.getId(), type, title, message, sale, actor);
        }
    }

    @Transactional(readOnly = true)
    public Inbox list(AuthUser user) {
        return new Inbox(inbox.countByRecipientIdAndReadFalse(user.id()),
                inbox.findByRecipientIdOrderByCreatedAtDescIdDesc(user.id(), PageRequest.of(0, PAGE)).stream()
                        .map(InboxItem::from).toList());
    }

    @Transactional(readOnly = true)
    public long unread(AuthUser user) {
        return inbox.countByRecipientIdAndReadFalse(user.id());
    }

    /** Marks the user's own notification as read; someone else's is reported as not found. */
    @Transactional
    public InboxNotification markRead(Long id, AuthUser user) {
        InboxNotification n = inbox.findByIdAndRecipientId(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " not found"));
        n.markRead();
        return n;
    }

    @Transactional
    public void markAllRead(AuthUser user) {
        inbox.markAllRead(user.id());
    }
}
