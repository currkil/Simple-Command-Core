package currkill.sc_core.forge;

import currkill.sc_csc.api.CscEvaluator;
import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.kernel.HostCscContext;
import currkill.sc_core.kernel.SessionStreams;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;

/**
 * 聊天栏 REPL：处于 REPL 模式的玩家，每条聊天消息都当作 CSC 语句执行。
 *
 * <p>消息会被取消，不会真的发到公屏。日志流/文件流挂在会话上下文上，所以跨多行输入
 * 也能继续往同一个流里写；{@code sys exit} 会关掉会话、退出 REPL 并给出提示。
 */
public final class ReplHandler {

    private ReplHandler() {
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String id = player.getUUID().toString();
        SessionStreams sessions = SimpleCommandCore.services().sessions();

        if (!SimpleCommandCore.services().repl().isActive(id)) {
            // 不在 REPL 时顺手清掉可能残留的会话，避免文件句柄泄漏
            if (sessions.get(id) != null) {
                sessions.close(id);
            }
            return;
        }
        event.setCanceled(true);

        ForgeExecutionContext execution = new ForgeExecutionContext(player.createCommandSourceStack());
        HostCscContext context;
        HostCscContext existing = sessions.get(id);
        if (existing != null) {
            context = existing;
        } else {
            if (!(execution.csc() instanceof HostCscContext created)) {
                return;
            }
            context = sessions.of(id, () -> created);
        }

        context.onExit(() -> {
            sessions.close(id);
            SimpleCommandCore.services().repl().disable(id);
            player.sendSystemMessage(Component.literal("[CSC] 已退出 REPL"));
        });
        CscEvaluator.evaluateScript(event.getRawText(), context);
    }
}
