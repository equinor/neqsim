package neqsim.thermo.util.empiric;

/**
 * <p>
 * Water class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Water {
    /**
     * <p>
     * waterDensity. Correlation of Kell (1975) for density of air free water at 1 atmosphere
     *
     * </p>
     *
     * @param temperature a double
     * @return a double
     */
    public static double waterDensity(double temperature) {
        double Tcelcius = temperature - 273.15;
        return (999.83952 + 16.945176 * Tcelcius - 7.9870401e-3 * Tcelcius * Tcelcius
                - 46.170461e-6 * Tcelcius * Tcelcius * Tcelcius
                + 105.56302e-9 * Tcelcius * Tcelcius * Tcelcius * Tcelcius
                - 280.54253e-12 * Tcelcius * Tcelcius * Tcelcius * Tcelcius * Tcelcius)
                / (1.0 + 16.897850e-3 * Tcelcius);
    }

    /**
     * <p>
     * density.
     * </p>
     *
     * @return a double
     */
    public double density() {
        return 1000.0;
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        Water testWater = new Water();
        System.out.println("water density " + Water.waterDensity(273.15 + 4));
    }
}
