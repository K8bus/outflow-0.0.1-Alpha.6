package pl.k8bus.outflow.cubic.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import pl.k8bus.outflow.cubic.math.CubePos;

/**
 * Adapter pozwalający na traktowanie pojedynczej sekcji z klasycznego ChunkAccess
 * jako naszej trójwymiarowej Kostki (CubeAccess).
 * Służy jako pomost między światem 2D a nowym systemem 3D.
 */
public class ChunkSectionCubeAdapter implements CubeAccess {

    private final ChunkAccess chunk;
    private final LevelChunkSection section;
    private final CubePos cubePos;

    public ChunkSectionCubeAdapter(ChunkAccess chunk, LevelChunkSection section, int sectionY) {
        this.chunk = chunk;
        this.section = section;
        this.cubePos = CubePos.fromChunkPos(chunk.getPos(), sectionY);
    }

    @Override
    public CubePos getCubePos() {
        return cubePos;
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state) {
        return chunk.setBlockState(pos, state);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return chunk.getFluidState(pos);
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        chunk.setBlockEntity(blockEntity);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return chunk.getBlockEntity(pos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        chunk.removeBlockEntity(pos);
    }

    @Override
    public ChunkStatus getStatus() {
        return chunk.getPersistedStatus();
    }

    @Override
    public LevelChunkSection getSection() {
        return section;
    }

    @Override
    public boolean isSurfaceCube() {
        return ((SurfaceSection) section).outflow$isSurface();
    }

    @Override
    public void setSurfaceCube(boolean surface) {
        ((SurfaceSection) section).outflow$setSurface(surface);
    }

    @Override
    public void setBiome(BlockPos pos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        // TODO: Odpowiednie rzutowanie PalettedContainerRO na PalettedContainer,
        // by móc zmodyfikować biome z użyciem funkcji specyficznej dla wersji.
    }

    @Override
    public ChunkAccess getChunk() {
        return chunk;
    }
}
