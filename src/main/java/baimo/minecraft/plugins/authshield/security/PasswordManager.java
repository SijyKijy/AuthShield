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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import baimo.minecraft.plugins.authshield.Config;

public class PasswordManager {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SALT_LENGTH = 16;
    private static final String PASSWORD_FILE = "config/authshield/playerdata.json";
    
    private final Map<String, String> passwords = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path passwordPath;
    
    public PasswordManager() {
        passwordPath = Path.of(PASSWORD_FILE);
        doInitialLoad();
    }
    
    private void doInitialLoad() {
        lock.writeLock().lock();
        try {
            if (Files.exists(passwordPath)) {
                try (FileReader reader = new FileReader(passwordPath.toFile())) {
                    Map<String, String> loadedPasswords = gson.fromJson(reader, 
                        new TypeToken<Map<String, String>>(){}.getType());
                    if (loadedPasswords != null) {
                        passwords.clear();
                        passwords.putAll(loadedPasswords);
                        LOGGER.info("已加载 {} 个玩家的密码数据", loadedPasswords.size());
                    }
                }
            } else {
                LOGGER.info("密码文件不存在，将在首次保存时创建");
            }
        } catch (IOException e) {
            LOGGER.error("加载密码文件失败: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void loadPasswords() {
        doInitialLoad();
    }
    
    public void savePasswords() {
        lock.readLock().lock();
        try {
            Files.createDirectories(passwordPath.getParent());
            
            try (FileWriter writer = new FileWriter(passwordPath.toFile())) {
                gson.toJson(passwords, writer);
                LOGGER.info("密码数据已保存到: {}", passwordPath);
            }
        } catch (IOException e) {
            LOGGER.error("保存密码文件失败: {}", e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        try {
            byte[] salt = new byte[SALT_LENGTH];
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
            LOGGER.error("密码加密失败: {}", e.getMessage());
            throw new SecurityException("密码加密失败", e);
        }
    }
    
    public boolean verifyPassword(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            if (combined.length <= SALT_LENGTH) {
                LOGGER.error("存储的密码哈希格式无效");
                return false;
            }
            
            byte[] salt = new byte[SALT_LENGTH];
            byte[] hash = new byte[combined.length - SALT_LENGTH];
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
        } catch (IllegalArgumentException e) {
            LOGGER.error("Base64 解码失败: {}", e.getMessage());
            return false;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.error("密码验证失败: {}", e.getMessage());
            throw new SecurityException("密码验证失败", e);
        }
    }
    
    public void setPassword(String uuid, String hashedPassword) {
        if (uuid == null || uuid.isEmpty() || hashedPassword == null || hashedPassword.isEmpty()) {
            throw new IllegalArgumentException("UUID 和密码哈希不能为空");
        }
        
        lock.writeLock().lock();
        try {
            passwords.put(uuid, hashedPassword);
            savePasswords();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public boolean hasPassword(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        lock.readLock().lock();
        try {
            return passwords.containsKey(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public String getPassword(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        
        lock.readLock().lock();
        try {
            return passwords.get(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void removePassword(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            if (passwords.remove(uuid) != null) {
                savePasswords();
                LOGGER.info("已删除玩家 {} 的密码数据", uuid);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int getPasswordCount() {
        lock.readLock().lock();
        try {
            return passwords.size();
        } finally {
            lock.readLock().unlock();
        }
    }
} 