import java.util.*;
import lombok.Getter;
import lombok.Setter;
import java.security.*;
import java.io.Serializable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

public class UserDatabase implements Serializable {

    @Getter @Setter
    private Map<String, User> users;

    @Getter @Setter
    private Map<String, String> userPasswords;

    @Getter @Setter
    private Map<String, String> userHosts;

    @Getter @Setter
    private Map<String, String[]> userPermissions;

    @Getter @Setter
    private Map<String, Boolean> userLocks;

    @Getter @Setter
    private Map<String, String> userCreationDates;

    @Getter @Setter
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
        String username = user.getUsername();
        users.put(username, user);
        userPasswords.put(username, hashPassword(user.getPassword()));
        userHosts.put(username, user.getHost());
        userPermissions.put(username, user.getPermissions());
        userLocks.put(username, user.isLock());
        userCreationDates.put(username, user.getCreationDate());
        userCreatorIds.put(username, user.getCreator_id());
    }

    public void removeUser(String username) {
        users.remove(username);
        userPasswords.remove(username);
        userHosts.remove(username);
        userPermissions.remove(username);
        userLocks.remove(username);
        userCreationDates.remove(username);
        userCreatorIds.remove(username);
    }

    public void updateUser(String username, User updatedUser) {
        if (users.containsKey(username)) {
            removeUser(username);
            addUser(updatedUser);
        }
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public void modifyPermissions(String username, String[] newPermissions) {
        if (users.containsKey(username)) {
            User user = users.get(username);
            user.setPermissions(newPermissions);
            userPermissions.put(username, newPermissions);
        }
    }

    public void setUserLock(String username, boolean lock) {
        if (users.containsKey(username)) {
            User user = users.get(username);
            user.setLock(lock);
            userLocks.put(username, lock);
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return password;
        }
    }

    public User encryptUser(User user) throws Exception {
        Class<?> encryptedUserClass = new ByteBuddy()
                .subclass(User.class)
                .method(ElementMatchers.named("getPassword"))
                .intercept(FixedValue.value("ENCRYPTED"))
                .make()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        User encryptedUser = (User) encryptedUserClass.getDeclaredConstructor(
                String.class, String.class, String.class, String[].class,
                boolean.class, String.class, String.class
        ).newInstance(
                user.getUsername(), user.getPassword(), user.getHost(),
                user.getPermissions(), user.isLock(), user.getCreationDate(),
                user.getCreator_id()
        );

        return encryptedUser;
    }
}