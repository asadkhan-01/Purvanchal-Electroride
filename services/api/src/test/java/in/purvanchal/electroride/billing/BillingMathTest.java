package in.purvanchal.electroride.billing;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingMathTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void intraStateSplitsGstEquallyAndRoundsToRupee() {
        BillingMath.Tax t = BillingMath.tax(d("95000.00"), d("5"), false);
        assertThat(t.cgst()).isEqualByComparingTo("2375.00");
        assertThat(t.sgst()).isEqualByComparingTo("2375.00");
        assertThat(t.igst()).isEqualByComparingTo("0");
        assertThat(t.roundOff()).isEqualByComparingTo("0");
        assertThat(t.grandTotal()).isEqualByComparingTo("99750.00");
    }

    @Test
    void interStateUsesIgstAndRoundOffLine() {
        BillingMath.Tax t = BillingMath.tax(d("84999.99"), d("5"), true);
        assertThat(t.igst()).isEqualByComparingTo("4250.00");
        assertThat(t.cgst()).isEqualByComparingTo("0");
        assertThat(t.roundOff()).isEqualByComparingTo("0.01");
        assertThat(t.grandTotal()).isEqualByComparingTo("89250.00");
    }

    @Test
    void halfPaisaRoundsUpPerComponent() {
        BillingMath.Tax t = BillingMath.tax(d("1000.10"), d("5"), false);
        assertThat(t.cgst()).isEqualByComparingTo("25.00"); // 25.0025 -> 25.00
        assertThat(t.grandTotal()).isEqualByComparingTo("1050.00");
        assertThat(t.roundOff()).isEqualByComparingTo("-0.10");
    }

    @Test
    void rejectsBadAmountsAndRates() {
        assertThatThrownBy(() -> BillingMath.amount(d("10.001"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> BillingMath.amount(d("0"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> BillingMath.rate(d("29"))).isInstanceOf(ResponseStatusException.class);
        assertThat(BillingMath.rate(null)).isEqualByComparingTo("5.00");
    }

    @Test
    void financialYearAndBranchCode() {
        assertThat(DocumentNumbers.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(DocumentNumbers.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
        assertThat(DocumentNumbers.financialYear(LocalDate.of(2099, 12, 1))).isEqualTo("2099-00");
        assertThat(DocumentNumbers.branchCode("Varanasi")).isEqualTo("VAR");
        assertThat(DocumentNumbers.branchCode("Mau")).isEqualTo("MAU");
    }
}
