package pl.k8bus.outflow.cubic.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import pl.k8bus.outflow.cubic.math.CubePos;

import java.util.HashMap;
import java.util.Map;

/**
 * Kostka generowana przez świat, analogiczna do ProtoChunk.
 * Przetrzymuje bloki w trakcie przechodzenia przez etapy ChunkStatus.
 */
public class ProtoCube implements CubeAccess {

    private final CubePos cubePos;
    private final LevelChunkSection section;
    private ChunkStatus status;
    private boolean isSurfaceCube = false;
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

    public ProtoCube(CubePos cubePos, net.minecraft.core.RegistryAccess registryAccess) {
        this.cubePos = cubePos;
        this.status = ChunkStatus.EMPTY;
        this.section = new LevelChunkSection(net.minecraft.world.level.chunk.PalettedContainerFactory.create(registryAccess));
    }

    public ProtoCube(CubePos cubePos, net.minecraft.world.level.chunk.PalettedContainerFactory factory) {
        this.cubePos = cubePos;
        this.status = ChunkStatus.EMPTY;
        this.section = new LevelChunkSection(factory);
    }

    @Override
    public CubePos getCubePos() {
        return cubePos;
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        return this.section.setBlockState(x, y, z, state);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        return this.section.getBlockState(x, y, z);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        return this.section.getFluidState(x, y, z);
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
    }

    @Override
    public ChunkStatus getStatus() {
        return status;
    }

    public void setStatus(ChunkStatus status) {
        this.status = status;
    }

    @Override
    public LevelChunkSection getSection() {
        return section;
    }

    @Override
    public boolean isSurfaceCube() {
        return isSurfaceCube;
    }

    @Override
    public void setSurfaceCube(boolean surface) {
        this.isSurfaceCube = surface;
    }

    @Override
    public void setBiome(BlockPos pos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        int x = (pos.getX() & 15) >> 2;
        int y = (pos.getY() & 15) >> 2;
        int z = (pos.getZ() & 15) >> 2;
        if (this.section.getBiomes() instanceof net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> container) {
            container.set(x, y, z, biome);
        }
    }
}
