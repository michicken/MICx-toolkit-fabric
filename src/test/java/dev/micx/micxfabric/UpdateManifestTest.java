package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 清单解析要能吃下服务端的正常输出，也要能吃下任何垃圾数据而不抛异常。 */
class UpdateManifestTest {
    private static final String SHA = "0123456789abcdef".repeat(4);

    private static String manifest(String version, String publishedAt, String filesBody) {
        return "{\"version\":\"" + version + "\",\"publishedAt\":\"" + publishedAt + "\","
                + "\"severeDelayMinutes\":60,\"outdatedDelayMinutes\":10,"
                + "\"files\":{" + filesBody + "}}";
    }

    private static String fabricEntry(String fileName, String url, String sha, long size) {
        return "\"fabric-26.2\":{\"fileName\":\"" + fileName + "\",\"url\":\"" + url + "\","
                + "\"sha256\":\"" + sha + "\",\"size\":" + size + "}";
    }

    @Test
    void theEntryVersionWinsOverTheTopLevelOne() {
        String json = "{\"version\":\"2.38.2\",\"publishedAt\":\"2026-09-22T04:00:00Z\",\"files\":{"
                + "\"fabric-26.2\":{\"version\":\"0.2.125\",\"fileName\":\"micx-fabric-0.2.125.jar\","
                + "\"url\":\"/zombies/dl/0.2.125/micx-fabric-0.2.125.jar\",\"sha256\":\"" + SHA + "\",\"size\":42}}}";

        UpdateManifest parsed = UpdateManifest.parse(json, UpdateManifest.PLATFORM_FABRIC);

        assertEquals("0.2.125", parsed.version());
        assertEquals("2.38.2", parsed.manifestVersion(), "顶层那份要原样留着给 1.8.9 的门控读，不能被 26.2 的版本号污染");
        assertTrue(parsed.usable());
    }

    @Test
    void noVersionAnywhereIsNotUsable() {
        UpdateManifest parsed = UpdateManifest.parse(
                "{\"publishedAt\":\"2026-09-22T04:00:00Z\",\"files\":{"
                        + fabricEntry("a.jar", "/a.jar", SHA, 10) + "}}",
                UpdateManifest.PLATFORM_FABRIC);
        assertFalse(parsed.usable());
        assertFalse(parsed.problems().isEmpty());
    }

    /**
     * 线上清单的快照。快照文件是发布 0.2.125 时从
     * {@code https://zombie.nienie.fun/zombies/version.json} 原样抓下来的，
     * 用来保证解析器认得服务器真的会吐出来的字节，而不是只认得测试里编的。
     */
    @Test
    void parsesTheManifestSnapshotTakenFromTheLiveServer() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/live-version-0.2.125.json")) {
            assertNotNull(input, "缺少线上清单快照");
            json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        UpdateManifest parsed = UpdateManifest.parse(json, UpdateManifest.PLATFORM_FABRIC);

        assertTrue(parsed.usable(), () -> "线上快照必须可用：" + parsed.problems());
        assertTrue(parsed.problems().isEmpty(), () -> "线上快照不该有解析问题：" + parsed.problems());
        assertEquals("0.2.125", parsed.version());
        assertEquals("0.2.125", parsed.manifestVersion(),
                "顶层那份是给 1.8.9 门控和 0.2.125 之前的客户端读的，两边必须一致");
        assertTrue(UpdateRules.compare(parsed.manifestVersion(), "2.38.2") < 0,
                "Fabric 版本号要一直小于 1.8.9 的 2.x：Forge 门控首位不同就按首位判，这样老用户才不会被拦");
        assertEquals("micx-fabric-0.2.125.jar", parsed.entry().fileName());
        assertTrue(UpdateRules.isSha256(parsed.entry().sha256()));
        assertTrue(parsed.entry().size() > 0);
        assertTrue(parsed.absoluteUrl("https://zombie.nienie.fun").startsWith("https://zombie.nienie.fun/zombies/dl/"));
    }

    @Test
    void readsTheFabricEntryOutOfTheSharedManifest() {
        String json = manifest("0.2.124", "2026-09-22T04:00:00Z",
                fabricEntry("micx-fabric-0.2.124.jar", "/zombies/dl/0.2.124/micx-fabric-0.2.124.jar", SHA, 1024)
                        + ",\"forge-1.8.9\":{\"fileName\":\"x.jar\",\"url\":\"/x.jar\",\"sha256\":\"" + SHA + "\",\"size\":1}");

        UpdateManifest parsed = UpdateManifest.parse(json, UpdateManifest.PLATFORM_FABRIC);

        assertTrue(parsed.usable());
        assertTrue(parsed.problems().isEmpty(), () -> "不该有解析问题：" + parsed.problems());
        assertEquals("0.2.124", parsed.version());
        assertEquals(Instant.parse("2026-09-22T04:00:00Z").toEpochMilli(), parsed.publishedAtMs());
        assertEquals("micx-fabric-0.2.124.jar", parsed.entry().fileName());
        assertEquals(1024L, parsed.entry().size());
        assertEquals(UpdateRules.DEFAULT_DELAY_MS, parsed.delayMs(), "没写 installDelayMinutes 时用默认撤回窗口");
    }

    @Test
    void relativeDownloadUrlsResolveAgainstTheSite() {
        UpdateManifest parsed = UpdateManifest.parse(
                manifest("0.2.124", "2026-09-22T04:00:00Z",
                        fabricEntry("a.jar", "/zombies/dl/a.jar", SHA, 10)),
                UpdateManifest.PLATFORM_FABRIC);
        assertEquals("https://zombie.nienie.fun/zombies/dl/a.jar",
                parsed.absoluteUrl("https://zombie.nienie.fun/"));
    }

    @Test
    void absoluteDownloadUrlsAreLeftAlone() {
        String url = "https://cdn.example.com/micx-fabric-0.2.124.jar";
        UpdateManifest parsed = UpdateManifest.parse(
                manifest("0.2.124", "2026-09-22T04:00:00Z", fabricEntry("a.jar", url, SHA, 10)),
                UpdateManifest.PLATFORM_FABRIC);
        assertEquals(url, parsed.absoluteUrl("https://zombie.nienie.fun"));
    }

    @Test
    void perReleaseDelayOverridesTheDefault() {
        UpdateManifest parsed = UpdateManifest.parse(
                "{\"version\":\"0.2.124\",\"publishedAt\":\"2026-09-22T04:00:00Z\",\"installDelayMinutes\":5,"
                        + "\"files\":{" + fabricEntry("a.jar", "/a.jar", SHA, 10) + "}}",
                UpdateManifest.PLATFORM_FABRIC);
        assertEquals(5L * 60_000L, parsed.delayMs());
    }

    @Test
    void theOldManifestWithoutFilesIsSimplyNotUsable() {
        UpdateManifest parsed = UpdateManifest.parse(
                "{\"version\":\"2.38.2\",\"publishedAt\":\"2026-09-13T14:00:00Z\"}", UpdateManifest.PLATFORM_FABRIC);
        assertFalse(parsed.usable());
        assertEquals("2.38.2", parsed.version(), "版本号还是能读出来，只是没有下载地址");
        assertNull(parsed.entry());
        assertFalse(parsed.problems().isEmpty());
    }

    @Test
    void garbageNeverThrows() {
        for (String json : new String[]{null, "", "   ", "not json", "[]", "{}", "{\"files\":[]}"}) {
            UpdateManifest parsed = UpdateManifest.parse(json, UpdateManifest.PLATFORM_FABRIC);
            assertNotNull(parsed);
            assertFalse(parsed.usable(), () -> "不该可用：" + json);
            assertFalse(parsed.problems().isEmpty(), () -> "应当记下问题：" + json);
        }
    }

    @Test
    void incompleteEntryIsRejected() {
        UpdateManifest parsed = UpdateManifest.parse(
                manifest("0.2.124", "2026-09-22T04:00:00Z",
                        fabricEntry("a.jar", "/a.jar", "too-short", 10)),
                UpdateManifest.PLATFORM_FABRIC);
        assertFalse(parsed.usable(), "sha256 不是 64 位十六进制就不能用");
        assertFalse(parsed.entry().complete());
    }

    @Test
    void brokenPublishTimeFallsBackToImmediate() {
        UpdateManifest parsed = UpdateManifest.parse(
                manifest("0.2.124", "昨天", fabricEntry("a.jar", "/a.jar", SHA, 10)),
                UpdateManifest.PLATFORM_FABRIC);
        assertEquals(0L, parsed.publishedAtMs());
        assertTrue(parsed.usable(), "时间坏掉不该拖累下载信息");
    }

    @Test
    void otherPlatformEntriesAreIgnored() {
        UpdateManifest parsed = UpdateManifest.parse(
                manifest("0.2.124", "2026-09-22T04:00:00Z",
                        "\"forge-1.8.9\":{\"fileName\":\"x.jar\",\"url\":\"/x.jar\",\"sha256\":\"" + SHA + "\",\"size\":1}"),
                UpdateManifest.PLATFORM_FABRIC);
        assertFalse(parsed.usable());
        assertNull(parsed.entry());
    }
}
