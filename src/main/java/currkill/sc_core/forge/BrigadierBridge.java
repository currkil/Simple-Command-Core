package currkill.sc_core.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import currkill.sc_core.Sc_core;
import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.api.CommandEffect;
import currkill.sc_core.api.ScArgumentType;
import currkill.sc_core.api.ScCommandNode;
import currkill.sc_core.kernel.CommandRouter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Brigadier 适配层：把加载器无关的 {@link ScCommandNode} 树接到 Forge 的指令注册事件上。
 *
 * <p>这是唯一 import Brigadier 的地方（配合 {@link ForgeExecutionContext}），
 * 内核部分保持平台无关。
 */
public final class BrigadierBridge {

    private BrigadierBridge() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandRouter router = new CommandRouter(SimpleCommandCore.namespaces(), Sc_core::warn);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sc");
        for (ScCommandNode child : router.scChildren()) {
            root.then(toBuilder(child));
        }
        dispatcher.register(root);

        for (ScCommandNode alias : router.aliasRoots()) {
            if (!alias.isLiteral()) {
                continue;
            }
            registerAlias(dispatcher, alias);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerAlias(CommandDispatcher<CommandSourceStack> dispatcher, ScCommandNode alias) {
        dispatcher.register((LiteralArgumentBuilder<CommandSourceStack>) toBuilder(alias));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> toBuilder(ScCommandNode node) {
        ArgumentBuilder<CommandSourceStack, ?> builder = node.isLiteral()
                ? Commands.literal(node.name())
                : Commands.argument(node.name(), argumentType(node.type()));

        if (node.permissionLevel() > 0) {
            int level = node.permissionLevel();
            builder.requires(source -> source.hasPermission(level));
        }
        CommandEffect effect = node.effect();
        if (effect != null) {
            builder.executes(context -> run(effect, context));
        }
        for (ScCommandNode child : node.children()) {
            builder.then(toBuilder(child));
        }
        return builder;
    }

    private static ArgumentType<?> argumentType(ScArgumentType type) {
        return switch (type) {
            case STRING -> StringArgumentType.string();
            case WORD -> StringArgumentType.word();
            case GREEDY_STRING -> StringArgumentType.greedyString();
            case INTEGER -> IntegerArgumentType.integer();
            case DOUBLE -> DoubleArgumentType.doubleArg();
            case BOOLEAN -> BoolArgumentType.bool();
        };
    }

    private static int run(CommandEffect effect, CommandContext<CommandSourceStack> context) {
        ForgeExecutionContext execution = new ForgeExecutionContext(context);
        try {
            return effect.execute(execution);
        } catch (RuntimeException e) {
            execution.error("指令执行失败：" + e);
            execution.gameLog("指令执行失败：" + e);
            return 0;
        }
    }
}
