package in.purvanchal.electroride.quotation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BookingQuotationRepository extends JpaRepository<BookingQuotation, Long> {
    Optional<BookingQuotation> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from BookingQuotation q where q.bookingId = :bookingId")
    Optional<BookingQuotation> lockByBookingId(@Param("bookingId") Long bookingId);
}
