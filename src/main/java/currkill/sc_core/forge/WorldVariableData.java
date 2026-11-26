package currkill.sc_core.forge;

import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;
import currkill.sc_core.api.VariableStore;
import currkill.sc_core.kernel.ValueCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 世界级变量表：SavedData 挂在 overworld，不分玩家，跨世界不共享。
 *
 * <p>每个变量存「声明类型 + 编码后的值」，所以存档里保留类型信息，读回来类型不丢。
 */
public final class WorldVariableData extends SavedData implements VariableStore {

    private static final String DATA_NAME = "sc_variables";
    private static final String TAG_VALUES = "values";
    private static final String TAG_TYPE = "type";
    private static final String TAG_VALUE = "value";

    private final Map<String, CscValue> values = new LinkedHashMap<>();
    private final Map<String, CscType> types = new LinkedHashMap<>();

    private WorldVariableData() {
    }

    private WorldVariableData(CompoundTag tag) {
        CompoundTag stored = tag.getCompound(TAG_VALUES);
        for (String name : stored.getAllKeys()) {
            CompoundTag entry = stored.getCompound(name);
            CscType type = CscType.parse(entry.getString(TAG_TYPE)).orElse(null);
            CscValue value = ValueCodec.decode(entry.getString(TAG_VALUE));
            if (type != null && value != null) {
                values.put(name, value);
                types.put(name, type);
            }
        }
    }

    public static WorldVariableData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WorldVariableData::new, WorldVariableData::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        for (Map.Entry<String, CscValue> entry : values.entrySet()) {
            CompoundTag item = new CompoundTag();
            CscType type = types.get(entry.getKey());
            item.putString(TAG_TYPE, type == null ? entry.getValue().type().keyword() : type.keyword());
            item.putString(TAG_VALUE, ValueCodec.encode(entry.getValue()));
            stored.put(entry.getKey(), item);
        }
        tag.put(TAG_VALUES, stored);
        return tag;
    }

    @Override
    public boolean has(String name) {
        return values.containsKey(name);
    }

    @Override
    public CscValue get(String name) {
        return values.get(name);
    }

    @Override
    public CscType typeOf(String name) {
        return types.get(name);
    }

    @Override
    public void declare(String name, CscType type, CscValue initial) {
        values.put(name, initial);
        types.put(name, type);
        setDirty();
    }

    @Override
    public void set(String name, CscValue value) {
        values.put(name, value);
        if (!types.containsKey(name)) {
            types.put(name, value.type());
        }
        setDirty();
    }

    @Override
    public void delete(String name) {
        if (values.remove(name) != null) {
            types.remove(name);
            setDirty();
        }
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(values.keySet());
    }

    @Override
    public Map<String, CscValue> snapshot() {
        return Map.copyOf(values);
    }
}
