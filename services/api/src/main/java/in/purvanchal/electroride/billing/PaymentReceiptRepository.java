package in.purvanchal.electroride.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    List<PaymentReceipt> findByBookingIdOrderByIdDesc(Long bookingId);
    List<PaymentReceipt> findByBookingIdIn(Collection<Long> bookingIds);
    boolean existsByBookingIdAndStatus(Long bookingId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentReceipt r where r.id = :id")
    Optional<PaymentReceipt> lockById(@Param("id") Long id);
}
