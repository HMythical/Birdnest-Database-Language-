import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyGenerator {
    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final String RSA_ALGORITHM = "RSA";

    // Generate an RSA key pair and return base64 encoded strings
    public java.security.KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyGen.initialize(DEFAULT_KEY_SIZE, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    // Generate SSH key pair
    public void generateSSHKeyPair(String comment, String outputPath) throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, DEFAULT_KEY_SIZE);

        // Save private key
        keyPair.writePrivateKey(outputPath + "/id_rsa");

        // Save public key
        keyPair.writePublicKey(outputPath + "/id_rsa.pub", comment);
        keyPair.dispose();
    }

    // Convert key to PEM format
    public String convertToPEM(Key key, boolean isPrivate) {
        String header = isPrivate ? "PRIVATE" : "PUBLIC";
        String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());

        return String.format("-----BEGIN %s KEY-----\n%s\n-----END %s KEY-----",
            header, encodedKey, header);
    }

    // Load public key from file
    public PublicKey loadPublicKey(String filename) throws Exception {
        byte[] keyBytes = readKeyFile(filename);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
        return kf.generatePublic(spec);
    }

    // Load private key from file
    public PrivateKey loadPrivateKey(String filename) throws Exception {
        byte[] keyBytes = readKeyFile(filename);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
        return kf.generatePrivate(spec);
    }

    // Utility method to read key file
    private byte[] readKeyFile(String filename) throws IOException {
        File f = new File(filename);
        FileInputStream fis = new FileInputStream(f);
        byte[] encoded = new byte[(int) f.length()];
        fis.read(encoded);
        fis.close();
        return encoded;
    }

    // Verify if a key pair matches
    public boolean verifyKeyPair(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        // Create test data
        byte[] testData = "test".getBytes();

        // Sign with private key
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(testData);
        byte[] signatureBytes = signature.sign();

        // Verify with public key
        signature.initVerify(publicKey);
        signature.update(testData);
        return signature.verify(signatureBytes);
    }
}
