import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;
import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.Home;
import currkill.sc_core.api.ModRegistration;
import currkill.sc_core.api.NamespaceConfig;
import currkill.sc_core.api.PlayerDataStore;
import currkill.sc_core.api.ScArgumentType;
import currkill.sc_core.api.ScCommandNode;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.api.VariableStore;
import currkill.sc_core.kernel.CommandRouter;
import currkill.sc_core.kernel.CoreServices;
import currkill.sc_core.kernel.CscCommands;
import currkill.sc_core.kernel.HostCscContext;
import currkill.sc_core.kernel.NamespaceEntry;
import currkill.sc_core.kernel.NamespaceRegistry;
import currkill.sc_core.kernel.ValueCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 内核冒烟测试：不启动 Minecraft，验证注册表、配置托管、指令路由、CSC 指令、
 * 变量编码、流目录与 REPL 会话表。
 */
public final class KernelSmokeTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sc-core-smoke");
        Path configRoot = root.resolve("config").resolve("sc");
        Path worldRoot = root.resolve("world");
        Files.createDirectories(worldRoot);

        ScPaths paths = new ScPaths(configRoot, root, root, () -> worldRoot);
        CoreServices services = new CoreServices(paths, CommandLogger.NOOP, msg -> System.out.println("WARN " + msg));
        SimpleCommandCore.init(services);

        // 1. 注册内置 csc 命名空间
        SimpleCommandCore.register(ModRegistration.builder("sc_core")
                .namespace("csc").commands(CscCommands::build).build());

        // 2. 再注册一个假子模组，验证短名与配置托管
        SimpleCommandCore.register(ModRegistration.builder("sc_test")
                .namespace("test")
                .commands(KernelSmokeTest::buildTestCommands)
                .shortcommand("ping")
                .shortcommand("nested/deep", "deep")
                .configDefaults(cfg -> cfg.defaultValue("test.cooldown", 30))
                .build());

        NamespaceRegistry registry = services.registry();
        check("csc 已注册", registry.contains("csc"));
        check("test 已注册", registry.contains("test"));
        check("重复注册被拒绝",
                String.valueOf(registry.register(ModRegistration.builder("x").namespace("test").build())), "false");

        // 3. 配置托管：保存 -> 重载
        NamespaceEntry testEntry = registry.get("test").orElseThrow();
        NamespaceConfig testConfig = testEntry.config();
        check("业务默认值已补齐", String.valueOf(testConfig.getInt("test.cooldown", -1)), "30");
        testConfig.put("shortcommand", false);
        testConfig.put("test.cooldown", 45);
        services.configs().save(testConfig);
        check("配置文件已写出", String.valueOf(Files.isRegularFile(configRoot.resolve("test.toml"))), "true");

        NamespaceConfig reloaded = new NamespaceConfig("test");
        reloaded.defaultValue("shortcommand", true);
        reloaded.defaultValue("test.cooldown", 30);
        services.configs().load("test", reloaded);
        check("重载后拿到写入值", String.valueOf(reloaded.getBoolean("shortcommand", true)), "false");
        check("重载后业务配置还在", String.valueOf(reloaded.getInt("test.cooldown", -1)), "45");
        testConfig.put("shortcommand", true);
        testConfig.put("test.cooldown", 30);

        // 4. 指令路由
        CommandRouter router = new CommandRouter(registry, msg -> System.out.println("WARN " + msg));
        List<ScCommandNode> scChildren = router.scChildren();
        ScCommandNode cscNode = find(scChildren, "csc");
        check("/sc csc 存在", String.valueOf(cscNode != null), "true");
        check("每个命名空间都有 shortcommand", String.valueOf(find(cscNode.children(), "shortcommand") != null), "true");
        check("csc 下有 run", String.valueOf(find(cscNode.children(), "run") != null), "true");
        check("csc 下有 repl", String.valueOf(find(cscNode.children(), "repl") != null), "true");
        check("csc 下有语句入口", String.valueOf(find(cscNode.children(), "statement") != null), "true");
        check("短名 ping 已注册", String.valueOf(find(router.aliasRoots(), "ping") != null), "true");
        check("多级路径短名 deep 已注册", String.valueOf(find(router.aliasRoots(), "deep") != null), "true");

        // 5. 通过 /sc csc <语句> 走完整链路
        FakeContext ctx = new FakeContext(paths);
        ScCommandNode statement = find(cscNode.children(), "statement");

        run(statement, ctx, "var new a int 1");
        check("语句：var new", text(ctx.vars.get("a")), "1");
        run(statement, ctx, "var set a %a% + 41");
        check("语句：var set 求值", text(ctx.vars.get("a")), "42");
        run(statement, ctx, "io cout \"a is %a%\" to text");
        check("语句：io cout", ctx.lastReply(), "a is 42");
        run(statement, ctx, "list new arr int [1, 2, 3]");
        check("语句：list new", text(ctx.vars.get("arr")), "[1, 2, 3]");
        check("语句：列表长度", String.valueOf(ctx.vars.get("arr").size()), "3");
        run(statement, ctx, "var delete a");
        check("语句：var delete", String.valueOf(ctx.vars.has("a")), "false");
        run(statement, ctx, "var set missing 1");
        check("语句错误会报到执行者", String.valueOf(ctx.hasError("变量不存在")), "true");

        // 6. run：读脚本文件
        Path script = paths.scriptFile("demo");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "var new x int 7\nio cout \"x is %x%\" to text\n");
        ScCommandNode runNode = find(cscNode.children(), "run");
        ScCommandNode scriptArg = find(runNode.children(), "script");
        ctx.args.put("script", "demo");
        expectCode("run 返回 1", scriptArg.effect().execute(ctx), 1);
        check("run 执行了脚本", ctx.lastReply(), "x is 7");

        ctx.args.put("script", "not-exists");
        expectCode("run 找不到脚本时失败", scriptArg.effect().execute(ctx), 0);

        // 7. 文件流与日志流（同一条指令里用 ; 分隔多条语句，流在本次执行内有效）
        run(statement, ctx, "file new f1 to notes.txt; io cout \"hello\" to f1");
        Path fileStream = paths.cscFilesDir().resolve("notes.txt");
        check("文件流落在固定目录", String.valueOf(Files.isRegularFile(fileStream)), "true");
        check("文件流内容", Files.readString(fileStream).trim(), "INFO hello");

        run(statement, ctx, "file new bad to ../escape.txt");
        check("文件流拒绝越界路径", String.valueOf(ctx.hasError("无法新建文件流")), "true");

        run(statement, ctx, "log new log1 to smoke.log; io cout \"line\" to log1");
        Path logFile = worldRoot.resolve("sc_logs").resolve("smoke.log");
        check("日志流落在 sc_logs", String.valueOf(Files.isRegularFile(logFile)), "true");
        check("日志流内容", Files.readString(logFile).trim(), "INFO line");

        // glog 是内置流，直接进游戏日志
        run(statement, ctx, "io cout \"to game log\" to glog");
        check("glog 写游戏日志", ctx.lastGameLog(), "[INFO] to game log");

        // 会话级上下文：跨多次执行保持日志流
        String sid = "session-player";
        HostCscContext session = services.sessions().of(sid, () -> new HostCscContext(
                ctx.vars, ctx::reply, ctx::error, ctx::gameLog, CommandLogger.NOOP, paths));
        currkill.sc_csc.api.CscEvaluator.evaluateScript("log new s1 to session.log", session);
        currkill.sc_csc.api.CscEvaluator.evaluateScript("io cout \"across lines\" to s1", session);
        services.sessions().close(sid);
        Path sessionLog = worldRoot.resolve("sc_logs").resolve("session.log");
        check("会话流跨多次执行", Files.readString(sessionLog).trim(), "INFO across lines");
        check("关闭会话后不残留", String.valueOf(services.sessions().size()), "0");

        // 8. 值的编码往返
        checkRoundTrip("int", CscValue.ofInt(-42));
        checkRoundTrip("float", CscValue.ofFloat(1.5D));
        checkRoundTrip("string", CscValue.ofString("a;b:c\n\"d\""));
        checkRoundTrip("bool", CscValue.ofBool(true));
        checkRoundTrip("list", CscValue.ofList(List.of(CscValue.ofInt(1), CscValue.ofString("x"))));
        checkRoundTrip("嵌套 list", CscValue.ofList(List.of(
                CscValue.ofList(List.of(CscValue.ofInt(1))), CscValue.ofBool(false))));

        // 9. REPL 会话表
        check("REPL 默认关闭", String.valueOf(services.repl().isActive("u1")), "false");
        services.repl().toggle("u1");
        check("REPL 可开启", String.valueOf(services.repl().isActive("u1")), "true");
        services.repl().toggle("u1");
        check("REPL 可关闭", String.valueOf(services.repl().isActive("u1")), "false");

        // 10. 路径约定
        check("备份目录", paths.backupsDir().toString(), worldRoot.resolve("sc_backups").toString());
        check("脚本目录", paths.scriptsDir().toString(), configRoot.resolve("scripts").toString());
        check("文件流根目录", paths.cscFilesDir().toString(),
                configRoot.resolve("csc").resolve("files").toString());

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- 辅助

    private static void run(ScCommandNode statementNode, FakeContext ctx, String statement) {
        ctx.args.put("statement", statement);
        statementNode.effect().execute(ctx);
    }

    private static String text(CscValue value) {
        return value == null ? "<null>" : value.asString();
    }

    private static void checkRoundTrip(String label, CscValue value) {
        CscValue decoded = ValueCodec.decode(ValueCodec.encode(value));
        check("编码往返 " + label,
                decoded == null ? "<null>" : decoded.type().keyword() + ":" + decoded.asString(),
                value.type().keyword() + ":" + value.asString());
    }

    private static List<ScCommandNode> buildTestCommands() {
        List<ScCommandNode> roots = new ArrayList<>();
        roots.add(ScCommandNode.literal("ping", ctx -> {
            ctx.reply("pong");
            return 1;
        }));
        roots.add(ScCommandNode.literal("nested").then(ScCommandNode.literal("deep", ctx -> 1)));
        roots.add(ScCommandNode.literal("typed").then(
                ScCommandNode.argument("value", ScArgumentType.GREEDY_STRING, ctx -> 1)));
        return roots;
    }

    private static ScCommandNode find(List<ScCommandNode> nodes, String name) {
        for (ScCommandNode node : nodes) {
            if (node.name().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private static void check(String label, boolean condition) {
        check(label, String.valueOf(condition), "true");
    }

    private static void check(String label, String actual, String expected) {
        if (expected.equals(actual)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + label + " -> got [" + actual + "] want [" + expected + "]");
        }
    }

    private static void expectCode(String label, int actual, int expected) {
        check(label, Integer.toString(actual), Integer.toString(expected));
    }

    /** 内存版变量表，行为与世界存档一致。 */
    private static final class MemoryVariables implements VariableStore {
        private final Map<String, CscValue> values = new LinkedHashMap<>();
        private final Map<String, CscType> types = new LinkedHashMap<>();

        public boolean has(String name) {
            return values.containsKey(name);
        }

        public CscValue get(String name) {
            return values.get(name);
        }

        public CscType typeOf(String name) {
            return types.get(name);
        }

        public void declare(String name, CscType type, CscValue initial) {
            values.put(name, initial);
            types.put(name, type);
        }

        public void set(String name, CscValue value) {
            values.put(name, value);
            types.putIfAbsent(name, value.type());
        }

        public void delete(String name) {
            values.remove(name);
            types.remove(name);
        }

        public Set<String> names() {
            return Set.copyOf(values.keySet());
        }

        public Map<String, CscValue> snapshot() {
            return Map.copyOf(values);
        }
    }

    /** 内存版玩家数据。 */
    private static final class MemoryPlayers implements PlayerDataStore {
        private final Map<UUID, Map<String, String>> data = new LinkedHashMap<>();

        public Optional<String> get(UUID player, String key) {
            return Optional.ofNullable(data.getOrDefault(player, Map.of()).get(key));
        }

        public void set(UUID player, String key, String value) {
            data.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(key, value);
        }

        public void remove(UUID player, String key) {
            data.getOrDefault(player, new LinkedHashMap<>()).remove(key);
        }

        public Set<String> keys(UUID player) {
            return Set.copyOf(data.getOrDefault(player, Map.of()).keySet());
        }

        public List<Home> homes(UUID player) {
            return List.of();
        }

        public void setHome(UUID player, Home home) {
        }

        public boolean removeHome(UUID player, String name) {
            return false;
        }
    }

    /** 内存版执行上下文。 */
    private static final class FakeContext implements ExecutionContext {
        final Map<String, String> args = new LinkedHashMap<>();
        final List<String> replies = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> gameLogs = new ArrayList<>();
        final MemoryVariables vars = new MemoryVariables();
        final MemoryPlayers players = new MemoryPlayers();
        private final ScPaths paths;

        FakeContext(ScPaths paths) {
            this.paths = paths;
        }

        String lastReply() {
            return replies.isEmpty() ? "" : replies.get(replies.size() - 1);
        }

        boolean hasError(String fragment) {
            return errors.stream().anyMatch(e -> e.contains(fragment));
        }

        public boolean isPlayer() {
            return true;
        }

        public String senderName() {
            return "Tester";
        }

        public String senderId() {
            return "00000000-0000-0000-0000-000000000001";
        }

        public String worldId() {
            return "minecraft:overworld";
        }

        public String arg(String name) {
            return args.getOrDefault(name, "");
        }

        public Optional<String> optionalArg(String name) {
            return Optional.ofNullable(args.get(name));
        }

        public int intArg(String name) {
            try {
                return Integer.parseInt(arg(name));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public double doubleArg(String name) {
            try {
                return Double.parseDouble(arg(name));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public boolean boolArg(String name) {
            return Boolean.parseBoolean(arg(name));
        }

        public void reply(String message) {
            replies.add(message);
        }

        public void error(String message) {
            errors.add(message);
        }

        public void gameLog(String message) {
            gameLogs.add(message);
        }

        String lastGameLog() {
            return gameLogs.isEmpty() ? "" : gameLogs.get(gameLogs.size() - 1);
        }

        public CommandLogger commandLog() {
            return CommandLogger.NOOP;
        }

        public ScPaths paths() {
            return paths;
        }

        public VariableStore variables() {
            return vars;
        }

        public PlayerDataStore players() {
            return players;
        }

        public currkill.sc_csc.api.CscContext csc() {
            return new HostCscContext(vars, this::reply, this::error, this::gameLog,
                    CommandLogger.NOOP, paths);
        }
    }
}
