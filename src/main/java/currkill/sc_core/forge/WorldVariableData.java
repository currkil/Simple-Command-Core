package currkill.sc_core.forge;

import currkill.sc_core.api.VariableProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 世界级变量表：SavedData 挂在 overworld，不分玩家，跨世界不共享。
 */
public final class WorldVariableData extends SavedData implements VariableProvider {

    private static final String DATA_NAME = "sc_variables";
    private static final String TAG_VALUES = "values";

    private final Map<String, String> values = new LinkedHashMap<>();

    private WorldVariableData() {
    }

    private WorldVariableData(CompoundTag tag) {
        CompoundTag stored = tag.getCompound(TAG_VALUES);
        for (String key : stored.getAllKeys()) {
            values.put(key, stored.getString(key));
        }
    }

    public static WorldVariableData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WorldVariableData::new, WorldVariableData::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        values.forEach(stored::putString);
        tag.put(TAG_VALUES, stored);
        return tag;
    }

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    @Override
    public void set(String name, String value) {
        values.put(name, value);
        setDirty();
    }

    @Override
    public void delete(String name) {
        if (values.remove(name) != null) {
            setDirty();
        }
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(values.keySet());
    }
}
