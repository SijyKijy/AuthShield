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
            
        } catch (IOException e) {
            LOGGER.error("Failed to load config: {}", e.getMessage(), e);
        } catch (JsonParseException e) {
            LOGGER.error("Failed to parse config JSON: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error loading config: {}", e.getMessage(), e);
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
            LOGGER.info(getLogMessage("config.loaded"), configPath);
            
            loadLoginConfig();
            loadPasswordConfig();
            loadRestrictionsConfig();
            loadMessagesConfig();
        }
    }

    private static void loadLoginConfig() {
        JsonObject login = config.getAsJsonObject("login");
        JsonObject timeout = login.getAsJsonObject("timeout");
        loginTimeoutEnabled = timeout.get("enabled").getAsBoolean();
        loginTimeoutSeconds = timeout.get("seconds").getAsInt();
        
        JsonObject attempts = login.getAsJsonObject("attempts");
        maxLoginAttempts = attempts.get("max").getAsInt();
        loginAttemptTimeoutMinutes = attempts.get("timeout_minutes").getAsInt();
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

        // 加载其他消息文本
        JsonObject texts = messages.getAsJsonObject("texts");
        registerMessage = texts.get("register").getAsString();
        loginSuccess = texts.get("success").getAsString();
        loginAlready = texts.get("already").getAsString();
        loginIncorrect = texts.get("incorrect").getAsString();
        loginTimeout = texts.get("timeout").getAsString();
    }

    private static void handleConfigError(String operation, Exception e) {
        LOGGER.error("Failed to {} config: {}", operation, e.getMessage(), e);
    }

    public static void loadTranslations() {
        try {
            Path langDir = Path.of("config/authshield/lang");
            Files.createDirectories(langDir);

            copyLanguageFile(langDir, "en_us.json");
            copyLanguageFile(langDir, "zh_cn.json");

            String lang = config.getAsJsonObject("settings").get("language").getAsString();
            Path langFile = langDir.resolve(lang + ".json");

            if (Files.exists(langFile)) {
                loadLanguageFile(langFile, lang);
            } else {
                loadDefaultLanguage();
            }
        } catch (Exception e) {
            handleConfigError("load translations", e);
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
                        LOGGER.info(getLogMessage("lang.created"), langFile);
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            handleConfigError("copy language file " + fileName, e);
        }
    }

    private static void loadLanguageFile(Path langFile, String lang) {
        try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            currentLanguage = lang;
            LOGGER.info(getLogMessage("lang.loaded"), lang);
        } catch (Exception e) {
            handleConfigError("load language file", e);
            loadDefaultLanguage();
        }
    }

    private static void loadDefaultLanguage() {
        try (InputStream in = Config.class.getResourceAsStream("/assets/authshield/lang/en_us.json")) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    currentLanguage = "en_us";
                }
            }
        } catch (IOException | JsonParseException e) {
            handleConfigError("load default language", e);
        }
    }

    public static Component getMessage(String key) {
        String text = translations.getOrDefault(key, key);
        LOGGER.debug("Getting message for key: {}, text: {}", key, text);
        return Component.literal(text);
    }

    public static Component getMessage(String key, Object... args) {
        String text = translations.getOrDefault(key, key);
        return Component.literal(String.format(text, args));
    }

    public static boolean validatePassword(String password, Component[] error) {
        if (password.length() < minPasswordLength) {
            error[0] = getMessage("authshield.register.password.tooshort", minPasswordLength);
            return false;
        }
        if (password.length() > maxPasswordLength) {
            error[0] = getMessage("authshield.register.password.toolong", maxPasswordLength);
            return false;
        }
        if (requireSpecialChar && !SPECIAL_CHARS.matcher(password).find()) {
            error[0] = getMessage("authshield.register.password.needsymbol");
            return false;
        }
        if (requireNumber && !NUMBERS.matcher(password).find()) {
            error[0] = getMessage("authshield.register.password.neednumber");
            return false;
        }
        if (requireUppercase && !UPPERCASE.matcher(password).find()) {
            error[0] = getMessage("authshield.register.password.needupper");
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


    public static String getLogMessage(String key) {
        if (currentLanguage.equals("zh_cn")) {
            return switch (key) {
                case "config.created" -> "已创建默认配置文件: {}";
                case "config.not_found" -> "在资源文件中找不到默认配置文件 config.json";
                case "config.loaded" -> "已从 {} 加载配置";
                case "mod.initialized" -> "AuthShield 安全系统已加载";
                case "password.loaded" -> "AuthShield 密码数据已加载";
                case "password.load_failed" -> "密码数据加载失败";
                case "lang.created" -> "已创建语言文件: {}";
                case "lang.loaded" -> "已加载语言文件: {}";
                default -> key;
            };
        } else {
            return switch (key) {
                case "config.created" -> "Created default config file: {}";
                case "config.not_found" -> "Default config.json not found in resources";
                case "config.loaded" -> "Loaded config from {}";
                case "mod.initialized" -> "AuthShield security system loaded";
                case "password.loaded" -> "AuthShield password data loaded";
                case "password.load_failed" -> "Failed to load password data";
                case "lang.created" -> "Created language file: {}";
                case "lang.loaded" -> "Loaded language file: {}";
                default -> key;
            };
        }
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }


    public static boolean reload() {
        try {
            loadTranslations();
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reload configuration", e);
            return false;
        }
    }


    public static int getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public static boolean isLoginTimeoutEnabled() {
        return loginTimeoutEnabled;
    }

    public static int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    public static int getLoginAttemptTimeoutMinutes() {
        return loginAttemptTimeoutMinutes;
    }

    public static int getMinPasswordLength() {
        return minPasswordLength;
    }

    public static int getMaxPasswordLength() {
        return maxPasswordLength;
    }

    public static boolean isRequireSpecialChar() {
        return requireSpecialChar;
    }

    public static boolean isRequireNumber() {
        return requireNumber;
    }

    public static boolean isRequireUppercase() {
        return requireUppercase;
    }

    public static String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public static String getPreLoginGamemode() {
        return preLoginGamemode;
    }

    public static List<PreLoginEffect> getPreLoginEffects() {
        return preLoginEffects;
    }

    public static boolean isTitleEnabled() {
        return titleEnabled;
    }

    public static boolean isSubtitleEnabled() {
        return subtitleEnabled;
    }

    public static boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public static int getActionbarInterval() {
        return actionbarInterval;
    }

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
