package baimo.minecraft.plugins.authshield;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

import baimo.minecraft.plugins.authshield.security.PasswordManager;
import baimo.minecraft.plugins.authshield.commands.CommandManager;
import baimo.minecraft.plugins.authshield.listeners.AuthEventListener;
import baimo.minecraft.plugins.authshield.player.PlayerManager;
import baimo.minecraft.plugins.authshield.util.PerformanceUtils;

@Mod(value = AuthShield.MODID)
public class AuthShield {
    public static final String MODID = "authshield";
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    private static AuthShield instance;
    
    private PasswordManager passwordManager;
    private PlayerManager playerManager;
    private CommandManager commandManager;
    private AuthEventListener eventListener;

    public AuthShield(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            LOGGER.warn("AuthShield is designed for server-side only!");
            return;
        }
        
        instance = this;
        
        try {
            // 初始化配置
            Config.loadConfig();
            Config.loadTranslations();
            
            // 初始化各个管理器
            this.passwordManager = new PasswordManager();
            this.playerManager = new PlayerManager();
            this.commandManager = new CommandManager(this);
            this.eventListener = new AuthEventListener(this);
            
            // 注册事件监听器
            NeoForge.EVENT_BUS.register(eventListener);
            NeoForge.EVENT_BUS.register(this);
            modEventBus.register(Config.class);
            
            // 显示欢迎信息
            LOGGER.info("");
            LOGGER.info("[ AuthShield ]");
                       
            if (Config.getCurrentLanguage().equals("zh_cn")) {
                LOGGER.info("感谢使用千屈的登录验证插件");
                LOGGER.info("欢迎加入开发者群 QQ群: 528651839");
                LOGGER.info("获取更多资讯与技术支持");
            }
            
            LOGGER.info("");
            LOGGER.info(Config.getLogMessage("mod.initialized"));
        } catch (Exception e) {
            LOGGER.error(Config.getLogMessage("mod.init_failed"), e);
        }
    }

    public static AuthShield getInstance() {
        return instance;
    }

    public PasswordManager getPasswordManager() {
        return passwordManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public AuthEventListener getEventListener() {
        return eventListener;
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        commandManager.registerCommands(event.getServer().getCommands().getDispatcher());
    }
    
    // 在服务器关闭时清理资源
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        LOGGER.info("Shutting down AuthShield...");
        try {
            // 关闭密码管理器
            if (passwordManager != null) {
                passwordManager.shutdown();
            }
            
            // 关闭线程池
            PerformanceUtils.shutdownExecutors();
            
            LOGGER.info("AuthShield shutdown completed successfully");
        } catch (Exception e) {
            LOGGER.error("Error during AuthShield shutdown: {}", e.getMessage());
        }
    }
}
