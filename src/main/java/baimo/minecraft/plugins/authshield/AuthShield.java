// Source code is decompiled from a .class file using FernFlower decompiler.
package baimo.minecraft.plugins.authshield;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

@Mod(
        value = "authshield",
        dist = {Dist.DEDICATED_SERVER}
)
public class AuthShield {
    private final Timer timer = new Timer(true);
    private static final ConcurrentMap<String, TimerTask> loginTasks = new ConcurrentHashMap();
    private static final long LOGIN_TIMEOUT_MILLIS = 60000L;
    private static final String PASSWORD_FILE = "passwords.json";
    private static final Map<String, String> passwords = new HashMap();
    private static final Set<String> loggedInPlayers = Sets.newConcurrentHashSet();
    private static final Gson gson = new Gson();
    public static final String MODID = "authshield";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AuthShield(IEventBus modEventBus, ModContainer modContainer) {
        try {
            // Register event listeners
            NeoForge.EVENT_BUS.register(this);
            modEventBus.register(Config.class);
            
            // Initialize config and translations
            Config.loadConfig();
            Config.loadTranslations();
            
            // Show startup message
            LOGGER.info("");
            LOGGER.info(ChatFormatting.GRAY + "[ " + 
                       ChatFormatting.LIGHT_PURPLE + "AuthShield" + 
                       ChatFormatting.GRAY + " ]");
                   
            if (Config.getCurrentLanguage().equals("zh_cn")) {
                LOGGER.info(ChatFormatting.WHITE + "感谢使用 " + 
                           ChatFormatting.LIGHT_PURPLE + "千屈" +
                           ChatFormatting.WHITE + " 的登录验证插件");
                LOGGER.info(ChatFormatting.WHITE + "欢迎加入开发社区 " +
                           ChatFormatting.AQUA + "QQ群: " + 
                           ChatFormatting.YELLOW + "528651839");
                LOGGER.info(ChatFormatting.WHITE + "获取更多资讯与技术支持");
            }
            
            LOGGER.info("");
            LOGGER.info(Config.getLogMessage("mod.initialized"));
        } catch (Exception e) {
            LOGGER.error(Config.getLogMessage("mod.init_failed"), e);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            loadPasswords();
            LOGGER.info(Config.getLogMessage("password.loaded"));
        } catch (Exception e) {
            LOGGER.error(Config.getLogMessage("password.load_failed"), e);
        }
    }

    private static String getPlayerUUID(Player player) {
        return player.getUUID().toString();
    }

    private static int signUp(CommandContext<CommandSourceStack> context) {
        Entity var2 = ((CommandSourceStack)context.getSource()).getEntity();
        if (var2 instanceof Player player) {
            String uuid = getPlayerUUID(player);
            if (passwords.containsKey(uuid)) {
                player.sendSystemMessage(Config.getMessage("authshield.register.exists"));
                return 1;
            }
            
            String password = StringArgumentType.getString(context, "password");
            String confirmPassword = StringArgumentType.getString(context, "confirmPassword");
            
            if (!password.equals(confirmPassword)) {
                player.sendSystemMessage(Config.getMessage("authshield.register.nomatch"));
                return 1;
            }

            // 楠岃瘉瀵嗙爜瑙勫垯
            Component[] error = new Component[1];
            if (!Config.validatePassword(password, error)) {
                player.sendSystemMessage(error[0]);
                return 1;
            }
            
            String hashedPassword = hashPassword(password);
            passwords.put(uuid, hashedPassword);
            savePasswords();
            player.sendSystemMessage(Config.getMessage("authshield.register.success"));

            loggedInPlayers.add(uuid);
            TimerTask task = loginTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }

            // 娓呴櫎闄愬埗鏁堟灉
            player.removeAllEffects();
            if (!player.getPersistentData().contains("loginMode")) {
                player.getPersistentData().putString("loginMode", "survival");
            }
            changeGameMode(player, player.getPersistentData().getString("loginMode"));

            // 娓呴櫎鏍囬
            if (Config.titleEnabled) {
                ((ServerPlayer)player).connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                ((ServerPlayer)player).connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            }

            showLoginSuccessEffect(player);

            return 1;
        }
        return 1;
    }

    private static int signIn(CommandContext<CommandSourceStack> context) {
        Entity var2 = ((CommandSourceStack)context.getSource()).getEntity();
        if (var2 instanceof Player player) {
            String uuid = getPlayerUUID(player);
            String password = StringArgumentType.getString(context, "password");
            String hashedPassword = hashPassword(password);
            
            if (isLoggedIn(player)) {
                player.sendSystemMessage(Config.getMessage("authshield.login.already"));
                return 1;
            }

            if (passwords.containsKey(uuid) && passwords.get(uuid).equals(hashedPassword)) {
                if (Config.titleEnabled) {
                    ((ServerPlayer)player).connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                    ((ServerPlayer)player).connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                }

                player.sendSystemMessage(Config.getMessage("authshield.login.success"));
                loggedInPlayers.add(uuid);
                TimerTask task = loginTasks.remove(uuid);
                if (task != null) {
                    task.cancel();
                }

                player.removeAllEffects();
                if (!player.getPersistentData().contains("loginMode")) {
                    player.getPersistentData().putString("loginMode", "survival");
                }
                changeGameMode(player, player.getPersistentData().getString("loginMode"));
                showLoginSuccessEffect(player);
            } else {
                player.sendSystemMessage(Config.getMessage("authshield.login.incorrect"));
            }
            return 1;
        }
        return 1;
    }

    private static void signOut(Player player) {
        loggedInPlayers.remove(getPlayerUUID(player));
    }

    @SubscribeEvent
    public void onPlayerReceivingDamage(LivingDamageEvent.Pre ev) {
        LivingEntity var3 = ev.getEntity();
        if (var3 instanceof Player player) {
            if (!isLoggedIn(player)) {
                ev.setNewDamage(0.0F);
            }
        }

    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem ev) {
        Player player = ev.getEntity();
        if (!isLoggedIn(player)) {
            ItemStack itemStack = player.getMainHandItem();
            if (itemStack.getItem() instanceof BucketItem) {
                ItemStack originalContent = itemStack.copy();
                itemStack.setCount(originalContent.getCount());
            }

            ev.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onItemUse(LivingEntityUseItemEvent.Start ev) {
        LivingEntity var3 = ev.getEntity();
        if (var3 instanceof Player player) {
            if (!isLoggedIn(player)) {
                ev.setCanceled(true);
            }
        }

    }

    @SubscribeEvent
    public void onCommandExecution(CommandEvent ev) {
        CommandSourceStack source = ev.getParseResults().getContext().getSource();
        Entity entity = source.getEntity();
        if (entity instanceof Player player) {
            if (!isLoggedIn(player)) {
                String command = ev.getParseResults().getReader().getString()
                    .replaceFirst(Pattern.quote("/"), "");
                if (!Config.isCommandAllowed(command.split(" ")[0])) {
                    player.sendSystemMessage(Config.getMessage("authshield.command.needlogin"));
                    ev.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent ev) {
        CommandDispatcher<CommandSourceStack> dispatcher = ev.getDispatcher();

        // 娉ㄥ唽鍛戒护
        LiteralArgumentBuilder<CommandSourceStack> register = Commands.literal("register")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.register"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                    .executes(AuthShield::signUp)));

        // 娉ㄥ唽绠?鍖栧懡浠 /reg
        LiteralArgumentBuilder<CommandSourceStack> registerAlias = Commands.literal("reg")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.register"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                    .executes(AuthShield::signUp)));

        LiteralArgumentBuilder<CommandSourceStack> login = Commands.literal("login")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.login"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .executes(AuthShield::signIn));

        LiteralArgumentBuilder<CommandSourceStack> loginAlias = Commands.literal("l")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.login"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .executes(AuthShield::signIn));

        LiteralArgumentBuilder<CommandSourceStack> changePassword = Commands.literal("changepassword")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.changepassword"), false);
                return 1;
            })
            .then(Commands.argument("oldPassword", StringArgumentType.word())
                .then(Commands.argument("newPassword", StringArgumentType.word())
                    .executes(AuthShield::changePassword)));

        // 娣诲姞甯姪鍛戒护
        LiteralArgumentBuilder<CommandSourceStack> help = Commands.literal("authshield")
            .then(Commands.literal("help")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    boolean isOp = source.hasPermission(2);
                    
                    source.sendSuccess(() -> Config.getMessage("authshield.help.header"), false);
                    source.sendSuccess(() -> Config.getMessage("authshield.help.register"), false);
                    source.sendSuccess(() -> Config.getMessage("authshield.help.login"), false);
                    source.sendSuccess(() -> Config.getMessage("authshield.help.changepassword"), false);
                    source.sendSuccess(() -> Config.getMessage("authshield.help.cp"), false);
                    
                    if (isOp) {
                        source.sendSuccess(() -> Config.getMessage("authshield.help.admin.unregister"), false);
                        source.sendSuccess(() -> Config.getMessage("authshield.help.admin.setfirstspawn"), false);
                    }
                    
                    source.sendSuccess(() -> Config.getMessage("authshield.help.footer"), false);
                    return 1;
                }));

        // 娣诲姞娉ㄩ攢鍛戒护
        LiteralArgumentBuilder<CommandSourceStack> unregister = Commands.literal("authshield")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("unregister")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(AuthShield::unregisterPlayer)));

        LiteralArgumentBuilder<CommandSourceStack> changePasswordAlias = Commands.literal("cp")
            .then(Commands.argument("oldPassword", StringArgumentType.word())
                .then(Commands.argument("newPassword", StringArgumentType.word())
                    .executes(AuthShield::changePassword)));

        // Add first spawn command
        LiteralArgumentBuilder<CommandSourceStack> setFirstSpawn = Commands.literal("authshield")
            .requires(source -> source.hasPermission(2))  // Requires OP permission
            .then(Commands.literal("setfirstspawn")
                .executes(AuthShield::setFirstSpawn));

        dispatcher.register(register);
        dispatcher.register(registerAlias);
        dispatcher.register(login);
        dispatcher.register(loginAlias);
        dispatcher.register(changePassword);
        dispatcher.register(changePasswordAlias);
        dispatcher.register(help);
        dispatcher.register(unregister);
        dispatcher.register(setFirstSpawn);
    }

    public static void promptLogin(Player player) {
        String uuid = getPlayerUUID(player);
        if (!passwords.containsKey(uuid)) {
            return;
        } else if (!isLoggedIn(player)) {
            player.sendSystemMessage(Config.getMessage("authshield.login"));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) && Config.getCurrentLanguage().equals("zh_cn")) { // Check if player is OP and language is Chinese
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("[ ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD)
                    .append(Component.literal("AuthShield").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                    .append(Component.literal(" ]").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD)));
                player.sendSystemMessage(Component.literal("感谢使用 ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("白陌").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(" 开发的登录验证插件").withStyle(ChatFormatting.WHITE)));
                player.sendSystemMessage(Component.literal("欢迎加入插件社区 ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("QQ群: ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("528651839").withStyle(ChatFormatting.YELLOW)));
                player.sendSystemMessage(Component.literal("获取更多插件资讯与技术支持").withStyle(ChatFormatting.WHITE));
                player.sendSystemMessage(Component.empty());
            }
            
            String uuid = getPlayerUUID(player);
            
            if (!passwords.containsKey(uuid)) {
                if (Config.isFirstSpawnSet()) {
                    ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, 
                        ResourceLocation.tryParse(Config.getFirstSpawnWorld()));
                    
                    if (player.serverLevel().dimension() != targetDim) {
                        ServerLevel targetLevel = player.server.getLevel(targetDim);
                        if (targetLevel != null) {
                            player.teleportTo(targetLevel,
                                Config.getFirstSpawnX(),
                                Config.getFirstSpawnY(),
                                Config.getFirstSpawnZ(),
                                player.getYRot(),
                                player.getXRot()
                            );
                        }
                    } else {
                        player.teleportTo(
                            Config.getFirstSpawnX(),
                            Config.getFirstSpawnY(),
                            Config.getFirstSpawnZ()
                        );
                    }
                }
                
                player.sendSystemMessage(Config.getMessage("authshield.register"));
                restrictPlayer(player);
                storeLocation(player);
                startTimer(player);
            } else if (!isLoggedIn(player)) {
                player.sendSystemMessage(Config.getMessage("authshield.login"));
                restrictPlayer(player);
                storeLocation(player);
                startTimer(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent ev) {
        Player player = ev.getEntity();
        if (!isLoggedIn(player)) {
            promptLogin(player);
            ev.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent ev) {
        Player player = ev.getPlayer();
        if (!isLoggedIn(player)) {
            promptLogin(player);
            ev.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String uuid = getPlayerUUID(player);
            loggedInPlayers.remove(uuid);
            TimerTask task = loginTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }
        }
    }

    @SubscribeEvent
    public void onItemDrop(ItemTossEvent ev) {
        Player player = ev.getPlayer();
        if (!isLoggedIn(player)) {
            ev.setCanceled(true);
            ItemStack itemStack = ev.getEntity().getItem();
            player.getInventory().add(itemStack);
            player.sendSystemMessage(Config.getMessage("authshield.drop.needlogin"));
        }

    }

    @SubscribeEvent
    public void onPlayerBreakBlock(LivingDestroyBlockEvent event) {
        LivingEntity var3 = event.getEntity();
        if (var3 instanceof Player player) {
            if (!isLoggedIn(player)) {
                promptLogin(player);
                event.setCanceled(true);
            }
        }

    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!isLoggedIn(player)) {
            promptLogin(player);
            event.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onPlayerInteract2(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!isLoggedIn(player)) {
            promptLogin(player);
            event.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onPlayerMove(PlayerTickEvent.Pre ev) {
        Player player = ev.getEntity();
        if (!isLoggedIn(player)) {
            moveToLoginCoords(player);
        }

    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            byte[] var4 = hashedBytes;
            int var5 = hashedBytes.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                byte b = var4[var6];
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException var8) {
            throw new RuntimeException(var8);
        }
    }

    private static void loadPasswords() {
        try {
            Path path = Path.of("config/authshield/passwords.json");
            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    Map<String, String> loadedPasswords = gson.fromJson(reader, 
                        new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
                    if (loadedPasswords != null) {
                        passwords.putAll(loadedPasswords);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("鍔犺浇瀵嗙爜鏂囦欢澶辫触", e);
        }
    }

    public void startTimer(Player player) {
        String uuid = getPlayerUUID(player);
        ServerPlayer serverPlayer = (ServerPlayer)player;
        TimerTask loginTask = new TimerTask() {
            @Override
            public void run() {
                if (!isLoggedIn(player)) {
                    ((ServerPlayer)player).connection.disconnect(Config.getMessage("authshield.login.timeout"));
                    loginTasks.remove(uuid);
                }
            }
        };
        loginTasks.put(uuid, loginTask);
        this.timer.schedule(loginTask, LOGIN_TIMEOUT_MILLIS);
    }

    private static void restrictPlayer(Player player) {
        // Add slowness effect
        MobEffectInstance slownessEffect = new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            Integer.MAX_VALUE,
            2,  // Level III slowness
            false,  // No particles
            false,  // No icon
            false   // No ambient
        );
        player.addEffect(slownessEffect);

        // Add resistance effect for invulnerability
        MobEffectInstance resistanceEffect = new MobEffectInstance(
            MobEffects.DAMAGE_RESISTANCE,
            Integer.MAX_VALUE,
            255,  // Max level for invulnerability
            false,
            false,
            false
        );
        player.addEffect(resistanceEffect);

        // Set to spectator mode
        changeGameMode(player, "spectator");
        
        // Show title and messages
        String uuid = getPlayerUUID(player);
        if (!passwords.containsKey(uuid)) {
            if (Config.titleEnabled) {
                Component title = Config.getMessage("authshield.title.register");
                Component subtitle = Config.getMessage("authshield.subtitle.register");
                showPersistentTitle((ServerPlayer)player, title, subtitle);
            }
            player.sendSystemMessage(Config.getMessage("authshield.register"));
        } else {
            if (Config.titleEnabled) {
                Component title = Config.getMessage("authshield.title");
                Component subtitle = Config.getMessage("authshield.subtitle");
                showPersistentTitle((ServerPlayer)player, title, subtitle);
            }
            player.sendSystemMessage(Config.getMessage("authshield.login"));
        }
    }

    private static void showPersistentTitle(ServerPlayer player, Component title, Component subtitle) {
        // 璁剧疆鏍囬鏄剧ず鏃堕棿
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 999999, 20));
        // 鏄剧ず鏍囬鍜屽壇鏍囬
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private static void changeGameMode(Player player, String mode) {
        ServerPlayer serverPlayer = (ServerPlayer)player;
        switch (mode.toLowerCase()) {
            case "survival":
                serverPlayer.setGameMode(GameType.SURVIVAL);
                break;
            case "creative":
                serverPlayer.setGameMode(GameType.CREATIVE);
                break;
            case "spectator":
                serverPlayer.setGameMode(GameType.SPECTATOR);
        }

    }

    private static void savePasswords() {
        try {
            Path path = Path.of("config/authshield/passwords.json");
            Files.createDirectories(path.getParent());
            
            try (FileWriter writer = new FileWriter(path.toFile())) {
                gson.toJson(passwords, writer);
                LOGGER.info("瀵嗙爜宸蹭繚瀛樺埌鏂囦欢: {}", path);
            }
        } catch (IOException e) {
            LOGGER.error("淇濆瓨瀵嗙爜鏂囦欢澶辫触", e);
        }
    }

    public static String getPlayerMode(Player player) {
        ServerPlayer serverPlayer = (ServerPlayer)player;
        GameType gameType = serverPlayer.gameMode.getGameModeForPlayer();
        
        return switch (gameType) {
            case ADVENTURE -> "adventure";
            case SURVIVAL -> "survival";
            case CREATIVE -> "creative";
            case SPECTATOR -> "spectator";
            default -> "unknown";
        };
    }

    public static void moveToLoginCoords(Player player) {
        double x = player.getPersistentData().getDouble("loginX");
        double y = player.getPersistentData().getDouble("loginY");
        double z = player.getPersistentData().getDouble("loginZ");
        player.teleportTo(x, y, z);
    }

    public static boolean isLoggedIn(Player player) {
        return loggedInPlayers.contains(getPlayerUUID(player));
    }

    public static void storeLocation(Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        player.getPersistentData().putDouble("loginX", x);
        player.getPersistentData().putDouble("loginY", y);
        player.getPersistentData().putDouble("loginZ", z);
    }

    private static int unregisterPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            source.sendFailure(Config.getMessage("authshield.unregister.no_permission"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetName);
        
        if (targetPlayer != null) {
            // 鎵惧埌鍦ㄧ嚎鐜╁锛岃幏鍙栧叾 UUID
            String uuid = getPlayerUUID(targetPlayer);
            if (passwords.containsKey(uuid)) {
                passwords.remove(uuid);
                loggedInPlayers.remove(uuid);
                TimerTask task = loginTasks.remove(uuid);
                if (task != null) {
                    task.cancel();
                }
                savePasswords();
                targetPlayer.connection.disconnect(Config.getMessage("authshield.unregister.kick_message"));
                source.sendSuccess(() -> Config.getMessage("authshield.unregister.success", targetName), true);
                return 1;
            }
        } else {
            for (Map.Entry<String, String> entry : passwords.entrySet()) {
                String uuid = entry.getKey();
                try {
                    String playerName = source.getServer().getProfileCache().get(UUID.fromString(uuid))
                        .map(GameProfile::getName)
                        .orElse(null);
                    if (targetName.equals(playerName)) {
                        passwords.remove(uuid);
                        loggedInPlayers.remove(uuid);
                        savePasswords();
                        source.sendSuccess(() -> Config.getMessage("authshield.unregister.success", targetName), true);
                        return 1;
                    }
                } catch (Exception e) {
                    LOGGER.error("Error while unregistering player", e);
                }
            }
        }

        source.sendFailure(Config.getMessage("authshield.unregister.not_found", targetName));
        return 0;
    }

    private static void showLoginSuccessEffect(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        ServerLevel level = serverPlayer.serverLevel();
        if (level == null) {
            return;
        }
        
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 鍦ㄧ帺瀹朵綅缃敓鎴愯灪鏃嬩笂鍗囩殑绮掑瓙
        for (int i = 0; i < 2; i++) {
            for (double y1 = 0; y1 < 2; y1 += 0.2) {
                double radius = 0.5;
                double angle = y1 * Math.PI * 4 + (i * Math.PI);
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                level.sendParticles(
                    ParticleTypes.END_ROD,
                    px, y + y1, pz,  // 浣嶇疆
                    1,  // 绮掑瓙鏁伴噺
                    0, 0, 0,  // 閫熷害
                    0  // 棰濆鏁版嵁
                );
            }
        }
    }

    private static int changePassword(CommandContext<CommandSourceStack> context) {
        Entity var2 = ((CommandSourceStack)context.getSource()).getEntity();
        if (var2 instanceof Player player) {
            String uuid = getPlayerUUID(player);

            if (!isLoggedIn(player)) {
                player.sendSystemMessage(Config.getMessage("authshield.changepassword.not_logged_in"));
                return 0;
            }

            String oldPassword = StringArgumentType.getString(context, "oldPassword");
            String newPassword = StringArgumentType.getString(context, "newPassword");
            String oldHashedPassword = hashPassword(oldPassword);
            if (!passwords.containsKey(uuid) || !passwords.get(uuid).equals(oldHashedPassword)) {
                player.sendSystemMessage(Config.getMessage("authshield.changepassword.incorrect"));
                return 0;
            }
            if (oldPassword.equals(newPassword)) {
                player.sendSystemMessage(Config.getMessage("authshield.changepassword.same"));
                return 0;
            }

            Component[] error = new Component[1];
            if (!Config.validatePassword(newPassword, error)) {
                player.sendSystemMessage(error[0]);
                return 0;
            }

            String newHashedPassword = hashPassword(newPassword);
            passwords.put(uuid, newHashedPassword);
            savePasswords();

            player.sendSystemMessage(Config.getMessage("authshield.changepassword.success"));
            showLoginSuccessEffect(player);
            
            return 1;
        }
        return 1;
    }

    private static int setFirstSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            source.sendFailure(Config.getMessage("authshield.setfirstspawn.no_permission"));
            return 0;
        }

        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            Config.setFirstSpawn(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.serverLevel().dimension().location().toString()
            );
            source.sendSuccess(() -> Config.getMessage("authshield.setfirstspawn.success"), true);
            return 1;
        }
        return 0;
    }
}
