package net.swofty.redisapi.util;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This utility class is used for sending JSONObjects over Redis instead of working with raw Strings.
 */
@Getter
public class RedisParsableMessage {
    private final String raw;
    private final JSONObject json;

    protected RedisParsableMessage(JSONObject json) {
        this.json = json;
        this.raw = json.toString();
    }

    public static RedisParsableMessage build(Map<String, String> fields) {
        return build(new JSONObject(fields));
    }

    public static RedisParsableMessage build(JSONObject obj) {
        return new RedisParsableMessage(obj);
    }

    /**
     * Wraps Redis messages into {@link RedisParsableMessage} making it effectively a {@link JSONObject}
     * @param raw the raw message to parse
     * @return a {@link RedisParsableMessage}
     */
    public static RedisParsableMessage parse(String raw) {
        String toParse = raw;
        if (raw.contains(";")) {
            // Limit 2 so semicolons inside the JSON payload survive the filter-id prefix strip
            toParse = raw.split(";", 2)[1];
        }
        return new RedisParsableMessage(new JSONObject(toParse));
    }

    /**
     * Formats the RedisParsableMessage into a string to be sent through Redis
     * @return the "serialized" version of this class
     */
    public String formatForSend() {
        return json.toString();
    }

    public <T> T get(String key, T defaultValue) {
        return json.has(key) ? (T) json.get(key) : defaultValue;
    }

    public UUID getUUID(String key) {
        return UUID.fromString(get(key, ""));
    }

    public JSONArray getJsonArray(String key) {
        return json.has(key) ? json.getJSONArray(key) : new JSONArray();
    }

    public List<String> getStringList(String key) {
        return json.has(key) ? json.getJSONArray(key).toList().stream().map(String::valueOf).toList() : new ArrayList<>();
    }

    public boolean getBoolean(String key) {
        return json.has(key) && json.getBoolean(key);
    }
}
