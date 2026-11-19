package in.purvanchal.electroride.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {
    List<BookingEvent> findByBookingIdOrderByIdDesc(Long bookingId);
}
