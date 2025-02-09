package baimo.minecraft.plugins.authshield.listeners;

import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import baimo.minecraft.plugins.authshield.AuthShield;
import baimo.minecraft.plugins.authshield.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
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

public class AuthEventListener {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private final AuthShield plugin;
    
    public AuthEventListener(AuthShield plugin) {
        this.plugin = plugin;
    }
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            plugin.getPasswordManager().loadPasswords();
            LOGGER.info(Config.getLogMessage("password.loaded"));
        } catch (Exception e) {
            LOGGER.error(Config.getLogMessage("password.load_failed"), e);
        }
    }
    
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String uuid = player.getUUID().toString();
            
            if (!plugin.getPasswordManager().hasPassword(uuid)) {
                if (Config.isFirstSpawnSet()) {
                    String worldId = Config.getFirstSpawnWorld();
                    if (worldId != null) {
                        ResourceLocation worldLocation = ResourceLocation.tryParse(worldId);
                        if (worldLocation != null) {
                            ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, worldLocation);
                            
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
                    }
                }
                
                player.sendSystemMessage(Config.getMessage("authshield.register"));
                plugin.getPlayerManager().applyRestrictions(player);
                plugin.getPlayerManager().startLoginTimer(player);
            } else if (!plugin.getPlayerManager().isLoggedIn(player)) {
                player.sendSystemMessage(Config.getMessage("authshield.login"));
                plugin.getPlayerManager().applyRestrictions(player);
                plugin.getPlayerManager().startLoginTimer(player);
            }
        }
    }
    
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            plugin.getPlayerManager().logout(player);
        }
    }
    
    @SubscribeEvent
    public void onPlayerReceivingDamage(LivingDamageEvent.Pre ev) {
        LivingEntity entity = ev.getEntity();
        if (entity instanceof Player player) {
            if (!plugin.getPlayerManager().isLoggedIn(player)) {
                ev.setNewDamage(0.0F);
            }
        }
    }
    
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem ev) {
        Player player = ev.getEntity();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
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
        LivingEntity entity = ev.getEntity();
        if (entity instanceof Player player) {
            if (!plugin.getPlayerManager().isLoggedIn(player)) {
                ev.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent
    public void onCommandExecution(CommandEvent ev) {
        Entity entity = ev.getParseResults().getContext().getSource().getEntity();
        if (entity instanceof Player player) {
            if (!plugin.getPlayerManager().isLoggedIn(player)) {
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
    public void onPlayerAttack(AttackEntityEvent ev) {
        Player player = ev.getEntity();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            promptLogin(player);
            ev.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent ev) {
        Player player = ev.getPlayer();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            promptLogin(player);
            ev.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onItemDrop(ItemTossEvent ev) {
        Player player = ev.getPlayer();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            ev.setCanceled(true);
            ItemStack itemStack = ev.getEntity().getItem();
            player.getInventory().add(itemStack);
            player.sendSystemMessage(Config.getMessage("authshield.drop.needlogin"));
        }
    }
    
    @SubscribeEvent
    public void onPlayerBreakBlock(LivingDestroyBlockEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            if (!plugin.getPlayerManager().isLoggedIn(player)) {
                promptLogin(player);
                event.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            promptLogin(player);
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onPlayerInteract2(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            promptLogin(player);
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onPlayerMove(PlayerTickEvent.Pre ev) {
        Player player = ev.getEntity();
        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            plugin.getPlayerManager().moveToLoginCoords(player);
        }
    }
    
    private void promptLogin(Player player) {
        String uuid = player.getUUID().toString();
        if (!plugin.getPasswordManager().hasPassword(uuid)) {
            // 如果玩家未注册，不需要提示登录
        } else if (!plugin.getPlayerManager().isLoggedIn(player)) {
            player.sendSystemMessage(Config.getMessage("authshield.login"));
        }
    }
} 