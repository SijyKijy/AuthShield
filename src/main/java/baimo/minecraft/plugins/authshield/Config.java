package baimo.minecraft.plugins.authshield;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

@EventBusSubscriber(modid = "authshield", bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern NUMBERS = Pattern.compile("\\d");
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static JsonObject config;
    private static Map<String, String> translations = new HashMap<>();
    private static String currentLanguage = "en_us"; 

    // 登录相关配置
    private static boolean loginTimeoutEnabled;
    private static int loginTimeoutSeconds;
    private static int maxLoginAttempts;
    private static int loginAttemptTimeoutMinutes;

    // 密码相关配置
    private static int minPasswordLength;
    private static int maxPasswordLength;
    private static boolean requireSpecialChar;
    private static boolean requireNumber;
    private static boolean requireUppercase;
    private static String hashAlgorithm;

    // 游戏限制配置
    private static String preLoginGamemode;
    private static List<PreLoginEffect> preLoginEffects;
    private static final Set<String> allowedCommands = new HashSet<>();

    // 消息显示配置
    private static boolean titleEnabled;
    private static boolean subtitleEnabled;
    private static boolean actionbarEnabled;
    private static int actionbarInterval;

    // 出生点配置
    private static double firstSpawnX;
    private static double firstSpawnY;
    private static double firstSpawnZ;
    private static String firstSpawnWorld;
    private static boolean firstSpawnSet = false;

    public static final String MODID = "authshield";
    public static final long LOGIN_TIMEOUT_MILLIS = 60000L;

    // 消息文本配置
    private static String loginTitle;
    private static String loginSubtitle;
    private static String registerMessage;
    private static String loginSuccess;
    private static String loginAlready;
    private static String loginIncorrect;
    private static String loginTimeout;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        loadConfig();
        loadTranslations();
    }

    public static void loadConfig() {
        try {
            Path configDir = Path.of("config/authshield");
            Files.createDirectories(configDir);

            Path configPath = configDir.resolve("config.json");
            if (Files.notExists(configPath)) {
                createDefaultConfig(configPath);
            }

            loadConfigFile(configPath);
            
        } catch (IOException | JsonParseException | SecurityException e) {
            handleConfigError("load", e);
        }
    }

    private static void createDefaultConfig(Path configPath) throws IOException {
        try (InputStream in = Config.class.getResourceAsStream("/config/authshield/config.json")) {
            if (in != null) {
                Files.copy(in, configPath);
                LOGGER.info(getLogMessage("config.created"), configPath);
            } else {
                LOGGER.error(getLogMessage("config.not_found"));
            }
        }
    }

    private static void loadConfigFile(Path configPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            config = new Gson().fromJson(reader, JsonObject.class);
            if (config == null) {
                LOGGER.error("配置文件为空或格式错误");
                throw new JsonParseException("配置文件为空或格式错误");
            }
            
            validateConfig();
            LOGGER.info(getLogMessage("config.loaded"), configPath);
            
            loadLoginConfig();
            loadPasswordConfig();
            loadRestrictionsConfig();
            loadMessagesConfig();
        }
    }

    private static void validateConfig() {
        // 检查必要的配置节点
        if (!config.has("settings")) {
            LOGGER.error("缺少 settings 配置节点");
            throw new JsonParseException("缺少 settings 配置节点");
        }
        if (!config.has("login")) {
            LOGGER.error("缺少 login 配置节点");
            throw new JsonParseException("缺少 login 配置节点");
        }
        if (!config.has("password")) {
            LOGGER.error("缺少 password 配置节点");
            throw new JsonParseException("缺少 password 配置节点");
        }
        if (!config.has("restrictions")) {
            LOGGER.error("缺少 restrictions 配置节点");
            throw new JsonParseException("缺少 restrictions 配置节点");
        }
        if (!config.has("messages")) {
            LOGGER.error("缺少 messages 配置节点");
            throw new JsonParseException("缺少 messages 配置节点");
        }
    }

    private static void loadLoginConfig() {
        try {
            JsonObject login = config.getAsJsonObject("login");
            JsonObject timeout = login.getAsJsonObject("timeout");
            loginTimeoutEnabled = timeout.get("enabled").getAsBoolean();
            loginTimeoutSeconds = timeout.get("seconds").getAsInt();
            
            JsonObject attempts = login.getAsJsonObject("attempts");
            maxLoginAttempts = attempts.get("max").getAsInt();
            loginAttemptTimeoutMinutes = attempts.get("timeout_minutes").getAsInt();
            
            LOGGER.debug("登录配置加载完成: timeout={}, attempts={}", loginTimeoutSeconds, maxLoginAttempts);
        } catch (Exception e) {
            LOGGER.error("加载登录配置失败: {}", e.getMessage());
            throw new JsonParseException("加载登录配置失败", e);
        }
    }

    private static void loadPasswordConfig() {
        JsonObject password = config.getAsJsonObject("password");
        minPasswordLength = password.get("min_length").getAsInt();
        maxPasswordLength = password.get("max_length").getAsInt();
        requireSpecialChar = password.get("require_special_char").getAsBoolean();
        requireNumber = password.get("require_number").getAsBoolean();
        requireUppercase = password.get("require_uppercase").getAsBoolean();
        hashAlgorithm = password.get("hash_algorithm").getAsString();
    }

    private static void loadRestrictionsConfig() {
        JsonObject restrictions = config.getAsJsonObject("restrictions");
        preLoginGamemode = restrictions.get("gamemode").getAsString();
        
        preLoginEffects = new ArrayList<>();
        JsonArray effects = restrictions.getAsJsonArray("effects");
        for (JsonElement effect : effects) {
            JsonObject effectObj = effect.getAsJsonObject();
            preLoginEffects.add(new PreLoginEffect(
                effectObj.get("id").getAsString(),
                effectObj.get("amplifier").getAsInt(),
                effectObj.get("particles").getAsBoolean(),
                effectObj.get("icon").getAsBoolean()
            ));
        }
        
        allowedCommands.clear();
        JsonArray commands = restrictions.getAsJsonArray("allowed_commands");
        for (JsonElement cmd : commands) {
            allowedCommands.add(cmd.getAsString());
        }
    }

    private static void loadMessagesConfig() {
        try {
            JsonObject messages = config.getAsJsonObject("messages");
            
            JsonObject title = messages.getAsJsonObject("title");
            titleEnabled = title.get("enabled").getAsBoolean();
            loginTitle = title.get("text").getAsString();

            JsonObject subtitle = messages.getAsJsonObject("subtitle");
            subtitleEnabled = subtitle.get("enabled").getAsBoolean();
            loginSubtitle = subtitle.get("text").getAsString();

            JsonObject actionbar = messages.getAsJsonObject("actionbar");
            actionbarEnabled = actionbar.get("enabled").getAsBoolean();
            actionbarInterval = actionbar.get("interval").getAsInt();

            // 设置默认消息文本
            registerMessage = "authshield.register";
            loginSuccess = "authshield.login.success";
            loginAlready = "authshield.login.already";
            loginIncorrect = "authshield.login.incorrect";
            loginTimeout = "authshield.login.timeout";

            // 尝试从配置文件加载自定义消息文本
            JsonObject texts = messages.has("texts") ? messages.getAsJsonObject("texts") : null;
            if (texts != null) {
                registerMessage = getTextOrDefault(texts, "register", registerMessage);
                loginSuccess = getTextOrDefault(texts, "success", loginSuccess);
                loginAlready = getTextOrDefault(texts, "already", loginAlready);
                loginIncorrect = getTextOrDefault(texts, "incorrect", loginIncorrect);
                loginTimeout = getTextOrDefault(texts, "timeout", loginTimeout);
            }
            
            LOGGER.debug("消息配置加载完成");
        } catch (Exception e) {
            LOGGER.error("加载消息配置失败: {}", e.getMessage());
            throw new JsonParseException("加载消息配置失败", e);
        }
    }

    private static String getTextOrDefault(JsonObject texts, String key, String defaultValue) {
        if (texts == null || !texts.has(key) || texts.get(key).isJsonNull()) {
            return defaultValue;
        }
        return texts.get(key).getAsString();
    }

    private static void handleConfigError(String operation, Exception e) {
        LOGGER.error("Failed to {} config: {}", operation, e.getMessage(), e);
    }

    public static void loadTranslations() {
        try {
            Path langDir = Path.of("config/authshield/lang");
            Files.createDirectories(langDir);

            // 复制默认语言文件
            copyLanguageFile(langDir, "en_us.json");
            copyLanguageFile(langDir, "zh_cn.json");

            // 获取配置中的语言设置
            String lang = config.getAsJsonObject("settings").get("language").getAsString();
            LOGGER.info("正在加载语言: {}", lang);

            Path langFile = langDir.resolve(lang + ".json");
            if (Files.exists(langFile)) {
                loadLanguageFile(langFile, lang);
                LOGGER.info("成功加载语言文件: {}", lang);
            } else {
                LOGGER.warn("找不到语言文件 {}，使用默认语言 en_us", lang);
                loadDefaultLanguage();
            }
        } catch (Exception e) {
            LOGGER.error("加载翻译文件失败: {}", e.getMessage());
            loadDefaultLanguage();
        }
    }

    private static void copyLanguageFile(Path langDir, String fileName) {
        try {
            Path langFile = langDir.resolve(fileName);
            if (Files.notExists(langFile)) {
                try (InputStream in = Config.class.getResourceAsStream("/assets/authshield/lang/" + fileName)) {
                    if (in != null) {
                        Files.copy(in, langFile);
                        LOGGER.info("已创建语言文件: {}", langFile);
                    } else {
                        LOGGER.error("无法找到内置语言文件: {}", fileName);
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            LOGGER.error("复制语言文件 {} 失败: {}", fileName, e.getMessage());
        }
    }

    private static void loadLanguageFile(Path langFile, String lang) {
        try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            Map<String, String> langMap = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            if (langMap == null || langMap.isEmpty()) {
                LOGGER.error("语言文件 {} 为空或格式错误", lang);
                loadDefaultLanguage();
                return;
            }

            // 验证必要的语言键
            List<String> missingKeys = validateLanguageKeys(langMap);
            if (!missingKeys.isEmpty()) {
                LOGGER.warn("语言文件 {} 缺少以下键: {}", lang, String.join(", ", missingKeys));
            }

            translations = langMap;
            currentLanguage = lang;
            LOGGER.info("已加载语言: {}", lang);
        } catch (Exception e) {
            LOGGER.error("加载语言文件 {} 失败: {}", lang, e.getMessage());
            loadDefaultLanguage();
        }
    }

    private static List<String> validateLanguageKeys(Map<String, String> langMap) {
        List<String> requiredKeys = List.of(
            "authshield.title",
            "authshield.subtitle",
            "authshield.register",
            "authshield.login",
            "authshield.login.success",
            "authshield.login.incorrect",
            "authshield.login.timeout"
        );

        List<String> missingKeys = new ArrayList<>();
        for (String key : requiredKeys) {
            if (!langMap.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        return missingKeys;
    }

    private static void loadDefaultLanguage() {
        try {
            try (InputStream in = Config.class.getResourceAsStream("/assets/authshield/lang/en_us.json")) {
                if (in != null) {
                    Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                    translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    currentLanguage = "en_us";
                    LOGGER.info("已加载默认语言: en_us");
                } else {
                    LOGGER.error("找不到默认语言文件");
                    translations = new HashMap<>();
                }
            }
        } catch (Exception e) {
            LOGGER.error("加载默认语言失败: {}", e.getMessage());
            translations = new HashMap<>();
        }
    }

    public static String getMessage(String key, Object... args) {
        String message = translations.getOrDefault(key, key);
        try {
            return String.format(message, args);
        } catch (Exception e) {
            LOGGER.error("格式化消息失败 [{}]: {}", key, e.getMessage());
            return message;
        }
    }

    public static Component getComponent(String key, Object... args) {
        return Component.literal(getMessage(key, args));
    }

    private static void saveConfig() {
        try {
            Path configPath = Path.of("config/authshield/config.json");
            try (FileWriter writer = new FileWriter(configPath.toFile(), StandardCharsets.UTF_8)) {
                new Gson().toJson(config, writer);
                LOGGER.info("配置已保存到: {}", configPath);
            }
        } catch (Exception e) {
            LOGGER.error("保存配置失败: {}", e.getMessage());
        }
    }

    public static boolean reload() {
        try {
            loadConfig();
            loadTranslations();
            LOGGER.info("配置已重新加载");
            return true;
        } catch (Exception e) {
            LOGGER.error("重新加载配置失败: {}", e.getMessage());
            return false;
        }
    }

    public static String getLogMessage(String key) {
        return translations.getOrDefault("authshield.log." + key, key);
    }

    // Getters for configuration values
    public static boolean isLoginTimeoutEnabled() { return loginTimeoutEnabled; }
    public static int getLoginTimeoutSeconds() { return loginTimeoutSeconds; }
    public static int getMaxLoginAttempts() { return maxLoginAttempts; }
    public static int getLoginAttemptTimeoutMinutes() { return loginAttemptTimeoutMinutes; }
    public static int getMinPasswordLength() { return minPasswordLength; }
    public static int getMaxPasswordLength() { return maxPasswordLength; }
    public static boolean isRequireSpecialChar() { return requireSpecialChar; }
    public static boolean isRequireNumber() { return requireNumber; }
    public static boolean isRequireUppercase() { return requireUppercase; }
    public static String getHashAlgorithm() { return hashAlgorithm; }
    public static String getPreLoginGamemode() { return preLoginGamemode; }
    public static List<PreLoginEffect> getPreLoginEffects() { return new ArrayList<>(preLoginEffects); }
    public static Set<String> getAllowedCommands() { return new HashSet<>(allowedCommands); }
    public static boolean isTitleEnabled() { return titleEnabled; }
    public static boolean isSubtitleEnabled() { return subtitleEnabled; }
    public static boolean isActionbarEnabled() { return actionbarEnabled; }
    public static int getActionbarInterval() { return actionbarInterval; }
    public static String getCurrentLanguage() { return currentLanguage; }

    public static boolean validatePassword(String password, Component[] error) {
        if (password.length() < minPasswordLength) {
            error[0] = getComponent("authshield.register.password.tooshort", minPasswordLength);
            return false;
        }
        if (password.length() > maxPasswordLength) {
            error[0] = getComponent("authshield.register.password.toolong", maxPasswordLength);
            return false;
        }
        if (requireSpecialChar && !SPECIAL_CHARS.matcher(password).find()) {
            error[0] = getComponent("authshield.register.password.needsymbol");
            return false;
        }
        if (requireNumber && !NUMBERS.matcher(password).find()) {
            error[0] = getComponent("authshield.register.password.neednumber");
            return false;
        }
        if (requireUppercase && !UPPERCASE.matcher(password).find()) {
            error[0] = getComponent("authshield.register.password.needupper");
            return false;
        }
        return true;
    }
    

    public static class PreLoginEffect {
        public final String id;
        public final int amplifier;
        public final boolean particles;
        public final boolean icon;
        
        public PreLoginEffect(String id, int amplifier, boolean particles, boolean icon) {
            this.id = id;
            this.amplifier = amplifier;
            this.particles = particles;
            this.icon = icon;
        }
    }
    

    public static boolean isCommandAllowed(String command) {
        return allowedCommands.contains(command);
    }
    
    public static JsonObject getConfig() {
        return config;
    }

    public static void setFirstSpawn(double x, double y, double z, String world) {
        firstSpawnX = x;
        firstSpawnY = y;
        firstSpawnZ = z;
        firstSpawnWorld = world;
        firstSpawnSet = true;
        saveFirstSpawnConfig();
    }

    private static void saveFirstSpawnConfig() {
        try {
            Path configPath = Path.of("config/authshield/config.json");
            if (!Files.exists(configPath)) {
                return;
            }

            JsonObject configJson = new Gson().fromJson(Files.newBufferedReader(configPath), JsonObject.class);
            if (!configJson.has("firstSpawn")) {
                configJson.add("firstSpawn", new JsonObject());
            }
            JsonObject firstSpawn = configJson.getAsJsonObject("firstSpawn");
            firstSpawn.addProperty("x", firstSpawnX);
            firstSpawn.addProperty("y", firstSpawnY);
            firstSpawn.addProperty("z", firstSpawnZ);
            firstSpawn.addProperty("world", firstSpawnWorld);
            firstSpawn.addProperty("set", true);

            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                new Gson().toJson(configJson, writer);
            }
        } catch (IOException | JsonParseException e) {
            handleConfigError("save first spawn config", e);
        }
    }

    public static boolean isFirstSpawnSet() {
        return firstSpawnSet;
    }

    public static double getFirstSpawnX() { return firstSpawnX; }
    public static double getFirstSpawnY() { return firstSpawnY; }
    public static double getFirstSpawnZ() { return firstSpawnZ; }
    public static String getFirstSpawnWorld() { return firstSpawnWorld; }

    public static String getLoginTitle() {
        return loginTitle;
    }

    public static String getLoginSubtitle() {
        return loginSubtitle;
    }

    public static String getRegisterMessage() {
        return registerMessage;
    }

    public static String getLoginSuccess() {
        return loginSuccess;
    }

    public static String getLoginAlready() {
        return loginAlready;
    }

    public static String getLoginIncorrect() {
        return loginIncorrect;
    }

    public static String getLoginTimeout() {
        return loginTimeout;
    }
}
