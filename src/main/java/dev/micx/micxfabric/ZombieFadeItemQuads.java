package dev.micx.micxfabric;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.AbstractList;
import java.util.List;

/** 手持淡化标记包装：携带提交瞬间的 fade alpha（0~255），回放阶段据此切管线/注色。 */
public final class ZombieFadeItemQuads extends AbstractList<BakedQuad> {
    private final List<BakedQuad> delegate;
    private final int alpha;

    public ZombieFadeItemQuads(List<BakedQuad> delegate, int alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    /** 本组 quads 的目标不透明度（0~255）。 */
    public int fadeAlpha() {
        return alpha;
    }

    @Override
    public BakedQuad get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
