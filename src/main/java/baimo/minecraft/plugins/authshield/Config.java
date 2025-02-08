package baimo.minecraft.plugins.authshield;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.io.InputStream;
import java.io.Reader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.reflect.TypeToken;
import java.io.FileWriter;

@EventBusSubscriber(modid = AuthShield.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static JsonObject config;
    private static Map<String, String> translations = new HashMap<>();
    private static String currentLanguage = "en_us"; // 默认英文

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    // Login settings
    public static boolean loginTimeoutEnabled;
    public static int loginTimeoutSeconds;
    public static int maxLoginAttempts;
    public static int loginAttemptTimeoutMinutes;

    // Password settings
    public static int minPasswordLength;
    public static int maxPasswordLength;
    public static boolean requireSpecialChar;
    public static boolean requireNumber;
    public static boolean requireUppercase;
    public static String hashAlgorithm;

    // Restriction settings
    public static String preLoginGamemode;
    public static List<PreLoginEffect> preLoginEffects;
    public static Set<String> allowedCommands;

    // Message settings
    public static boolean titleEnabled;
    public static int titleFadeIn;
    public static int titleStay;
    public static int titleFadeOut;
    public static boolean subtitleEnabled;
    public static boolean actionbarEnabled;
    public static int actionbarInterval;

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    public static final String MODID = "authshield";
    public static final String PASSWORD_FILE = "passwords.json";
    public static final long LOGIN_TIMEOUT_MILLIS = 60000L;

    // Message fields
    public static String loginTitle;
    public static String loginSubtitle;
    public static String registerMessage;
    public static String loginSuccess;
    public static String loginAlready;
    public static String loginIncorrect;
    public static String loginTimeout;

    private static double firstSpawnX;
    private static double firstSpawnY;
    private static double firstSpawnZ;
    private static String firstSpawnWorld;
    private static boolean firstSpawnSet = false;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }

    public static void loadConfig() {
        try {
            // 确保配置目录存在
            Path configDir = Path.of("config/authshield");
            Files.createDirectories(configDir);

            // 复制默认配置文件
            Path configPath = configDir.resolve("config.json");
            if (Files.notExists(configPath)) {
                try (InputStream in = Config.class.getResourceAsStream("/config/authshield/config.json")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                        LOGGER.info(getLogMessage("config.created"), configPath);
                    } else {
                        LOGGER.error(getLogMessage("config.not_found"));
                        return;
                    }
                }
            }

            // 读取配置文件
            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = new Gson().fromJson(reader, JsonObject.class);
                LOGGER.info(getLogMessage("config.loaded"), configPath);
            }
            
            // Load login settings
            JsonObject login = config.getAsJsonObject("login");
            JsonObject timeout = login.getAsJsonObject("timeout");
            loginTimeoutEnabled = timeout.get("enabled").getAsBoolean();
            loginTimeoutSeconds = timeout.get("seconds").getAsInt();
            
            JsonObject attempts = login.getAsJsonObject("attempts");
            maxLoginAttempts = attempts.get("max").getAsInt();
            loginAttemptTimeoutMinutes = attempts.get("timeout_minutes").getAsInt();

            // Load password settings
            JsonObject password = config.getAsJsonObject("password");
            minPasswordLength = password.get("min_length").getAsInt();
            maxPasswordLength = password.get("max_length").getAsInt();
            requireSpecialChar = password.get("require_special_char").getAsBoolean();
            requireNumber = password.get("require_number").getAsBoolean();
            requireUppercase = password.get("require_uppercase").getAsBoolean();
            hashAlgorithm = password.get("hash_algorithm").getAsString();

            // Load restriction settings
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
            
            allowedCommands = new HashSet<>();
            JsonArray commands = restrictions.getAsJsonArray("allowed_commands");
            for (JsonElement cmd : commands) {
                allowedCommands.add(cmd.getAsString());
            }

            // Load message settings
            JsonObject messages = config.getAsJsonObject("messages");
            JsonObject title = messages.getAsJsonObject("title");
            titleEnabled = title.get("enabled").getAsBoolean();
            titleFadeIn = title.get("fade_in").getAsInt();
            titleStay = title.get("stay").getAsInt();
            titleFadeOut = title.get("fade_out").getAsInt();

            JsonObject subtitle = messages.getAsJsonObject("subtitle");
            subtitleEnabled = subtitle.get("enabled").getAsBoolean();

            JsonObject actionbar = messages.getAsJsonObject("actionbar");
            actionbarEnabled = actionbar.get("enabled").getAsBoolean();
            actionbarInterval = actionbar.get("interval").getAsInt();
            
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }
    }
    
    public static void loadTranslations() {
        try {
            // 复制默认语言文件
            Path langDir = Path.of("config/authshield/lang");
            Files.createDirectories(langDir);

            // 复制英文语言文件
            copyLanguageFile(langDir, "en_us.json");
            // 复制中文语言文件
            copyLanguageFile(langDir, "zh_cn.json");

            // 获取系统语言
            String lang = java.util.Locale.getDefault().toString().toLowerCase();
            if (lang.contains("zh")) {
                currentLanguage = "zh_cn";
            } else {
                currentLanguage = "en_us";
            }

            // 加载语言文件
            Path langFile = langDir.resolve(currentLanguage + ".json");
            if (Files.exists(langFile)) {
                try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
                    translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    LOGGER.info("已加载语言文件: {}", langFile);
                }
            } else {
                LOGGER.warn("找不到语言文件: {}, 将使用英文(en_us)", currentLanguage);
                loadDefaultLanguage();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load language file", e);
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
                        LOGGER.info("Created language file: {}", langFile);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to copy language file: {}", fileName, e);
        }
    }

    private static void loadDefaultLanguage() {
        try (InputStream in = Config.class.getResourceAsStream("/assets/authshield/lang/en_us.json")) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load default language file", e);
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

    // Validate password
    public static boolean validatePassword(String password, Component[] error) {
        if (password.length() < minPasswordLength) {
            error[0] = getMessage("authshield.register.password.tooshort", minPasswordLength);
            return false;
        }
        if (password.length() > maxPasswordLength) {
            error[0] = getMessage("authshield.register.password.toolong", maxPasswordLength);
            return false;
        }
        if (requireSpecialChar && !password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            error[0] = getMessage("authshield.register.password.needsymbol");
            return false;
        }
        if (requireNumber && !password.matches(".*\\d.*")) {
            error[0] = getMessage("authshield.register.password.neednumber");
            return false;
        }
        if (requireUppercase && !password.matches(".*[A-Z].*")) {
            error[0] = getMessage("authshield.register.password.needupper");
            return false;
        }
        return true;
    }
    
    // Effect configuration class
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
    
    // Helper methods
    public static boolean isCommandAllowed(String command) {
        return allowedCommands.contains(command);
    }
    
    public static JsonObject getConfig() {
        return config;
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
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
        } catch (IOException e) {
            LOGGER.error("Failed to save first spawn config", e);
        }
    }

    public static boolean isFirstSpawnSet() {
        return firstSpawnSet;
    }

    public static double getFirstSpawnX() { return firstSpawnX; }
    public static double getFirstSpawnY() { return firstSpawnY; }
    public static double getFirstSpawnZ() { return firstSpawnZ; }
    public static String getFirstSpawnWorld() { return firstSpawnWorld; }

    // 添加获取日志消息的方法
    public static String getLogMessage(String key) {
        if (currentLanguage.equals("zh_cn")) {
            switch (key) {
                case "config.created": return "已创建默认配置文件: {}";
                case "config.not_found": return "在资源文件中找不到默认配置文件 config.json";
                case "config.loaded": return "已从 {} 加载配置";
                case "password.loaded": return "AuthShield 密码数据已加载";
                case "password.load_failed": return "加载密码数据失败";
                default: return key;
            }
        } else {
            switch (key) {
                case "config.created": return "Created default config file: {}";
                case "config.not_found": return "Default config.json not found in resources";
                case "config.loaded": return "Loaded config from {}";
                case "password.loaded": return "AuthShield password data loaded";
                case "password.load_failed": return "Failed to load password data";
                default: return key;
            }
        }
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }
}
