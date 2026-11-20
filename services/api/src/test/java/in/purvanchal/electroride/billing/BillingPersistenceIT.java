package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.enquiry.Enquiry;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BillingPersistenceIT {

    @Autowired org.springframework.jdbc.core.JdbcTemplate testBranchesJdbc;

    /** The seeded branches are the real ones; tests use their own test branches. */
    @BeforeEach
    void testBranches() {
        for (String b : java.util.List.of("Varanasi", "Ghazipur"))
            testBranchesJdbc.update("INSERT INTO branches (name, invoice_address) VALUES (?, ?) ON CONFLICT DO NOTHING", b, "Test address, " + b);
    }
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("billing_it").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("DEALER_GSTIN", () -> "09TESTX0000X1Z0");
        p.add("DEALER_ADDRESS", () -> "Test Road, Varanasi");
        p.add("DEALER_ADDRESS_GHAZIPUR", () -> "Test Market, Ghazipur");
        p.add("DEALER_PHONE", () -> "9000000000");
    }

    static final String PW = "billing-test-password-123";
    @Autowired TestRestTemplate rest;
    @Autowired AppUserRepository users;
    @Autowired EnquiryRepository enquiries;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;

    @BeforeEach
    void users() {
        user("bill-admin", "ADMIN", null, "Admin");
        user("bill-manager", "BRANCH_MANAGER", "Varanasi", "Manager");
        user("bill-other", "BRANCH_MANAGER", "Ghazipur", "Other");
        user("bill-sales", "SALESPERSON", "Varanasi", "Asha");
        user("bill-sales2", "SALESPERSON", "Varanasi", "Ravi");
    }

    private void user(String username, String role, String branch, String name) {
        if (users.findByUsername(username).isPresent()) return;
        AppUser u = new AppUser();
        u.username = username; u.passwordHash = encoder.encode(PW);
        u.displayName = name; u.role = role; u.branch = branch;
        users.save(u);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map> call(String who, HttpMethod method, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(who, PW);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, h), Map.class);
    }
    private ResponseEntity<Map> post(String who, String path, Object body) { return call(who, HttpMethod.POST, path, body); }
    private ResponseEntity<Map> get(String who, String path) { return call(who, HttpMethod.GET, path, null); }

    /** Creates an enquiry for Asha, books a fresh vehicle and approves a Rs 95,000 quotation. */
    private long quotedBooking() { return quotedBooking("95000.00"); }

    private long quotedBooking(String price) {
        String chassis = "BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Vehicle v = new Vehicle();
        v.chassis = chassis; v.model = "City Glide"; v.variant = "Standard"; v.colour = "White"; v.branch = "Varanasi";
        v.motorSerial = "M" + chassis; v.batterySerials = new java.util.ArrayList<>(java.util.List.of("B1" + chassis, "B2" + chassis)); v.chargerSerial = "C" + chassis; v.controllerSerial = "K" + chassis;
        vehicles.save(v);
        Enquiry e = new Enquiry();
        e.customerName = "Billing Customer"; e.phone = "9000000500"; e.branch = "Varanasi";
        e.salesperson = "Asha"; e.source = "Walk-in"; e.interestedModel = "City Glide"; e.stage = "Interested";
        e = enquiries.save(e);
        long id = ((Number) post("bill-sales", "/api/v1/bookings", Map.of("enquiryId", e.id, "chassis", chassis)).getBody().get("id")).longValue();
        assertThat(post("bill-sales", "/api/v1/bookings/" + id + "/quotation",
            Map.of("vehiclePrice", price, "discount", "2000.00", "accessories", "1500.00", "otherCharges", "500.00"))
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("bill-manager", "/api/v1/bookings/" + id + "/quotation/approve", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private Map<String, Object> pay(String amount, String mode, String ref) {
        return ref == null ? Map.of("amount", amount, "mode", mode) : Map.of("amount", amount, "mode", mode, "reference", ref);
    }

    @Test
    void fullSaleFromAdvanceToDelivery() {
        long id = quotedBooking();
        String b = "/api/v1/bookings/" + id;

        // Estimated payable before invoice: 95,000 + 5% GST = 99,750.
        Map acc = get("bill-sales", b + "/account").getBody();
        assertThat(acc.get("estimated")).isEqualTo(true);
        assertThat(acc.get("payable").toString()).isEqualTo("99750.0");

        ResponseEntity<Map> advance = post("bill-sales", b + "/payments", pay("10000", "UPI", "UTR123456"));
        assertThat(advance.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(advance.getBody().get("receiptNumber").toString()).matches("RCT/VAR/\\d{4}-\\d{2}/\\d{4}");

        assertThat(post("bill-manager", b + "/invoice", Map.of("customerAddress", "Lanka, Varanasi")).getStatusCode())
            .as("cannot invoice a pending booking").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-manager", b + "/confirm", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("bill-sales", b + "/invoice", Map.of("customerAddress", "Lanka, Varanasi")).getStatusCode())
            .as("salesperson cannot invoice").isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> inv = post("bill-manager", b + "/invoice", Map.of("customerAddress", "Lanka, Varanasi"));
        assertThat(inv.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inv.getBody().get("invoiceNumber").toString()).matches("INV/VAR/\\d{4}-\\d{2}/\\d{4}");
        assertThat(inv.getBody().get("cgst").toString()).isEqualTo("2375.0");
        assertThat(inv.getBody().get("grandTotal").toString()).isEqualTo("99750.0");
        assertThat(post("bill-manager", b + "/invoice", Map.of("customerAddress", "x")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-manager", b + "/cancel", Map.of("reason", "x")).getStatusCode())
            .as("invoiced booking cannot be cancelled").isEqualTo(HttpStatus.CONFLICT);

        assertThat(post("bill-manager", b + "/deliver", null).getStatusCode()).as("balance due").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-sales", b + "/payments", pay("90000", "FINANCE", "LN-1")).getStatusCode())
            .as("finance needs company").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("bill-sales", b + "/payments", Map.of("amount", "80000", "mode", "FINANCE",
            "reference", "LN-778", "financeCompany", "Test Finance Ltd")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("bill-sales", b + "/payments", pay("9750.01", "UPI", "UTR2")).getStatusCode())
            .as("overpayment").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-sales", b + "/payments", pay("9750", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        acc = get("bill-sales", b + "/account").getBody();
        assertThat(acc.get("balance").toString()).isEqualTo("0.0");
        assertThat(post("bill-sales", b + "/deliver", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> delivered = post("bill-manager", b + "/deliver", null);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delivered.getBody().get("status")).isEqualTo("DELIVERED");
        Long vehicleId = ((Number) delivered.getBody().get("vehicleId")).longValue();
        assertThat(vehicles.findById(vehicleId).orElseThrow().status).isEqualTo("SOLD");
        assertThat(post("bill-sales", b + "/payments", pay("1", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map summary = get("bill-manager", "/api/v1/finance/summary").getBody();
        assertThat(((Number) summary.get("deliveredThisMonth")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(get("bill-other", "/api/v1/finance/summary").getBody().get("invoicesThisMonth")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void guardsOnPaymentsAndCancellation() {
        long id = quotedBooking();
        String b = "/api/v1/bookings/" + id;
        assertThat(post("bill-sales", b + "/payments", pay("5000", "UPI", null)).getStatusCode())
            .as("UPI needs reference").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("bill-sales", b + "/payments", pay("5000", "BITCOIN", "x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("bill-sales2", b + "/payments", pay("5000", "CASH", null)).getStatusCode())
            .as("other salesperson cannot see booking").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("bill-other", b + "/payments", pay("5000", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("bill-sales", b + "/payments", Map.of("amount", "100", "mode", "CASH", "receivedOn", "2999-01-01"))
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> cash = post("bill-sales", b + "/payments", pay("199999", "CASH", null));
        assertThat(cash.getStatusCode()).as("over balance").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-sales", b + "/payments", pay("50000", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        java.util.List<Map> receipts = (java.util.List<Map>) get("bill-sales", b + "/account").getBody().get("receipts");
        long receiptId = ((Number) receipts.get(0).get("id")).longValue();

        assertThat(post("bill-manager", b + "/cancel", Map.of("reason", "Customer withdrew")).getStatusCode())
            .as("live receipts block cancellation").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-sales", b + "/payments/" + receiptId + "/void", Map.of("reason", "refund")).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> voided = post("bill-manager", b + "/payments/" + receiptId + "/void", Map.of("reason", "Refunded in cash"));
        assertThat(voided.getBody().get("status")).isEqualTo("VOIDED");
        assertThat(post("bill-manager", b + "/payments/" + receiptId + "/void", Map.of("reason", "again")).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-manager", b + "/cancel", Map.of("reason", "Customer withdrew")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void interStateInvoiceWithGstin() {
        long id = quotedBooking();
        String b = "/api/v1/bookings/" + id;
        assertThat(post("bill-manager", b + "/confirm", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("bill-manager", b + "/invoice", Map.of("customerAddress", "Patna, Bihar", "gstRate", "18",
            "interState", true, "customerGstin", "10abcde1234f1z5")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map inv = get("bill-sales", b + "/invoice").getBody();
        assertThat(inv.get("taxType")).isEqualTo("IGST");
        assertThat(inv.get("igst").toString()).isEqualTo("17100.0");
        assertThat(inv.get("cgst").toString()).isEqualTo("0.0");
        assertThat(inv.get("grandTotal").toString()).isEqualTo("112100.0");
        assertThat(inv.get("customerGstin")).isEqualTo("10ABCDE1234F1Z5");
        assertThat(get("bill-other", b + "/invoice").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cumulativeCashMustStayBelowTwoLakh() {
        long id = quotedBooking("250000.00"); // taxable 2,50,000 -> payable 2,62,500
        String b = "/api/v1/bookings/" + id;
        assertThat(post("bill-sales", b + "/payments", pay("150000", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("bill-sales", b + "/payments", pay("50000", "CASH", null)).getStatusCode())
            .as("cumulative cash reaching 2,00,000").isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("bill-sales", b + "/payments", pay("49999", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("bill-sales", b + "/payments", pay("62501", "UPI", "UTR-77")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get("bill-sales", b + "/account").getBody().get("balance").toString()).isEqualTo("0.0");
    }

    @Test
    void invoiceCarriesKycSubsidyDealerAndAllBatteries() {
        long id = quotedBooking();
        String b = "/api/v1/bookings/" + id;
        assertThat(post("bill-manager", b + "/confirm", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> base = new java.util.HashMap<>(Map.of("customerAddress", "Test Village, Ghazipur",
            "customerRelation", "S/o", "customerGuardianName", "Test Father", "idType", "pan", "idNumber", "abcde1234f",
            "financier", "Test Finance Ltd", "subsidy", "5000"));
        base.put("aadhaarLast4", "123412341234");
        assertThat(post("bill-manager", b + "/invoice", base).getStatusCode()).as("full Aadhaar refused").isEqualTo(HttpStatus.BAD_REQUEST);
        base.put("aadhaarLast4", "1234");
        base.put("customerRelation", "X/o");
        assertThat(post("bill-manager", b + "/invoice", base).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        base.put("customerRelation", "S/o");
        ResponseEntity<Map> inv = post("bill-manager", b + "/invoice", base);
        assertThat(inv.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map body = inv.getBody();
        assertThat(body.get("grandTotal").toString()).isEqualTo("94750.0"); // 99,750 - 5,000 subsidy
        assertThat(body.get("subsidy").toString()).isEqualTo("5000.0");
        assertThat(body.get("idType")).isEqualTo("PAN");
        assertThat(body.get("idNumber")).isEqualTo("ABCDE1234F");
        assertThat(body.get("aadhaarLast4")).isEqualTo("1234");
        assertThat(body.get("dealerGstin")).isEqualTo("09TESTX0000X1Z0");
        assertThat(body.get("dealerAddress")).as("branch invoice address").isEqualTo("Test address, Varanasi");
        assertThat(body.get("batterySerials").toString()).contains(", ");
        assertThat(body.get("chargerSerial").toString()).startsWith("C");
        assertThat(get("bill-sales", b + "/account").getBody().get("payable").toString()).isEqualTo("94750.0");
    }

    private Map<String, Object> pastSale(String branch, String chassis, String paperNo, java.time.LocalDate invoiceDate,
                                         java.time.LocalDate deliveredOn, java.util.List<Map<String, Object>> payments, boolean newVehicle) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("branch", branch); body.put("salesperson", "Asha");
        body.put("customerName", "Past Customer"); body.put("customerPhone", "9000000601");
        body.put("customer", Map.of("customerAddress", "Test Village, Varanasi", "customerRelation", "S/o",
            "customerGuardianName", "Past Father", "aadhaarLast4", "6001", "financier", "Test Finance Ltd"));
        body.put("chassis", chassis);
        if (newVehicle) body.put("newVehicle", Map.of("model", "ZL-PRO", "variant", "Standard", "colour", "Red",
            "motorSerial", "PM-" + chassis, "batterySerials", java.util.List.of("PB1-" + chassis, "PB2-" + chassis),
            "chargerSerial", "PC-" + chassis, "controllerSerial", "PK-" + chassis));
        body.put("paperBookNumber", "16"); body.put("paperInvoiceNumber", paperNo);
        body.put("invoiceDate", invoiceDate.toString()); body.put("deliveredOn", deliveredOn.toString());
        body.put("vehiclePrice", "47619.05"); body.put("discount", "0"); body.put("accessories", "0"); body.put("otherCharges", "0");
        body.put("payments", payments);
        return body;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void managerRecordsBackdatedPaperSale() {
        java.time.LocalDate today = BillingService.today();
        java.time.LocalDate inv = today.minusDays(40), delivered = today.minusDays(38);
        String chassis = "PAST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String paperNo = String.valueOf(100 + new java.util.Random().nextInt(800));
        java.util.List<Map<String, Object>> pays = java.util.List.of(
            Map.of("amount", "10000", "mode", "UPI", "reference", "UTR-P1", "receivedOn", today.minusDays(45).toString()),
            Map.of("amount", "35000", "mode", "FINANCE", "reference", "LN-P1", "financeCompany", "Test Finance Ltd",
                "receivedOn", today.minusDays(36).toString()));
        Map<String, Object> body = pastSale("Varanasi", chassis, paperNo, inv, delivered, pays, true);

        assertThat(post("bill-sales", "/api/v1/past-sales", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("bill-other", "/api/v1/past-sales", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map future = new java.util.HashMap<>(body); future.put("invoiceDate", today.plusDays(1).toString());
        assertThat(post("bill-manager", "/api/v1/past-sales", future).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map early = new java.util.HashMap<>(body); early.put("deliveredOn", inv.minusDays(1).toString());
        assertThat(post("bill-manager", "/api/v1/past-sales", early).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> res = post("bill-manager", "/api/v1/past-sales", body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map invoice = (Map) res.getBody().get("invoice");
        assertThat(invoice.get("source")).isEqualTo("PAPER");
        assertThat(invoice.get("invoiceNumber")).isEqualTo("PAPER/VAR/16/" + paperNo);
        assertThat(invoice.get("invoiceDate")).isEqualTo(inv.toString());
        assertThat(invoice.get("grandTotal").toString()).isEqualTo("50000.0");
        assertThat(invoice.get("batterySerials").toString()).isEqualTo("PB1-" + chassis + ", PB2-" + chassis);
        Map booking = (Map) res.getBody().get("booking");
        assertThat(booking.get("status")).isEqualTo("DELIVERED");
        assertThat(booking.get("recordedFromPaper")).isEqualTo(true);
        long id = ((Number) booking.get("id")).longValue();
        assertThat(vehicles.findByChassis(chassis).orElseThrow().status).isEqualTo("SOLD");

        assertThat(post("bill-manager", "/api/v1/past-sales", pastSale("Varanasi", chassis + "X", paperNo, inv, delivered, java.util.List.of(), true))
            .getStatusCode()).as("same paper number twice").isEqualTo(HttpStatus.CONFLICT);

        // Rs 5,000 still due: the salesperson collects it now on the delivered sale.
        Map acc = get("bill-sales", "/api/v1/bookings/" + id + "/account").getBody();
        assertThat(acc.get("balance").toString()).isEqualTo("5000.0");
        assertThat(post("bill-sales", "/api/v1/bookings/" + id + "/payments", pay("5000", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("bill-sales", "/api/v1/bookings/" + id + "/payments", pay("1", "CASH", null)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Revenue lands in the month of the paper invoice; payments in their own months.
        String month = java.time.YearMonth.from(inv).toString();
        Map summary = get("bill-manager", "/api/v1/finance/summary?month=" + month).getBody();
        assertThat(new java.math.BigDecimal(summary.get("invoicedThisMonth").toString())).isGreaterThanOrEqualTo(new java.math.BigDecimal("50000"));

        // Existing stock path: an available vehicle in the branch is sold directly; a reserved one is refused.
        long reservedBooking = quotedBooking();
        String reservedChassis = vehicles.findById(((Number) get("bill-sales", "/api/v1/bookings/" + reservedBooking).getBody().get("vehicleId")).longValue()).orElseThrow().chassis;
        assertThat(post("bill-manager", "/api/v1/past-sales", pastSale("Varanasi", reservedChassis, paperNo + "9", inv, delivered, java.util.List.of(), false))
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void realBranchesAndAddressesAreSeededAndUnknownBranchesRefused() {
        ResponseEntity<java.util.List> res = rest.exchange("http://localhost:" + port + "/api/v1/branches", HttpMethod.GET,
            new HttpEntity<>(null, auth("bill-sales")), java.util.List.class);
        java.util.List<Map> list = res.getBody();
        assertThat(list).anySatisfy(b -> {
            assertThat(b.get("name")).isEqualTo("Atraoli-Ghazipur");
            assertThat(b.get("invoiceAddress")).isEqualTo("Atraoli (Maharana Pratap ke Bagal Me) Ghazipur");
        });
        assertThat(list).anySatisfy(b -> {
            assertThat(b.get("name")).isEqualTo("Naini-Prayagraj");
            assertThat(b.get("invoiceAddress")).isEqualTo("LIC Campus, Mewala Ki Bagiya Naini, Prayagraj");
        });
        assertThat(post("bill-admin", "/api/v1/inventory", Map.of("chassis", "NOBRANCH1", "model", "X", "variant", "Y", "branch", "Mau",
            "motorSerial", "NB-M", "batterySerials", java.util.List.of("NB-B"), "chargerSerial", "NB-C", "controllerSerial", "NB-K"))
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("bill-admin", "/api/v1/users", Map.of("username", "nobranch.user", "password", PW, "displayName", "No Branch",
            "role", "SALESPERSON", "branch", "Mau")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void salesReportFiltersByDateModelAndBranchScope() {
        java.time.LocalDate today = BillingService.today(), old = today.minusDays(200);
        String chassis = "RPT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String paperNo = "R" + (1000 + new java.util.Random().nextInt(8000));
        Map<String, Object> body = pastSale("Varanasi", chassis, paperNo, old, old,
            java.util.List.of(Map.of("amount", "20000", "mode", "UPI", "reference", "UTR-R1", "receivedOn", old.toString())), true);
        assertThat(post("bill-manager", "/api/v1/past-sales", body).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String range = "/api/v1/reports/sales?from=" + old + "&to=" + old;
        assertThat(get("bill-sales", range).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map report = get("bill-manager", range).getBody();
        java.util.List<Map> rows = (java.util.List<Map>) report.get("rows");
        Map row = rows.stream().filter(r -> chassis.equals(r.get("chassis"))).findFirst().orElseThrow();
        assertThat(row.get("invoiceNumber")).isEqualTo("16/" + paperNo);
        assertThat(row.get("model")).isEqualTo("ZL-PRO");
        assertThat(row.get("salesperson")).isEqualTo("Asha");
        assertThat(row.get("received").toString()).isEqualTo("20000.0");
        assertThat(row.get("balance").toString()).isEqualTo("30000.0");
        assertThat(row.get("paymentModes")).isEqualTo("UPI");
        assertThat(((Map) report.get("totals")).get("count")).isEqualTo(rows.size());

        assertThat(((java.util.List) get("bill-manager", range + "&model=zl-pro").getBody().get("rows"))).isNotEmpty();
        assertThat(((java.util.List) get("bill-manager", range + "&model=City%20Glide").getBody().get("rows"))).isEmpty();
        assertThat(((java.util.List) get("bill-manager", "/api/v1/reports/sales?from=" + old.minusDays(1) + "&to=" + old.minusDays(1))
            .getBody().get("rows"))).isEmpty();
        assertThat(((java.util.List) get("bill-other", range).getBody().get("rows"))).as("Ghazipur manager sees no Varanasi sales").isEmpty();
        assertThat(get("bill-other", range + "&branch=Varanasi").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(((java.util.List) get("bill-admin", range + "&branch=Varanasi").getBody().get("rows"))).isNotEmpty();
        assertThat(get("bill-admin", "/api/v1/reports/sales?from=" + today + "&to=" + old).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders auth(String who) { HttpHeaders h = new HttpHeaders(); h.setBasicAuth(who, PW); return h; }
}
