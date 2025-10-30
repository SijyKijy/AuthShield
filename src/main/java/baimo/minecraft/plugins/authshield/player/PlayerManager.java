package baimo.minecraft.plugins.authshield.player;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.Sets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import baimo.minecraft.plugins.authshield.AuthShield;
import baimo.minecraft.plugins.authshield.Config;
import baimo.minecraft.plugins.authshield.effects.EffectManager;

public class PlayerManager {
    private final Timer timer = new Timer(true);
    private final Set<String> loggedInPlayers = Sets.newConcurrentHashSet();
    private final ConcurrentMap<String, TimerTask> loginTasks = new ConcurrentHashMap<>();
    
    public boolean isLoggedIn(Player player) {
        String uuid = getPlayerUUID(player);
        if (Config.isRegistrationOptional()
            && !AuthShield.getInstance().getPasswordManager().hasPassword(uuid)) {
            return true;
        }
        return loggedInPlayers.contains(uuid);
    }
    
    public void login(Player player) {
        String uuid = getPlayerUUID(player);
        loggedInPlayers.add(uuid);
        cancelLoginTimer(uuid);
        removeRestrictions(player);
    }
    
    public void logout(Player player) {
        String uuid = getPlayerUUID(player);
        loggedInPlayers.remove(uuid);
        cancelLoginTimer(uuid);
    }
    
    public void startLoginTimer(Player player) {
        String uuid = getPlayerUUID(player);
        TimerTask loginTask = new TimerTask() {
            @Override
            public void run() {
                if (!isLoggedIn(player)) {
                    ((ServerPlayer)player).connection.disconnect(
                        Config.getMessage("authshield.login.timeout", 
                            Config.getLoginTimeoutSeconds()));
                    loginTasks.remove(uuid);
                }
            }
        };
        loginTasks.put(uuid, loginTask);
        timer.schedule(loginTask, Config.getLoginTimeoutSeconds() * 1000L);
    }
    
    public void cancelLoginTimer(String uuid) {
        TimerTask task = loginTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
    
    public void applyRestrictions(Player player) {
        // 应用减速效果
        MobEffectInstance slownessEffect = new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            Integer.MAX_VALUE,
            2,
            false,
            false,
            false
        );
        player.addEffect(slownessEffect);

        // 设置无敌状态
        player.setInvulnerable(true);

        // 切换游戏模式
        changeGameMode(player, "spectator");
        
        // 存储玩家位置
        storeLocation(player);
        
        // 显示标题
        EffectManager.showLoginTitle(player);
    }
    
    public void removeRestrictions(Player player) {
        player.removeAllEffects();
        player.setInvulnerable(false);
        if (!player.getPersistentData().contains("loginMode")) {
            player.getPersistentData().putString("loginMode", "survival");
        }
        changeGameMode(player, player.getPersistentData().getString("loginMode"));
    }
    
    public void storeLocation(Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        player.getPersistentData().putDouble("loginX", x);
        player.getPersistentData().putDouble("loginY", y);
        player.getPersistentData().putDouble("loginZ", z);
    }
    
    public void moveToLoginCoords(Player player) {
        double x = player.getPersistentData().getDouble("loginX");
        double y = player.getPersistentData().getDouble("loginY");
        double z = player.getPersistentData().getDouble("loginZ");
        player.teleportTo(x, y, z);
    }
    
    private void changeGameMode(Player player, String mode) {
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
                break;
        }
    }
    
    private String getPlayerUUID(Player player) {
        return player.getUUID().toString();
    }
    
    public String getPlayerMode(Player player) {
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
} 