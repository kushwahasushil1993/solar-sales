package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solar.sales.support.TestSenders;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SPA reads XSRF-TOKEN and echoes it on POST/PUT/DELETE, so every response must be able to issue it.
 * Runs in its own context: Spring Security Test's csrf() helper (used by the other test classes) permanently
 * swaps the CSRF token repository in a shared context, which would hide the real cookie behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Import(TestSenders.class)
class CsrfCookieTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @Test
    void anonymousPagesReceiveReadableCsrfCookie() throws Exception {
        // the login/register pages need it before their first POST
        mvc.perform(get("/api/auth/providers"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void signedInRequestsReceiveCsrfCookie() throws Exception {
        AppUser u = users.save(new AppUser("cookie", "cookie-" + System.nanoTime() + "@test.local", Role.USER));
        mvc.perform(get("/api/auth/me").with(oauth2Login().oauth2User(new AuthUser(u.getId(), u.getEmail(), "cookie", Role.USER))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
