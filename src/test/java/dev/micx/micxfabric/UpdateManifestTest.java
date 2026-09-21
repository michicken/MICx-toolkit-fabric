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
