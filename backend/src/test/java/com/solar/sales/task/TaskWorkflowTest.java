package com.solar.sales.task;

import com.jayway.jsonpath.JsonPath;
import com.solar.sales.dto.SaleRequest;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.Role;
import com.solar.sales.notification.NotificationService;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.SaleService;
import com.solar.sales.support.TestSenders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class TaskWorkflowTest {

    @Autowired MockMvc mvc;
    @Autowired SaleService saleService;
    @Autowired NotificationService customerEmails;
    @Autowired SolarSaleRepository sales;
    @Autowired AppUserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired ActivityRepository activities;
    @Autowired InboxRepository inbox;

    private AppUser admin;
    private AppUser ravi;
    private AppUser meena;
    private SaleResponse job;

    @BeforeEach
    void setUp() {
        inbox.deleteAll();
        activities.deleteAll();
        notifications.deleteAll();
        sales.deleteAll();
        users.deleteAll();
        admin = users.save(new AppUser("Anita Admin", "admin@test.local", Role.ADMIN));
        ravi = users.save(new AppUser("Ravi Agent", "ravi@test.local", Role.AGENT));
        meena = users.save(AppUser.withPhone("Meena Agent", "+919800000001"));
        meena.setRole(Role.AGENT);
        meena = users.save(meena);
        job = sale("INV-T1");
    }

    @Test
    void assignedTaskIsAcceptedByOpeningTheNotificationAndAdminIsTold() throws Exception {
        mvc.perform(post("/api/tasks/" + job.id() + "/assign").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + ravi.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskState").value("ASSIGNED"))
                .andExpect(jsonPath("$.agentName").value("Ravi Agent"));

        // agent: notification + task waiting for acceptance
        String inboxJson = mvc.perform(get("/api/inbox").with(as(ravi)))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].message", containsString("Kavita Joshi (INV-T1)")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/tasks/mine").with(as(ravi))).andExpect(jsonPath("$.awaitingAcceptance").value(1));

        Integer notificationId = JsonPath.read(inboxJson, "$.items[0].id");
        mvc.perform(post("/api/inbox/" + notificationId + "/open").with(as(ravi)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.task.taskState").value("ACCEPTED"))
                .andExpect(jsonPath("$.notification.read").value(true));
        mvc.perform(get("/api/inbox").with(as(ravi))).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/tasks/mine").with(as(ravi)))
                .andExpect(jsonPath("$.awaitingAcceptance").value(0))
                .andExpect(jsonPath("$.inHand").value(1));

        // admin: confirmation in their notifications, activity logged
        mvc.perform(get("/api/inbox").with(as(admin)))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].message").value("Ravi Agent accepted the installation for Kavita Joshi (INV-T1)."));
        mvc.perform(get("/api/agents/" + ravi.getId() + "/activity").with(as(admin)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].action").value("ASSIGNED"))
                .andExpect(jsonPath("$[1].actorName").value("Anita Admin"));
    }

    @Test
    void agentPicksUpOpenTaskAndWorksOnIt() throws Exception {
        mvc.perform(get("/api/tasks/available").with(as(ravi)))
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-T1"))
                .andExpect(jsonPath("$[0].totalAmount").doesNotExist()); // agents never see prices

        mvc.perform(post("/api/tasks/" + job.id() + "/pick").with(as(ravi)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskState").value("ACCEPTED"));
        mvc.perform(get("/api/tasks/available").with(as(meena))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/tasks/" + job.id() + "/pick").with(as(meena)).with(csrf()))
                .andExpect(status().isConflict());

        setStatus(ravi, "SCHEDULED", LocalDate.now().plusDays(2).toString()).andExpect(status().isOk());
        setStatus(ravi, "IN_PROGRESS", null).andExpect(status().isOk());
        setStatus(ravi, "INSTALLED", null)
                .andExpect(jsonPath("$.installationStatus").value("INSTALLED"))
                .andExpect(jsonPath("$.installationUpdatedBy").value("Ravi Agent"));

        mvc.perform(get("/api/tasks/mine").with(as(ravi)))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.tasks", hasSize(1)));
        mvc.perform(get("/api/tasks/mine").with(as(meena))).andExpect(jsonPath("$.tasks", hasSize(0)));

        // customer emails still go out, triggered by the agent
        assertThat(customerEmails.forSale(job.id())).extracting(n -> n.type() + " by " + n.triggeredBy())
                .contains("INSTALLATION_COMPLETED by Ravi Agent");
        // admins see every step
        mvc.perform(get("/api/inbox").with(as(admin)))
                .andExpect(jsonPath("$.items[0].title").value("Installation completed"))
                .andExpect(jsonPath("$.items[3].type").value("TASK_PICKED"));
        mvc.perform(get("/api/agents").with(as(admin)))
                .andExpect(jsonPath("$[?(@.fullName == 'Ravi Agent')].completed").value(1))
                .andExpect(jsonPath("$[?(@.fullName == 'Meena Agent')].completed").value(0));
        mvc.perform(get("/api/activity").with(as(admin)))
                .andExpect(jsonPath("$[0].detail", containsString("In progress → Installed")));
    }

    @Test
    void onlyTheOwnerCanUpdateAndOnlyAfterAccepting() throws Exception {
        assign(ravi).andExpect(status().isOk());
        setStatus(ravi, "SCHEDULED", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Accept the task before updating its status"));
        setStatus(meena, "SCHEDULED", null).andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/" + job.id() + "/accept").with(as(meena)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/tasks/" + job.id() + "/accept").with(as(ravi)).with(csrf())).andExpect(status().isOk());
        setStatus(ravi, "SCHEDULED", null).andExpect(status().isOk());
    }

    @Test
    void reassigningTellsBothAgentsAndUnassignReopensTheTask() throws Exception {
        assign(ravi);
        assign(meena).andExpect(jsonPath("$.agentName").value("Meena Agent"));
        mvc.perform(get("/api/inbox").with(as(ravi))).andExpect(jsonPath("$.items[0].type").value("TASK_UNASSIGNED"));
        mvc.perform(get("/api/inbox").with(as(meena))).andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"));

        // ravi opens his stale notification: not accepted, explained
        String json = mvc.perform(get("/api/inbox").with(as(ravi))).andReturn().getResponse().getContentAsString();
        Integer oldAssigned = JsonPath.read(json, "$.items[1].id");
        mvc.perform(post("/api/inbox/" + oldAssigned + "/open").with(as(ravi)).with(csrf()))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value("This task is not assigned to you"));

        mvc.perform(post("/api/tasks/" + job.id() + "/unassign").with(as(admin)).with(csrf()))
                .andExpect(jsonPath("$.taskState").value("OPEN"))
                .andExpect(jsonPath("$.agentName").doesNotExist());
        mvc.perform(get("/api/tasks/available").with(as(ravi))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void assignmentNeedsAnActiveAgent() throws Exception {
        AppUser sales = users.save(new AppUser("Sales Person", "sales@test.local", Role.USER));
        assign(sales).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.agentId").exists());
    }

    @Test
    void agentsOnlyReachTheirTaskPages() throws Exception {
        mvc.perform(get("/api/sales").with(as(ravi))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").with(as(ravi))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/sales/" + job.id() + "/installation").with(as(ravi)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationStatus\":\"SCHEDULED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/agents").with(as(ravi))).andExpect(status().isForbidden());
        assign(ravi);
        mvc.perform(post("/api/tasks/" + job.id() + "/assign").with(as(ravi)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + ravi.getId() + "}"))
                .andExpect(status().isForbidden());

        // admins and sales users don't use the agent endpoints
        mvc.perform(get("/api/tasks/mine").with(as(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales").with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void twoAgentsRacingForOneTaskOnlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> raviPick = () -> mvc.perform(post("/api/tasks/" + job.id() + "/pick").with(as(ravi)).with(csrf()))
                    .andReturn().getResponse().getStatus();
            Callable<Integer> meenaPick = () -> mvc.perform(post("/api/tasks/" + job.id() + "/pick").with(as(meena)).with(csrf()))
                    .andReturn().getResponse().getStatus();
            var a = pool.submit(raviPick);
            var b = pool.submit(meenaPick);
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdown();
        }
        assertThat(activities.findAll()).hasSize(1);
    }

    @Test
    void notificationsArePrivate() throws Exception {
        assign(ravi);
        String json = mvc.perform(get("/api/inbox").with(as(ravi))).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.items[0].id");
        mvc.perform(post("/api/inbox/" + id + "/open").with(as(meena)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/inbox/read-all").with(as(ravi)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/inbox").with(as(ravi))).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void dispatchBoardListsNewRecordsAndBulkAssigns() throws Exception {
        SaleResponse second = sale("INV-T2");
        SaleResponse third = sale("INV-T3");
        SaleResponse installed = sale("INV-T4");
        AuthUser a = new AuthUser(admin.getId(), admin.getEmail(), admin.getFullName(), Role.ADMIN);
        saleService.updateInstallation(installed.id(), new com.solar.sales.dto.InstallationUpdate(
                InstallationStatus.INSTALLED, LocalDate.now()), a, false);

        mvc.perform(get("/api/tasks/board").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(3))          // installed job left out
                .andExpect(jsonPath("$.assigned").value(0))
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.tasks[0].invoiceNumber").value("INV-T3")); // newest first
        mvc.perform(get("/api/tasks/board").param("search", "t2").with(as(admin)))
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].invoiceNumber").value("INV-T2"));

        mvc.perform(post("/api/tasks/assign").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleIds\":[%d,%d,%d,%d],\"agentId\":%d}".formatted(
                                job.id(), second.id(), installed.id(), job.id(), ravi.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(2))
                .andExpect(jsonPath("$.skipped", hasSize(1)))
                .andExpect(jsonPath("$.skipped[0]").value("INV-T4: already installed"));

        mvc.perform(get("/api/tasks/board").param("state", "ASSIGNED").with(as(admin)))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.assigned").value(2))
                .andExpect(jsonPath("$.tasks[0].agentName").value("Ravi Agent"));
        mvc.perform(get("/api/inbox").with(as(ravi))).andExpect(jsonPath("$.unread").value(2)); // one per task

        mvc.perform(post("/api/tasks/assign").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleIds\":[%d],\"agentId\":%d}".formatted(third.id(), admin.getId())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks/assign").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleIds\":[],\"agentId\":%d}".formatted(ravi.getId())))
                .andExpect(status().isBadRequest());

        // admin-only
        mvc.perform(get("/api/tasks/board").with(as(ravi))).andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/assign").with(as(ravi)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleIds\":[%d],\"agentId\":%d}".formatted(third.id(), ravi.getId())))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private SaleResponse sale(String invoice) {
        AuthUser a = new AuthUser(admin.getId(), admin.getEmail(), admin.getFullName(), Role.ADMIN);
        return saleService.create(new SaleRequest(invoice, LocalDate.now().minusDays(5), "Kavita Joshi", "9811122233",
                "kavita@example.com", "Pune", "Maharashtra", "Monocrystalline", new BigDecimal("5"), 10, "Luminous",
                new BigDecimal("300000"), BigDecimal.ZERO, InstallationStatus.PENDING, null, null), a, false);
    }

    private ResultActions assign(AppUser agent) throws Exception {
        return mvc.perform(post("/api/tasks/" + job.id() + "/assign").with(as(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + agent.getId() + "}"));
    }

    private ResultActions setStatus(AppUser agent, String status, String date) throws Exception {
        String body = date == null ? "{\"installationStatus\":\"%s\"}".formatted(status)
                : "{\"installationStatus\":\"%s\",\"installationDate\":\"%s\"}".formatted(status, date);
        return mvc.perform(patch("/api/tasks/" + job.id() + "/status").with(as(agent)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private RequestPostProcessor as(AppUser u) {
        AppUser now = users.findById(u.getId()).orElseThrow();
        return oauth2Login().oauth2User(new AuthUser(now.getId(), now.getEmail(), now.getFullName(), now.getRole(),
                now.getPasswordVersion()));
    }

}
