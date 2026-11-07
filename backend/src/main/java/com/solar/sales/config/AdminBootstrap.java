package com.solar.sales.config;

import com.solar.sales.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Guarantees there is an admin to log in with; further admins are promoted from the Users page. */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserService userService,
                          @Value("${app.admin.name}") String name,
                          @Value("${app.admin.email}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userService = userService;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        boolean generated = password.isBlank();
        String effective = generated ? randomPassword() : password;
        if (!userService.ensureAdmin(name, email, effective)) {
            return;
        }
        if (generated) {
            // shown once, only when the account is first created; set ADMIN_PASSWORD to choose your own
            log.warn("""

                    ==================================================================
                      Created admin account: {}
                      Generated password:    {}
                      Save it now - it will not be shown again.
                    ==================================================================""", email, effective);
        } else {
            log.info("Created admin account {} (password from ADMIN_PASSWORD / app.admin.password)", email);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        // letters + digits guaranteed so it also satisfies the registration password rule
        return "Sa1" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
