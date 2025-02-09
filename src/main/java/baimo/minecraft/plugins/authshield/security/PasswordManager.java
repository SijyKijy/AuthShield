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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import baimo.minecraft.plugins.authshield.Config;
import baimo.minecraft.plugins.authshield.util.PerformanceUtils;

public class PasswordManager {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static final Gson gson = new Gson();
    private static final String PASSWORD_FILE = "playerdata.json";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final Map<String, String> passwords = new HashMap<>();
    
    // 密码哈希缓存，缓存验证结果30分钟
    private final Cache<String, Boolean> verificationCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
    
    // 密码哈希缓存，缓存哈希值12小时
    private final Cache<String, String> hashCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(12, TimeUnit.HOURS)
        .build();
    
    public PasswordManager() {
        loadPasswords();
    }
    
    public void loadPasswords() {
        Path path = Path.of("config/authshield/playerdata.json");
        LOGGER.info("Loading password data from: {}", path);
        
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(path)) {
                    try (FileReader reader = new FileReader(path.toFile())) {
                        Map<String, String> loadedPasswords = gson.fromJson(reader, 
                            new TypeToken<Map<String, String>>(){}.getType());
                        if (loadedPasswords != null) {
                            passwords.putAll(loadedPasswords);
                            LOGGER.info("Successfully loaded {} password entries", loadedPasswords.size());
                        } else {
                            LOGGER.warn("Password file exists but is empty");
                        }
                    }
                } else {
                    LOGGER.info("Password file does not exist, will be created when first password is set");
                }
            } catch (JsonSyntaxException e) {
                LOGGER.error("Failed to parse password file - corrupted data: {}", e.getMessage());
                throw new SecurityException("Password file is corrupted", e);
            } catch (IOException e) {
                LOGGER.error("Failed to read password file: {}", e.getMessage());
                throw new SecurityException("Cannot access password file", e);
            }
        }, PerformanceUtils.getIoExecutor());
    }
    
    public void savePasswords() {
        Path path = Path.of("config/authshield/playerdata.json");
        LOGGER.debug("Saving {} password entries to: {}", passwords.size(), path);
        
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                
                try (FileWriter writer = new FileWriter(path.toFile())) {
                    gson.toJson(passwords, writer);
                    LOGGER.info("Successfully saved password data to: {}", path);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to save password file: {}", e.getMessage());
                throw new SecurityException("Cannot write password file", e);
            }
        }, PerformanceUtils.getIoExecutor());
    }
    
    public CompletableFuture<String> hashPasswordAsync(String password) {
        return CompletableFuture.supplyAsync(() -> {
            String cachedHash = hashCache.getIfPresent(password);
            if (cachedHash != null) {
                LOGGER.debug("Using cached password hash");
                return cachedHash;
            }
            
            LOGGER.debug("Hashing password with algorithm: {}", Config.getHashAlgorithm());
            try {
                byte[] salt = new byte[Config.getHashSaltLength()];
                SECURE_RANDOM.nextBytes(salt);
                
                PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    Config.getHashIterations(),
                    Config.getHashKeyLength()
                );
                
                SecretKeyFactory skf = SecretKeyFactory.getInstance(Config.getHashAlgorithm());
                byte[] hash = skf.generateSecret(spec).getEncoded();
                spec.clearPassword();
                
                byte[] combined = new byte[salt.length + hash.length];
                System.arraycopy(salt, 0, combined, 0, salt.length);
                System.arraycopy(hash, 0, combined, salt.length, hash.length);
                
                String hashedPassword = Base64.getEncoder().encodeToString(combined);
                hashCache.put(password, hashedPassword);
                LOGGER.debug("Password hashed successfully");
                return hashedPassword;
                
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Unsupported hashing algorithm: {}", Config.getHashAlgorithm());
                throw new SecurityException("Unsupported password hashing algorithm", e);
            } catch (InvalidKeySpecException e) {
                LOGGER.error("Invalid key specification for password hashing: {}", e.getMessage());
                throw new SecurityException("Invalid password hashing parameters", e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error during password hashing: {}", e.getMessage());
                throw new SecurityException("Failed to hash password", e);
            }
        }, PerformanceUtils.getHashExecutor());
    }
    
    public CompletableFuture<Boolean> verifyPasswordAsync(String password, String storedHash) {
        String cacheKey = password + ":" + storedHash;
        Boolean cachedResult = verificationCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            LOGGER.debug("Using cached verification result");
            return CompletableFuture.completedFuture(cachedResult);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.debug("Verifying password hash");
            try {
                byte[] combined = Base64.getDecoder().decode(storedHash);
                
                byte[] salt = new byte[Config.getHashSaltLength()];
                byte[] hash = new byte[combined.length - Config.getHashSaltLength()];
                System.arraycopy(combined, 0, salt, 0, salt.length);
                System.arraycopy(combined, salt.length, hash, 0, hash.length);
                
                PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    Config.getHashIterations(),
                    Config.getHashKeyLength()
                );
                
                SecretKeyFactory skf = SecretKeyFactory.getInstance(Config.getHashAlgorithm());
                byte[] testHash = skf.generateSecret(spec).getEncoded();
                spec.clearPassword();
                
                boolean matches = MessageDigest.isEqual(hash, testHash);
                verificationCache.put(cacheKey, matches);
                LOGGER.debug("Password verification completed: {}", matches ? "matched" : "not matched");
                return matches;
                
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid stored hash format: {}", e.getMessage());
                throw new SecurityException("Stored password hash is invalid", e);
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Unsupported hashing algorithm: {}", Config.getHashAlgorithm());
                throw new SecurityException("Unsupported password hashing algorithm", e);
            } catch (InvalidKeySpecException e) {
                LOGGER.error("Invalid key specification for password verification: {}", e.getMessage());
                throw new SecurityException("Invalid password hashing parameters", e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error during password verification: {}", e.getMessage());
                throw new SecurityException("Failed to verify password", e);
            }
        }, PerformanceUtils.getHashExecutor());
    }
    
    public void setPassword(String uuid, String hashedPassword) {
        LOGGER.info("Setting password for player UUID: {}", uuid);
        passwords.put(uuid, hashedPassword);
        savePasswords();
        // 清除该用户的所有缓存
        clearUserCache(uuid);
        LOGGER.info("Password successfully set for player UUID: {}", uuid);
    }
    
    public boolean hasPassword(String uuid) {
        boolean exists = passwords.containsKey(uuid);
        LOGGER.debug("Checking password existence for UUID {}: {}", uuid, exists);
        return exists;
    }
    
    public String getPassword(String uuid) {
        String password = passwords.get(uuid);
        LOGGER.debug("Retrieved password hash for UUID: {}", uuid);
        return password;
    }
    
    public void removePassword(String uuid) {
        LOGGER.info("Removing password for player UUID: {}", uuid);
        passwords.remove(uuid);
        savePasswords();
        // 清除该用户的所有缓存
        clearUserCache(uuid);
        LOGGER.info("Password successfully removed for player UUID: {}", uuid);
    }
    
    private void clearUserCache(String uuid) {
        // 清除与该用户相关的所有缓存
        verificationCache.asMap().keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }
    
    // 在插件关闭时调用，清理资源
    public void shutdown() {
        LOGGER.info("Shutting down PasswordManager...");
        // 清除所有缓存
        verificationCache.invalidateAll();
        hashCache.invalidateAll();
        // 保存所有未保存的密码
        savePasswords();
    }
} 