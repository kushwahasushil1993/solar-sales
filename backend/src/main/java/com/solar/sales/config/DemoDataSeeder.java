package com.solar.sales.config;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.repository.SolarSaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fills an empty database with realistic-looking sales so the dashboard has something to show. */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] FIRST = {"Ramesh", "Priya", "Amit", "Sunita", "Vikram", "Neha", "Rahul", "Kavita",
            "Arjun", "Pooja", "Sanjay", "Meena", "Deepak", "Anjali", "Manoj"};
    private static final String[] LAST = {"Sharma", "Patel", "Verma", "Gupta", "Singh", "Iyer", "Reddy", "Nair",
            "Joshi", "Kulkarni"};
    private static final String[][] PLACES = {{"Pune", "Maharashtra"}, {"Mumbai", "Maharashtra"},
            {"Ahmedabad", "Gujarat"}, {"Jaipur", "Rajasthan"}, {"Bengaluru", "Karnataka"}, {"Lucknow", "Uttar Pradesh"},
            {"Hyderabad", "Telangana"}, {"Indore", "Madhya Pradesh"}};
    private static final String[] PANELS = {"Monocrystalline", "Monocrystalline", "Polycrystalline", "Bifacial", "Thin-Film"};
    private static final String[] INVERTERS = {"Luminous", "Growatt", "SMA", "Havells", "Sungrow"};
    private static final String[] SALESPEOPLE = {"Anita Sharma", "Rohit Mehta", "Farhan Ali", "Divya Rao"};
    private static final double[] CAPACITIES = {1, 2, 3, 3, 5, 5, 7.5, 10, 15, 25};

    private final SolarSaleRepository repository;

    public DemoDataSeeder(SolarSaleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now();
        List<SolarSale> sales = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            SolarSale s = new SolarSale();
            LocalDate purchased = today.minusDays(rnd.nextInt(360));
            String[] place = PLACES[rnd.nextInt(PLACES.length)];
            String first = FIRST[rnd.nextInt(FIRST.length)];
            String last = LAST[rnd.nextInt(LAST.length)];
            double kw = CAPACITIES[rnd.nextInt(CAPACITIES.length)];
            BigDecimal total = BigDecimal.valueOf(kw * (55_000 + rnd.nextInt(15_000))).setScale(0, RoundingMode.HALF_UP);

            s.setInvoiceNumber("DEMO-%04d".formatted(1 + i));
            s.setPurchaseDate(purchased);
            s.setCustomerName(first + " " + last);
            s.setPhone("9" + (100_000_000L + rnd.nextInt(899_999_999)));
            s.setEmail((first + "." + last).toLowerCase() + "@example.com");
            s.setCity(place[0]);
            s.setState(place[1]);
            s.setPanelType(PANELS[rnd.nextInt(PANELS.length)]);
            s.setSystemCapacityKw(BigDecimal.valueOf(kw));
            s.setPanelCount((int) Math.ceil(kw * 1000 / 540));
            s.setInverterBrand(INVERTERS[rnd.nextInt(INVERTERS.length)]);
            s.setTotalAmount(total);
            int pay = rnd.nextInt(10);
            s.setAmountPaid(pay < 6 ? total : pay < 9 ? total.multiply(BigDecimal.valueOf(0.4)).setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            long age = today.toEpochDay() - purchased.toEpochDay();
            if (age > 30) {
                s.setInstallationStatus(InstallationStatus.INSTALLED);
                s.setInstallationDate(purchased.plusDays(7 + rnd.nextInt(20)));
            } else if (age > 7) {
                s.setInstallationStatus(InstallationStatus.SCHEDULED);
                s.setInstallationDate(today.plusDays(1 + rnd.nextInt(14)));
            }
            s.setSalesperson(SALESPEOPLE[rnd.nextInt(SALESPEOPLE.length)]);
            s.setSource("DEMO");
            s.setCreatedBy(null, "Demo data");
            sales.add(s);
        }
        repository.saveAll(sales);
        log.info("Seeded {} demo solar sales (disable with app.seed-demo-data=false)", sales.size());
    }
}
