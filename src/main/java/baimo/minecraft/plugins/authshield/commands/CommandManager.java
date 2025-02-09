package baimo.minecraft.plugins.authshield.commands;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import baimo.minecraft.plugins.authshield.AuthShield;
import baimo.minecraft.plugins.authshield.Config;
import baimo.minecraft.plugins.authshield.effects.EffectManager;

public class CommandManager {
    private final AuthShield plugin;
    
    public CommandManager(AuthShield plugin) {
        this.plugin = plugin;
    }
    
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 注册命令
        registerRegisterCommand(dispatcher);
        registerLoginCommand(dispatcher);
        registerChangePasswordCommand(dispatcher);
        registerAdminCommands(dispatcher);
    }
    
    private void registerRegisterCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> register = Commands.literal("register")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.register"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                    .executes(this::signUp)));

        LiteralArgumentBuilder<CommandSourceStack> registerAlias = Commands.literal("reg")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.register"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                    .executes(this::signUp)));

        dispatcher.register(register);
        dispatcher.register(registerAlias);
    }
    
    private void registerLoginCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> login = Commands.literal("login")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.login"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .executes(this::signIn));

        LiteralArgumentBuilder<CommandSourceStack> loginAlias = Commands.literal("l")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.login"), false);
                return 1;
            })
            .then(Commands.argument("password", StringArgumentType.word())
                .executes(this::signIn));

        dispatcher.register(login);
        dispatcher.register(loginAlias);
    }
    
    private void registerChangePasswordCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> changePassword = Commands.literal("changepassword")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.changepassword"), false);
                return 1;
            })
            .then(Commands.argument("oldPassword", StringArgumentType.word())
                .then(Commands.argument("newPassword", StringArgumentType.word())
                    .executes(this::changePassword)));

        LiteralArgumentBuilder<CommandSourceStack> changePasswordAlias = Commands.literal("cp")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Config.getMessage("authshield.usage.cp"), false);
                return 1;
            })
            .then(Commands.argument("oldPassword", StringArgumentType.word())
                .then(Commands.argument("newPassword", StringArgumentType.word())
                    .executes(this::changePassword)));

        dispatcher.register(changePassword);
        dispatcher.register(changePasswordAlias);
    }
    
    private void registerAdminCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> help = Commands.literal("authshield")
            .executes(this::showHelp);

        help.then(Commands.literal("unregister")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", StringArgumentType.word())
                .requires(source -> source.hasPermission(2))
                .executes(this::unregisterPlayer)));

        help.then(Commands.literal("setfirstspawn")
            .requires(source -> source.hasPermission(2))
            .executes(this::setFirstSpawn));

        help.then(Commands.literal("reload")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                if (Config.reload()) {
                    context.getSource().sendSuccess(() -> 
                        Config.getMessage("authshield.reload.success"), true);
                } else {
                    context.getSource().sendFailure(
                        Config.getMessage("authshield.reload.failed"));
                }
                return 1;
            }));

        dispatcher.register(help);
    }
    
    private int signUp(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        if (!(entity instanceof Player player)) {
            return 1;
        }

        String uuid = player.getUUID().toString();
        if (plugin.getPasswordManager().hasPassword(uuid)) {
            player.sendSystemMessage(Config.getMessage("authshield.register.exists"));
            return 1;
        }
        
        String password = StringArgumentType.getString(context, "password");
        String confirmPassword = StringArgumentType.getString(context, "confirmPassword");
        
        if (!password.equals(confirmPassword)) {
            player.sendSystemMessage(Config.getMessage("authshield.register.nomatch"));
            return 1;
        }

        Component[] error = new Component[1];
        if (!Config.validatePassword(password, error)) {
            player.sendSystemMessage(error[0]);
            return 1;
        }
        
        String hashedPassword = plugin.getPasswordManager().hashPassword(password);
        plugin.getPasswordManager().setPassword(uuid, hashedPassword);
        player.sendSystemMessage(Config.getMessage("authshield.register.success"));

        plugin.getPlayerManager().login(player);
        EffectManager.showLoginSuccessEffect(player);

        return 1;
    }
    
    private int signIn(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        if (!(entity instanceof Player player)) {
            return 1;
        }

        String uuid = player.getUUID().toString();
        String password = StringArgumentType.getString(context, "password");
        
        if (plugin.getPlayerManager().isLoggedIn(player)) {
            player.sendSystemMessage(Config.getMessage("authshield.login.already"));
            return 1;
        }

        if (!plugin.getPasswordManager().hasPassword(uuid)) {
            player.sendSystemMessage(Config.getMessage("authshield.login.not_registered"));
            return 1;
        }

        String storedHash = plugin.getPasswordManager().getPassword(uuid);
        if (plugin.getPasswordManager().verifyPassword(password, storedHash)) {
            plugin.getPlayerManager().login(player);
            player.sendSystemMessage(Config.getMessage("authshield.login.success"));
            EffectManager.showLoginSuccessEffect(player);
        } else {
            player.sendSystemMessage(Config.getMessage("authshield.login.incorrect"));
        }
        return 1;
    }
    
    private int changePassword(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        if (!(entity instanceof Player player)) {
            return 1;
        }

        String uuid = player.getUUID().toString();

        if (!plugin.getPlayerManager().isLoggedIn(player)) {
            player.sendSystemMessage(Config.getMessage("authshield.changepassword.not_logged_in"));
            return 0;
        }

        String oldPassword = StringArgumentType.getString(context, "oldPassword");
        String newPassword = StringArgumentType.getString(context, "newPassword");
        
        String storedHash = plugin.getPasswordManager().getPassword(uuid);
        if (!plugin.getPasswordManager().verifyPassword(oldPassword, storedHash)) {
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

        String newHashedPassword = plugin.getPasswordManager().hashPassword(newPassword);
        plugin.getPasswordManager().setPassword(uuid, newHashedPassword);

        player.sendSystemMessage(Config.getMessage("authshield.changepassword.success"));
        EffectManager.showLoginSuccessEffect(player);
        
        return 1;
    }
    
    private int unregisterPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            source.sendFailure(Config.getMessage("authshield.unregister.no_permission"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetName);
        
        if (targetPlayer != null) {
            String uuid = targetPlayer.getUUID().toString();
            if (plugin.getPasswordManager().hasPassword(uuid)) {
                plugin.getPasswordManager().removePassword(uuid);
                plugin.getPlayerManager().logout(targetPlayer);
                targetPlayer.connection.disconnect(Config.getMessage("authshield.unregister.kick_message"));
                source.sendSuccess(() -> Config.getMessage("authshield.unregister.success", targetName), true);
                return 1;
            }
        } else {
            // 尝试从缓存中查找离线玩家
            source.getServer().getProfileCache()
                .get(targetName)
                .ifPresent(profile -> {
                    String uuid = profile.getId().toString();
                    if (plugin.getPasswordManager().hasPassword(uuid)) {
                        plugin.getPasswordManager().removePassword(uuid);
                        source.sendSuccess(() -> 
                            Config.getMessage("authshield.unregister.success", targetName), true);
                    }
                });
        }

        source.sendFailure(Config.getMessage("authshield.unregister.not_found", targetName));
        return 0;
    }
    
    private int setFirstSpawn(CommandContext<CommandSourceStack> context) {
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
    
    private int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean isOp = source.hasPermission(2);
        
        source.sendSuccess(() -> Config.getMessage("authshield.help.header"), false);
        source.sendSuccess(() -> Config.getMessage("authshield.help.register"), false);
        source.sendSuccess(() -> Config.getMessage("authshield.help.login"), false);
        source.sendSuccess(() -> Config.getMessage("authshield.help.changepassword"), false);
        
        if (isOp) {
            source.sendSuccess(() -> Config.getMessage("authshield.help.admin.unregister"), false);
            source.sendSuccess(() -> Config.getMessage("authshield.help.admin.setfirstspawn"), false);
        }
        
        source.sendSuccess(() -> Config.getMessage("authshield.help.footer"), false);
        return 1;
    }
} 