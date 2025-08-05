import lombok.Getter;
import lombok.Setter;
import java.util.*;
import java.time.LocalDateTime;
import java.io.Serializable;

public class Tree implements Serializable {
    @Getter @Setter private String name;
    @Getter @Setter private String description;
    @Getter @Setter private String owner;
    @Getter @Setter private int maxNests;
    @Getter @Setter private int maxEggsPerNest;
    @Getter @Setter private int maxUsers;
    @Getter @Setter private String creationDate;
    @Getter @Setter private Branch rootBranch;
    @Getter @Setter private Map<String, Branch> branchIndex;
    @Getter @Setter private boolean isLocked;
    @Getter @Setter private String encryptionKey;
    @Getter @Setter private List<String> adminUsers;
    @Setter private Map<String, Nest> nests;  // Removed @Getter since we have a custom getNests() method

    public Tree(String name, String description, String owner, int maxNests,
                int maxEggsPerNest, int maxUsers, String encryptionKey) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.maxNests = maxNests;
        this.maxEggsPerNest = maxEggsPerNest;
        this.maxUsers = maxUsers;
        this.encryptionKey = encryptionKey;
        this.creationDate = LocalDateTime.now().toString();
        this.rootBranch = new Branch(name, owner, null);
        this.branchIndex = new HashMap<>();
        this.nests = new HashMap<>();
        this.adminUsers = new ArrayList<>();
        this.adminUsers.add(owner);
        this.isLocked = false;
    }

    public Branch createBranch(String path, String name, String owner) {
        if (isLocked) {
            throw new IllegalStateException("Tree is locked");
        }

        Branch parentBranch = getBranchByPath(path);
        if (parentBranch == null) {
            throw new IllegalArgumentException("Parent branch not found: " + path);
        }

        Branch newBranch = new Branch(name, owner, parentBranch);
        parentBranch.addSubBranch(newBranch);
        branchIndex.put(newBranch.getFullPath(), newBranch);
        return newBranch;
    }

    public Branch getBranchByPath(String path) {
        if (path.equals("/")) {
            return rootBranch;
        }
        return branchIndex.get(path);
    }

    public void addNest(String branchPath, Nest nest) {
        if (isLocked) {
            throw new IllegalStateException("Tree is locked");
        }

        Branch branch = getBranchByPath(branchPath);
        if (branch == null) {
            throw new IllegalArgumentException("Branch not found: " + branchPath);
        }

        branch.addNest(nest);
    }

    public void removeNest(String nestName) {
        if (isLocked) {
            throw new IllegalStateException("Tree is locked");
        }
        nests.remove(nestName);
    }

    public Nest getNest(String nestName) {
        return nests.get(nestName);
    }

    public Collection<Nest> getNests() {
        return rootBranch.getAllNests();
    }

    public boolean hasNest(String nestName) {
        return nests.containsKey(nestName);
    }

    public void lock() {
        this.isLocked = true;
        rootBranch.lock();
    }

    public void unlock(String adminUser) {
        if (!adminUsers.contains(adminUser)) {
            throw new SecurityException("Only admin users can unlock the tree");
        }
        this.isLocked = false;
        rootBranch.unlock();
    }

    public void addAdmin(String adminUser, String currentAdmin) {
        if (!adminUsers.contains(currentAdmin)) {
            throw new SecurityException("Only existing admins can add new admins");
        }
        if (!adminUsers.contains(adminUser)) {
            adminUsers.add(adminUser);
        }
    }

    public void removeAdmin(String adminUser, String currentAdmin) {
        if (!adminUsers.contains(currentAdmin)) {
            throw new SecurityException("Only existing admins can remove admins");
        }
        if (adminUsers.size() <= 1) {
            throw new IllegalStateException("Cannot remove last admin");
        }
        if (adminUser.equals(owner)) {
            throw new IllegalStateException("Cannot remove tree owner from admins");
        }
        adminUsers.remove(adminUser);
    }

    public boolean isAdmin(String username) {
        return adminUsers.contains(username);
    }

    public void validateNestOperations() {
        for (Nest nest : nests.values()) {
            if (nest.getEggs().size() > maxEggsPerNest) {
                throw new IllegalStateException("Nest " + nest.getName() + " exceeds maximum egg limit");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Tree[name=%s, nests=%d, owner=%s, locked=%b]",
            name, nests.size(), owner, isLocked);
    }
}
