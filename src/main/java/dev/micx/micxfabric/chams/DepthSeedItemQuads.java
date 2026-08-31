package dev.micx.micxfabric.chams;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.AbstractList;
import java.util.List;

/** 物品四边形标记包装：种子阶段提交的物品 quads 由此类型识别并换用种子 RenderType。 */
public final class DepthSeedItemQuads extends AbstractList<BakedQuad> {
    private final List<BakedQuad> delegate;

    public DepthSeedItemQuads(List<BakedQuad> delegate) {
        this.delegate = delegate;
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
