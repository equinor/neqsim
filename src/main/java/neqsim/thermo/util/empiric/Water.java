package neqsim.thermo.util.empiric;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Water class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Water {
  /**
   * <p>
   * waterDensity. Correlation of Kell (1975) for density of air free water at 1 atmosphere.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public static double waterDensity(double temperature) {
    double tempCelsius = temperature - 273.15;
    return (999.83952 + 16.945176 * tempCelsius - 7.9870401e-3 * tempCelsius * tempCelsius
        - 46.170461e-6 * tempCelsius * tempCelsius * tempCelsius
        + 105.56302e-9 * tempCelsius * tempCelsius * tempCelsius * tempCelsius
        - 280.54253e-12 * tempCelsius * tempCelsius * tempCelsius * tempCelsius * tempCelsius)
        / (1.0 + 16.897850e-3 * tempCelsius);
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
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    Water testWater = new Water();
    System.out.println("water density " + Water.waterDensity(273.15 + 4));
  }
}
