package dev.micx.micxfabric;

/** Minecraft-free predicates for the Forge TOO/Clown signatures. */
final class ZombieThreatRules {
    static final int TOO_CHEST_COLOR = 43_570;
    static final int SLIME_BABY_COLOR = 14_381_203;
    static final int CLOWN_YELLOW = 16_776_960;

    private ZombieThreatRules() {
    }

    /**
     * Forge semantics: the exact signature is preferred; tooStrictGreen enables the
     * historical green-leather fallback and therefore is intentionally not stricter.
     */
    static boolean isToo(boolean baby, boolean skullHelmet, boolean diamondSword,
                         Integer chestColor, boolean tooStrictGreen) {
        if (!baby || !skullHelmet || chestColor == null) return false;
        if (chestColor != SLIME_BABY_COLOR
                && diamondSword && Math.abs(chestColor - TOO_CHEST_COLOR) < 500) return true;
        return tooStrictGreen && chestColor != SLIME_BABY_COLOR && isGreenLeather(chestColor);
    }

    static boolean isGreenLeather(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return green > 50 && green * 100 > red * 115 && green * 100 > blue * 115;
    }

    static boolean isClown(boolean baby, Integer chestColor, Integer legsColor, Integer bootsColor) {
        if (baby || chestColor == null) return false;
        if (Math.abs(chestColor - CLOWN_YELLOW) < 5_000) return true;
        return legsColor != null && bootsColor != null
                && Math.abs(bootsColor - legsColor) > 500_000
                && Math.abs(legsColor - chestColor) > 500_000
                && Math.abs(bootsColor - chestColor) > 500_000;
    }

    /** Giant detection must be supplied by an explicit entity type, never by a name. */
    static boolean isGiantEntityType(boolean explicitGiantEntityType) {
        return explicitGiantEntityType;
    }
}
