package baimo.minecraft.plugins.authshield.security;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import baimo.minecraft.plugins.authshield.Config;

public class PasswordManager {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static final Gson gson = new Gson();
    private static final String PASSWORD_FILE = "playerdata.json";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final Map<String, String> passwords = new HashMap<>();
    
    public PasswordManager() {
        loadPasswords();
    }
    
    public void loadPasswords() {
        try {
            Path path = Path.of("config/authshield/playerdata.json");
            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    Map<String, String> loadedPasswords = gson.fromJson(reader, 
                        new TypeToken<Map<String, String>>(){}.getType());
                    if (loadedPasswords != null) {
                        passwords.putAll(loadedPasswords);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error(Config.getLogMessage("password.load.failed"), e);
        }
    }
    
    public void savePasswords() {
        try {
            Path path = Path.of("config/authshield/playerdata.json");
            Files.createDirectories(path.getParent());
            
            try (FileWriter writer = new FileWriter(path.toFile())) {
                gson.toJson(passwords, writer);
                LOGGER.info(Config.getLogMessage("password.saved"), path);
            }
        } catch (IOException e) {
            LOGGER.error(Config.getLogMessage("password.save.failed"), e);
        }
    }
    
    public String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
            );
            
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.error(Config.getLogMessage("password.hash.failed"), e);
            throw new RuntimeException(e);
        }
    }
    
    public boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            
            byte[] salt = new byte[16];
            byte[] hash = new byte[combined.length - 16];
            System.arraycopy(combined, 0, salt, 0, salt.length);
            System.arraycopy(combined, salt.length, hash, 0, hash.length);
            
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
            );
            
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] testHash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            
            return MessageDigest.isEqual(hash, testHash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.error(Config.getLogMessage("password.verify.failed"), e);
            throw new RuntimeException(e);
        }
    }
    
    public void setPassword(String uuid, String hashedPassword) {
        passwords.put(uuid, hashedPassword);
        savePasswords();
    }
    
    public boolean hasPassword(String uuid) {
        return passwords.containsKey(uuid);
    }
    
    public String getPassword(String uuid) {
        return passwords.get(uuid);
    }
    
    public void removePassword(String uuid) {
        passwords.remove(uuid);
        savePasswords();
    }
} 