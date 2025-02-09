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
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = AuthShield.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern NUMBERS = Pattern.compile("\\d");
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Gson gson = new Gson();
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static JsonObject config;
    private static Map<String, String> translations = new HashMap<>();
    private static String currentLanguage = "en_us"; // Ĭ��Ӣ��

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");


    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);


    public static boolean loginTimeoutEnabled;
    public static int loginTimeoutSeconds;
    public static int maxLoginAttempts;
    public static int loginAttemptTimeoutMinutes;


    public static int minPasswordLength;
    public static int maxPasswordLength;
    public static boolean requireSpecialChar;
    public static boolean requireNumber;
    public static boolean requireUppercase;
    public static String hashAlgorithm;

    public static String preLoginGamemode;
    public static List<PreLoginEffect> preLoginEffects;
    public static Set<String> allowedCommands;

    public static boolean titleEnabled;
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
            Path configDir = Path.of("config/authshield");
            Files.createDirectories(configDir);

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

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                config = new Gson().fromJson(reader, JsonObject.class);
                LOGGER.info(getLogMessage("config.loaded"), configPath);
            }
            
            JsonObject login = config.getAsJsonObject("login");
            JsonObject timeout = login.getAsJsonObject("timeout");
            loginTimeoutEnabled = timeout.get("enabled").getAsBoolean();
            loginTimeoutSeconds = timeout.get("seconds").getAsInt();
            
            JsonObject attempts = login.getAsJsonObject("attempts");
            maxLoginAttempts = attempts.get("max").getAsInt();
            loginAttemptTimeoutMinutes = attempts.get("timeout_minutes").getAsInt();

            JsonObject password = config.getAsJsonObject("password");
            minPasswordLength = password.get("min_length").getAsInt();
            maxPasswordLength = password.get("max_length").getAsInt();
            requireSpecialChar = password.get("require_special_char").getAsBoolean();
            requireNumber = password.get("require_number").getAsBoolean();
            requireUppercase = password.get("require_uppercase").getAsBoolean();
            hashAlgorithm = password.get("hash_algorithm").getAsString();

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

            JsonObject messages = config.getAsJsonObject("messages");
            JsonObject title = messages.getAsJsonObject("title");
            titleEnabled = title.get("enabled").getAsBoolean();

            JsonObject subtitle = messages.getAsJsonObject("subtitle");
            subtitleEnabled = subtitle.get("enabled").getAsBoolean();

            JsonObject actionbar = messages.getAsJsonObject("actionbar");
            actionbarEnabled = actionbar.get("enabled").getAsBoolean();
            actionbarInterval = actionbar.get("interval").getAsInt();
            
        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
        }
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
                try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
                    translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    currentLanguage = lang;
                    LOGGER.info(getLogMessage("lang.loaded"), lang);
                }
            } else {
                loadDefaultLanguage();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load translations", e);
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


    public static String getLogMessage(String key) {
        if (currentLanguage.equals("zh_cn")) {
            switch (key) {
                case "config.created": return "已创建默认配置文件: {}";
                case "config.not_found": return "在资源文件中找不到默认配置文件 config.json";
                case "config.loaded": return "已从 {} 加载配置";
                case "mod.initialized": return "AuthShield 安全系统已加载";
                case "password.loaded": return "AuthShield 密码数据已加载";
                case "password.load_failed": return "密码数据加载失败";
                case "lang.created": return "已创建语言文件: {}";
                case "lang.loaded": return "已加载语言文件: {}";
                default: return key;
            }
        } else {
            switch (key) {
                case "config.created": return "Created default config file: {}";
                case "config.not_found": return "Default config.json not found in resources";
                case "config.loaded": return "Loaded config from {}";
                case "mod.initialized": return "AuthShield security system loaded";
                case "password.loaded": return "AuthShield password data loaded";
                case "password.load_failed": return "Failed to load password data";
                case "lang.created": return "Created language file: {}";
                case "lang.loaded": return "Loaded language file: {}";
                default: return key;
            }
        }
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }


    public static boolean reload() {
        try {
            loadConfig();
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
}
