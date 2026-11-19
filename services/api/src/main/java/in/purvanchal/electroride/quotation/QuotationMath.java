package in.purvanchal.electroride.quotation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class QuotationMath {
    private QuotationMath() {}

    public static BigDecimal subtotal(BigDecimal vehiclePrice, BigDecimal discount,
                                      BigDecimal accessories, BigDecimal otherCharges) {
        BigDecimal price = exact(vehiclePrice);
        BigDecimal off = exact(discount);
        BigDecimal extras = exact(accessories);
        BigDecimal fees = exact(otherCharges);
        if (price.signum() <= 0 || off.signum() < 0 || extras.signum() < 0 || fees.signum() < 0
                || off.compareTo(price) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quotation amounts");
        }
        BigDecimal subtotal = price.subtract(off).add(extras).add(fees);
        if (subtotal.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtotal must be positive");
        // Deliberately excludes GST, registration, insurance and government subsidy computations.
        return subtotal;
    }

    private static BigDecimal exact(BigDecimal input) {
        if (input == null || input.precision() > 14)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required and must fit 14 digits");
        try {
            BigDecimal amount = input.setScale(2, RoundingMode.UNNECESSARY);
            if (amount.precision() > 14) throw new ArithmeticException("Amount too large");
            return amount;
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use amounts with at most two decimal places");
        }
    }
}
