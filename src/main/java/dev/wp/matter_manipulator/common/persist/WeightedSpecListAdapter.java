package dev.wp.matter_manipulator.common.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import java.lang.reflect.Type;

public class WeightedSpecListAdapter implements JsonSerializer<WeightedSpecList>, JsonDeserializer<WeightedSpecList> {

    @Override
    public JsonElement serialize(WeightedSpecList src, Type typeOfSrc, JsonSerializationContext context) {
        var arr = new JsonArray();
        for (var e : src.entries) {
            var obj = new JsonObject();
            obj.add("spec", context.serialize(e.spec, BlockSpec.class));
            obj.addProperty("weight", e.weight);
            arr.add(obj);
        }
        return arr;
    }

    @Override
    public WeightedSpecList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        var list = new WeightedSpecList();
        for (var el : json.getAsJsonArray()) {
            var obj = el.getAsJsonObject();
            BlockSpec spec = context.deserialize(obj.get("spec"), BlockSpec.class);
            int weight = obj.get("weight").getAsInt();
            list.add(spec, weight);
        }
        return list;
    }
}
