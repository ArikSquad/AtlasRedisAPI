package net.swofty.redisapi.api.requests;

import net.swofty.redisapi.api.ChannelRegistry;
import net.swofty.redisapi.api.RedisAPI;
import net.swofty.redisapi.util.RedisParsableMessage;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DataRequest {
    private static final long TIMEOUT_MS = 1_000;

    private static final Map<String, CompletableFuture<JSONObject>> PENDING = new ConcurrentHashMap<>();

    private final String id;
    private final String filter;
    private final String key;
    private final JSONObject data;

    /**
     * Create a new data request to get a specific object of data from a specific server.
     * @param filterID the destination of the request. Can be "all" for all listeners.
     * @param key a unique key for the data request.
     */
    public DataRequest(String filterID, String key, JSONObject data) {
        this.id = UUID.randomUUID().toString();
        this.filter = filterID;
        this.key = key;
        this.data = data == null ? new JSONObject() : data;
    }

    /**
     * Publishes the request and returns a future that completes the moment a response arrives
     * (or after {@value #TIMEOUT_MS}ms with a null response, same as before) - without ever
     * blocking or parking a thread while waiting. The future is fulfilled by {@link #receive}
     * when the corresponding {@link DataRequestResponder} response comes back over Redis.
     *
     * @return a {@link DataResponse} which must be handled in async.
     */
    public CompletableFuture<DataResponse> await() {
        long start = System.currentTimeMillis();

        CompletableFuture<JSONObject> responseFuture = new CompletableFuture<>();
        PENDING.put(id, responseFuture);

        JSONObject request = new JSONObject();
        request.put("id", id);
        request.put("key", key);
        request.put("data", data);
        request.put("sender", RedisAPI.getInstance().getFilterId()); // We assumne your FilterID is set before using this.
        request.put("stream", StreamType.REQUEST.name());
        RedisAPI.getInstance().publishMessage(filter, ChannelRegistry.getFromName("data-request"), RedisParsableMessage.build(request).formatForSend());

        return responseFuture
                .completeOnTimeout(null, TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .thenApply(response -> new DataResponse(response, System.currentTimeMillis() - start))
                .whenComplete((response, error) -> PENDING.remove(id)); // cleanup either way - success or timeout
    }

    /**
     * Called by whatever listens for incoming Redis messages when a {@code RESPONSE}-stream
     * message with a matching id comes back. This is the only thing that completes a pending
     * future - replaces the old pattern of putting straight into a shared map and having callers
     * poll it.
     *
     * @param id the request id this response is answering.
     * @param response the response payload.
     */
    public static void receive(String id, JSONObject response) {
        CompletableFuture<JSONObject> future = PENDING.remove(id);
        if (future != null)
            future.complete(response);
    }

    public enum StreamType {
        REQUEST,
        RESPONSE
    }
}
