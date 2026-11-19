package in.purvanchal.electroride.quotation;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class QuotationMathTest {
    @Test void exactDecimalCalculation() {
        assertThat(QuotationMath.subtotal(new BigDecimal("105000.25"), new BigDecimal("5000.10"),
            new BigDecimal("2250.25"), new BigDecimal("750.00")))
            .isEqualByComparingTo("103000.40");
    }
    @Test void rejectsDiscountExceedingVehiclePrice() {
        assertThatThrownBy(() -> QuotationMath.subtotal(
            new BigDecimal("1000"), new BigDecimal("1001"), BigDecimal.ZERO, BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void rejectsNegativeExtrasAndZeroSubtotal() {
        assertThatThrownBy(() -> QuotationMath.subtotal(
            new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QuotationMath.subtotal(
            new BigDecimal("1000"), new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void rejectsFractionalPaiseAndExcessivePrecision() {
        assertThatThrownBy(() -> QuotationMath.subtotal(
            new BigDecimal("1000.001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QuotationMath.subtotal(
            new BigDecimal("999999999999999"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
    }
}
