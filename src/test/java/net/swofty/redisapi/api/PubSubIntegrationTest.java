package net.swofty.redisapi.api;

import net.swofty.redisapi.TestRedis;
import net.swofty.redisapi.events.EventRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PubSubIntegrationTest {

    private static final String FILTER_ID = "pubsub-test-instance";
    private static final String CHANNEL = "test-pubsub-channel";
    private static final LinkedBlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

    private static RedisAPI api;

    @BeforeAll
    static void setUp() {
        TestRedis.requireOrSkip();

        JedisPubSub previous = EventRegistry.pubSub;
        api = TestRedis.freshInstance(FILTER_ID);
        api.registerChannel(CHANNEL, e -> RECEIVED.add(e.message));
        api.startListeners();
        TestRedis.awaitSubscribed(previous, ChannelRegistry.registeredChannels.size());
    }

    @AfterAll
    static void tearDown() {
        if (api != null) api.shutdown();
    }

    @Test
    void messageWithMatchingFilterIdIsDelivered() throws Exception {
        String payload = "match-" + UUID.randomUUID();

        api.publishMessage(FILTER_ID, ChannelRegistry.getFromName(CHANNEL), payload).get(5, TimeUnit.SECONDS);

        String received = pollFor(payload, 5_000);
        assertNotNull(received, "expected message published with our own filter id to arrive");
        assertTrue(received.endsWith(payload));
    }

    @Test
    void messageWithAllFilterIsDelivered() throws Exception {
        String payload = "broadcast-" + UUID.randomUUID();

        api.publishMessage("all", ChannelRegistry.getFromName(CHANNEL), payload).get(5, TimeUnit.SECONDS);

        assertNotNull(pollFor(payload, 5_000), "expected message published to \"all\" to arrive");
    }

    @Test
    void messageWithForeignFilterIdIsDropped() throws Exception {
        String payload = "foreign-" + UUID.randomUUID();

        api.publishMessage("some-other-instance", ChannelRegistry.getFromName(CHANNEL), payload).get(5, TimeUnit.SECONDS);

        assertNull(pollFor(payload, 700), "message for another filter id must not reach this instance");
    }

    /** Drains the queue looking for a message containing the marker, waiting up to timeoutMs. */
    private static String pollFor(String marker, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return null;
            String message = RECEIVED.poll(remaining, TimeUnit.MILLISECONDS);
            if (message == null) return null;
            if (message.contains(marker)) return message;
        }
    }
}
