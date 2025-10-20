package com.github.beemerwt.mcrpg.util;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonNull;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

public class JanksonConvert {
    public static JsonElement toJson(Object value) {
        switch (value) {
            case JsonPrimitive jp -> { return jp; }
            case String s -> { return JsonPrimitive.of(s); }
            case Byte b -> { return JsonPrimitive.of((long)b); }
            case Short sh -> { return JsonPrimitive.of((long)sh); }
            case Integer i -> { return JsonPrimitive.of((long)i); }
            case Long l -> { return JsonPrimitive.of(l); }
            case Float f -> { return JsonPrimitive.of((double)f); }
            case Double d -> { return JsonPrimitive.of(d); }
            case Boolean b -> { return JsonPrimitive.of(b); }
            case Character c -> { return JsonPrimitive.of(c.toString()); }
            default -> { return JsonNull.INSTANCE; }
        }
    }

    public static String toString(JsonObject obj, String key, String def) {
        var v = obj.get(key);
        if (v instanceof JsonPrimitive p) {
            String s = p.asString();
            if (!s.isEmpty()) return s;
        }
        return def;
    }
}
