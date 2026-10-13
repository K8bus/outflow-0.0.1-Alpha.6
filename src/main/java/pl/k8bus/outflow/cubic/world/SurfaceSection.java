package pl.k8bus.outflow.cubic.world;

/**
 * Interfejs dodawany przez Mixin do LevelChunkSection.
 * Pozwala na oznaczanie sekcji (16x16x16) jako zawierających powierzchnię terenu.
 */
public interface SurfaceSection {
    boolean outflow$isSurface();
    void outflow$setSurface(boolean surface);
}
