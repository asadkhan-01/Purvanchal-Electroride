package in.purvanchal.electroride.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByBookingId(Long bookingId);
    boolean existsByBookingId(Long bookingId);
    List<Invoice> findByBookingIdIn(Collection<Long> bookingIds);
    List<Invoice> findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(java.time.LocalDate from, java.time.LocalDate to);
}
