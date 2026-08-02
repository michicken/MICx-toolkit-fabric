package dev.micx.micxfabric;

import java.util.HashSet;
import java.util.Set;

/** Once-per-round/event guards for client-side chat side effects. */
public final class AutoBehaviorLatch {
    private final Set<String> tokens = new HashSet<>();

    public void clear() {
        tokens.clear();
    }

    public boolean claim(String token) {
        return token != null && !token.isBlank() && tokens.add(token);
    }

    public boolean claimRound(String action, int round) {
        return claim(action + ":round:" + round);
    }

    public boolean claimGameOver(int round) {
        return claim("game-over:round:" + round);
    }

    public boolean claimed(String token) {
        return tokens.contains(token);
    }
}
