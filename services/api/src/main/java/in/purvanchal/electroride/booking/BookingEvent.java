package in.purvanchal.electroride.booking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_events")
public class BookingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "booking_id", nullable = false) public Long bookingId;
    @Column(nullable = false) public String event;
    @Column(name = "actor_username", nullable = false) public String actorUsername;
    public String details;
    @Column(name = "occurred_at", insertable = false, updatable = false) public OffsetDateTime occurredAt;
}
