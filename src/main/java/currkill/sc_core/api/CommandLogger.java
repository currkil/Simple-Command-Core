package currkill.sc_core.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * 指令日志。独立文件，按大小滚动，线程安全，IO 失败静默忽略（不能因为写日志把指令搞崩）。
 *
 * <p>默认落在 {@code <world>/sc_logs/command.log}。世界目录还不可用时不写。
 */
public final class CommandLogger {

    /** 什么都不做的日志器，用于服务器未就绪或测试。 */
    public static final CommandLogger NOOP = new CommandLogger((Supplier<Path>) null, 0L, 0);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_MAX_BYTES = 1024L * 1024L;
    private static final int DEFAULT_BACKUPS = 3;

    private final Supplier<Path> fileSupplier;
    private final long maxBytes;
    private final int maxBackups;
    private final Object lock = new Object();
    private boolean disabled;

    public CommandLogger(Path file, long maxBytes, int maxBackups) {
        this(file == null ? (Supplier<Path>) null : () -> file, maxBytes, maxBackups);
    }

    public CommandLogger(Supplier<Path> fileSupplier, long maxBytes, int maxBackups) {
        this.fileSupplier = fileSupplier;
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        this.maxBackups = maxBackups < 0 ? DEFAULT_BACKUPS : maxBackups;
    }

    public static CommandLogger at(Supplier<Path> fileSupplier) {
        return new CommandLogger(fileSupplier, DEFAULT_MAX_BYTES, DEFAULT_BACKUPS);
    }

    public void severe(String message) {
        write("ERROR", message);
    }

    /** 隐式转换只进指令日志，不打扰玩家。 */
    public void conversion(String message) {
        write("CONVERT", message);
    }

    public void info(String message) {
        write("INFO", message);
    }

    public void write(String level, String message) {
        if (disabled || fileSupplier == null) {
            return;
        }
        Path file;
        try {
            file = fileSupplier.get();
        } catch (RuntimeException e) {
            return;
        }
        if (file == null) {
            return;
        }
        String line = "[" + TIMESTAMP.format(LocalDateTime.now()) + "] [" + level + "] " + message
                + System.lineSeparator();
        synchronized (lock) {
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                rotateIfNeeded(file);
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException e) {
                // 写日志失败不能让指令失败：静默降级，避免每条指令都抛。
                disabled = true;
            }
        }
    }

    private void rotateIfNeeded(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        if (Files.size(file) < maxBytes) {
            return;
        }
        if (maxBackups <= 0) {
            Files.deleteIfExists(file);
            return;
        }
        for (int i = maxBackups - 1; i >= 1; i--) {
            Path from = sibling(file, i);
            Path to = sibling(file, i + 1);
            if (Files.isRegularFile(from)) {
                Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(file, sibling(file, 1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path sibling(Path file, int index) {
        return file.resolveSibling(file.getFileName() + "." + index);
    }
}
