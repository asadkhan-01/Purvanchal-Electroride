package in.purvanchal.electroride.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact INR GST arithmetic. Rates and place-of-supply treatment need accountant review. */
public final class BillingMath {
    private BillingMath() {}

    public static final BigDecimal DEFAULT_GST_RATE = new BigDecimal("5.00");
    /** Income Tax Act s.269ST: cash of Rs 2 lakh or more for one transaction is not allowed. */
    public static final BigDecimal CASH_LIMIT = new BigDecimal("200000.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    public record Tax(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal roundOff, BigDecimal grandTotal) {}

    public static Tax tax(BigDecimal taxable, BigDecimal rate, boolean interState) {
        BigDecimal r = rate(rate);
        BigDecimal cgst = BigDecimal.ZERO.setScale(2), sgst = cgst, igst = cgst;
        if (interState) {
            igst = pct(taxable, r);
        } else {
            BigDecimal half = r.divide(TWO, 4, RoundingMode.UNNECESSARY);
            cgst = pct(taxable, half);
            sgst = cgst;
        }
        BigDecimal exact = taxable.add(cgst).add(sgst).add(igst);
        BigDecimal rounded = exact.setScale(0, RoundingMode.HALF_UP).setScale(2);
        return new Tax(cgst, sgst, igst, rounded.subtract(exact), rounded);
    }

    /** Pre-invoice payable estimate: approved quotation plus GST at the default rate. */
    public static BigDecimal estimatedTotal(BigDecimal taxable) {
        return tax(taxable, DEFAULT_GST_RATE, false).grandTotal();
    }

    public static BigDecimal amount(BigDecimal input) {
        if (input == null) throw bad("Amount is required");
        try {
            BigDecimal a = input.setScale(2, RoundingMode.UNNECESSARY);
            if (a.signum() <= 0 || a.precision() > 14) throw bad("Amount must be positive and fit 14 digits");
            return a;
        } catch (ArithmeticException ex) {
            throw bad("Use amounts with at most two decimal places");
        }
    }

    static BigDecimal rate(BigDecimal rate) {
        if (rate == null) return DEFAULT_GST_RATE;
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("28")) > 0 || rate.scale() > 2)
            throw bad("GST rate must be between 0 and 28 with at most two decimals");
        return rate.setScale(2);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
