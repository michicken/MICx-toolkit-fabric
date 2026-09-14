package dev.micx.micxfabric;

import java.util.List;

/**
 * RankUpTool 的变形句库：每条都含 {@link #RANK_TOKEN} 占位符，发送时替换成选中的档位。
 *
 * <p>用户定稿 2026-09-15：不要只发「&lt;Rank&gt; pls」，要有长短不一的表达。
 * 配合 {@link RankUpToolDeck} 使用——一轮内每条只用一次，避免机械重复。
 *
 * <p>写法约束（有测试兜底）：全是可打印 ASCII、不含引号/反斜杠/花括号，替换后长度远小于
 * 原版聊天 256 字符上限；不承诺任何回报、不冒充官方、只求 Rank。
 */
public final class RankUpToolMessages {
    public static final String RANK_TOKEN = "{rank}";

    /** 变形句模板，顺序即面板预览顺序（发送顺序由洗牌袋打乱）。 */
    public static final List<String> TEMPLATES = List.of(
            /* ---- 极短句 ---- */
            "{rank} pls",
            "pls {rank}",
            "{rank} pls pls",
            "pls pls {rank}",
            "{rank} please",
            "please {rank}",
            "{rank} plz",
            "plz {rank}",
            "{rank} anyone?",
            "{rank}?",
            "gift {rank} pls",
            "give {rank} pls",
            "need {rank}",
            "want {rank}",
            "hoping for {rank}",
            "{rank} for me?",
            "{rank} for me pls",
            "{rank} would be nice",
            "{rank} someday pls",
            "{rank} one day",
            "{rank} when",
            "{rank} vibes pls",
            "{rank} dream",
            "{rank} pls pls pls",
            "{rank} pls pls pls pls pls",
            "{rank} pls, i asked nicely",
            "pwease {rank}",
            "{rank} pls uwu",
            "{rank} pretty pls",
            "{rank} pls, pretty pls",

            /* ---- 问句 ---- */
            "who can gift me {rank}?",
            "can someone gift me {rank}?",
            "can someone gift me {rank} pls?",
            "anyone able to gift {rank}?",
            "anyone here gifting {rank}?",
            "is anyone gifting {rank} today?",
            "would anyone gift me {rank}?",
            "who wants to gift {rank}?",
            "who can spare a {rank}?",
            "any kind soul gifting {rank}?",
            "can i get {rank} pls?",
            "may i have {rank} pls?",
            "could i get {rank} please?",
            "is it {rank} day today?",
            "who is the {rank} hero here?",
            "any {rank} angels around?",
            "does anyone have a spare {rank}?",
            "anyone kind enough to gift {rank}?",
            "what are the odds someone gifts {rank}?",
            "who feels generous today? {rank} pls",
            "any chance of a {rank} today?",
            "someone gift {rank}? asking for a friend",
            "who is in a gifting mood? {rank} pls",
            "care to gift {rank} today?",

            /* ---- 中句：说明在求什么 ---- */
            "saving up for {rank}, who can speed it up?",
            "i am one rank away from {rank}, help pls",
            "been asking for {rank} for weeks, pls someone",
            "still no {rank}, who will be my hero?",
            "one day i will have {rank}, hopefully today",
            "waiting patiently for {rank}, anyone?",
            "small gift big smile: {rank} pls",
            "{rank} is all i want this month",
            "santa pls, {rank} is on my list",
            "someone make my day, {rank} pls",
            "the best gift ever is {rank}, anyone?",
            "{rank} makes every game better, pls gift",
            "shouting into the void: {rank} pls",
            "daily {rank} request, here we go",
            "{rank} request of the day, who is in?",
            "kind stranger pls, {rank} for me",
            "who has spare love and {rank}?",
            "asking nicely: {rank} pls",
            "asking loudly: {rank} PLEASEEE",
            "{rank} pls with a cherry on top",
            "manifesting {rank} right now",
            "manifesting {rank}, join me pls",
            "{rank} pls, from your favorite zombies noob",
            "{rank} pls, i bring the vibes to every game",
            "{rank} pls, my armor is crying for it",
            "{rank} pls, the void answered last time",
            "{rank} pls, i typed this with my own 2 hands",
            "bless this lobby with {rank} pls",

            /* ---- 长句 / 花式 ---- */
            "i will be the happiest player if someone gifts me {rank}",
            "if you gift me {rank} i will be your biggest fan",
            "dreaming of {rank}, someone make it real pls",
            "my whole team wants {rank}, help us out?",
            "gifting season is now, {rank} pls",
            "i promise to say thanks 100 times if i get {rank}",
            "imagine me with {rank}, pls make it happen",
            "everyone with {rank} is my inspiration, pls gift me",
            "i grind every day for {rank}, any help?",
            "if i get {rank} i will cry of joy",
            "i am saving every coin for {rank}, pls boost me",
            "i believe in {rank} miracles, pls",
            "if u gift {rank} u r my favorite person",
            "{rank} please, i say with confidence",
            "me: has no {rank} | chat: gifts {rank} -> pls",
            "{rank} shaped hole in my heart, pls fill it",
            "one {rank} pls, extra thanks on the side",
            "{rank} pls, ill name my sword after you",
            "who is the next {rank} legend?",
            "i need {rank} like i need air",
            "{rank} would complete me, pls",
            "pls make me {rank} blessed",
            "{rank} pls, im running out of words",
            "{rank} pls (i will remember this forever)",
            "{rank} for the vibes, pls",
            "help a villager out: {rank} pls",
            "{rank} pls, my zombies stats deserve it",
            "{rank} pls, i survived round 105 for this",
            "{rank} pls, im the one who revives everyone",
            "{rank} pls, i only play zombies i swear",
            "who can gift {rank}? i will carry you in zombies",
            "{rank} pls, ill share my powerups",
            "{rank} pls, i never give up on round 100",
            "{rank} pls, team player since day one",
            "gift {rank} and join the good karma club",
            "{rank} pls, i promise no more r105 deaths",
            "someone out there loves gifting {rank}, do it pls",
            "{rank} pls, i believe in random acts of kindness",
            "{rank} pls, today is the day i feel it",
            "{rank} pls, i made a wish at the well",
            "{rank} pls, i promise to pay it forward",        
            "{rank} pls, consider it a birthday gift",
            "{rank} pls, i will do my best in every round",
            "{rank} pls, my win rate will thank you",
            "{rank} pls, im the last one alive every game",
            "{rank} pls, i never loot teammates chests",
            "{rank} pls, i always share my windows",
            "if you gift me {rank} i will remember you for the rest of my life",
            "i have been grinding zombies for months and still no {rank}, pls help",
            "my friends all have {rank} and i am the only one left behind, pls",
            "if anyone here can gift {rank} today i will be thankful forever",
            "i just hit a new personal best in zombies and {rank} is the perfect reward",
            "thinking about {rank} all day, someone make this wish come true pls",
            "i will happily carry anyone who gifts me {rank} in zombies rounds",
            "every round i play i keep hoping someone will gift me {rank} pls",
            "i am the one who always revives the team, can i get {rank} pls",
            "if you are reading this and you can gift {rank}, today is the day pls",
            "i never ask for anything in chat but today i am asking for {rank} pls",
            "gifting {rank} to a random player is the best karma you can get today",
            "i would scream with joy in the chat if someone gifts me {rank}",
            "the only thing on my wishlist is {rank} and maybe some golden apples",
            "i saved the team on round 100 and all i ask for is {rank} pls",
            "somebody out there has a spare {rank} and i am sending my best vibes",
            "i will type thank you in every language i know if i get {rank}",
            "i am not picky, any rank is great, but {rank} is the dream",
            "if you gift me {rank} i will protect you from every zombie i see",
            "i am asking one more time before i go to sleep, {rank} pls",
            "my whole lobby would cheer if someone gifted {rank} right now",
            "i keep telling my friends about {rank} and they say keep dreaming",
            "if this message reaches a kind gifter, please consider {rank} for me",
            "i will draw a picture of your skin if someone gifts me {rank}",
            "i am saving every coin i earn in zombies to reach {rank} one day",
            "whoever gifts {rank} to me becomes my favorite player in this lobby",
            "i have the worst luck with gifts, so please make an exception and gift {rank}",
            "i believe the next kind stranger in this lobby will gift me {rank}",
            "one day i will look back at this chat and remember who gifted me {rank}",
            "if the chat could grant wishes, my first wish would be {rank} pls",
            "i will be the best teammate you ever had if you gift me {rank}",
            "i am telling everyone that this lobby has the kindest gifters, {rank} pls",
            "the day someone gifts me {rank} i will name my next pet after them"
    );

    private RankUpToolMessages() {
    }

    public static int size() {
        return TEMPLATES.size();
    }

    /** 按下标取模板；下标越界会被折回合法范围，不会抛。 */
    public static String template(int index) {
        if (TEMPLATES.isEmpty()) return "";
        int normalized = Math.floorMod(index, TEMPLATES.size());
        return TEMPLATES.get(normalized);
    }

    /** 把模板里的 {@code {rank}} 全部替换成档位（档位按 {@link RankUpToolRules} 归一化）。 */
    public static String fill(String template, String rank) {
        if (template == null || template.isEmpty()) return "";
        return template.replace(RANK_TOKEN, RankUpToolRules.normalizeRank(rank)).trim();
    }
}
