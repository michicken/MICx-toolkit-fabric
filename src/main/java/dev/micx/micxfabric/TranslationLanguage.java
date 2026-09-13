package dev.micx.micxfabric;

import java.util.Locale;

/** Supported translation languages and stable ISO-639-1 codes（对照 1.8.9 TranslationLanguage）。 */
public enum TranslationLanguage {
    AUTO("auto", "自动识别", "自动识别", "the detected source language"),
    ZH("zh", "简体中文", "中文", "Simplified Chinese"),
    EN("en", "English", "英语", "English"),
    DE("de", "Deutsch", "德语", "German"),
    FR("fr", "Français", "法语", "French"),
    JA("ja", "日本語", "日语", "Japanese"),
    RU("ru", "Русский", "俄语", "Russian"),
    ES("es", "Español", "西班牙语", "Spanish"),
    IT("it", "Italiano", "意大利语", "Italian"),
    PT("pt", "Português", "葡萄牙语", "Portuguese"),
    KO("ko", "한국어", "韩语", "Korean"),
    NL("nl", "Nederlands", "荷兰语", "Dutch"),
    PL("pl", "Polski", "波兰语", "Polish"),
    TR("tr", "Türkçe", "土耳其语", "Turkish");

    private final String code;
    private final String displayName;
    private final String zhName;
    private final String promptName;

    TranslationLanguage(String code, String displayName, String zhName, String promptName) {
        this.code = code;
        this.displayName = displayName;
        this.zhName = zhName;
        this.promptName = promptName;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
    /** 面板显示用的中文名（英语/日语/法语…）。 */
    public String zhName() { return zhName; }
    public String promptName() { return promptName; }

    /** Canonicalizes ISO code, language name, hyphen and underscore spellings. */
    public static TranslationLanguage fromCode(String raw) {
        if (raw == null) return AUTO;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (TranslationLanguage language : values()) {
            if (language.code.equals(value) || language.displayName.toLowerCase(Locale.ROOT).equals(value)
                    || language.promptName.toLowerCase(Locale.ROOT).equals(value)) return language;
        }
        if ("中文".equals(value) || "汉语".equals(value)) return ZH;
        if ("英语".equals(value) || "英文".equals(value)) return EN;
        if ("德语".equals(value)) return DE;
        if ("法语".equals(value)) return FR;
        if ("日语".equals(value)) return JA;
        if ("俄语".equals(value)) return RU;
        if ("西班牙语".equals(value)) return ES;
        if ("意大利语".equals(value)) return IT;
        if ("葡萄牙语".equals(value)) return PT;
        if ("韩语".equals(value)) return KO;
        return AUTO;
    }

    /** Target languages offered for outgoing chat; AUTO and Chinese are source-only here. */
    public static TranslationLanguage[] outgoingTargets() {
        return new TranslationLanguage[]{EN, DE, FR, JA, RU, ES, IT, PT, KO, NL, PL, TR};
    }

    public static TranslationLanguage nextOutgoingTarget(TranslationLanguage current) {
        TranslationLanguage[] targets = outgoingTargets();
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == current) return targets[(i + 1) % targets.length];
        }
        return targets[0];
    }
}
