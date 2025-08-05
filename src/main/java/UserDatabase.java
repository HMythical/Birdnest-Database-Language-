import java.util.*;
import lombok.Getter;
import lombok.Setter;
import java.security.*;
import java.io.Serializable;
import javax.crypto.SecretKey;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.GCMParameterSpec;

public class UserDatabase implements Serializable {
    @Getter @Setter private Map<String, User> users;
    @Getter @Setter private Map<String, String> userPasswords;
    @Getter @Setter private Map<String, String> userHosts;
    @Getter @Setter private Map<String, String[]> userPermissions;
    @Getter @Setter private Map<String, Boolean> userLocks;
    @Getter @Setter private Map<String, String> userCreationDates;
    @Getter @Setter private Map<String, String> userCreatorIds;
    @Getter @Setter private Map<String, byte[]> userSalts;
    private final Encryptor encryptor;
    private static final String MASTER_KEY = System.getenv("BDL_MASTER_KEY");
    private final ByteBuddy byteBuddy;
    private transient Class<?> secureUserClass;
    private final SecurityLogger securityLogger;
    private final SecurityOperations securityOps;
    private final Map<String, Long> failedLoginAttempts;
    private final Map<String, Long> lastActivityTime;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION = 30 * 60 * 1000; // 30 minutes
    private static final long INACTIVITY_THRESHOLD = 24 * 60 * 60 * 1000; // 24 hours

    public UserDatabase() {
        users = new HashMap<>();
        userPasswords = new HashMap<>();
        userHosts = new HashMap<>();
        userPermissions = new HashMap<>();
        userLocks = new HashMap<>();
        userCreationDates = new HashMap<>();
        userCreatorIds = new HashMap<>();
        userSalts = new HashMap<>();
        this.encryptor = new Encryptor();
        this.byteBuddy = new ByteBuddy();
        this.failedLoginAttempts = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
        try {
            this.securityLogger = new SecurityLogger(encryptor);
            this.securityOps = new SecurityOperations(this, encryptor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize security components", e);
        }
        initializeSecureUserClass();
    }

    private void initializeSecureUserClass() {
        try {
            // Create a secure user class with ByteBuddy that intercepts all sensitive operations
            secureUserClass = byteBuddy
                .subclass(User.class)
                .method(ElementMatchers.any())
                .intercept(MethodDelegation.to(new SecurityInterceptor()))
                .make()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize secure user class", e);
        }
    }

    // Security interceptor for ByteBuddy
    public class SecurityInterceptor {
        @RuntimeType
        public Object intercept(@This Object proxy, @Origin Method method, @AllArguments Object[] args) throws Exception {
            String methodName = method.getName();

            // Intercept getters/setters for sensitive data
            if (methodName.contains("Password") || methodName.contains("Host")) {
                if (methodName.startsWith("set")) {
                    return handleSensitiveDataSet(proxy, method, args);
                } else if (methodName.startsWith("get")) {
                    return handleSensitiveDataGet(proxy, method, args);
                }
            }

            // Log sensitive operations
            if (isSensitiveOperation(methodName)) {
                logSecurityEvent(methodName, proxy);
            }

            return method.invoke(proxy, args);
        }

        private Object handleSensitiveDataSet(Object proxy, Method method, Object[] args) throws Exception {
            if (args == null || args.length == 0) return null;

            String value = (String) args[0];
            byte[] salt = generateSalt();
            SecretKey key = encryptor.getAESKeyFromPassword(MASTER_KEY, salt);

            // Encrypt with AES-GCM
            String encrypted = encryptor.encryptGCM(value, key);
            args[0] = encrypted;

            return method.invoke(proxy, args);
        }

        private Object handleSensitiveDataGet(Object proxy, Method method, Object[] args) throws Exception {
            Object encryptedValue = method.invoke(proxy, args);
            if (encryptedValue == null) return null;

            User user = (User) proxy;
            byte[] salt = userSalts.get(user.getUsername());
            if (salt == null) return encryptedValue;

            SecretKey key = encryptor.getAESKeyFromPassword(MASTER_KEY, salt);
            return encryptor.decryptGCM((String) encryptedValue, key);
        }
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private boolean isSensitiveOperation(String methodName) {
        return methodName.contains("Password") ||
               methodName.contains("Permission") ||
               methodName.contains("Lock") ||
               methodName.contains("Auth");
    }

    private void logSecurityEvent(String operation, Object user) {
        // Implement secure logging here
    }

    public void addUser(User user) throws Exception {
        if (MASTER_KEY == null) {
            throw new SecurityException("Master encryption key not found");
        }

        String username = user.getUsername();
        byte[] salt = generateSalt();
        userSalts.put(username, salt);

        // Track user creation
        lastActivityTime.put(user.getUsername(), System.currentTimeMillis());
        securityLogger.logSecurityEvent(user.getUsername(), "USER_CREATED",
            "New user created with role: " + Arrays.toString(user.getPermissions()));

        // Create encrypted user using both ByteBuddy proxy and direct encryption
        User secureUser = createSecureProxy(user);
        User encryptedUser = encryptor.encryptUserData(secureUser, MASTER_KEY);

        users.put(user.getUsername(), encryptedUser);
        SecretKey userKey = encryptor.getAESKeyFromPassword(MASTER_KEY + username, salt);
        userPasswords.put(username, encryptor.encryptGCM(user.getPassword(), userKey));
        userHosts.put(username, encryptor.encryptGCM(user.getHost(), userKey));
        userPermissions.put(username, user.getPermissions());
        userLocks.put(username, user.isLock());
        userCreationDates.put(username, user.getCreationDate());
        userCreatorIds.put(username, user.getCreator_id());
    }

    private User createSecureProxy(User user) throws Exception {
        return (User) secureUserClass
            .getDeclaredConstructor(String.class, String.class, String.class,
                                  String[].class, boolean.class, String.class, String.class)
            .newInstance(user.getUsername(), user.getPassword(), user.getHost(),
                        user.getPermissions(), user.isLock(),
                        user.getCreationDate(), user.getCreator_id());
    }

    public User getUser(String username) {
        try {
            User user = users.get(username);
            if (user == null) {
                return null;
            }

            // Update last activity time
            lastActivityTime.put(username, System.currentTimeMillis());

            // Get the user's salt and create their key
            byte[] salt = userSalts.get(username);
            if (salt == null) {
                throw new SecurityException("No salt found for user: " + username);
            }
            SecretKey key = encryptor.getAESKeyFromPassword(MASTER_KEY, salt);

            // Create a new user with decrypted data
            User decryptedUser = new User(
                username,
                encryptor.decryptGCM(user.getPassword(), key),
                encryptor.decryptGCM(user.getHost(), key),
                userPermissions.get(username),
                userLocks.get(username),
                userCreationDates.get(username),
                userCreatorIds.get(username)
            );

            return decryptedUser;
        } catch (Exception e) {
            // Log the error and return null instead of throwing
            securityLogger.logSecurityEvent(username, "USER_ACCESS_ERROR",
                "Failed to retrieve user: " + e.getMessage());
            return null;
        }
    }

    public void updateUser(String username, User updatedUser) throws Exception {
        removeUser(username);
        addUser(updatedUser);
    }

    public boolean authenticateUser(String username, String password, Encryptor.AuthType authType) throws Exception {
        User user = getUser(username);
        if (user == null) return false;

        switch (authType) {
            case PASSWORD:
                String storedHash = userPasswords.get(username);
                return encryptor.verifyPassword(password, storedHash);

            case AUTH_KEY:
                return encryptor.verifyAuthKey(password, user.getPassword());

            case TWO_FACTOR:
                String phoneNumber = user.getHost(); // Using host field for phone number
                return encryptor.verify2FACode(phoneNumber, password);

            default:
                throw new IllegalArgumentException("Unsupported authentication type");
        }
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

    public CompletableFuture<List<User>> bulkAddUsers(List<User> users) {
        return securityOps.bulkAddUsers(users);
    }

    public CompletableFuture<List<User>> bulkUpdateUsers(List<User> users) {
        return securityOps.bulkUpdateUsers(users);
    }

    public boolean verifyUserCredentials(String username, String password, Encryptor.AuthType authType) throws Exception {
        if (isUserLockedOut(username)) {
            securityLogger.logSecurityEvent(username, "LOGIN_ATTEMPT_BLOCKED",
                "User is temporarily locked out due to too many failed attempts");
            return false;
        }

        boolean isValid = false;
        try {
            isValid = authenticateUser(username, password, authType);
            if (isValid) {
                resetFailedAttempts(username);
                updateLastActivity(username);
                securityLogger.logSecurityEvent(username, "LOGIN_SUCCESS",
                    "Successful login with auth type: " + authType);
            } else {
                recordFailedAttempt(username);
                securityLogger.logSecurityEvent(username, "LOGIN_FAILURE",
                    "Failed login attempt with auth type: " + authType);
            }
        } catch (Exception e) {
            securityLogger.logSecurityEvent(username, "LOGIN_ERROR",
                "Error during login: " + e.getMessage());
            throw e;
        }
        return isValid;
    }

    private void recordFailedAttempt(String username) {
        long attempts = failedLoginAttempts.getOrDefault(username, 0L) + 1;
        failedLoginAttempts.put(username, attempts);

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            setUserLock(username, true);
            securityLogger.logSecurityEvent(username, "ACCOUNT_LOCKED",
                "Account locked due to too many failed attempts");
        }
    }

    private boolean isUserLockedOut(String username) {
        Long lastFailure = failedLoginAttempts.get(username);
        if (lastFailure == null) return false;

        return failedLoginAttempts.getOrDefault(username, 0L) >= MAX_LOGIN_ATTEMPTS &&
               System.currentTimeMillis() - lastFailure < LOCKOUT_DURATION;
    }

    private void resetFailedAttempts(String username) {
        failedLoginAttempts.remove(username);
    }

    public void updateLastActivity(String username) {
        lastActivityTime.put(username, System.currentTimeMillis());
    }

    public void checkInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        lastActivityTime.forEach((username, lastActivity) -> {
            if (currentTime - lastActivity > INACTIVITY_THRESHOLD) {
                try {
                    setUserLock(username, true);
                    securityLogger.logSecurityEvent(username, "ACCOUNT_INACTIVE",
                        "Account locked due to inactivity");
                } catch (Exception e) {
                    securityLogger.logSecurityEvent(username, "LOCK_ERROR",
                        "Failed to lock inactive account: " + e.getMessage());
                }
            }
        });
    }

    public void rotateUserKeys() {
        securityOps.bulkRotateKeys();
    }

    public void shutdown() {
        securityOps.shutdown();
    }
}
