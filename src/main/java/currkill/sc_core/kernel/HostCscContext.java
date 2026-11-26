package currkill.sc_core.kernel;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;
import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.api.VariableStore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CSC 的宿主上下文实现：变量来自世界存档，输出接到执行者，流落到固定目录。
 *
 * <p>这里不含任何 Minecraft 类型，所以 Paper 适配层将来可以直接复用。
 *
 * <p>目录约束：
 * <ul>
 *   <li>日志流：{@code <world>/sc_logs/}</li>
 *   <li>文件流：{@code <config>/csc/files/}，拒绝绝对路径与 {@code ..}</li>
 * </ul>
 */
public final class HostCscContext implements CscContext {

    private final VariableStore variables;
    private final Consumer<String> out;
    private final Consumer<String> err;
    private final Consumer<String> gameLog;
    private final CommandLogger commandLog;
    private final ScPaths paths;
    private final Map<String, Stream> streams = new LinkedHashMap<>();
    private Runnable exitHandler;

    public HostCscContext(VariableStore variables, Consumer<String> out, Consumer<String> err,
                          Consumer<String> gameLog, CommandLogger commandLog, ScPaths paths) {
        this.variables = variables;
        this.out = out == null ? text -> {
        } : out;
        this.err = err == null ? text -> {
        } : err;
        this.gameLog = gameLog == null ? text -> {
        } : gameLog;
        this.commandLog = commandLog == null ? CommandLogger.NOOP : commandLog;
        this.paths = paths;
    }

    public void onExit(Runnable handler) {
        this.exitHandler = handler;
    }

    // ---------------------------------------------------------------- 变量

    @Override
    public boolean hasVariable(String name) {
        return variables.has(name);
    }

    @Override
    public CscValue getVariable(String name) {
        return variables.get(name);
    }

    @Override
    public CscType declaredType(String name) {
        return variables.typeOf(name);
    }

    @Override
    public void declareVariable(String name, CscType type, CscValue initial) {
        variables.declare(name, type, initial);
    }

    @Override
    public void setVariable(String name, CscValue value) {
        variables.set(name, value);
    }

    @Override
    public void deleteVariable(String name) {
        variables.delete(name);
    }

    @Override
    public Set<String> variableNames() {
        return variables.names();
    }

    // ---------------------------------------------------------------- 输出

    @Override
    public void cout(String text) {
        out.accept(text);
    }

    @Override
    public void cerr(String text) {
        err.accept(text);
    }

    // ---------------------------------------------------------------- 流

    @Override
    public boolean hasStream(String name) {
        return isBuiltinStream(name) || streams.containsKey(name);
    }

    /** {@code text} 与 {@code glog} 是内置流，不需要 log/file 打开。 */
    private static boolean isBuiltinStream(String name) {
        return "text".equals(name) || "glog".equals(name);
    }

    @Override
    public boolean openLog(String name, String fileName) {
        try {
            Path dir = paths.logsDir();
            Files.createDirectories(dir);
            return open(name, safeChild(dir, fileName));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean openFile(String name, String path) {
        try {
            Path dir = paths.cscFilesDir();
            Files.createDirectories(dir);
            Path target = safeChild(dir, path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            return open(name, target);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean open(String name, Path target) throws IOException {
        closeStream(name);
        BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        streams.put(name, new Stream(writer));
        return true;
    }

    @Override
    public boolean closeStream(String name) {
        Stream stream = streams.remove(name);
        if (stream == null) {
            return false;
        }
        stream.close();
        return true;
    }

    @Override
    public boolean setStreamLevel(String name, String level) {
        Stream stream = streams.get(name);
        if (stream == null) {
            return false;
        }
        stream.level = level == null ? "INFO" : level.toUpperCase();
        return true;
    }

    @Override
    public void writeStream(String name, String text, String level) {
        String effective = level == null ? "INFO" : level.toUpperCase();
        if ("text".equals(name)) {
            if ("ERR".equals(effective) || "ERROR".equals(effective)) {
                err.accept(text);
            } else {
                out.accept(text);
            }
            return;
        }
        if ("glog".equals(name)) {
            gameLog.accept("[" + effective + "] " + text);
            return;
        }
        Stream stream = streams.get(name);
        if (stream == null) {
            report(CscDiagnostic.severe("流不存在：" + name, -1));
            return;
        }
        stream.write((level != null ? effective : stream.level) + " " + text);
    }

    /** 关闭所有流，指令结束时调用。 */
    public void closeAll() {
        for (Stream stream : streams.values()) {
            stream.close();
        }
        streams.clear();
    }

    // ---------------------------------------------------------------- 诊断

    @Override
    public void report(CscDiagnostic diagnostic) {
        if (diagnostic.isSevere()) {
            err.accept("[CSC] " + diagnostic.message());
            gameLog.accept("[CSC] " + diagnostic.message());
            commandLog.severe(diagnostic.toString());
        } else {
            commandLog.conversion(diagnostic.toString());
        }
    }

    @Override
    public void exitScript() {
        if (exitHandler != null) {
            exitHandler.run();
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 只允许落在 base 之内：拒绝绝对路径与 {@code ..}。 */
    private static Path safeChild(Path base, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("路径为空");
        }
        Path candidate = Path.of(relative);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("不允许绝对路径：" + relative);
        }
        Path resolved = base.resolve(candidate).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new IllegalArgumentException("路径越界：" + relative);
        }
        return resolved;
    }

    private static final class Stream {
        private final BufferedWriter writer;
        private String level = "INFO";

        Stream(BufferedWriter writer) {
            this.writer = writer;
        }

        void write(String text) {
            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
                // 写流失败不影响脚本继续；下一次写还会重试
            }
        }

        void close() {
            try {
                writer.close();
            } catch (IOException ignored) {
                // 关不掉也没关系
            }
        }
    }
}
