package currkill.sc_core.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 全家桶统一的路径解析：世界目录、配置目录、备份目录、日志目录、缓存目录。
 *
 * <p>配置：{@code <game>/config/sc/<namespace>.toml}
 * <br>备份：{@code <world>/sc_backups/}
 * <br>日志：{@code <world>/sc_logs/}
 * <br>缓存：{@code ~/.sc/mapping/}（跨存档共享）
 *
 * <p>世界目录在服务器启动前还不存在，因此用 {@link Supplier} 延迟取。
 */
public final class ScPaths {

    private final Path configRoot;
    private final Path gameDir;
    private final Path homeDir;
    private final Supplier<Path> worldRoot;

    /**
     * @param configRoot 全家桶配置根目录，通常是 {@code <game>/config/sc}
     * @param gameDir    游戏目录
     * @param homeDir    用户主目录，用于跨存档共享的缓存
     * @param worldRoot  世界存档根目录的取用器，服务器未启动时可返回 {@code null}
     */
    public ScPaths(Path configRoot, Path gameDir, Path homeDir, Supplier<Path> worldRoot) {
        this.configRoot = configRoot;
        this.gameDir = gameDir;
        this.homeDir = homeDir;
        this.worldRoot = worldRoot;
    }

    /** 配置根目录 {@code <game>/config/sc}。 */
    public Path configRoot() {
        return configRoot;
    }

    /** 游戏目录。 */
    public Path gameDir() {
        return gameDir;
    }

    /** 用户主目录。 */
    public Path homeDir() {
        return homeDir;
    }

    /** 世界存档根目录；服务器还没起来时为 {@code null}。 */
    public Path worldRoot() {
        return worldRoot == null ? null : worldRoot.get();
    }

    private Path requireWorld() {
        Path world = worldRoot();
        if (world == null) {
            throw new IllegalStateException("世界目录尚不可用（服务器未启动）");
        }
        return world;
    }

    /** 某个命名空间的配置文件：{@code config/sc/<namespace>.toml}。 */
    public Path namespaceConfig(String namespace) {
        return configRoot.resolve(namespace + ".toml");
    }

    /** 世界级备份目录 {@code <world>/sc_backups}。 */
    public Path backupsDir() {
        return requireWorld().resolve("sc_backups");
    }

    /** 世界级日志目录 {@code <world>/sc_logs}。 */
    public Path logsDir() {
        return requireWorld().resolve("sc_logs");
    }

    /** 日志目录下的一个文件。 */
    public Path logFile(String name) {
        return logsDir().resolve(name);
    }

    /** 跨存档共享的缓存目录 {@code ~/.sc/mapping}。 */
    public Path cacheDir() {
        return homeDir.resolve(".sc").resolve("mapping");
    }

    /** 缓存目录下的一个文件。 */
    public Path cacheFile(String name) {
        return cacheDir().resolve(name);
    }

    /** 脚本目录 {@code config/sc/scripts}。 */
    public Path scriptsDir() {
        return configRoot.resolve("scripts");
    }

    /** 脚本文件；不带 {@code .csc} 后缀时自动补上。 */
    public Path scriptFile(String name) {
        String fileName = name.toLowerCase().endsWith(".csc") ? name : name + ".csc";
        return scriptsDir().resolve(fileName);
    }

    /** CSC 文件流的根目录 {@code config/sc/csc/files}；脚本只能写到这里面。 */
    public Path cscFilesDir() {
        return configRoot.resolve("csc").resolve("files");
    }

    /** 确保目录存在，失败直接抛出（配置/日志目录属于必需路径）。 */
    public static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建目录 " + dir, e);
        }
    }

    /** 缓存目录允许失败：写不进去就退回内存缓存，不崩。 */
    public boolean ensureCacheDir(Path dir) {
        try {
            Files.createDirectories(dir);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 把文本写到缓存文件；失败返回 false（调用方应退回内存缓存）。 */
    public boolean writeCache(String name, String content) {
        Path dir = cacheDir();
        if (!ensureCacheDir(dir)) {
            return false;
        }
        try {
            Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 读缓存文件；不存在或读失败返回 {@code null}。 */
    public String readCache(String name) {
        try {
            Path file = cacheDir().resolve(name);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
