package dev.micx.micxfabric;

import java.util.Locale;

/** Supported translation languages and stable ISO-639-1 codes（对照 1.8.9 TranslationLanguage）。 */
public enum TranslationLanguage {
    AUTO("auto", "自动识别", "the detected source language"),
    ZH("zh", "简体中文", "Simplified Chinese"),
    EN("en", "English", "English"),
    DE("de", "Deutsch", "German"),
    FR("fr", "Français", "French"),
    JA("ja", "日本語", "Japanese"),
    RU("ru", "Русский", "Russian"),
    ES("es", "Español", "Spanish"),
    IT("it", "Italiano", "Italian"),
    PT("pt", "Português", "Portuguese"),
    KO("ko", "한국어", "Korean"),
    NL("nl", "Nederlands", "Dutch"),
    PL("pl", "Polski", "Polish"),
    TR("tr", "Türkçe", "Turkish");

    private final String code;
    private final String displayName;
    private final String promptName;

    TranslationLanguage(String code, String displayName, String promptName) {
        this.code = code;
        this.displayName = displayName;
        this.promptName = promptName;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
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
