package dev.wp.matter_manipulator.common.persist;

import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;

public final class PersistAdapters {
    private PersistAdapters() {}

    public static final JsonSerializer<BlockSpec> BLOCK_SPEC_SERIALIZER = (src, typeOfSrc, context) -> {
        var obj = new JsonObject();
        obj.addProperty("block", BuiltInRegistries.BLOCK.getKey(src.state.getBlock()).toString());
        obj.addProperty("state", NbtUtils.writeBlockState(src.state).toString());
        return obj;
    };

    public static final JsonDeserializer<BlockSpec> BLOCK_SPEC_DESERIALIZER = (json, typeOfT, context) -> {
        try {
            var obj = json.getAsJsonObject();
            var stateNbt = TagParser.parseTag(obj.get("state").getAsString());
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateNbt);
            return new BlockSpec(state);
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize BlockSpec", e);
        }
    };

    public static final JsonSerializer<Location> LOCATION_SERIALIZER = (src, typeOfSrc, context) -> {
        var obj = new JsonObject();
        obj.addProperty("dim", src.dimension.location().toString());
        obj.addProperty("x", src.pos.getX());
        obj.addProperty("y", src.pos.getY());
        obj.addProperty("z", src.pos.getZ());
        return obj;
    };

    public static final JsonDeserializer<Location> LOCATION_DESERIALIZER = (json, typeOfT, context) -> {
        var obj = json.getAsJsonObject();
        var dimKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(obj.get("dim").getAsString()));
        var pos = new BlockPos(
                obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
        return new Location(dimKey, pos);
    };

    public static final JsonSerializer<WeightedSpecList> WEIGHTED_LIST_SERIALIZER = (src, typeOfSrc, context) -> {
        var arr = new JsonArray();
        for (var e : src.entries) {
            var obj = new JsonObject();
            obj.add("spec", context.serialize(e.spec, BlockSpec.class));
            obj.addProperty("weight", e.weight);
            arr.add(obj);
        }
        return arr;
    };

    public static final JsonDeserializer<WeightedSpecList> WEIGHTED_LIST_DESERIALIZER = (json, typeOfT, context) -> {
        var list = new WeightedSpecList();
        for (var el : json.getAsJsonArray()) {
            var obj = el.getAsJsonObject();
            BlockSpec spec = context.deserialize(obj.get("spec"), BlockSpec.class);
            int weight = obj.get("weight").getAsInt();
            list.add(spec, weight);
        }
        return list;
    };
}
