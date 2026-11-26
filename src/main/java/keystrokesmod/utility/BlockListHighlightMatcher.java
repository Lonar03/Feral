package keystrokesmod.utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;

final class BlockListHighlightMatcher {
    private final BlockListSetting setting;
    private Set<String> matcherIds;
    private Map<String, Object> matcherWildcards;
    private int lastListHash;

    BlockListHighlightMatcher(BlockListSetting setting) {
        this.setting = setting;
        this.rebuildMatcher();
    }

    void beginScanPass() {
        int h = this.setting.getBlocks().hashCode();
        if (h != this.lastListHash) {
            this.rebuildMatcher();
        }
    }

    boolean isActive() {
        return !this.setting.getBlocks().isEmpty();
    }

    boolean matchesBlock(BlockState state) {
        if (state == null) {
            return false;
        }

        Block block = state.getBlock();
        if (block == null) {
            return false;
        }

        Object nameObj = Block.REGISTRY.getKey(block);
        if (nameObj == null) {
            return false;
        }

        String registryId = nameObj.toString();
        if (this.matcherWildcards.containsKey(registryId)) {
            return true;
        }

        int meta = block.getMetadataFromState(state);
        return meta != 0 && this.matcherIds.contains(registryId + ":" + meta)
                ? true
                : this.matcherIds.contains(registryId);
    }

    private void rebuildMatcher() {
        List<String> blocks = this.setting.getBlocks();
        this.lastListHash = blocks.hashCode();
        this.matcherIds = new HashSet<>();
        this.matcherWildcards = new HashMap<>();

        for (String id : blocks) {
            if (id.endsWith(":*")) {
                String base = id.substring(0, id.length() - 2);
                this.matcherWildcards.put(base, null);
            } else {
                this.matcherIds.add(id);
            }
        }
    }
}
