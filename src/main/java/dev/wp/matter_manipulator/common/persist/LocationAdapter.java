package dev.wp.matter_manipulator.common.persist;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.wp.matter_manipulator.common.building.Location;
import java.lang.reflect.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class LocationAdapter implements JsonSerializer<Location>, JsonDeserializer<Location> {

    @Override
    public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("dim", src.dimension.location().toString());
        obj.addProperty("x", src.pos.getX());
        obj.addProperty("y", src.pos.getY());
        obj.addProperty("z", src.pos.getZ());
        return obj;
    }

    @Override
    public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        var obj = json.getAsJsonObject();
        var dimKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(obj.get("dim").getAsString()));
        var pos = new BlockPos(
                obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
        return new Location(dimKey, pos);
    }
}
