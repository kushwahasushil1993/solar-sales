package com.solar.sales.task;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.entity.TaskState;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {
    }

    /** What an agent sees about an installation job: no prices or payments. */
    public record TaskResponse(Long id, String invoiceNumber, String customerName, String phone, String city,
                               String state, String panelType, BigDecimal systemCapacityKw, Integer panelCount,
                               String inverterBrand, LocalDate purchaseDate, InstallationStatus installationStatus,
                               LocalDate installationDate, TaskState taskState, Long agentId, String agentName,
                               LocalDateTime assignedAt, LocalDateTime acceptedAt, String installationUpdatedBy,
                               LocalDateTime installationUpdatedAt, LocalDateTime createdAt) {
        public static TaskResponse from(SolarSale s) {
            return new TaskResponse(s.getId(), s.getInvoiceNumber(), s.getCustomerName(), s.getPhone(), s.getCity(),
                    s.getState(), s.getPanelType(), s.getSystemCapacityKw(), s.getPanelCount(), s.getInverterBrand(),
                    s.getPurchaseDate(), s.getInstallationStatus(), s.getInstallationDate(), s.getTaskState(),
                    s.getAgentId(), s.getAgentName(), s.getAssignedAt(), s.getAcceptedAt(),
                    s.getInstallationUpdatedBy(), s.getInstallationUpdatedAt(), s.getCreatedAt());
        }
    }

    /** Admin dispatch board: one tab's records plus the counts for all tabs (installed jobs excluded). */
    public record Board(long open, long assigned, long accepted, List<TaskResponse> tasks) {
    }

    public record BulkAssignRequest(
            @jakarta.validation.constraints.NotEmpty(message = "Select at least one record") List<Long> saleIds,
            @NotNull(message = "Choose an agent") Long agentId) {
    }

    public record BulkAssignResult(int assigned, List<String> skipped) {
    }

    public record MyTasks(long awaitingAcceptance, long inHand, long completed, List<TaskResponse> tasks) {
    }

    public record AssignRequest(@NotNull(message = "Choose an agent") Long agentId) {
    }

    public record AgentSummary(Long id, String fullName, String email, String phone, boolean enabled,
                               long awaitingAcceptance, long inProgress, long completed, LocalDateTime lastActivityAt) {
    }

    public record ActivityResponse(Long id, ActivityAction action, String actorName, String actorRole, Long saleId,
                                   String invoiceNumber, String customerName, Long agentId, String agentName,
                                   String detail, LocalDateTime createdAt) {
        public static ActivityResponse from(Activity a) {
            return new ActivityResponse(a.getId(), a.getAction(), a.getActorName(), a.getActorRole(), a.getSaleId(),
                    a.getInvoiceNumber(), a.getCustomerName(), a.getAgentId(), a.getAgentName(), a.getDetail(),
                    a.getCreatedAt());
        }
    }

    public record InboxItem(Long id, InboxType type, String title, String message, Long saleId, String actorName,
                            boolean read, LocalDateTime createdAt) {
        public static InboxItem from(InboxNotification n) {
            return new InboxItem(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getSaleId(), n.getActorName(),
                    n.isRead(), n.getCreatedAt());
        }
    }

    public record Inbox(long unread, List<InboxItem> items) {
    }

    /** Result of opening a notification; for an assigned task, opening it accepts the task. */
    public record OpenResult(InboxItem notification, boolean accepted, TaskResponse task, String message) {
    }
}
