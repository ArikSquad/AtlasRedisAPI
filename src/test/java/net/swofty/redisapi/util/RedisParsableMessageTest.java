package net.swofty.redisapi.util;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisParsableMessageTest {

    @Test
    void buildAndParseRoundTrip() {
        JSONObject json = new JSONObject().put("id", "abc").put("count", 3);

        RedisParsableMessage parsed = RedisParsableMessage.parse(RedisParsableMessage.build(json).formatForSend());

        assertEquals("abc", parsed.get("id", "missing"));
        assertEquals(3, (int) parsed.get("count", 0));
    }

    @Test
    void buildFromMap() {
        RedisParsableMessage message = RedisParsableMessage.build(Map.of("key", "value"));

        assertEquals("value", message.get("key", "missing"));
    }

    @Test
    void parseStripsFilterIdPrefix() {
        String wire = "some-filter;" + new JSONObject().put("a", 1);

        assertEquals(1, (int) RedisParsableMessage.parse(wire).get("a", 0));
    }

    @Test
    void parsePreservesSemicolonsInsidePayload() {
        String wire = "some-filter;" + new JSONObject().put("value", "a;b;c");

        assertEquals("a;b;c", RedisParsableMessage.parse(wire).get("value", ""));
    }

    @Test
    void parseWithoutPrefix() {
        RedisParsableMessage parsed = RedisParsableMessage.parse(new JSONObject().put("x", true).toString());

        assertTrue(parsed.getBoolean("x"));
    }

    @Test
    void getReturnsDefaultForMissingKey() {
        RedisParsableMessage message = RedisParsableMessage.build(new JSONObject());

        assertEquals("fallback", message.get("nope", "fallback"));
    }

    @Test
    void getUUIDRoundTrip() {
        UUID uuid = UUID.randomUUID();
        RedisParsableMessage message = RedisParsableMessage.build(new JSONObject().put("uuid", uuid.toString()));

        assertEquals(uuid, message.getUUID("uuid"));
    }

    @Test
    void getStringListAndBooleanDefaults() {
        RedisParsableMessage message = RedisParsableMessage.build(
                new JSONObject().put("list", List.of("a", "b")));

        assertEquals(List.of("a", "b"), message.getStringList("list"));
        assertEquals(List.of(), message.getStringList("missing"));
        assertFalse(message.getBoolean("missing"));
        assertTrue(message.getJsonArray("missing").isEmpty());
    }
}
