package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 变形句库与洗牌袋的离线回归。 */
class RankUpToolMessagesTest {

    @Test
    void libraryHasAtLeastOneHundredVariants() {
        assertTrue(RankUpToolMessages.size() >= 100,
                "变形句不足 100 条：实际 " + RankUpToolMessages.size());
    }

    @Test
    void everyTemplateCarriesTheRankToken() {
        for (String template : RankUpToolMessages.TEMPLATES) {
            assertTrue(template.contains(RankUpToolMessages.RANK_TOKEN),
                    "模板缺 {rank} 占位符: " + template);
        }
    }

    @Test
    void templatesAreUniqueIgnoringCase() {
        Set<String> seen = new HashSet<>();
        for (String template : RankUpToolMessages.TEMPLATES) {
            assertTrue(seen.add(template.toLowerCase(Locale.ROOT)), "重复模板: " + template);
        }
        assertEquals(seen.size(), RankUpToolMessages.size());
    }

    @Test
    void filledMessagesAreChatSafe() {
        for (String template : RankUpToolMessages.TEMPLATES) {
            for (String rank : RankUpToolRules.RANKS) {
                String message = RankUpToolMessages.fill(template, rank);
                assertFalse(message.contains("{") || message.contains("}"),
                        "替换后仍有花括号: " + message);
                assertFalse(message.contains(RankUpToolMessages.RANK_TOKEN), "占位符没换掉: " + message);
                assertTrue(message.contains(rank), "没带上档位: " + message);
                // 原版聊天上限 256 字符；这里远远留出余量，也避开客户端截断
                assertTrue(message.length() <= 120, "过长(" + message.length() + "): " + message);
                for (int i = 0; i < message.length(); i++) {
                    char c = message.charAt(i);
                    assertTrue(c >= 0x20 && c < 0x7F, "非可打印 ASCII(" + c + "): " + message);
                }
            }
        }
    }

    @Test
    void fillNormalizesRankAndKeepsTemplateShape() {
        assertEquals("can someone gift me MVP++ pls?", RankUpToolMessages.fill(
                "can someone gift me {rank} pls?", "mvp++"));
        // 不认识的档位回落默认档位，而不是原样透传
        assertEquals("want VIP", RankUpToolMessages.fill("want {rank}", "ADMIN"));
        assertEquals("", RankUpToolMessages.fill(null, "VIP"));
        assertEquals("", RankUpToolMessages.fill("", "VIP"));
    }

    @Test
    void templateIndexWrapsInsteadOfThrowing() {
        assertEquals(RankUpToolMessages.TEMPLATES.get(0), RankUpToolMessages.template(0));
        assertEquals(RankUpToolMessages.TEMPLATES.get(RankUpToolMessages.size() - 1),
                RankUpToolMessages.template(-1));
        assertEquals(RankUpToolMessages.TEMPLATES.get(0),
                RankUpToolMessages.template(RankUpToolMessages.size()));
        assertEquals(RankUpToolMessages.TEMPLATES.get(1),
                RankUpToolMessages.template(RankUpToolMessages.size() + 1));
    }

    @Test
    void deckUsesEveryTemplateOncePerRound() {
        int size = RankUpToolMessages.size();
        RankUpToolDeck deck = new RankUpToolDeck(size, new Random(20260915L));
        assertEquals(size, deck.remaining());
        Set<Integer> round = new HashSet<>();
        for (int i = 0; i < size; i++) {
            int index = deck.next();
            assertTrue(index >= 0 && index < size, "下标越界: " + index);
            assertTrue(round.add(index), "同一轮里重复了模板 " + index);
            assertEquals(size - i - 1, deck.remaining());
        }
        assertEquals(size, round.size());
        // 发满一轮后本轮剩余 0；再取一句会自动开新一轮（不会卡住）
        assertEquals(0, deck.remaining());
        int nextRoundFirst = deck.next();
        assertTrue(nextRoundFirst >= 0 && nextRoundFirst < size, "新一轮下标越界: " + nextRoundFirst);
        assertEquals(size - 1, deck.remaining());
    }

    @Test
    void deckNeverRepeatsTheSameLineAcrossRounds() {
        int size = RankUpToolMessages.size();
        RankUpToolDeck deck = new RankUpToolDeck(size, new Random(7L));
        int previous = Integer.MIN_VALUE;
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < size; i++) {
                int index = deck.next();
                assertNotEquals(previous, index, "连着两句相同模板（第 " + round + " 轮第 " + i + " 句）");
                previous = index;
            }
        }
    }

    @Test
    void deckHandlesEmptyLibraryAndReshuffle() {
        RankUpToolDeck empty = new RankUpToolDeck(0, new Random(1L));
        assertEquals(-1, empty.next());
        assertEquals(0, empty.remaining());

        RankUpToolDeck single = new RankUpToolDeck(1, new Random(2L));
        assertEquals(0, single.next());
        assertEquals(0, single.next());

        RankUpToolDeck deck = new RankUpToolDeck(RankUpToolMessages.size(), new Random(3L));
        int first = deck.next();
        deck.reshuffle();
        assertEquals(RankUpToolMessages.size(), deck.remaining());
        // 重洗后一轮照样覆盖全部模板（首句可能与重洗前相同，属于正常）
        Set<Integer> round = new HashSet<>();
        round.add(deck.next());
        for (int i = 1; i < RankUpToolMessages.size(); i++) {
            assertTrue(round.add(deck.next()), "重洗后一轮内重复");
        }
        assertTrue(round.contains(first) || round.size() == RankUpToolMessages.size());
    }

    @Test
    void librarySpansShortAndLongSentences() {
        long shortOnes = RankUpToolMessages.TEMPLATES.stream()
                .filter(template -> RankUpToolMessages.fill(template, "MVP++").length() <= 24)
                .count();
        long longOnes = RankUpToolMessages.TEMPLATES.stream()
                .filter(template -> RankUpToolMessages.fill(template, "MVP++").length() >= 55)
                .count();
        assertTrue(shortOnes >= 20, "短句太少: " + shortOnes);
        assertTrue(longOnes >= 20, "长句太少: " + longOnes);
        assertTrue(RankUpToolMessages.size() >= 100, "句库总量不足");
        List<String> templates = RankUpToolMessages.TEMPLATES;
        assertFalse(templates.get(0).isBlank());
    }
}
