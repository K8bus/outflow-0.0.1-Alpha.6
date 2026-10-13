package pl.k8bus.outflow.cubic.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import pl.k8bus.outflow.cubic.math.CubePos;

/**
 * Interfejs dla każdej Kostki (Cube), analogiczny do ChunkAccess.
 * Reprezentuje trójwymiarowy fragment świata 16x16x16.
 */
public interface CubeAccess {

    /**
     * Zwraca trójwymiarową pozycję tej kostki.
     */
    CubePos getCubePos();

    /**
     * Ustawia stan bloku w lokalnych koordynatach (0-15) lub absolutnych.
     */
    @Nullable
    BlockState setBlockState(BlockPos pos, BlockState state);

    /**
     * Pobiera stan bloku z podanej pozycji.
     */
    BlockState getBlockState(BlockPos pos);

    /**
     * Pobiera stan płynu z podanej pozycji.
     */
    FluidState getFluidState(BlockPos pos);

    /**
     * Dodaje BlockEntity do kostki.
     */
    void setBlockEntity(BlockEntity blockEntity);

    /**
     * Pobiera BlockEntity z danej pozycji.
     */
    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    /**
     * Usuwa BlockEntity z danej pozycji.
     */
    void removeBlockEntity(BlockPos pos);

    /**
     * Zwraca status generowania tej kostki (np. EMPTY, CARVERS, FEATURES, FULL).
     * Będziemy na razie używać klasycznego ChunkStatus jako mapowania.
     */
    ChunkStatus getStatus();

    /**
     * Zwraca jedyną sekcję bloków, z której składa się ta kostka.
     * Kostka 16x16x16 zawsze ma tylko 1 sekcję.
     */
    LevelChunkSection getSection();

    /**
     * Zwraca true jeśli kostka jest oznaczona jako "Surface" (powierzchniowa).
     * Przydatne dla OutflowScanner, aby wiedział czy skanować tę kostkę pod kątem gór.
     */
    boolean isSurfaceCube();
    
    /**
     * Oznacza kostkę jako powierzchniową.
     */
    void setSurfaceCube(boolean surface);

    /**
     * Zmienia biom w danym bloku (działa z rozdzielczością 4x4x4).
     */
    void setBiome(BlockPos pos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome);

    /**
     * Zwraca nadrzędny ChunkAccess (jeśli dostępny).
     */
    @Nullable
    default net.minecraft.world.level.chunk.ChunkAccess getChunk() {
        return null;
    }
}
