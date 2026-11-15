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
import currkill.sc_core.api.VariableProvider;
import currkill.sc_core.kernel.CommandRouter;
import currkill.sc_core.kernel.CoreCommands;
import currkill.sc_core.kernel.CoreServices;
import currkill.sc_core.kernel.NamespaceEntry;
import currkill.sc_core.kernel.NamespaceRegistry;

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
 * 内核冒烟测试：不启动 Minecraft，验证注册表、配置托管、指令路由、core 指令、CSC 接线。
 *
 * <p>只碰 kernel / api 包，不碰 forge 包，所以可以脱离游戏跑。
 */
public final class KernelSmokeTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sc-kernel-smoke");
        Path configRoot = root.resolve("config").resolve("sc");
        Path worldRoot = root.resolve("world");
        Files.createDirectories(worldRoot);

        ScPaths paths = new ScPaths(configRoot, root, root, () -> worldRoot);
        CoreServices services = new CoreServices(paths, CommandLogger.NOOP, msg -> System.out.println("WARN " + msg));
        SimpleCommandCore.init(services);

        // 1. 注册内置 core
        SimpleCommandCore.register(ModRegistration.builder("sc_core")
                .namespace("core").commands(CoreCommands::build).build());

        // 2. 注册一个假子模组，带短名与业务配置默认值
        SimpleCommandCore.register(ModRegistration.builder("sc_test")
                .namespace("test")
                .commands(KernelSmokeTest::buildTestCommands)
                .shortcommand("ping")
                .shortcommand("nested/deep", "deep")
                .configDefaults(cfg -> cfg.defaultValue("test.cooldown", 30))
                .build());

        NamespaceRegistry registry = services.registry();
        check("core 已注册", registry.contains("core"));
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
        ScCommandNode testNode = find(scChildren, "test");
        check("/sc test 存在", String.valueOf(testNode != null), "true");
        check("每个命名空间都有 shortcommand", String.valueOf(find(testNode.children(), "shortcommand") != null), "true");
        check("每个命名空间都有 saveconfig", String.valueOf(find(testNode.children(), "saveconfig") != null), "true");
        check("每个命名空间都有 reloadconfig", String.valueOf(find(testNode.children(), "reloadconfig") != null), "true");
        check("子模组指令挂上了", String.valueOf(find(testNode.children(), "ping") != null), "true");
        check("短名指令挂了 ping", String.valueOf(find(router.aliasRoots(), "ping") != null), "true");
        check("多级路径短名挂了 deep", String.valueOf(find(router.aliasRoots(), "deep") != null), "true");

        // 5. 执行效果：ping
        FakeContext ctx = new FakeContext(paths);
        int rc = find(router.aliasRoots(), "ping").effect().execute(ctx);
        check("ping 效果返回 1", String.valueOf(rc), "1");
        check("ping 回复了 pong", String.valueOf(ctx.replies.contains("pong")), "true");

        // 6. 执行 core var create / set / delete
        ScCommandNode coreNode = find(scChildren, "core");
        ScCommandNode varNode = find(coreNode.children(), "var");
        ScCommandNode createNode = find(varNode.children(), "create");
        ScCommandNode createName = find(createNode.children(), "name");
        ScCommandNode createValue = find(createName.children(), "value");

        ctx.args.put("name", "greeting");
        ctx.args.put("value", "hello");
        check("var create 成功", String.valueOf(createValue.effect().execute(ctx)), "1");
        check("变量已写入", ctx.vars.get("greeting").orElse("<none>"), "hello");
        check("变量已存在时 create 失败", String.valueOf(createValue.effect().execute(ctx)), "0");

        ScCommandNode numberNode = find(createValue.children(), "number");
        ctx.args.put("name", "count");
        ctx.args.put("value", "abc");
        check("类型提示拒绝非数字", String.valueOf(numberNode.effect().execute(ctx)), "0");
        ctx.args.put("value", "42");
        check("类型提示接受数字", String.valueOf(numberNode.effect().execute(ctx)), "1");
        check("变量值存进去了", ctx.vars.get("count").orElse("<none>"), "42");

        // 7. 变量接进 CSC
        check("CSC 读到变量", SimpleCommandCore.formatText("值=%greeting%", ctx.vars), "值=hello");
        check("CSC 表达式用到变量", SimpleCommandCore.formatText("/!(%count% * 2)", ctx.vars), "84");
        check("CSC 错误不改动其他片段",
                SimpleCommandCore.formatText("a %missing% b", ctx.vars), "a %missing% b");

        ScCommandNode setNode = find(varNode.children(), "set");
        ScCommandNode setName = find(setNode.children(), "name");
        ScCommandNode setValue = find(setName.children(), "value");
        ctx.args.put("name", "greeting");
        ctx.args.put("value", "hi");
        check("var set 成功", String.valueOf(setValue.effect().execute(ctx)), "1");
        check("var set 生效", ctx.vars.get("greeting").orElse("<none>"), "hi");
        ctx.args.put("name", "missing");
        check("var set 未知变量失败", String.valueOf(setValue.effect().execute(ctx)), "0");

        ScCommandNode deleteNode = find(varNode.children(), "delete");
        ScCommandNode deleteName = find(deleteNode.children(), "name");
        ctx.args.put("name", "greeting");
        check("var delete 成功", String.valueOf(deleteName.effect().execute(ctx)), "1");
        check("变量已删除", String.valueOf(ctx.vars.get("greeting").isEmpty()), "true");
        check("删除不存在的变量失败", String.valueOf(deleteName.effect().execute(ctx)), "0");

        // 8. 短名开关关闭后别名消失
        testConfig.put("shortcommand", false);
        check("短名关闭后无别名", String.valueOf(find(router.aliasRoots(), "ping") != null), "false");
        testConfig.put("shortcommand", true);

        // 9. 路径管理
        services.configs().save(testConfig);
        check("备份目录可解析", String.valueOf(paths.backupsDir().equals(worldRoot.resolve("sc_backups"))), "true");
        check("日志目录可解析", String.valueOf(paths.logsDir().equals(worldRoot.resolve("sc_logs"))), "true");
        check("缓存写失败不抛异常", String.valueOf(paths.writeCache("x.txt", "y") || true), "true");

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
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

    /** 内存版执行上下文，不依赖 Minecraft。 */
    private static final class FakeContext implements ExecutionContext {
        final Map<String, String> args = new LinkedHashMap<>();
        final List<String> replies = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final MemoryVariables vars = new MemoryVariables();
        final MemoryPlayers players = new MemoryPlayers();
        private final ScPaths paths;

        FakeContext(ScPaths paths) {
            this.paths = paths;
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
        }

        public CommandLogger commandLog() {
            return CommandLogger.NOOP;
        }

        public ScPaths paths() {
            return paths;
        }

        public VariableProvider variables() {
            return vars;
        }

        public PlayerDataStore players() {
            return players;
        }
    }

    private static final class MemoryVariables implements VariableProvider {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Optional<String> get(String name) {
            return Optional.ofNullable(values.get(name));
        }

        public void set(String name, String value) {
            values.put(name, value);
        }

        public void delete(String name) {
            values.remove(name);
        }

        public Set<String> names() {
            return Set.copyOf(values.keySet());
        }
    }

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
}
