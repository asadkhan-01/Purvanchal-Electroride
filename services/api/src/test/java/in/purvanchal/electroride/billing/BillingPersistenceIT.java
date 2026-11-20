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
        assertThat(body.get("dealerAddress")).isEqualTo("Test Road, Varanasi");
        assertThat(body.get("batterySerials").toString()).contains(", ");
        assertThat(body.get("chargerSerial").toString()).startsWith("C");
        assertThat(get("bill-sales", b + "/account").getBody().get("payable").toString()).isEqualTo("94750.0");
    }
}
