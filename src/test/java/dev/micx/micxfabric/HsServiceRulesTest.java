package dev.micx.micxfabric;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HsServiceRulesTest {
    @Test
    void validatesCountAndMinecraftNames() {
        assertEquals(1, HsDispatchService.parseCount(new String[]{"hs", "1"}));
        assertEquals(3, HsDispatchService.parseCount(new String[]{"hs", "3"}));
        assertEquals(-1, HsDispatchService.parseCount(new String[]{"hs", "0"}));
        assertEquals(-1, HsDispatchService.parseCount(new String[]{"hs", "4"}));
        assertEquals(-1, HsDispatchService.parseCount(new String[]{"hs"}));
        assertTrue(HsDispatchService.isPlayerName("MICx_123"));
        assertFalse(HsDispatchService.isPlayerName("bad name"));
        assertFalse(HsDispatchService.isPlayerName("this_name_is_too_long"));
    }

    @Test
    void derivesDispatchAndQueueEndpointsAndParsesResponses() {
        String job = "https://example.test/zombies/hs/jobs";
        assertEquals("https://example.test/zombies/hs/dispatch", HsConfig.deriveEndpoint(job, "dispatch"));
        assertEquals("https://example.test/zombies/hs/queue", HsConfig.deriveEndpoint(job, "queue"));
        assertEquals("", HsConfig.deriveEndpoint("http://example.test/zombies/hs/jobs", "queue"));

        HsDispatchService.DispatchResult dispatch = HsDispatchService.parseDispatch(
                "{\"ok\":true,\"available\":[\"a\"],\"queued\":[\"b\"],\"wait_est_seconds\":12}");
        assertTrue(dispatch.ok());
        assertEquals(List.of("a"), dispatch.available());
        assertEquals(12, dispatch.waitEstSeconds());

        HsDispatchService.QueueResult queue = HsDispatchService.parseQueue(
                "{\"ready_bots\":[\"a\"],\"pending\":[\"b\"]}");
        assertEquals(List.of("a"), queue.readyBots());
        assertEquals(1, queue.pendingCount());
        assertEquals(HsDispatchService.DispatchFailure.NONE, dispatch.failure());
        assertEquals(HsDispatchService.DispatchFailure.NONE, queue.failure());
    }

    @Test
    void encodesTheLegacyBearerAsCompleteTokenJson() {
        JsonObject token = new JsonObject();
        token.addProperty("token", "opaque-value");
        token.addProperty("exp", 123L);
        token.addProperty("sig", "signature");
        String bearer = HsDispatchService.bearerFor(token);
        String decoded = new String(Base64.getDecoder().decode(bearer), StandardCharsets.UTF_8);
        assertEquals(token.toString(), decoded);
        assertFalse(bearer.contains("opaque-value"));
    }

    @Test
    void classifiesHttpFailuresWithoutExposingResponseBody() {
        assertEquals(HsDispatchService.DispatchFailure.HTTP_STATUS,
                HsDispatchService.classifyHttpResult(401, "secret").failure());
        assertEquals(HsDispatchService.DispatchFailure.HTTP_STATUS,
                HsDispatchService.classifyHttpResult(503, "secret").failure());
        assertEquals("backend authentication rejected",
                HsDispatchService.dispatchFailureMessage(HsDispatchService.DispatchFailure.HTTP_STATUS, 401));
        assertEquals("backend server error (HTTP 503)",
                HsDispatchService.dispatchFailureMessage(HsDispatchService.DispatchFailure.HTTP_STATUS, 503));
        assertFalse(HsDispatchService.dispatchFailureMessage(HsDispatchService.DispatchFailure.HTTP_STATUS, 403)
                .contains("secret"));
    }

    @Test
    void filtersInvalidBotNamesAndRejectsMalformedResponses() {
        HsDispatchService.DispatchResult dispatch = HsDispatchService.parseDispatch(
                "{\"ok\":true,\"available\":[\"GoodBot\",\"/p evil\",123],\"queued\":[\"Queued_1\"]}");
        assertEquals(List.of("GoodBot"), dispatch.available());
        assertEquals(HsDispatchService.DispatchFailure.NONE, dispatch.failure());

        HsDispatchService.DispatchResult malformed = HsDispatchService.parseDispatch("not-json");
        assertEquals(HsDispatchService.DispatchFailure.JSON_ERROR, malformed.failure());
        assertEquals("invalid backend response",
                HsDispatchService.dispatchFailureMessage(malformed.failure(), malformed.status()));
    }
}
