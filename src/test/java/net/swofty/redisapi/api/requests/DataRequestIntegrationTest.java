package net.swofty.redisapi.api.requests;

import net.swofty.redisapi.TestRedis;
import net.swofty.redisapi.api.ChannelRegistry;
import net.swofty.redisapi.api.RedisAPI;
import net.swofty.redisapi.events.EventRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRequestIntegrationTest {

    private static final String FILTER_ID = "data-request-test";
    private static final String ECHO_KEY = "echo";

    private static RedisAPI api;

    @BeforeAll
    static void setUp() {
        TestRedis.requireOrSkip();

        JedisPubSub previous = EventRegistry.pubSub;
        api = TestRedis.freshInstance(FILTER_ID);
        DataRequestResponder.create(ECHO_KEY, request ->
                new JSONObject()
                        .put("echoed", request.optString("value", "none"))
                        .put("answered", true));
        api.startListeners();
        TestRedis.awaitSubscribed(previous, ChannelRegistry.registeredChannels.size());
    }

    @AfterAll
    static void tearDown() {
        if (api != null) api.shutdown();
    }

    @Test
    void roundTripCompletesWithResponderData() throws Exception {
        DataRequest request = new DataRequest(FILTER_ID, ECHO_KEY, new JSONObject().put("value", "ping"));

        DataResponse response = request.await().get(5, TimeUnit.SECONDS);

        assertNotNull(response.data(), "expected a response before the timeout");
        assertEquals("ping", response.data().getString("echoed"));
        assertTrue(response.data().getBoolean("answered"));
        assertTrue(response.latency() >= 0 && response.latency() < 5_000);
    }

    @Test
    void nullRequestDataIsSentAsEmptyPayload() throws Exception {
        DataRequest request = new DataRequest(FILTER_ID, ECHO_KEY, null);

        DataResponse response = request.await().get(5, TimeUnit.SECONDS);

        assertNotNull(response.data());
        assertEquals("none", response.data().getString("echoed"));
    }

    @Test
    void allFilterIsAnswered() throws Exception {
        DataRequest request = new DataRequest("all", ECHO_KEY, new JSONObject().put("value", "broadcast"));

        DataResponse response = request.await().get(5, TimeUnit.SECONDS);

        assertNotNull(response.data());
        assertEquals("broadcast", response.data().getString("echoed"));
    }

    @Test
    void semicolonsInPayloadSurviveTheWireFormat() throws Exception {
        DataRequest request = new DataRequest(FILTER_ID, ECHO_KEY, new JSONObject().put("value", "a;b;c"));

        DataResponse response = request.await().get(5, TimeUnit.SECONDS);

        assertNotNull(response.data());
        assertEquals("a;b;c", response.data().getString("echoed"));
    }

    @Test
    void unansweredRequestTimesOutWithNullData() throws Exception {
        DataRequest request = new DataRequest("nobody-listening", ECHO_KEY, new JSONObject());

        long start = System.currentTimeMillis();
        DataResponse response = request.await().get(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertNull(response.data(), "no listener matches the filter, so the request must time out");
        assertTrue(elapsed < 4_000, "timeout should fire at ~1s, took " + elapsed + "ms");
    }

    @Test
    void unknownResponderKeyTimesOutWithNullData() throws Exception {
        DataRequest request = new DataRequest(FILTER_ID, "no-such-responder", new JSONObject());

        DataResponse response = request.await().get(5, TimeUnit.SECONDS);

        assertNull(response.data());
    }

    @Test
    void concurrentRequestsResolveIndependently() throws Exception {
        int count = 10;
        List<CompletableFuture<DataResponse>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(new DataRequest(FILTER_ID, ECHO_KEY, new JSONObject().put("value", "req-" + i)).await());
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        for (int i = 0; i < count; i++) {
            JSONObject data = futures.get(i).get().data();
            assertNotNull(data, "request " + i + " timed out");
            assertEquals("req-" + i, data.getString("echoed"), "response was matched to the wrong request");
        }
    }
}
