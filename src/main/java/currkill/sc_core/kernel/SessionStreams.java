package currkill.sc_core.kernel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 长生命周期会话的 CSC 上下文。
 *
 * <p>日志流与文件流是执行期资源，正常情况下一条指令执行完就该关闭。但 REPL 是「逐行输入」的
 * 连续会话：`log new log1 to x` 与下一行的 `io cout "..." to log1` 必须落在同一个上下文里，
 * 否则流会消失。这里按执行者 id 缓存上下文，REPL 退出时统一关闭。
 */
public final class SessionStreams {

    private final Map<String, HostCscContext> sessions = new HashMap<>();

    /** 取（或创建）某个执行者的会话上下文。 */
    public synchronized HostCscContext of(String id, Supplier<HostCscContext> factory) {
        return sessions.computeIfAbsent(id, key -> factory.get());
    }

    public synchronized HostCscContext get(String id) {
        return sessions.get(id);
    }

    /** 关闭并丢弃会话：所有打开的流都会落盘。 */
    public synchronized void close(String id) {
        HostCscContext context = sessions.remove(id);
        if (context != null) {
            context.closeAll();
        }
    }

    public synchronized void closeAll() {
        for (HostCscContext context : sessions.values()) {
            context.closeAll();
        }
        sessions.clear();
    }

    public synchronized int size() {
        return sessions.size();
    }

}
