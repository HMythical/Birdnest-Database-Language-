import java.util.*;
import java.util.concurrent.*;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import java.util.stream.Collectors;

public class SecurityOperations {
    private final UserDatabase userDatabase;
    private final Encryptor encryptor;
    private final SecurityLogger securityLogger;
    private final ExecutorService executorService;
    private static final int BATCH_SIZE = 100;

    public SecurityOperations(UserDatabase userDatabase, Encryptor encryptor) throws Exception {
        this.userDatabase = userDatabase;
        this.encryptor = encryptor;
        this.securityLogger = new SecurityLogger(encryptor);
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    // Bulk user operations
    public CompletableFuture<List<User>> bulkAddUsers(List<User> users) {
        return CompletableFuture.supplyAsync(() -> {
            List<User> addedUsers = new ArrayList<>();
            List<List<User>> batches = splitIntoBatches(users, BATCH_SIZE);

            for (List<User> batch : batches) {
                try {
                    List<User> processedBatch = processBatch(batch, this::addUserSecurely);
                    addedUsers.addAll(processedBatch);
                } catch (Exception e) {
                    securityLogger.logSecurityEvent("SYSTEM", "BULK_ADD_FAILURE",
                        "Failed to add batch of users: " + e.getMessage());
                }
            }

            return addedUsers;
        }, executorService);
    }

    public CompletableFuture<List<User>> bulkUpdateUsers(List<User> users) {
        return CompletableFuture.supplyAsync(() -> {
            List<User> updatedUsers = new ArrayList<>();
            List<List<User>> batches = splitIntoBatches(users, BATCH_SIZE);

            for (List<User> batch : batches) {
                try {
                    List<User> processedBatch = processBatch(batch, this::updateUserSecurely);
                    updatedUsers.addAll(processedBatch);
                } catch (Exception e) {
                    securityLogger.logSecurityEvent("SYSTEM", "BULK_UPDATE_FAILURE",
                        "Failed to update batch of users: " + e.getMessage());
                }
            }

            return updatedUsers;
        }, executorService);
    }

    // Secure batch processing
    private List<User> processBatch(List<User> batch, ProcessingFunction processor) {
        return batch.stream()
            .map(user -> {
                try {
                    return processor.process(user);
                } catch (Exception e) {
                    securityLogger.logSecurityEvent(user.getUsername(), "PROCESSING_FAILURE",
                        "Failed to process user: " + e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    // Function interface for processing
    @FunctionalInterface
    private interface ProcessingFunction {
        User process(User user) throws Exception;
    }

    // Secure user operations
    private User addUserSecurely(User user) throws Exception {
        // Generate unique salt for user
        byte[] userSalt = new byte[16];
        new SecureRandom().nextBytes(userSalt);

        // Create encryption key for user
        SecretKey userKey = encryptor.getAESKeyFromPassword(user.getUsername() + user.getCreationDate(), userSalt);

        // Encrypt sensitive data
        User encryptedUser = encryptor.encryptUserData(user, userKey.toString());
        userDatabase.addUser(encryptedUser);

        securityLogger.logSecurityEvent(user.getUsername(), "USER_ADDED",
            "User added successfully with encrypted data");

        return encryptedUser;
    }

    private User updateUserSecurely(User user) throws Exception {
        User existingUser = userDatabase.getUser(user.getUsername());
        if (existingUser == null) {
            throw new IllegalArgumentException("User not found: " + user.getUsername());
        }

        // Re-encrypt user data with existing salt
        User encryptedUser = encryptor.encryptUserData(user, existingUser.getPassword());
        userDatabase.updateUser(user.getUsername(), encryptedUser);

        securityLogger.logSecurityEvent(user.getUsername(), "USER_UPDATED",
            "User updated successfully with re-encrypted data");

        return encryptedUser;
    }

    // Utility methods
    private <T> List<List<T>> splitIntoBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            batches.add(items.subList(i, Math.min(items.size(), i + batchSize)));
        }
        return batches;
    }

    // Additional security features
    public void rotateUserKeys(String username) throws Exception {
        User user = userDatabase.getUser(username);
        if (user == null) return;

        // Generate new encryption key
        SecretKey newKey = encryptor.generateAESKey();

        // Re-encrypt user data with new key
        User reencryptedUser = encryptor.encryptUserData(user, newKey.toString());
        userDatabase.updateUser(username, reencryptedUser);

        securityLogger.logSecurityEvent(username, "KEY_ROTATION",
            "User encryption keys rotated successfully");
    }

    public void bulkRotateKeys() {
        userDatabase.getUsers().values().forEach(user -> {
            try {
                rotateUserKeys(user.getUsername());
            } catch (Exception e) {
                securityLogger.logSecurityEvent(user.getUsername(), "KEY_ROTATION_FAILURE",
                    "Failed to rotate user keys: " + e.getMessage());
            }
        });
    }

    public void lockInactiveUsers(long inactivityThreshold) {
        long currentTime = System.currentTimeMillis();
        userDatabase.getUsers().values().stream()
            .filter(user -> !user.isLock()) // Only check unlocked users
            .forEach(user -> {
                try {
                    // Check last activity timestamp (you'll need to add this field to User class)
                    if (currentTime - Long.parseLong(user.getCreationDate()) > inactivityThreshold) {
                        userDatabase.setUserLock(user.getUsername(), true);
                        securityLogger.logSecurityEvent(user.getUsername(), "USER_LOCKED",
                            "User locked due to inactivity");
                    }
                } catch (Exception e) {
                    securityLogger.logSecurityEvent(user.getUsername(), "LOCK_FAILURE",
                        "Failed to lock inactive user: " + e.getMessage());
                }
            });
    }

    // Cleanup resources
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        securityLogger.shutdown();
    }
}
