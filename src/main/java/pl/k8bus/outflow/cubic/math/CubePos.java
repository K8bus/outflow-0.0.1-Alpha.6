package pl.k8bus.outflow.cubic.math;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Reprezentuje pozycję kostki (Cube) o rozmiarze 16x16x16.
 * Jest to ulepszona, trójwymiarowa wersja ChunkPos.
 */
public record CubePos(int x, int y, int z) {
    
    /**
     * Konwertuje absolutną pozycję bloku na pozycję kostki.
     */
    public static CubePos fromBlockPos(BlockPos pos) {
        return new CubePos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
    
    /**
     * Konwertuje klasyczną pozycję chunkową oraz y na pozycję kostki.
     */
    public static CubePos fromChunkPos(ChunkPos chunkPos, int y) {
        return new CubePos(chunkPos.x(), y, chunkPos.z());
    }
    
    /**
     * Zwraca klasyczny ChunkPos (rzutowanie na płaszczyznę XZ).
     */
    public ChunkPos toChunkPos() {
        return new ChunkPos(x, z);
    }
    
    /**
     * Pobiera minimalną koordynatę bloku w osi X
     */
    public int getMinBlockX() {
        return x << 4;
    }
    
    /**
     * Pobiera minimalną koordynatę bloku w osi Y
     */
    public int getMinBlockY() {
        return y << 4;
    }
    
    /**
     * Pobiera minimalną koordynatę bloku w osi Z
     */
    public int getMinBlockZ() {
        return z << 4;
    }
    
    /**
     * Pobiera maksymalną koordynatę bloku w osi X
     */
    public int getMaxBlockX() {
        return (x << 4) | 15;
    }
    
    /**
     * Pobiera maksymalną koordynatę bloku w osi Y
     */
    public int getMaxBlockY() {
        return (y << 4) | 15;
    }
    
    /**
     * Pobiera maksymalną koordynatę bloku w osi Z
     */
    public int getMaxBlockZ() {
        return (z << 4) | 15;
    }
    
    /**
     * Zwraca pozycję sekcji (SectionPos to wewnętrzna klasa MC dla 16x16x16).
     */
    public SectionPos asSectionPos() {
        return SectionPos.of(x, y, z);
    }
    
    /**
     * Wylicza prosty dystans (Chebyshev distance, tj. max(dx, dy, dz))
     */
    public int getChebyshevDistance(CubePos other) {
        return Math.max(Math.max(Math.abs(this.x - other.x), Math.abs(this.y - other.y)), Math.abs(this.z - other.z));
    }
    
    /**
     * Dystans kwadratowy (do kulistego render distance).
     */
    public int getDistanceSquared(CubePos other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
    
    public long asLong() {
        // Kodujemy x, y, z do 64-bitowego longa
        // Używamy 22 bitów na X, 20 bitów na Y, 22 bity na Z
        long lX = (long)(this.x & 0x3FFFFF);
        long lY = (long)(this.y & 0xFFFFF);
        long lZ = (long)(this.z & 0x3FFFFF);
        return lX | (lZ << 22) | (lY << 44);
    }
    
    public static CubePos fromLong(long packed) {
        int x = (int)(packed & 0x3FFFFF);
        int z = (int)((packed >>> 22) & 0x3FFFFF);
        int y = (int)((packed >>> 44) & 0xFFFFF);
        
        // Znak (sign extension)
        if (x >= 0x200000) x -= 0x400000;
        if (z >= 0x200000) z -= 0x400000;
        if (y >= 0x80000) y -= 0x100000;
        
        return new CubePos(x, y, z);
    }
}
