package in.purvanchal.electroride.billing;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Locale;

/**
 * Supplier details printed on invoices, from environment settings (never committed):
 * DEALER_GSTIN, DEALER_PHONE, DEALER_ADDRESS, and optional per-branch
 * DEALER_ADDRESS_<BRANCH> (e.g. DEALER_ADDRESS_GHAZIPUR) / DEALER_PHONE_<BRANCH>.
 */
@Component
public class DealerProfile {
    private final Environment env;

    public DealerProfile(Environment env) { this.env = env; }

    public record Details(String gstin, String address, String phone) {}

    public Details forBranch(String branch) {
        String key = branch == null ? "" : branch.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        return new Details(
            clean(env.getProperty("DEALER_GSTIN"), 15),
            clean(first(env.getProperty("DEALER_ADDRESS_" + key), env.getProperty("DEALER_ADDRESS")), 300),
            clean(first(env.getProperty("DEALER_PHONE_" + key), env.getProperty("DEALER_PHONE")), 20));
    }

    private static String first(String a, String b) { return a != null && !a.isBlank() ? a : b; }

    private static String clean(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
