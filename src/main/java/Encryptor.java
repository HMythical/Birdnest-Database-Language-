import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class Encryptor {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ARGON_ITERATIONS = 3;
    private static final int ARGON_MEMORY = 65536; // 64MB
    private static final int ARGON_PARALLELISM = 4;
    private static final int ARGON_SALT_LENGTH = 16;
    private static final int ARGON_HASH_LENGTH = 32;

    // AES-GCM
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    public static String hashPassword(String password) {
        byte[] salt = new byte[ARGON_SALT_LENGTH];
        RANDOM.nextBytes(salt);

        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withIterations(ARGON_ITERATIONS)
                .withMemoryAsKB(ARGON_MEMORY)
                .withParallelism(ARGON_PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] hash = new byte[ARGON_HASH_LENGTH];
        generator.generateBytes(password.toCharArray(), hash);

        return Base64.getEncoder().encodeToString(hash);
    }

    public static String encryptData(String data, byte[] key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv)
        );

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }
}