package pl.k8bus.outflow.worldgen.math;

public final class SDFUtils {

    private SDFUtils() {}

    /**
     * GLADKIE PRZEJŚCIE (Smoothstep) - Odpowiada za realistyczne brzegi.
     * Hermitowa interpolacja, która tworzy krzywą w kształcie litery S.
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        if (x <= edge0) return 0.0;
        if (x >= edge1) return 1.0;
        double t = (x - edge0) / (edge1 - edge0);
        return t * t * (3.0 - 2.0 * t); // Klasyczny wzór z grafiki 3D
    }

    /**
     * PROFIL BATYMETRYCZNY (Misa jeziora).
     * Zwraca odległość od środka. Używamy dx i dz jako różnicy między aktualnym punktem a środkiem.
     */
    public static double sdfCircle(double dx, double dz, double radius) {
        return Math.sqrt(dx * dx + dz * dz) - radius;
    }

    /**
     * RZEKA MEANDRUJĄCA (Zniekształcenie przestrzeni).
     * Zakrzywia "prostą" rzekę przy użyciu fali sinusoidalnej, tworząc meandry w płaskich dolinach.
     * * @param dx, dz - współrzędne od początku doliny rzecznej
     * @param width - szerokość koryta
     * @param amplitude - jak szeroko rzeka "rzuca" się na boki
     * @param frequency - jak gęste są zakręty (np. 0.05)
     */
    public static double sdfMeanderingRiver(double dx, double dz, double width, double amplitude, double frequency) {
        // Obliczamy wygięcie koryta za pomocą sinusa
        double curve = amplitude * Math.sin(dx * frequency);
        // Odległość to różnica między pozycją Z a krzywą meandru, minus szerokość rzeki
        return Math.abs(dz - curve) - width;
    }

    /**
     * GŁADKIE ŁĄCZENIE (Polynomial Smooth Minimum).
     * Pozwala stopić dwa jeziora w jedno lub połączyć rzekę z jeziorem bez ostrego kąta.
     * * @param a, b - odległości SDF z dwóch różnych kształtów
     * @param k - siła wygładzania (im większa, tym bardziej rozmyte połączenie)
     */
    public static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }
}