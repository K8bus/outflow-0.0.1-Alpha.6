package pl.k8bus.outflow.worldgen.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import pl.k8bus.outflow.worldgen.feature.lake.LakeCacheKey;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public final class NoiseCache {

    private static final ConcurrentHashMap<Long, BlockPos> BASIN_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<LakeCacheKey, CompletableFuture<List<?>>> LAKE_GRID_FUTURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> HEIGHT_CACHE = new ConcurrentHashMap<>(8192);
    private static final int MAX_HEIGHT_CACHE_SIZE = 65536;

    public static final ExecutorService WORKER_POOL = ForkJoinPool.commonPool();

    private NoiseCache() {}

    public static int getBaseHeightCached(int x, int z, ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState) {
        long key = asLong(x, z);
        Integer cached = HEIGHT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int h = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        if (HEIGHT_CACHE.size() > MAX_HEIGHT_CACHE_SIZE) {
            HEIGHT_CACHE.clear();
        }
        HEIGHT_CACHE.put(key, h);
        return h;
    }

    public static BlockPos getOrCalculateBasin(int gridX, int gridZ, Supplier<BlockPos> calculator) {
        long key = asLong(gridX, gridZ);
        return BASIN_CACHE.computeIfAbsent(key, k -> calculator.get());
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getOrCalculateLakeGrid(LakeCacheKey key, Supplier<List<T>> calculator) {
        CompletableFuture<List<?>> future = LAKE_GRID_FUTURES.computeIfAbsent(key, k ->
            CompletableFuture.supplyAsync(calculator::get, WORKER_POOL)
        );
        try {
            return (List<T>) future.join();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static <T> void prefetchLakeGrid(LakeCacheKey key, Supplier<List<T>> calculator) {
        LAKE_GRID_FUTURES.computeIfAbsent(key, k ->
            CompletableFuture.supplyAsync(calculator::get, WORKER_POOL)
        );
    }

    public static void clear() {
        BASIN_CACHE.clear();
        LAKE_GRID_FUTURES.clear();
        HEIGHT_CACHE.clear();
    }

    public static long asLong(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }
}