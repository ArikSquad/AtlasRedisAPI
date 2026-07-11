package net.swofty.redisapi;

import net.swofty.redisapi.api.RedisAPI;
import net.swofty.redisapi.events.EventRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.net.URI;

/**
 * Shared plumbing for integration tests that need a live Redis.
 * Configure the target with the REDIS_URI env var (defaults to redis://localhost:6379).
 * Tests are skipped when Redis is unreachable, except in CI where they fail hard.
 */
public final class TestRedis {

    private TestRedis() {
    }

    public static String uri() {
        String env = System.getenv("REDIS_URI");
        return env != null ? env : "redis://localhost:6379";
    }

    public static void requireOrSkip() {
        boolean reachable;
        try (Jedis jedis = new Jedis(URI.create(uri()))) {
            reachable = "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception e) {
            reachable = false;
        }
        if (System.getenv("CI") != null) {
            Assertions.assertTrue(reachable, "Redis must be reachable in CI at " + uri());
        }
        Assumptions.assumeTrue(reachable, "No Redis reachable at " + uri() + " - skipping integration tests");
    }

    public static RedisAPI freshInstance(String filterId) {
        RedisAPI api = RedisAPI.generateInstance(uri());
        api.setFilterId(filterId);
        return api;
    }

    /**
     * Blocks until the subscriber thread started by startListeners() has confirmed
     * its subscriptions, so a publish immediately afterwards is not lost.
     *
     * @param previousPubSub the value of EventRegistry.pubSub before startListeners() was called
     * @param expectedChannels how many channels the new subscriber must have confirmed
     */
    public static void awaitSubscribed(JedisPubSub previousPubSub, int expectedChannels) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            JedisPubSub pubSub = EventRegistry.pubSub;
            if (pubSub != null && pubSub != previousPubSub && pubSub.getSubscribedChannels() >= expectedChannels) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Assertions.fail("Redis subscriber did not confirm " + expectedChannels + " channel subscriptions within 10s");
    }
}
