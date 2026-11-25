package keystrokesmod.utility.displace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.world.gen.WorldGeneratorType;

final class VoidGeometry implements VoidDisplacePlanner.Terrain {
    private static final VoidGeometry.Cell EMPTY = new VoidGeometry.Cell(Collections.emptyList(), null, null, 0.546);
    private static final VoidGeometry.Column UNLOADED = new VoidGeometry.Column(null);
    private static final int CAPTURE_PADDING = 3;
    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int firstChunkX;
    private final int firstChunkZ;
    private final int chunkDepth;
    private final WorldChunk[] chunks;
    private final VoidGeometry.Column[] columns;
    private final BitSet dirtyColumns;
    private final boolean debugWorld;
    private final Mutable pos = new Mutable();
    private final Box region;
    private boolean surfacesBuilt;
    private VoidGeometry.Node surfaces;

    VoidGeometry(World world, VoidDisplacePlanner.Input input, boolean capture, VoidGeometry previous) {
        this.world = world;
        int padding = capture ? 3 : 0;
        int margin = margin(input) + padding;
        int centerX = MathHelper.floor((input.box.minX + input.box.maxX) * 0.5);
        int centerY = MathHelper.floor(input.box.minY);
        int centerZ = MathHelper.floor((input.box.minZ + input.box.maxZ) * 0.5);
        this.minX = centerX - margin;
        this.minY = previous != null && centerY - 12 >= previous.minY && centerY + 6 <= previous.minY + previous.sizeY
                ? previous.minY
                : centerY - 12 - padding;
        this.minZ = centerZ - margin;
        this.sizeX = this.sizeZ = margin * 2 + 1;
        this.sizeY = 18 + padding * 2;
        this.columns = new VoidGeometry.Column[this.sizeX * this.sizeZ];
        this.dirtyColumns = capture ? new BitSet(this.columns.length) : null;
        if (capture) {
            this.dirtyColumns.set(0, this.columns.length);
        }

        this.debugWorld = world.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES;
        this.region = new Box(
                this.minX - 1,
                this.minY - 1,
                this.minZ - 1,
                this.minX + this.sizeX + 1,
                this.minY + this.sizeY + 1,
                this.minZ + this.sizeZ + 1);
        this.firstChunkX = this.minX >> 4;
        this.firstChunkZ = this.minZ >> 4;
        int lastChunkX = this.minX + this.sizeX - 1 >> 4;
        int lastChunkZ = this.minZ + this.sizeZ - 1 >> 4;
        this.chunkDepth = lastChunkZ - this.firstChunkZ + 1;
        this.chunks = new WorldChunk[(lastChunkX - this.firstChunkX + 1) * this.chunkDepth];
        this.refreshChunks();
        if (previous != null && this.minY == previous.minY) {
            this.reuseColumns(previous);
        }
    }

    private VoidGeometry(VoidGeometry source) {
        this.world = null;
        this.minX = source.minX;
        this.minY = source.minY;
        this.minZ = source.minZ;
        this.sizeX = source.sizeX;
        this.sizeY = source.sizeY;
        this.sizeZ = source.sizeZ;
        this.firstChunkX = this.firstChunkZ = this.chunkDepth = 0;
        this.chunks = null;
        this.columns = (VoidGeometry.Column[]) source.columns.clone();
        this.dirtyColumns = null;
        this.debugWorld = false;
        this.region = source.region;
    }

    private static int margin(VoidDisplacePlanner.Input input) {
        return (int) Math.ceil(input.radius + 4.5 + 2.0);
    }

    private void reuseColumns(VoidGeometry previous) {
        int endX = Math.min(this.minX + this.sizeX, previous.minX + previous.sizeX);
        int endZ = Math.min(this.minZ + this.sizeZ, previous.minZ + previous.sizeZ);

        for (int x = Math.max(this.minX, previous.minX); x < endX; x++) {
            for (int z = Math.max(this.minZ, previous.minZ); z < endZ; z++) {
                int previousIndex = previous.columnIndex(x, z);
                if (!previous.dirtyColumns.get(previousIndex) && this.chunk(x, z) == previous.chunk(x, z)) {
                    int index = this.columnIndex(x, z);
                    this.columns[index] = previous.columns[previousIndex];
                    this.dirtyColumns.clear(index);
                }
            }
        }
    }

    boolean covers(VoidDisplacePlanner.Input input) {
        int x = MathHelper.floor((input.box.minX + input.box.maxX) * 0.5);
        int y = MathHelper.floor(input.box.minY);
        int z = MathHelper.floor((input.box.minZ + input.box.maxZ) * 0.5);
        int margin = margin(input);
        return x - margin >= this.minX
                && x + margin < this.minX + this.sizeX
                && z - margin >= this.minZ
                && z + margin < this.minZ + this.sizeZ
                && y - 12 >= this.minY
                && y + 6 <= this.minY + this.sizeY;
    }

    boolean invalidate(int x1, int y1, int z1, int x2, int y2, int z2) {
        int firstX = Math.max(this.minX, x1 - 1);
        int lastX = Math.min(this.minX + this.sizeX - 1, x2 + 1);
        int firstZ = Math.max(this.minZ, z1 - 1);
        int lastZ = Math.min(this.minZ + this.sizeZ - 1, z2 + 1);
        if (firstX <= lastX && firstZ <= lastZ && y1 <= this.minY + this.sizeY && y2 >= 0) {
            for (int x = firstX; x <= lastX; x++) {
                this.dirtyColumns.set(this.columnIndex(x, firstZ), this.columnIndex(x, lastZ) + 1);
            }

            return true;
        } else {
            return false;
        }
    }

    VoidGeometry capture(long deadline) {
        this.refreshChunks();

        for (int index = this.dirtyColumns.nextSetBit(0); index >= 0; index = this.dirtyColumns.nextSetBit(index + 1)) {
            if (System.nanoTime() - deadline >= 0L) {
                return null;
            }

            int x = this.minX + index / this.sizeZ;
            int z = this.minZ + index % this.sizeZ;
            WorldChunk chunk = this.chunk(x, z);
            if (chunk == null) {
                this.columns[index] = UNLOADED;
            } else {
                VoidGeometry.Column column = new VoidGeometry.Column(new VoidGeometry.Cell[this.sizeY]);
                column.lowestSupport = this.readLowestSupport(chunk, x, z);

                for (int y = Math.max(0, this.minY); y <= Math.min(255, this.minY + this.sizeY - 1); y++) {
                    if (this.emptySection(chunk, y)) {
                        y |= 15;
                    } else {
                        column.cells[y - this.minY] = this.readCell(chunk, x, y, z);
                    }
                }

                this.columns[index] = column;
            }

            this.dirtyColumns.clear(index);
        }

        return new VoidGeometry(this);
    }

    private void refreshChunks() {
        for (int index = 0; index < this.chunks.length; index++) {
            int x = this.firstChunkX + index / this.chunkDepth;
            int z = this.firstChunkZ + index % this.chunkDepth;
            WorldChunk previous = this.chunks[index];
            WorldChunk current = this.world.isChunkLoaded(
                            this.pos.set(Math.max(this.minX, x << 4), 64, Math.max(this.minZ, z << 4)), false)
                    ? this.world.getChunkAt(x, z)
                    : null;
            if (previous != current) {
                this.chunks[index] = current;
                if (this.dirtyColumns != null) {
                    this.invalidate(x << 4, 0, z << 4, (x << 4) + 15, 255, (z << 4) + 15);
                }
            }
        }
    }

    private void buildSurfaces() {
        List<Box> anchors = new ArrayList<>();

        for (int x = this.minX; x < this.minX + this.sizeX; x++) {
            for (int z = this.minZ; z < this.minZ + this.sizeZ; z++) {
                if (!this.columnLoaded(x, z)) {
                    anchors.add(new Box(x, this.minY, z, x + 1, this.minY + this.sizeY, z + 1));
                } else {
                    for (int y = Math.max(0, this.minY); y <= Math.min(255, this.minY + this.sizeY - 1); y++) {
                        VoidGeometry.Cell cell = this.cell(x, y, z);
                        if (cell.anchor != null) {
                            anchors.add(cell.anchor);
                        }
                    }
                }
            }
        }

        Box[] boxes = anchors.toArray(new Box[0]);
        this.surfaces = boxes.length == 0 ? null : new VoidGeometry.Node(boxes, 0, boxes.length);
        this.surfacesBuilt = true;
    }

    @Override
    public boolean loaded(Box box) {
        int firstX = MathHelper.floor(box.minX);
        int lastX = MathHelper.floor(box.maxX);
        int firstZ = MathHelper.floor(box.minZ);
        int lastZ = MathHelper.floor(box.maxZ);
        if (firstX >= this.minX
                && lastX < this.minX + this.sizeX
                && firstZ >= this.minZ
                && lastZ < this.minZ + this.sizeZ
                && !(box.minY < this.minY)
                && !(box.maxY >= this.minY + this.sizeY)) {
            for (int x = firstX; x <= lastX; x++) {
                for (int z = firstZ; z <= lastZ; z++) {
                    if (!this.columnLoaded(x, z)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<Box> collisions(Box box) {
        List<Box> result = null;

        for (int x = Math.max(this.minX, MathHelper.floor(box.minX) - 1);
                x <= Math.min(this.minX + this.sizeX - 1, MathHelper.floor(box.maxX) + 1);
                x++) {
            for (int z = Math.max(this.minZ, MathHelper.floor(box.minZ) - 1);
                    z <= Math.min(this.minZ + this.sizeZ - 1, MathHelper.floor(box.maxZ) + 1);
                    z++) {
                for (int y = Math.max(this.minY, MathHelper.floor(box.minY) - 1);
                        y <= Math.min(this.minY + this.sizeY - 1, MathHelper.floor(box.maxY) + 1);
                        y++) {
                    List<Box> collisions = this.cell(x, y, z).collisions;

                    for (int index = 0; index < collisions.size(); index++) {
                        Box collision = collisions.get(index);
                        if (collision.intersects(box)) {
                            if (result == null) {
                                result = new ArrayList<>();
                            }

                            result.add(collision);
                        }
                    }
                }
            }
        }

        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public boolean voidBelow(double x, double y, double z) {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        if (blockX >= this.minX
                && blockX < this.minX + this.sizeX
                && blockZ >= this.minZ
                && blockZ < this.minZ + this.sizeZ) {
            VoidGeometry.Column column = this.column(blockX, blockZ);
            if (column == UNLOADED) {
                return false;
            }

            if (column.lowestSupport == -1) {
                column.lowestSupport = this.readLowestSupport(this.chunk(blockX, blockZ), blockX, blockZ);
            }

            return column.lowestSupport > MathHelper.floor(y - 0.01);
        } else {
            return false;
        }
    }

    private int readLowestSupport(WorldChunk chunk, int x, int z) {
        for (int y = 0; y <= Math.min(255, this.minY + this.sizeY); y++) {
            if (this.emptySection(chunk, y)) {
                y |= 15;
            } else {
                Block block = chunk.getBlockState(this.pos.set(x, y, z)).getBlock();
                if (block.getMaterial().blocksMovement() || isRecoverable(block)) {
                    return y;
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    boolean hasVoidBelow(double y) {
        int blockY = MathHelper.floor(y - 0.01);

        for (VoidGeometry.Column column : this.columns) {
            if (column == null || column != UNLOADED && column.lowestSupport > blockY) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double friction(double x, double y, double z) {
        VoidGeometry.Cell cell = this.cell(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        return cell.friction;
    }

    @Override
    public VoidDisplacePlanner.Surface nearest(Vec3d position, double limit) {
        if (!this.surfacesBuilt) {
            this.buildSurfaces();
        }

        VoidGeometry.Nearest nearest = new VoidGeometry.Nearest(position, limit * limit);
        if (this.surfaces != null) {
            this.surfaces.search(nearest);
        }

        Box box = nearest.box;
        Vec3d point = box == null
                ? null
                : new Vec3d(
                        MathHelper.clamp(position.x, box.minX, box.maxX),
                        MathHelper.clamp(position.y, box.minY, box.maxY),
                        MathHelper.clamp(position.z, box.minZ, box.maxZ));
        return new VoidDisplacePlanner.Surface(Math.sqrt(nearest.distanceSq), point);
    }

    @Override
    public boolean recoveryVolume(Box box) {
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
                    VoidGeometry.Cell cell = this.cell(x, y, z);
                    if (cell.recovery != null && cell.recovery.intersects(box)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private VoidGeometry.Cell cell(int x, int y, int z) {
        if (x >= this.minX
                && x < this.minX + this.sizeX
                && y >= this.minY
                && y < this.minY + this.sizeY
                && z >= this.minZ
                && z < this.minZ + this.sizeZ) {
            VoidGeometry.Column column = this.column(x, z);
            if (column == UNLOADED) {
                return EMPTY;
            }

            int index = y - this.minY;
            VoidGeometry.Cell cell = column.cells[index];
            if (cell == null && this.world != null) {
                WorldChunk chunk = this.chunk(x, z);
                cell = y >= 0 && y <= 255 && !this.emptySection(chunk, y) ? this.readCell(chunk, x, y, z) : EMPTY;
                column.cells[index] = cell;
            }

            return cell == null ? EMPTY : cell;
        } else {
            return EMPTY;
        }
    }

    private int columnIndex(int x, int z) {
        return (x - this.minX) * this.sizeZ + z - this.minZ;
    }

    private boolean columnLoaded(int x, int z) {
        return this.world == null ? this.columns[this.columnIndex(x, z)] != UNLOADED : this.chunk(x, z) != null;
    }

    private VoidGeometry.Column column(int x, int z) {
        int index = this.columnIndex(x, z);
        VoidGeometry.Column column = this.columns[index];
        if (column == null) {
            column = this.chunk(x, z) == null ? UNLOADED : new VoidGeometry.Column(new VoidGeometry.Cell[this.sizeY]);
            this.columns[index] = column;
        }

        return column;
    }

    private VoidGeometry.Cell readCell(WorldChunk chunk, int x, int y, int z) {
        this.pos.set(x, y, z);
        BlockState state = chunk.getBlockState(this.pos);
        Block block = state.getBlock();
        if (block == Blocks.AIR) {
            return EMPTY;
        }

        Box recovery = isRecoverable(block) ? new Box(x, y, z, x + 1, y + 1, z + 1) : null;
        Box anchor = null;
        List<Box> collisions;
        if (block.isCube()) {
            anchor = new Box(x, y, z, x + 1, y + 1, z + 1);
            collisions = Collections.singletonList(anchor);
        } else {
            collisions = new ArrayList<>();
            block.updateShape(this.world, this.pos);
            block.addCollisions(this.world, this.pos, state, this.region, collisions, null);
            if (this.dirtyColumns != null
                    && recovery == null
                    && block.canRayTrace(state, false)
                    && !block.canBeReplaced(this.world, this.pos)) {
                anchor = block.getOutlineShape(this.world, this.pos);
            }
        }

        return new VoidGeometry.Cell(
                collisions, recovery, recovery != null ? recovery : anchor, block.slipperiness * 0.91);
    }

    private WorldChunk chunk(int x, int z) {
        return this.chunks[((x >> 4) - this.firstChunkX) * this.chunkDepth + (z >> 4) - this.firstChunkZ];
    }

    private boolean emptySection(WorldChunk chunk, int y) {
        WorldChunkSection section = chunk.getSections()[y >> 4];
        return !this.debugWorld && (section == null || section.isEmpty());
    }

    private static boolean isRecoverable(Block block) {
        return block.getMaterial().isLiquid() || block == Blocks.WEB || block == Blocks.LADDER || block == Blocks.VINE;
    }

    private static double distanceSq(Box box, Vec3d point) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class Cell {
        private final List<Box> collisions;
        private final Box recovery;
        private final Box anchor;
        private final double friction;

        private Cell(List<Box> collisions, Box recovery, Box anchor, double friction) {
            this.collisions = collisions;
            this.recovery = recovery;
            this.anchor = anchor;
            this.friction = friction;
        }
    }

    private static final class Column {
        private final VoidGeometry.Cell[] cells;
        private int lowestSupport = -1;

        private Column(VoidGeometry.Cell[] cells) {
            this.cells = cells;
        }
    }

    private static final class Nearest {
        private final Vec3d origin;
        private double distanceSq;
        private Box box;

        private Nearest(Vec3d origin, double distanceSq) {
            this.origin = origin;
            this.distanceSq = distanceSq;
        }
    }

    private static final class Node {
        private final Box bounds;
        private final Box[] boxes;
        private final int from;
        private final int to;
        private final VoidGeometry.Node left;
        private final VoidGeometry.Node right;

        private Node(Box[] boxes, int from, int to) {
            this.boxes = boxes;
            this.from = from;
            this.to = to;
            Box bounds = boxes[from];

            for (int index = from + 1; index < to; index++) {
                bounds = bounds.union(boxes[index]);
            }

            this.bounds = bounds;
            if (to - from <= 8) {
                this.left = this.right = null;
            } else {
                double width = bounds.maxX - bounds.minX;
                double height = bounds.maxY - bounds.minY;
                double depth = bounds.maxZ - bounds.minZ;
                int axis = width >= height && width >= depth ? 0 : (height >= depth ? 1 : 2);
                Arrays.sort(
                        boxes,
                        from,
                        to,
                        Comparator.comparingDouble(box -> axis == 0
                                ? box.minX + box.maxX
                                : (axis == 1 ? box.minY + box.maxY : box.minZ + box.maxZ)));
                int middle = from + to >>> 1;
                this.left = new VoidGeometry.Node(boxes, from, middle);
                this.right = new VoidGeometry.Node(boxes, middle, to);
            }
        }

        private void search(VoidGeometry.Nearest nearest) {
            if (!(VoidGeometry.distanceSq(this.bounds, nearest.origin) >= nearest.distanceSq)) {
                if (this.left == null) {
                    for (int index = this.from; index < this.to; index++) {
                        Box box = this.boxes[index];
                        double distance = VoidGeometry.distanceSq(box, nearest.origin);
                        if (distance < nearest.distanceSq) {
                            nearest.distanceSq = distance;
                            nearest.box = box;
                        }
                    }
                } else {
                    VoidGeometry.Node first = VoidGeometry.distanceSq(this.left.bounds, nearest.origin)
                                    <= VoidGeometry.distanceSq(this.right.bounds, nearest.origin)
                            ? this.left
                            : this.right;
                    VoidGeometry.Node second = first == this.left ? this.right : this.left;
                    first.search(nearest);
                    second.search(nearest);
                }
            }
        }
    }
}
