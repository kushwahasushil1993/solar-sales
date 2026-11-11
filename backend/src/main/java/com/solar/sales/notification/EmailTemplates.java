package com.solar.sales.notification;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.entity.SolarSale;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds the subject, HTML (templates/email/notification.html) and plain-text body for each workflow email. */
@Component
public class EmailTemplates {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final ITemplateEngine engine;
    private final CompanyProperties company;

    public EmailTemplates(ITemplateEngine engine, CompanyProperties company) {
        this.engine = engine;
        this.company = company;
    }

    public record Step(String label, String when, boolean done) {
    }

    public record Line(String label, String value) {
    }

    public EmailMessage render(SolarSale sale, NotificationType type) {
        String inv = sale.getInvoiceNumber();
        String kw = sale.getSystemCapacityKw().stripTrailingZeros().toPlainString() + " kW";
        String firstName = sale.getCustomerName().trim().split("\\s+")[0];
        String installDate = date(sale.getInstallationDate());
        boolean fullyPaid = sale.getBalanceDue().signum() == 0;

        String subject;
        String heading;
        String intro;
        String next;
        boolean showInvoice;
        switch (type) {
            case INVOICE -> {
                subject = "Invoice " + inv + " from " + company.name();
                heading = "Thank you for your order";
                intro = "Thank you for choosing " + company.name() + " for your " + kw
                        + " solar power system. Your invoice is below.";
                // the invoice can be (re)sent at any stage, so "next" follows the current installation status
                next = switch (sale.getInstallationStatus()) {
                    case PENDING -> "What happens next: our team will contact you to schedule the installation.";
                    case SCHEDULED -> sale.getInstallationDate() != null
                            ? "Your installation is scheduled for " + installDate + "."
                            : "We are scheduling your installation and will confirm the date shortly.";
                    case IN_PROGRESS -> "Your installation is in progress.";
                    case INSTALLED -> fullyPaid ? "Your installation is complete and this invoice is fully paid. Thank you!"
                            : "Your installation is complete. Balance due: " + money(sale.getBalanceDue()) + ".";
                };
                showInvoice = true;
            }
            case INSTALLATION_SCHEDULED -> {
                subject = "Your solar installation is scheduled (" + inv + ")";
                heading = "Installation scheduled";
                intro = sale.getInstallationDate() != null
                        ? "Good news! The installation of your " + kw + " solar system is scheduled for " + installDate + "."
                        : "Good news! We are scheduling the installation of your " + kw
                          + " solar system and will confirm the exact date shortly.";
                next = "Please make sure our team can access the roof and installation site on the day.";
                showInvoice = false;
            }
            case INSTALLATION_IN_PROGRESS -> {
                subject = "Installation in progress (" + inv + ")";
                heading = "Installation in progress";
                intro = "Our team has started installing your " + kw + " solar system.";
                next = "We will let you know as soon as the installation is complete.";
                showInvoice = false;
            }
            case PAYMENT_RECEIVED -> {
                subject = "Payment received for " + inv;
                heading = "Payment received, thank you";
                intro = "We have received your payment for invoice " + inv + ". Amount paid so far: "
                        + money(sale.getAmountPaid()) + ". Your updated invoice is below.";
                next = fullyPaid ? "Your invoice is now fully paid."
                        : "Balance due: " + money(sale.getBalanceDue()) + ".";
                showInvoice = true;
            }
            case INVOICE_UPDATED -> {
                subject = "Updated invoice " + inv + " from " + company.name();
                heading = "Your invoice has been updated";
                intro = "We have updated the amounts on your invoice " + inv + ". The latest invoice is below.";
                next = fullyPaid ? "Your invoice is fully paid."
                        : "Balance due: " + money(sale.getBalanceDue()) + ".";
                showInvoice = true;
            }
            case INSTALLATION_COMPLETED -> {
                subject = "Your solar system is installed (" + inv + ")";
                heading = "Installation completed";
                intro = "Your " + kw + " solar system " + (sale.getInstallationDate() != null
                        ? "was installed on " + installDate : "has been installed")
                        + ". Thank you for going solar with " + company.name() + "!";
                next = fullyPaid ? "Your invoice is fully paid. Enjoy your clean energy!"
                        : "A balance of " + money(sale.getBalanceDue()) + " is due. Please contact us to complete the payment.";
                showInvoice = false; // the invoice is only sent with the order confirmation
            }
            default -> throw new IllegalArgumentException("Unknown notification type " + type);
        }

        // only the stages already reached; upcoming ones are not listed
        List<Step> steps = steps(sale).stream().filter(Step::done).toList();
        List<Line> billTo = Stream.of(
                        new Line("Name", sale.getCustomerName()),
                        new Line("Location", join(", ", sale.getCity(), sale.getState())),
                        new Line("Phone", sale.getPhone()),
                        new Line("Email", sale.getEmail()))
                .filter(l -> l.value() != null && !l.value().isBlank())
                .toList();
        String description = "Solar PV system, " + kw
                + (sale.getPanelType() != null ? " " + sale.getPanelType() : "")
                + (sale.getPanelCount() != null ? " (" + sale.getPanelCount() + " panels)" : "")
                + (sale.getInverterBrand() != null ? ", " + sale.getInverterBrand() + " inverter" : "");

        Context ctx = new Context(INDIA);
        ctx.setVariable("company", company);
        ctx.setVariable("heading", heading);
        ctx.setVariable("greeting", "Dear " + firstName + ",");
        ctx.setVariable("intro", intro);
        ctx.setVariable("next", next);
        ctx.setVariable("showInvoice", showInvoice);
        ctx.setVariable("invoiceNumber", inv);
        ctx.setVariable("invoiceDate", date(sale.getPurchaseDate()));
        ctx.setVariable("billTo", billTo);
        ctx.setVariable("description", description);
        ctx.setVariable("total", money(sale.getTotalAmount()));
        ctx.setVariable("paid", money(sale.getAmountPaid()));
        ctx.setVariable("balance", money(sale.getBalanceDue()));
        ctx.setVariable("fullyPaid", fullyPaid);
        ctx.setVariable("paymentStatus", title(sale.getPaymentStatus().name()));
        ctx.setVariable("steps", steps);
        String html = engine.process("email/notification", ctx);

        String text = String.join("\n",
                heading.toUpperCase(INDIA), "",
                "Dear " + firstName + ",", "", intro, "",
                showInvoice
                        ? "Invoice " + inv + " (" + date(sale.getPurchaseDate()) + ")\n" + description + "\nTotal: "
                          + money(sale.getTotalAmount()) + "\nPaid: " + money(sale.getAmountPaid()) + "\nBalance due: "
                          + money(sale.getBalanceDue()) + "\n"
                        : "",
                "Progress:",
                steps.stream().map(s -> "  ✓ " + s.label() + (s.when().isEmpty() ? "" : " - " + s.when()))
                        .collect(Collectors.joining("\n")),
                "", next, "",
                company.name() + Stream.of(company.phone(), company.email(), company.website())
                        .filter(v -> v != null && !v.isBlank()).map(v -> " | " + v).collect(Collectors.joining()));
        return new EmailMessage(sale.getEmail(), subject, html, text);
    }

    private static List<Step> steps(SolarSale sale) {
        InstallationStatus status = sale.getInstallationStatus();
        int reached = status.ordinal();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Order placed", date(sale.getPurchaseDate()), true));
        steps.add(new Step("Installation scheduled",
                status == InstallationStatus.SCHEDULED && sale.getInstallationDate() != null
                        ? "for " + date(sale.getInstallationDate()) : "",
                reached >= InstallationStatus.SCHEDULED.ordinal()));
        steps.add(new Step("Installation in progress", "", reached >= InstallationStatus.IN_PROGRESS.ordinal()));
        steps.add(new Step("Installed",
                status == InstallationStatus.INSTALLED ? date(sale.getInstallationDate()) : "",
                status == InstallationStatus.INSTALLED));
        return steps;
    }

    /** ₹ with Indian digit grouping (₹3,25,000); java.text formats only support a single group size. */
    static String money(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        String[] parts = value.abs().toPlainString().split("\\.");
        String digits = parts[0];
        StringBuilder grouped = new StringBuilder();
        if (digits.length() <= 3) {
            grouped.append(digits);
        } else {
            String head = digits.substring(0, digits.length() - 3);
            for (int i = head.length(); i > 0; i -= 2) {
                grouped.insert(0, head.substring(Math.max(0, i - 2), i));
                if (i > 2) {
                    grouped.insert(0, ',');
                }
            }
            grouped.append(',').append(digits.substring(digits.length() - 3));
        }
        return (value.signum() < 0 ? "-" : "") + "₹" + grouped + ("00".equals(parts[1]) ? "" : "." + parts[1]);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String join(String sep, String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(sep));
    }
}
