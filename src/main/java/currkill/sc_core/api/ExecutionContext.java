package currkill.sc_core.api;

import java.util.Optional;

/**
 * 指令执行上下文。不依赖 Brigadier：Brigadier 适配层构造它，Paper 适配层将来也构造它。
 *
 * <p>效果类（{@link CommandEffect}）只通过本接口拿参数、回消息、取变量、取路径。
 */
public interface ExecutionContext {

    /** 是不是玩家执行的（控制台/命令方块为 false）。 */
    boolean isPlayer();

    /** 执行者名字：玩家名，或 {@code Server}。 */
    String senderName();

    /** 执行者 id：玩家 UUID 字符串，或 {@code server}。 */
    String senderId();

    /** 所在维度 id，例如 {@code minecraft:overworld}。 */
    String worldId();

    /** 取一个字符串参数；参数不存在时返回空串。 */
    String arg(String name);

    Optional<String> optionalArg(String name);

    /** 取整数参数；缺失或不是数字时返回 0。 */
    int intArg(String name);

    double doubleArg(String name);

    boolean boolArg(String name);

    /** 给执行者发一条普通消息。 */
    void reply(String message);

    /** 给执行者发一条错误消息（红色）。 */
    void error(String message);

    /** 写游戏日志（服务端日志文件）。 */
    void gameLog(String message);

    /** 指令日志（sc_logs/command.log）。 */
    CommandLogger commandLog();

    /** 路径解析。 */
    ScPaths paths();

    /** 当前世界的变量表。 */
    VariableProvider variables();

    /** 跨世界的玩家数据。 */
    PlayerDataStore players();
}
