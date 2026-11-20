package in.purvanchal.electroride.branch;

import jakarta.persistence.*;

@Entity
@Table(name = "branches")
public class Branch {
    @Id public String name;
    @Column(name = "invoice_address", nullable = false) public String invoiceAddress;
    @Column(nullable = false) public boolean active = true;
}
