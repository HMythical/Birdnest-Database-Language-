import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Encryptor {
    // Twilio credentials should be set via environment variables
    private static final String TWILIO_ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
    private static final String TWILIO_AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");
    private static final String TWILIO_PHONE_NUMBER = System.getenv("TWILIO_PHONE_NUMBER");

    // For storing pending 2FA verifications
    private static final Map<String, String> pendingVerifications = new HashMap<>();

    public enum AuthType {
        PASSWORD,
        AUTH_KEY,
        TWO_FACTOR
    }

    // Password handling
    public String hashPassword(String password) {
        try {
            // Generate a random salt
            byte[] salt = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);

            // Configure Argon2 parameters
            Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withParallelism(4)
                .withMemoryAsKB(65536) // 64MB
                .withIterations(3);

            // Generate the hash
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());
            byte[] hash = new byte[32];
            generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);

            // Combine salt and hash
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    public boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            byte[] salt = Arrays.copyOfRange(combined, 0, 16);
            byte[] hash = Arrays.copyOfRange(combined, 16, combined.length);

            Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withParallelism(4)
                .withMemoryAsKB(65536)
                .withIterations(3);

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());
            byte[] checkHash = new byte[32];
            generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), checkHash);

            return Arrays.equals(hash, checkHash);
        } catch (Exception e) {
            throw new RuntimeException("Error verifying password", e);
        }
    }

    // Auth key handling
    public String generateAuthKey() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Error generating auth key", e);
        }
    }

    public boolean verifyAuthKey(String providedKey, String storedKey) {
        return providedKey.equals(storedKey);
    }

    // 2FA handling
    public String initiate2FA(String phoneNumber) {
        try {
            // Initialize Twilio
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);

            // Generate a 6-digit code
            String verificationCode = generateVerificationCode();

            // Store the code
            pendingVerifications.put(phoneNumber, verificationCode);

            // Send SMS
            Message message = Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(TWILIO_PHONE_NUMBER),
                "Your BDL verification code is: " + verificationCode
            ).create();

            return verificationCode;
        } catch (Exception e) {
            throw new RuntimeException("Error initiating 2FA", e);
        }
    }

    public boolean verify2FACode(String phoneNumber, String code) {
        String storedCode = pendingVerifications.get(phoneNumber);
        if (storedCode != null && storedCode.equals(code)) {
            pendingVerifications.remove(phoneNumber); // Clean up after successful verification
            return true;
        }
        return false;
    }

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    // Utility method to determine if a phone number is valid
    public boolean isValidPhoneNumber(String phoneNumber) {
        // Basic validation - should be enhanced based on requirements
        return phoneNumber.matches("\\+?\\d{10,15}");
    }

    // AES encryption constants
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int SALT_LENGTH = 16;
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_ALGO = "AES/GCM/NoPadding";
    private static final String AES_CBC_ALGO = "AES/CBC/PKCS5Padding";

    // Generate a secure AES key
    public SecretKey generateAESKey() throws NoSuchAlgorithmException {
        javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    // Generate a key from password using PBKDF2
    public SecretKey getAESKeyFromPassword(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            65536,
            AES_KEY_SIZE
        );
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), AES_ALGORITHM);
    }

    // AES-GCM encryption
    public String encryptGCM(String plaintext, SecretKey key) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        cipher.updateAAD(salt);

        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine salt + nonce + ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + nonce.length + cipherText.length);
        buffer.put(salt);
        buffer.put(nonce);
        buffer.put(cipherText);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    // AES-GCM decryption
    public String decryptGCM(String encryptedData, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        buffer.get(salt);
        buffer.get(nonce);

        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        cipher.updateAAD(salt);

        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    // AES-CBC encryption
    public String encryptCBC(String plaintext, SecretKey key) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_CBC_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine salt + iv + ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(cipherText);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    // AES-CBC decryption
    public String decryptCBC(String encryptedData, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[16];
        buffer.get(salt);
        buffer.get(iv);

        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(AES_CBC_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    // Utility method to encrypt sensitive user data
    public User encryptUserData(User user, String encryptionKey) throws Exception {
        SecretKey key = getAESKeyFromPassword(encryptionKey, new byte[SALT_LENGTH]);

        // Encrypt sensitive fields
        String encryptedPassword = encryptGCM(user.getPassword(), key);
        String encryptedHost = encryptGCM(user.getHost(), key);

        return new User(
            user.getUsername(),
            encryptedPassword,
            encryptedHost,
            user.getPermissions(),
            user.isLock(),
            user.getCreationDate(),
            user.getCreator_id()
        );
    }

    // Utility method to decrypt sensitive user data
    public User decryptUserData(User user, String encryptionKey) throws Exception {
        SecretKey key = getAESKeyFromPassword(encryptionKey, new byte[SALT_LENGTH]);

        // Decrypt sensitive fields
        String decryptedPassword = decryptGCM(user.getPassword(), key);
        String decryptedHost = decryptGCM(user.getHost(), key);

        return new User(
            user.getUsername(),
            decryptedPassword,
            decryptedHost,
            user.getPermissions(),
            user.isLock(),
            user.getCreationDate(),
            user.getCreator_id()
        );
    }
}