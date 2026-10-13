package pl.k8bus.outflow.cubic.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
 * Pełna Kostka (Cube) załadowana w świecie.
 * Analogiczna do LevelChunk w klasycznym systemie Minecrafta.
 */
public class Cube implements CubeAccess {

    private final Level level;
    private final CubePos cubePos;
    private final LevelChunkSection section;
    private boolean isSurfaceCube = false;
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

    public Cube(Level level, CubePos cubePos, ProtoCube protoCube) {
        this.level = level;
        this.cubePos = cubePos;
        this.section = protoCube.getSection();
        this.isSurfaceCube = protoCube.isSurfaceCube();
        
        // Kopiowanie entities z proto
        // TODO: właściwa inicjalizacja block entity z NBT dla LevelChunk
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
        
        BlockState oldState = this.section.setBlockState(x, y, z, state);
        
        if (oldState != state && !this.level.isClientSide()) {
            // Logika aktualizacji światła / update'u bloku mogłaby być wywołana tutaj
        }
        
        return oldState;
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
        if (!level.isClientSide()) {
            level.setBlockEntity(blockEntity);
        }
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
        if (!level.isClientSide()) {
            level.removeBlockEntity(pos);
        }
    }

    @Override
    public ChunkStatus getStatus() {
        return ChunkStatus.FULL;
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
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        // TODO: Odpowiednie rzutowanie PalettedContainerRO na PalettedContainer,
        // by móc zmodyfikować biome z użyciem funkcji specyficznej dla wersji.
    }
}
