package com.solar.sales.task;

import com.solar.sales.dto.InstallationUpdate;
import com.solar.sales.security.AuthUser;
import com.solar.sales.task.TaskDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Access rules (agent vs admin endpoints) are in SecurityConfig. */
@Tag(name = "Tasks", description = "Installation tasks: agents pick up / accept and update them; admins assign")
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService tasks;
    private final InboxService inbox;

    public TaskController(TaskService tasks, InboxService inbox) {
        this.tasks = tasks;
        this.inbox = inbox;
    }

    // ---------------------------------------------------------------- agent

    @Operation(summary = "Open installation tasks anyone can pick up (agent)")
    @GetMapping("/tasks/available")
    public List<TaskResponse> available() {
        return tasks.available();
    }

    @Operation(summary = "My tasks: only those I picked up or was assigned (agent)")
    @GetMapping("/tasks/mine")
    public MyTasks mine(@AuthenticationPrincipal AuthUser agent) {
        return tasks.mine(agent);
    }

    @Operation(summary = "Pick up an open task (agent); 409 if someone was faster")
    @PostMapping("/tasks/{id}/pick")
    public TaskResponse pick(@PathVariable Long id, @AuthenticationPrincipal AuthUser agent) {
        return tasks.pick(id, agent);
    }

    @Operation(summary = "Accept a task assigned to me (agent)")
    @PostMapping("/tasks/{id}/accept")
    public TaskResponse accept(@PathVariable Long id, @AuthenticationPrincipal AuthUser agent) {
        return tasks.accept(id, agent);
    }

    @Operation(summary = "Update the installation status of my accepted task (agent)")
    @PatchMapping("/tasks/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody InstallationUpdate update,
                                     @Parameter(description = "Email the customer about the new status")
                                     @RequestParam(defaultValue = "true") boolean notify,
                                     @AuthenticationPrincipal AuthUser agent) {
        return tasks.updateStatus(id, update, agent, notify);
    }

    // ---------------------------------------------------------------- admin

    @Operation(summary = "Assign (or reassign) a task to an agent (admin); the agent accepts it from notifications")
    @PostMapping("/tasks/{id}/assign")
    public TaskResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest body,
                               @AuthenticationPrincipal AuthUser admin) {
        return tasks.assign(id, body.agentId(), admin);
    }

    @Operation(summary = "Dispatch board: records by task state, newest first (admin)",
            description = "state OPEN = new records without an agent; ASSIGNED = waiting for acceptance; ACCEPTED.")
    @GetMapping("/tasks/board")
    public Board board(@RequestParam(defaultValue = "OPEN") com.solar.sales.entity.TaskState state,
                       @RequestParam(required = false) String search) {
        return tasks.board(state, search);
    }

    @Operation(summary = "Assign several records to one agent (admin)")
    @PostMapping("/tasks/assign")
    public BulkAssignResult assignMany(@Valid @RequestBody BulkAssignRequest body, @AuthenticationPrincipal AuthUser admin) {
        return tasks.assignMany(body.saleIds(), body.agentId(), admin);
    }

    @Operation(summary = "Take a task away from its agent (admin)")
    @PostMapping("/tasks/{id}/unassign")
    public TaskResponse unassign(@PathVariable Long id, @AuthenticationPrincipal AuthUser admin) {
        return tasks.unassign(id, admin);
    }

    @Operation(summary = "All agents with their workload (admin)")
    @GetMapping("/agents")
    public List<AgentSummary> agents() {
        return tasks.agents();
    }

    @Operation(summary = "Everything done by or for one agent, newest first (admin)")
    @GetMapping("/agents/{id}/activity")
    public List<ActivityResponse> agentActivity(@PathVariable Long id) {
        return tasks.activity(id);
    }

    @Operation(summary = "Recent task activity of all agents (admin)")
    @GetMapping("/activity")
    public List<ActivityResponse> activity() {
        return tasks.activity(null);
    }

    // ---------------------------------------------------------------- notifications (any signed-in user)

    @Operation(summary = "My notifications (newest 50) and unread count")
    @GetMapping("/inbox")
    public Inbox inbox(@AuthenticationPrincipal AuthUser user) {
        return inbox.list(user);
    }

    @Operation(summary = "Open a notification: marks it read; for an assigned task (agents) it also accepts the task")
    @PostMapping("/inbox/{id}/open")
    public OpenResult open(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        InboxNotification n = inbox.markRead(id, user);
        if (n.getType() == InboxType.TASK_ASSIGNED && n.getSaleId() != null
                && user.role() == com.solar.sales.entity.Role.AGENT) {
            try {
                TaskResponse task = tasks.accept(n.getSaleId(), user);
                return new OpenResult(InboxItem.from(n), true, task, "Task accepted");
            } catch (RuntimeException e) {
                return new OpenResult(InboxItem.from(n), false, null, e.getMessage());
            }
        }
        return new OpenResult(InboxItem.from(n), false, null, null);
    }

    @Operation(summary = "Mark all my notifications as read")
    @PostMapping("/inbox/read-all")
    public void readAll(@AuthenticationPrincipal AuthUser user) {
        inbox.markAllRead(user);
    }
}
