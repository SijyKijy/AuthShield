package baimo.minecraft.plugins.authshield;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import baimo.minecraft.plugins.authshield.commands.CommandManager;
import baimo.minecraft.plugins.authshield.listeners.AuthEventListener;
import baimo.minecraft.plugins.authshield.player.PlayerManager;
import baimo.minecraft.plugins.authshield.security.PasswordManager;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

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
            modEventBus.register(Config.class);
            NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
                this.commandManager.registerCommands(event.getServer().getCommands().getDispatcher());
            });
            
            // 显示欢迎信息
            LOGGER.info("");
            LOGGER.info(ChatFormatting.GRAY + "[ " + 
                       ChatFormatting.LIGHT_PURPLE + "AuthShield" + 
                       ChatFormatting.GRAY + " ]");
                       
            if (Config.getCurrentLanguage().equals("zh_cn")) {
                LOGGER.info(ChatFormatting.WHITE + "感谢使用 " + 
                           ChatFormatting.LIGHT_PURPLE + "千屈" +
                           ChatFormatting.WHITE + " 的登录验证插件");
                LOGGER.info(ChatFormatting.WHITE + "欢迎加入开发者群 " +
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
}
