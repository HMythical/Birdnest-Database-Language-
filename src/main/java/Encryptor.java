import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
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
}