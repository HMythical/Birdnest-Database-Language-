import lombok.Getter;
import lombok.Setter;
import java.util.*;
import java.io.Serializable;
import java.time.LocalDateTime;

public class Branch implements Serializable {
    @Getter @Setter private String name;
    @Getter @Setter private Branch parentBranch;
    @Getter @Setter private List<Branch> subBranches;
    @Getter @Setter private List<Nest> nests;
    @Getter @Setter private String owner;
    @Getter @Setter private String[] permissions;
    @Getter @Setter private boolean isLocked;
    @Getter @Setter private String creationDate;
    @Getter @Setter private int maxNests;

    public Branch(String name, String owner, Branch parentBranch) {
        this.name = name;
        this.owner = owner;
        this.parentBranch = parentBranch;
        this.subBranches = new ArrayList<>();
        this.nests = new ArrayList<>();
        this.permissions = new String[]{"OWNER"};
        this.isLocked = false;
        this.creationDate = LocalDateTime.now().toString();
        this.maxNests = 100; // Default value, can be overridden
    }

    public void addSubBranch(Branch branch) {
        if (isLocked) {
            throw new IllegalStateException("Branch is locked");
        }
        branch.setParentBranch(this);
        subBranches.add(branch);
    }

    public void removeSubBranch(String branchName) {
        if (isLocked) {
            throw new IllegalStateException("Branch is locked");
        }
        subBranches.removeIf(b -> b.getName().equals(branchName));
    }

    public Branch getSubBranch(String branchName) {
        return subBranches.stream()
                         .filter(b -> b.getName().equals(branchName))
                         .findFirst()
                         .orElse(null);
    }

    public void addNest(Nest nest) {
        if (isLocked) {
            throw new IllegalStateException("Branch is locked");
        }
        if (nests.size() >= maxNests) {
            throw new IllegalStateException("Maximum number of nests reached for this branch");
        }
        nests.add(nest);
    }

    public void removeNest(String nestName) {
        if (isLocked) {
            throw new IllegalStateException("Branch is locked");
        }
        nests.removeIf(n -> n.getName().equals(nestName));
    }

    public Nest getNest(String nestName) {
        return nests.stream()
                   .filter(n -> n.getName().equals(nestName))
                   .findFirst()
                   .orElse(null);
    }

    public List<Nest> getAllNests() {
        List<Nest> allNests = new ArrayList<>(nests);
        for (Branch subBranch : subBranches) {
            allNests.addAll(subBranch.getAllNests());
        }
        return allNests;
    }

    public void lock() {
        this.isLocked = true;
        // Lock all sub-branches and nests
        subBranches.forEach(Branch::lock);
        nests.forEach(Nest::lock);
    }

    public void unlock() {
        this.isLocked = false;
        // Unlock all sub-branches and nests
        subBranches.forEach(Branch::unlock);
        nests.forEach(Nest::unlock);
    }

    public boolean hasPermission(String username, String permission) {
        if (username.equals(owner)) return true;
        return Arrays.asList(permissions).contains(permission);
    }

    public String getFullPath() {
        if (parentBranch == null) {
            return "/" + name;
        }
        return parentBranch.getFullPath() + "/" + name;
    }

    public List<Branch> getPathToRoot() {
        List<Branch> path = new ArrayList<>();
        Branch current = this;
        while (current != null) {
            path.add(0, current);
            current = current.getParentBranch();
        }
        return path;
    }

    @Override
    public String toString() {
        return String.format("Branch[name=%s, nests=%d, subBranches=%d, owner=%s, locked=%b]",
            name, nests.size(), subBranches.size(), owner, isLocked);
    }
}
