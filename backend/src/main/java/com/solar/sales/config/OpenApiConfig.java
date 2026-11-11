package com.solar.sales.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI solarSalesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SolarSales API")
                        .version("v1")
                        .description("""
                                Solar sales records, installation tracking and Excel purchase upload.

                                **Authentication:** sign in to the web app first (email + password, mobile OTP, Google \
                                or GitHub). \
                                Swagger UI then uses your browser session cookie, and sends the CSRF token for \
                                POST/PUT/DELETE automatically. Admin-only endpoints return 403 for sales users.

                                * Register: `POST /api/auth/register`
                                * Password sign-in: `POST /api/auth/login` (form fields `email`, `password`) → 204
                                * Mobile sign-in: `POST /api/auth/otp/request` (JSON), then `POST /api/auth/otp/verify` \
                                (form fields `phone`, `code`) → 204, or 200 `REGISTRATION_REQUIRED` for a new number, \
                                then `POST /api/auth/otp/register` (form fields `fullName`, `email`) → 204
                                * OAuth sign-in starts at `GET /oauth2/authorization/{google|github}`
                                * Sign out: `POST /api/auth/logout`"""))
                .components(new Components().addSecuritySchemes("session", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Session cookie set after signing in")))
                .addSecurityItem(new SecurityRequirement().addList("session"));
    }
}
