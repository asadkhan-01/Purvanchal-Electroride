package in.purvanchal.electroride.inventory;

import in.purvanchal.electroride.security.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InventoryPolicy {
    public boolean canView(AppUser user, Vehicle vehicle) {
        if ("ADMIN".equals(user.role)) return true;
        if (user.branch == null) return false;
        return user.branch.equals(vehicle.branch)
            || ("IN_TRANSIT".equals(vehicle.status) && user.branch.equals(vehicle.transferDestination));
    }

    public void requireView(AppUser user, Vehicle vehicle) {
        if (!canView(user, vehicle)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
    }

    public void requireManager(AppUser user, String branch) {
        if ("ADMIN".equals(user.role)) return;
        if (!"BRANCH_MANAGER".equals(user.role) || !branch.equals(user.branch))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient inventory access");
    }

    public void requireReceiver(AppUser user, Vehicle vehicle) {
        requireManager(user, vehicle.transferDestination);
    }
}
