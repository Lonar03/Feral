package keystrokesmod.utility;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket.BlockUpdate;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunksS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

public final class SharedBlockHighlightCache {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final SharedBlockHighlightCache INSTANCE = new SharedBlockHighlightCache();
    private final Map<Long, Set<BlockPos>> blockListByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<BlockPos>> bedFootByChunk = new ConcurrentHashMap<>();
    private final Set<SharedBlockHighlightCache.UpdateListener> updateListeners = ConcurrentHashMap.newKeySet();
    private final Deque<long[]> scanQueue = new ArrayDeque<>();
    private BlockListHighlightMatcher blockListMatcher;
    private boolean bedAttached;

    private SharedBlockHighlightCache() {}

    public static SharedBlockHighlightCache get() {
        return INSTANCE;
    }

    public void attachBlockList(BlockListSetting setting) {
        this.blockListMatcher = new BlockListHighlightMatcher(setting);
    }

    public void detachBlockList() {
        this.blockListMatcher = null;
        this.blockListByChunk.clear();
    }

    public void attachBed() {
        this.bedAttached = true;
    }

    public void detachBed() {
        this.bedAttached = false;
        this.bedFootByChunk.clear();
    }

    private boolean isBlockListActive() {
        return this.blockListMatcher != null && this.blockListMatcher.isActive();
    }

    private boolean isBedActive() {
        return this.bedAttached;
    }

    private static boolean isBedFoot(BlockState state) {
        return state != null && state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedBlock.Part.FOOT;
    }

    public boolean anyConsumerActive() {
        return this.isBlockListActive() || this.isBedActive();
    }

    public void clear() {
        this.blockListByChunk.clear();
        this.bedFootByChunk.clear();
        this.scanQueue.clear();

        for (SharedBlockHighlightCache.UpdateListener listener : this.updateListeners) {
            listener.onCacheCleared();
        }
    }

    public void addUpdateListener(SharedBlockHighlightCache.UpdateListener listener) {
        if (listener != null) {
            this.updateListeners.add(listener);
        }
    }

    public void removeUpdateListener(SharedBlockHighlightCache.UpdateListener listener) {
        if (listener != null) {
            this.updateListeners.remove(listener);
        }
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (this.anyConsumerActive()) {
            this.scanQueue.addLast(new long[] {chunkX, chunkZ});

            for (SharedBlockHighlightCache.UpdateListener listener : this.updateListeners) {
                listener.onChunkQueued(chunkX, chunkZ);
            }
        }
    }

    public void removeChunk(int chunkX, int chunkZ) {
        long k = key(chunkX, chunkZ);
        this.blockListByChunk.remove(k);
        this.bedFootByChunk.remove(k);

        for (SharedBlockHighlightCache.UpdateListener listener : this.updateListeners) {
            listener.onChunkRemoved(chunkX, chunkZ);
        }
    }

    public void enqueueLoadedChunks() {
        if (this.anyConsumerActive()) {
            this.scanQueue.clear();
            if (mc.world != null && mc.player != null) {
                int rd = mc.options.viewDistance;
                int pcx = (int) mc.player.x >> 4;
                int pcz = (int) mc.player.z >> 4;

                for (int cx = pcx - rd; cx <= pcx + rd; cx++) {
                    for (int cz = pcz - rd; cz <= pcz + rd; cz++) {
                        WorldChunk chunk = mc.world.getChunkAt(cx, cz);
                        if (chunk != null && !(chunk instanceof EmptyChunk)) {
                            this.enqueueChunk(cx, cz);
                        }
                    }
                }
            }
        }
    }

    public void tickScan(int maxSections) {
        if (mc.world != null && this.anyConsumerActive()) {
            if (this.blockListMatcher != null) {
                this.blockListMatcher.beginScanPass();
            }

            int remaining = maxSections;

            while (remaining > 0 && !this.scanQueue.isEmpty()) {
                long[] cpos = this.scanQueue.pollFirst();
                int cx = (int) cpos[0];
                int cz = (int) cpos[1];
                WorldChunk chunk = mc.world.getChunkAt(cx, cz);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    remaining -= this.scanChunk(chunk);
                }
            }
        }
    }

    public void onBlockChange(BlockPos pos, BlockState newState) {
        if (this.blockListMatcher != null) {
            this.blockListMatcher.beginScanPass();
        }

        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        BlockPos immutablePos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        if (this.isBlockListActive()) {
            if (this.blockListMatcher.matchesBlock(newState)) {
                this.blockListByChunk
                        .computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet())
                        .add(immutablePos);
            } else {
                Set<BlockPos> set = this.blockListByChunk.get(ck);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }

        if (this.isBedActive()) {
            if (isBedFoot(newState)) {
                this.bedFootByChunk
                        .computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet())
                        .add(immutablePos);
            } else {
                Set<BlockPos> set = this.bedFootByChunk.get(ck);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }

        for (SharedBlockHighlightCache.UpdateListener listener : this.updateListeners) {
            listener.onBlockChanged(immutablePos, newState);
        }
    }

    public void onBlockListSettingsChanged() {
        if (this.blockListMatcher != null) {
            this.blockListMatcher.beginScanPass();
        }

        this.rescanBlockListLayer();
    }

    private void rescanBlockListLayer() {
        this.blockListByChunk.clear();
        this.scanQueue.clear();
        if (this.anyConsumerActive()) {
            this.enqueueLoadedChunks();
        }
    }

    public Iterable<Entry<Long, Set<BlockPos>>> entriesBlockList() {
        return this.blockListByChunk.entrySet();
    }

    public Iterable<Entry<Long, Set<BlockPos>>> entriesBedFeet() {
        return this.bedFootByChunk.entrySet();
    }

    public int totalBlockList() {
        int n = 0;

        for (Set<BlockPos> s : this.blockListByChunk.values()) {
            n += s.size();
        }

        return n;
    }

    public int totalBedFeet() {
        int n = 0;

        for (Set<BlockPos> s : this.bedFootByChunk.values()) {
            n += s.size();
        }

        return n;
    }

    public boolean containsBlockList(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = this.blockListByChunk.get(ck);
        return set != null && set.contains(pos);
    }

    public void handleReceivePacket(ReceivePacketEvent e) {
        if (this.anyConsumerActive()) {
            if (e.getPacket() instanceof BlockUpdateS2CPacket) {
                BlockUpdateS2CPacket pkt = (BlockUpdateS2CPacket) e.getPacket();
                this.onBlockChange(pkt.getPos(), pkt.getBlockState());
            } else if (e.getPacket() instanceof BlocksUpdateS2CPacket) {
                BlocksUpdateS2CPacket pkt = (BlocksUpdateS2CPacket) e.getPacket();

                for (BlockUpdate data : pkt.getUpdates()) {
                    this.onBlockChange(data.getBlockPos(), data.getBlockState());
                }
            } else if (e.getPacket() instanceof WorldChunkS2CPacket) {
                WorldChunkS2CPacket pkt = (WorldChunkS2CPacket) e.getPacket();
                if (pkt.getSections() == 0) {
                    this.removeChunk(pkt.getChunkX(), pkt.getChunkZ());
                } else {
                    this.enqueueChunk(pkt.getChunkX(), pkt.getChunkZ());
                }
            } else if (e.getPacket() instanceof WorldChunksS2CPacket) {
                WorldChunksS2CPacket pkt = (WorldChunksS2CPacket) e.getPacket();

                for (int i = 0; i < pkt.getChunkCount(); i++) {
                    this.enqueueChunk(pkt.getChunkX(i), pkt.getChunkZ(i));
                }
            }
        }
    }

    private int scanChunk(WorldChunk chunk) {
        int scanned = 0;
        long ck = key(chunk.chunkX, chunk.chunkZ);
        Set<BlockPos> blockFound = ConcurrentHashMap.newKeySet();
        Set<BlockPos> bedFound = ConcurrentHashMap.newKeySet();
        WorldChunkSection[] sections = chunk.getSections();
        int baseX = chunk.chunkX << 4;
        int baseZ = chunk.chunkZ << 4;

        for (int si = 0; si < sections.length; si++) {
            WorldChunkSection section = sections[si];
            if (section != null) {
                scanned++;
                int baseY = si << 4;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state != null) {
                                BlockPos matchPos = null;
                                if (this.isBlockListActive() && this.blockListMatcher.matchesBlock(state)) {
                                    matchPos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                    blockFound.add(matchPos);
                                }

                                if (this.isBedActive() && isBedFoot(state)) {
                                    bedFound.add(
                                            matchPos != null
                                                    ? matchPos
                                                    : new BlockPos(baseX + x, baseY + y, baseZ + z));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (this.isBlockListActive()) {
            if (!blockFound.isEmpty()) {
                this.blockListByChunk.put(ck, blockFound);
            } else {
                this.blockListByChunk.remove(ck);
            }
        }

        if (this.isBedActive()) {
            if (!bedFound.isEmpty()) {
                this.bedFootByChunk.put(ck, bedFound);
            } else {
                this.bedFootByChunk.remove(ck);
            }
        }

        return Math.max(scanned, 1);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | cz & 4294967295L;
    }

    public interface UpdateListener {
        void onBlockChanged(BlockPos var1, BlockState var2);

        void onChunkQueued(int var1, int var2);

        void onChunkRemoved(int var1, int var2);

        void onCacheCleared();
    }
}
