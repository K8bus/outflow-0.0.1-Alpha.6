package pl.k8bus.outflow.worldgen.feature.lake;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class LakeCenterData {
    public final int startX;
    public final int startZ;
    public final int centerY;
    public final int radius;
    public final int maxDepth;
    public int waterLevel;
    public final boolean valid;
    public final double[] radiusProfile;
    public final BlockPos spillwayPos;
    public final List<LakeCenterData> connectedLobes = new ArrayList<>();

    public LakeCenterData(int startX, int startZ, int centerY, int radius, int maxDepth, int waterLevel,
            boolean valid, double[] radiusProfile) {
        this(startX, startZ, centerY, radius, maxDepth, waterLevel, valid, radiusProfile, null);
    }

    public LakeCenterData(int startX, int startZ, int centerY, int radius, int maxDepth, int waterLevel,
            boolean valid, double[] radiusProfile, BlockPos spillwayPos) {
        this.startX = startX;
        this.startZ = startZ;
        this.centerY = centerY;
        this.radius = radius;
        this.maxDepth = maxDepth;
        this.waterLevel = waterLevel;
        this.valid = valid;
        this.radiusProfile = radiusProfile;
        this.spillwayPos = spillwayPos;
    }

    public void connectLobe(LakeCenterData other) {
        if (other != this && !connectedLobes.contains(other)) {
            connectedLobes.add(other);
        }
    }
}
