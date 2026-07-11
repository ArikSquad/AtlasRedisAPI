package net.swofty.redisapi.api;

import net.swofty.redisapi.exceptions.ChannelAlreadyRegisteredException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelRegistryTest {

    @Test
    void registeringDuplicateChannelThrows() {
        String name = "duplicate-test-" + UUID.randomUUID();
        ChannelRegistry.registerChannel(new RedisChannel(name, (e) -> {
        }));

        assertThrows(ChannelAlreadyRegisteredException.class,
                () -> ChannelRegistry.registerChannel(new RedisChannel(name, (e) -> {
                })));
    }

    @Test
    void getFromNameReturnsChannelWithThatName() {
        String name = "lookup-test-" + UUID.randomUUID();

        RedisChannel channel = ChannelRegistry.getFromName(name);

        assertEquals(name, channel.channelName);
        assertEquals(ChannelFunctionType.CONSUMER, channel.functionType);
    }
}
