package currkill.sc_core.forge;

import com.mojang.brigadier.context.CommandContext;
import currkill.sc_core.Sc_core;
import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.PlayerDataStore;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.api.VariableProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Forge 平台的 {@link ExecutionContext} 实现：把 Brigadier 的 {@code CommandContext} 包成内核能用的抽象。
 */
public final class ForgeExecutionContext implements ExecutionContext {

    private final CommandContext<CommandSourceStack> context;
    private final CommandSourceStack source;

    public ForgeExecutionContext(CommandContext<CommandSourceStack> context) {
        this.context = context;
        this.source = context.getSource();
    }

    @Override
    public boolean isPlayer() {
        return source.getEntity() instanceof ServerPlayer;
    }

    @Override
    public String senderName() {
        return source.getTextName();
    }

    @Override
    public String senderId() {
        Entity entity = source.getEntity();
        return entity == null ? "server" : entity.getUUID().toString();
    }

    @Override
    public String worldId() {
        return source.getLevel().dimension().location().toString();
    }

    @Override
    public String arg(String name) {
        Object raw = rawArgument(name);
        return raw == null ? "" : String.valueOf(raw);
    }

    @Override
    public Optional<String> optionalArg(String name) {
        Object raw = rawArgument(name);
        return raw == null ? Optional.empty() : Optional.of(String.valueOf(raw));
    }

    @Override
    public int intArg(String name) {
        String raw = arg(name);
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public double doubleArg(String name) {
        String raw = arg(name);
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0.0D;
        }
    }

    @Override
    public boolean boolArg(String name) {
        return Boolean.parseBoolean(arg(name).trim());
    }

    @Override
    public void reply(String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    @Override
    public void error(String message) {
        source.sendFailure(Component.literal(message));
    }

    @Override
    public void gameLog(String message) {
        Sc_core.LOGGER.warn("[SC] {}", message);
    }

    @Override
    public CommandLogger commandLog() {
        return SimpleCommandCore.commandLog();
    }

    @Override
    public ScPaths paths() {
        return SimpleCommandCore.paths();
    }

    @Override
    public VariableProvider variables() {
        return WorldVariableData.get(source.getServer());
    }

    @Override
    public PlayerDataStore players() {
        return PlayerDataStorage.get(source.getServer());
    }

    private Object rawArgument(String name) {
        try {
            return context.getArgument(name, Object.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
