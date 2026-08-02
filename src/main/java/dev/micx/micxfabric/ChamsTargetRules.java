package dev.micx.micxfabric;

/** Minecraft-free target predicates for the textured model Chams path. */
final class ChamsTargetRules {
    private ChamsTargetRules() {
    }

    static boolean isTarget(boolean wither, boolean player, boolean villager,
                            boolean hostile, boolean wolf, boolean ironGolem,
                            boolean alive, boolean deathAnimation) {
        if (!alive || deathAnimation || wither || player || villager) return false;
        return hostile || wolf || ironGolem;
    }
}
