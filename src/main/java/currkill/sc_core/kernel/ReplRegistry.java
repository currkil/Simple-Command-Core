package currkill.sc_core.kernel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天栏 REPL 的会话表：谁现在是「逐行执行脚本」模式。
 *
 * <p>放在内核里，好让指令层与平台的聊天事件层共用同一份状态。
 */
public final class ReplRegistry {

    private final Set<String> active = ConcurrentHashMap.newKeySet();

    /** 切换某个执行者的 REPL 状态，返回切换后的状态。 */
    public boolean toggle(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return false;
        }
        if (active.contains(senderId)) {
            active.remove(senderId);
            return false;
        }
        active.add(senderId);
        return true;
    }

    public boolean isActive(String senderId) {
        return senderId != null && active.contains(senderId);
    }

    public void disable(String senderId) {
        if (senderId != null) {
            active.remove(senderId);
        }
    }

    public int size() {
        return active.size();
    }
}
