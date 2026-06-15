package dev.wp.matter_manipulator.common.persist;

import dev.wp.matter_manipulator.common.building.BlockSpec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class BlockSpecAdapter implements JsonSerializer<BlockSpec>, JsonDeserializer<BlockSpec> {
    @Override
    public JsonElement serialize(BlockSpec src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty(
                "block", BuiltInRegistries.BLOCK.getKey(src.state.getBlock()).toString());
        obj.addProperty("state", NbtUtils.writeBlockState(src.state).toString());
        return obj;
    }

    @Override
    public BlockSpec deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        var obj = json.getAsJsonObject();
        try {
            var stateNbt = TagParser.parseTag(obj.get("state").getAsString());
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateNbt);
            return new BlockSpec(state);
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize BlockSpec", e);
        }
    }
}
