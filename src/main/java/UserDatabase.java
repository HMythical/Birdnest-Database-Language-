import java.util.*;
import lombok.Getter;
import lombok.Setter;
import java.security.*;
import java.io.Serializable;
import net.bytebuddy.*;

public class UserDatabase {

    @Getter @Setter
    private Map<String, User> users;
    private Map<String, String> userPasswords;
    private Map<String, String> userHosts;
    private Map<String, String[]> userPermissions;
    private Map<String, Boolean> userLocks;
    private Map<String, String> userCreationDates;
    private Map<String, String> userCreatorIds;


    public UserDatabase() {
        users = new HashMap<>();
        userPasswords = new HashMap<>();
        userHosts = new HashMap<>();
        userPermissions = new HashMap<>();
        userLocks = new HashMap<>();
        userCreationDates = new HashMap<>();
        userCreatorIds = new HashMap<>();
    }

    public void addUser(User user) {
        if (users.containsKey(user.getUsername())) {
            throw new IllegalArgumentException("User already exists");
        }
        users.put(user.getUsername(), user);
        userPasswords.put(user.getUsername(), user.getPassword());
        userHosts.put(user.getUsername(), user.getHost());
        userPermissions.put(user.getUsername(), user.getPermissions());
        userLocks.put(user.getUsername(), user.isLock());
        userCreationDates.put(user.getUsername(), user.getCreationDate());
        userCreatorIds.put(user.getUsername(), user.getCreator_id());
    }



    public User getUser(String username) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("User does not exist");
        }
        return users.get(username);
    }

    public void removeUser(String username) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("User does not exist");
        }
        users.remove(username);
        userPasswords.remove(username);
        userHosts.remove(username);
        userPermissions.remove(username);
        userLocks.remove(username);
        userCreationDates.remove(username);
        userCreatorIds.remove(username);
    }

    public boolean encryptUserData(String username, String key) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("User does not exist");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = key.getBytes();
            byte[] hashedKey = md.digest(keyBytes);

            // Encrypt user data using the hashed key (this is a placeholder for actual encryption logic)
            String encryptedPassword = Base64.getEncoder().encodeToString(hashedKey);
            userPasswords.put(username, encryptedPassword);

            // You can add more encryption logic for other fields if needed

            return true;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }
}
