package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

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
    }
}
