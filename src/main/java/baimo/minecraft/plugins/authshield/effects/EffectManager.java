package baimo.minecraft.plugins.authshield.effects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import baimo.minecraft.plugins.authshield.AuthShield;
import baimo.minecraft.plugins.authshield.Config;

public class EffectManager {
    public static void showLoginTitle(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        String uuid = player.getUUID().toString();
        if (!Config.isTitleEnabled()) {
            return;
        }

        Component title;
        Component subtitle;
        
        if (AuthShield.getInstance().getPasswordManager().hasPassword(uuid)) {
            title = Config.getComponent("authshield.title");
            subtitle = Config.getComponent("authshield.subtitle");
        } else {
            title = Config.getComponent("authshield.title.register");
            subtitle = Config.getComponent("authshield.subtitle.register");
        }
        
        showPersistentTitle(serverPlayer, title, subtitle);
    }
    
    public static void showLoginSuccessEffect(Player player) {
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

        // 创建螺旋上升的粒子效果
        for (int i = 0; i < 2; i++) {
            for (double y1 = 0; y1 < 2; y1 += 0.2) {
                double radius = 0.5;
                double angle = y1 * Math.PI * 4 + (i * Math.PI);
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                level.sendParticles(
                    ParticleTypes.END_ROD,
                    px, y + y1, pz,
                    1,  // 粒子数量
                    0, 0, 0,  // 速度
                    0   // 额外数据
                );
            }
        }
        
        // 清除标题
        if (Config.isTitleEnabled()) {
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        }
    }
    
    private static void showPersistentTitle(ServerPlayer player, Component title, Component subtitle) {
        // 设置标题显示时间（持续24小时）
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            0,  // 淡入时间
            20 * 60 * 60 * 24,  // 持续时间
            0   // 淡出时间
        ));

        // 发送标题和副标题
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
} 