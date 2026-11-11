package com.solar.sales.task;

import com.solar.sales.dto.InstallationUpdate;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.Role;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.entity.TaskState;
import com.solar.sales.exception.BadRequestException;
import com.solar.sales.exception.ConflictException;
import com.solar.sales.exception.ForbiddenOperationException;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.SaleService;
import com.solar.sales.task.TaskDtos.ActivityResponse;
import com.solar.sales.task.TaskDtos.AgentSummary;
import com.solar.sales.task.TaskDtos.MyTasks;
import com.solar.sales.task.TaskDtos.TaskResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Installation tasks: admins assign them to agents (who accept from their notifications), agents can also pick up
 * open ones, and then update the installation status of their own tasks. Every step is logged as an Activity and
 * notified in-app to the other side.
 */
@Service
public class TaskService {

    private static final int ACTIVITY_LIMIT = 200;

    private final SolarSaleRepository sales;
    private final AppUserRepository users;
    private final ActivityRepository activities;
    private final InboxService inbox;
    private final SaleService saleService;

    public TaskService(SolarSaleRepository sales, AppUserRepository users, ActivityRepository activities,
                       InboxService inbox, SaleService saleService) {
        this.sales = sales;
        this.users = users;
        this.activities = activities;
        this.inbox = inbox;
        this.saleService = saleService;
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public TaskResponse assign(Long saleId, Long agentId, AuthUser admin) {
        SolarSale sale = find(saleId);
        if (sale.getInstallationStatus() == InstallationStatus.INSTALLED) {
            throw new BadRequestException("saleId", "This installation is already completed");
        }
        AppUser agent = users.findById(agentId)
                .filter(u -> u.getRole() == Role.AGENT && u.isEnabled())
                .orElseThrow(() -> new BadRequestException("agentId", "Choose an active agent"));
        if (Objects.equals(sale.getAgentId(), agent.getId())) {
            return TaskResponse.from(sale); // already theirs
        }
        Long previousId = sale.getAgentId();
        String previousName = sale.getAgentName();
        sale.assignTo(agent.getId(), agent.getFullName());

        log(admin, ActivityAction.ASSIGNED, sale, agent.getId(), agent.getFullName(),
                previousId == null ? null : "Reassigned from " + previousName);
        inbox.notifyUser(agent.getId(), InboxType.TASK_ASSIGNED, "New task assigned",
                admin.fullName() + " assigned you the installation for " + describe(sale) + ". Open this to accept it.",
                sale, admin.fullName());
        if (previousId != null) {
            inbox.notifyUser(previousId, InboxType.TASK_UNASSIGNED, "Task reassigned",
                    "The installation for " + describe(sale) + " was given to " + agent.getFullName() + ".",
                    sale, admin.fullName());
        }
        return TaskResponse.from(sale);
    }

    /** Dispatch board for admins (installed jobs are left out). */
    @Transactional(readOnly = true)
    public TaskDtos.Board board(TaskState state, String search) {
        String q = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        List<TaskResponse> tasks = sales.board(state, q, PageRequest.of(0, 500)).stream().map(TaskResponse::from).toList();
        return new TaskDtos.Board(
                sales.countByTaskStateAndInstallationStatusNot(TaskState.OPEN, InstallationStatus.INSTALLED),
                sales.countByTaskStateAndInstallationStatusNot(TaskState.ASSIGNED, InstallationStatus.INSTALLED),
                sales.countByTaskStateAndInstallationStatusNot(TaskState.ACCEPTED, InstallationStatus.INSTALLED),
                tasks);
    }

    /** Assigns several records to one agent; records that can't be assigned are reported, not fatal. */
    @Transactional
    public TaskDtos.BulkAssignResult assignMany(List<Long> saleIds, Long agentId, AuthUser admin) {
        users.findById(agentId).filter(u -> u.getRole() == Role.AGENT && u.isEnabled())
                .orElseThrow(() -> new BadRequestException("agentId", "Choose an active agent"));
        int assigned = 0;
        List<String> skipped = new java.util.ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(saleIds)) {
            SolarSale sale = sales.findById(id).orElse(null);
            if (sale == null) {
                skipped.add("#" + id + ": not found");
            } else if (sale.getInstallationStatus() == InstallationStatus.INSTALLED) {
                skipped.add(sale.getInvoiceNumber() + ": already installed");
            } else if (Objects.equals(sale.getAgentId(), agentId)) {
                skipped.add(sale.getInvoiceNumber() + ": already with this agent");
            } else {
                assign(id, agentId, admin);
                assigned++;
            }
        }
        return new TaskDtos.BulkAssignResult(assigned, skipped);
    }

    @Transactional
    public TaskResponse unassign(Long saleId, AuthUser admin) {
        SolarSale sale = find(saleId);
        if (sale.getAgentId() == null) {
            return TaskResponse.from(sale);
        }
        Long agentId = sale.getAgentId();
        String agentName = sale.getAgentName();
        sale.unassign();
        log(admin, ActivityAction.UNASSIGNED, sale, agentId, agentName, null);
        inbox.notifyUser(agentId, InboxType.TASK_UNASSIGNED, "Task removed",
                admin.fullName() + " removed you from the installation for " + describe(sale) + ".", sale, admin.fullName());
        return TaskResponse.from(sale);
    }

    @Transactional(readOnly = true)
    public List<AgentSummary> agents() {
        Map<Long, long[]> counts = new HashMap<>(); // [awaiting, inProgress, completed]
        for (Object[] row : sales.agentWorkload()) {
            long[] c = counts.computeIfAbsent((Long) row[0], k -> new long[3]);
            TaskState state = (TaskState) row[1];
            boolean installed = row[2] == InstallationStatus.INSTALLED;
            long n = ((Number) row[3]).longValue();
            if (installed) {
                c[2] += n;
            } else if (state == TaskState.ASSIGNED) {
                c[0] += n;
            } else {
                c[1] += n;
            }
        }
        Map<Long, LocalDateTime> last = new HashMap<>();
        for (Object[] row : activities.lastActivityPerAgent()) {
            last.put((Long) row[0], (LocalDateTime) row[1]);
        }
        return users.findByRoleOrderByFullNameAsc(Role.AGENT).stream()
                .map(u -> {
                    long[] c = counts.getOrDefault(u.getId(), new long[3]);
                    return new AgentSummary(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(),
                            c[0], c[1], c[2], last.get(u.getId()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> activity(Long agentId) {
        PageRequest page = PageRequest.of(0, ACTIVITY_LIMIT);
        return (agentId == null
                ? activities.findAllByOrderByCreatedAtDescIdDesc(page)
                : activities.findByAgentIdOrderByCreatedAtDescIdDesc(agentId, page))
                .stream().map(ActivityResponse::from).toList();
    }

    // ---------------------------------------------------------------- agent

    @Transactional(readOnly = true)
    public List<TaskResponse> available() {
        return sales.findByTaskStateAndInstallationStatusNotOrderByPurchaseDateAscIdAsc(TaskState.OPEN,
                InstallationStatus.INSTALLED).stream().map(TaskResponse::from).toList();
    }

    /** Only the tasks this agent picked up or was given. */
    @Transactional(readOnly = true)
    public MyTasks mine(AuthUser agent) {
        List<TaskResponse> tasks = sales.findByAgentIdOrderByAssignedAtDescIdDesc(agent.id()).stream()
                .map(TaskResponse::from).toList();
        long completed = tasks.stream().filter(t -> t.installationStatus() == InstallationStatus.INSTALLED).count();
        long awaiting = tasks.stream().filter(t -> t.taskState() == TaskState.ASSIGNED
                && t.installationStatus() != InstallationStatus.INSTALLED).count();
        return new MyTasks(awaiting, tasks.size() - completed - awaiting, completed, tasks);
    }

    /** First come, first served. */
    @Transactional
    public TaskResponse pick(Long saleId, AuthUser agent) {
        find(saleId);
        if (sales.pickIfOpen(saleId, agent.id(), agent.fullName(), LocalDateTime.now()) == 0) {
            throw new ConflictException("This task is no longer available. Someone else may have picked it up.");
        }
        SolarSale sale = find(saleId);
        log(agent, ActivityAction.PICKED, sale, agent.id(), agent.fullName(), null);
        inbox.notifyAdmins(InboxType.TASK_PICKED, "Task picked up",
                agent.fullName() + " picked up the installation for " + describe(sale) + ".", sale, agent.fullName());
        return TaskResponse.from(sale);
    }

    @Transactional
    public TaskResponse accept(Long saleId, AuthUser agent) {
        SolarSale sale = find(saleId);
        requireOwner(sale, agent);
        if (sale.getTaskState() == TaskState.ACCEPTED) {
            return TaskResponse.from(sale); // already accepted
        }
        sale.markAccepted();
        log(agent, ActivityAction.ACCEPTED, sale, agent.id(), agent.fullName(), null);
        inbox.notifyAdmins(InboxType.TASK_ACCEPTED, "Task accepted",
                agent.fullName() + " accepted the installation for " + describe(sale) + ".", sale, agent.fullName());
        return TaskResponse.from(sale);
    }

    /** The agent updates the installation status of their own accepted task (the customer is emailed as usual). */
    @Transactional
    public TaskResponse updateStatus(Long saleId, InstallationUpdate update, AuthUser agent, boolean notifyCustomer) {
        SolarSale sale = find(saleId);
        requireOwner(sale, agent);
        if (sale.getTaskState() != TaskState.ACCEPTED) {
            throw new ConflictException("Accept the task before updating its status");
        }
        InstallationStatus before = sale.getInstallationStatus();
        LocalDate dateBefore = sale.getInstallationDate();
        saleService.updateInstallation(saleId, update, agent, notifyCustomer);
        sale = find(saleId);

        if (sale.getInstallationStatus() != before || !Objects.equals(sale.getInstallationDate(), dateBefore)) {
            String detail = label(before) + " → " + label(sale.getInstallationStatus())
                    + (sale.getInstallationDate() != null ? " (" + sale.getInstallationDate() + ")" : "");
            log(agent, ActivityAction.STATUS_CHANGED, sale, agent.id(), agent.fullName(), detail);
            inbox.notifyAdmins(InboxType.TASK_STATUS_UPDATED,
                    sale.getInstallationStatus() == InstallationStatus.INSTALLED ? "Installation completed" : "Task updated",
                    agent.fullName() + " updated " + describe(sale) + ": " + detail + ".", sale, agent.fullName());
        }
        return TaskResponse.from(sale);
    }

    // ---------------------------------------------------------------- helpers

    private void requireOwner(SolarSale sale, AuthUser agent) {
        if (!Objects.equals(sale.getAgentId(), agent.id())) {
            throw new ForbiddenOperationException("This task is not assigned to you");
        }
    }

    private void log(AuthUser actor, ActivityAction action, SolarSale sale, Long agentId, String agentName, String detail) {
        activities.save(new Activity(actor.id(), actor.fullName(), actor.role().name(), action, sale.getId(),
                sale.getInvoiceNumber(), sale.getCustomerName(), agentId, agentName, detail));
    }

    private SolarSale find(Long id) {
        return sales.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
    }

    private static String describe(SolarSale sale) {
        return sale.getCustomerName() + " (" + sale.getInvoiceNumber() + ")";
    }

    private static String label(InstallationStatus status) {
        String s = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
