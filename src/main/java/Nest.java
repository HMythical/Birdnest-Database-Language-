import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.io.Serializable;

public class Nest implements Serializable {
    @Getter @Setter private String name;
    @Getter @Setter private String creationDate;
    @Getter @Setter private List<Egg> eggs;
    @Getter @Setter private List<Nest> subNests;
    @Getter @Setter private String owner;
    @Getter @Setter private String[] permissions;
    @Getter @Setter private boolean isLocked;
    @Getter @Setter private String status;

    public Nest(String name, String owner) {
        this.name = name;
        this.owner = owner;
        this.creationDate = LocalDateTime.now().toString();
        this.eggs = new ArrayList<>();
        this.subNests = new ArrayList<>();
        this.permissions = new String[]{"OWNER"};
        this.isLocked = false;
        this.status = "ACTIVE";
    }

    public void addEgg(Egg egg) {
        if (isLocked) {
            throw new IllegalStateException("Cannot modify locked nest");
        }
        eggs.add(egg);
    }

    public void removeEgg(String eggName) {
        if (isLocked) {
            throw new IllegalStateException("Cannot modify locked nest");
        }
        eggs.removeIf(egg -> egg.getName().equals(eggName));
    }

    public Egg getEgg(String eggName) {
        return eggs.stream()
                  .filter(egg -> egg.getName().equals(eggName))
                  .findFirst()
                  .orElse(null);
    }

    public void addSubNest(Nest nest) {
        if (isLocked) {
            throw new IllegalStateException("Cannot modify locked nest");
        }
        subNests.add(nest);
    }

    public void removeSubNest(String nestName) {
        if (isLocked) {
            throw new IllegalStateException("Cannot modify locked nest");
        }
        subNests.removeIf(nest -> nest.getName().equals(nestName));
    }

    public Nest getSubNest(String nestName) {
        return subNests.stream()
                      .filter(nest -> nest.getName().equals(nestName))
                      .findFirst()
                      .orElse(null);
    }

    public void lock() {
        this.isLocked = true;
        // Recursively lock all sub-nests
        subNests.forEach(Nest::lock);
    }

    public void unlock() {
        this.isLocked = false;
        // Recursively unlock all sub-nests
        subNests.forEach(Nest::unlock);
    }

    public boolean hasPermission(String username, String permission) {
        // Implementation would depend on your permission system
        // For now, owner has all permissions
        return username.equals(owner) ||
               (permissions != null && java.util.Arrays.asList(permissions).contains(permission));
    }

    public void modifyPermissions(String username, String[] newPermissions) {
        if (!this.owner.equals(username) &&
            !java.util.Arrays.asList(this.permissions).contains("ADMIN")) {
            throw new SecurityException("Insufficient permissions to modify nest permissions");
        }
        this.permissions = newPermissions;
    }

    @Override
    public String toString() {
        return String.format("Nest[name=%s, eggs=%d, subNests=%d, owner=%s, status=%s]",
            name, eggs.size(), subNests.size(), owner, status);
    }
}
