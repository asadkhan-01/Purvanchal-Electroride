package in.purvanchal.electroride.security;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true) public String username;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(name = "display_name", nullable = false) public String displayName;
    @Column(nullable = false) public String role;
    public String branch;
    @Column(nullable = false) public boolean enabled = true;
}
