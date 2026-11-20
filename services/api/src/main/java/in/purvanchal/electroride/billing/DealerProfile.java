package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.branch.Branches;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Locale;

/**
 * Supplier details printed on invoices. GSTIN and mobile are the same for every branch
 * (app.dealer.* in application.yml, overridable by DEALER_GSTIN / DEALER_PHONE); the address is
 * the branch's invoice address from the branches table, overridable by DEALER_ADDRESS_<BRANCH>.
 */
@Component
public class DealerProfile {
    private final Environment env;
    private final Branches branches;

    public DealerProfile(Environment env, Branches branches) { this.env = env; this.branches = branches; }

    public record Details(String gstin, String address, String phone) {}

    public Details forBranch(String branch) {
        String key = branch == null ? "" : branch.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        return new Details(
            clean(first(env.getProperty("DEALER_GSTIN"), env.getProperty("app.dealer.gstin")), 15),
            clean(first(env.getProperty("DEALER_ADDRESS_" + key), branches.invoiceAddress(branch), env.getProperty("DEALER_ADDRESS")), 300),
            clean(first(env.getProperty("DEALER_PHONE_" + key), env.getProperty("DEALER_PHONE"), env.getProperty("app.dealer.phone")), 20));
    }

    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String clean(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
