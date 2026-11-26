package currkill.sc_core;

import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.forge.BrigadierBridge;
import currkill.sc_core.forge.ReplHandler;
import currkill.sc_core.kernel.CoreServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SC Core 的模组入口。只做装配：把平台相关的路径/日志/事件接到内核上，不写任何业务逻辑。
 */
@Mod(SimpleCommandCore.MOD_ID)
public class Sc_core {

    public static final Logger LOGGER = LoggerFactory.getLogger("sc_core");

    public Sc_core() {
        Path configRoot = FMLPaths.CONFIGDIR.get().resolve("sc");
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path homeDir = Paths.get(System.getProperty("user.home", "."));

        ScPaths paths = new ScPaths(configRoot, gameDir, homeDir, Sc_core::worldRoot);
        CommandLogger commandLog = CommandLogger.at(() -> {
            Path world = worldRoot();
            return world == null ? null : world.resolve("sc_logs").resolve("command.log");
        });

        SimpleCommandCore.init(new CoreServices(paths, commandLog, Sc_core::warn));
        SimpleCommandCore.registerBuiltins();

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ReplHandler::onChat);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BrigadierBridge.register(event.getDispatcher());
    }

    /** 世界存档根目录；服务器没起来返回 null（{@link ScPaths} 与日志会自行降级）。 */
    private static Path worldRoot() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        try {
            return server.getWorldPath(LevelResource.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void warn(String message) {
        LOGGER.warn("[SC] {}", message);
    }
}
