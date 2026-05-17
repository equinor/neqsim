package neqsim.pvtsimulation.regression;

/**
 * Experimental viscosity data point for PVT regression.
 *
 * @author ESOL
 * @version 1.0
 */
public class ViscosityDataPoint {
  private double pressure;
  private double temperature;
  private double viscosity;
  private String phaseName;

  /**
   * Create a viscosity data point.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K
   * @param viscosity dynamic viscosity in Pa s
   * @param phaseName phase name: gas, oil, liquid, aqueous, or water
   * @throws IllegalArgumentException if phaseName is null or empty
   */
  public ViscosityDataPoint(double pressure, double temperature, double viscosity,
      String phaseName) {
    if (phaseName == null || phaseName.trim().isEmpty()) {
      throw new IllegalArgumentException("Viscosity phase name must be specified");
    }
    this.pressure = pressure;
    this.temperature = temperature;
    this.viscosity = viscosity;
    this.phaseName = phaseName.trim().toLowerCase();
  }

  /**
   * Get pressure.
   *
   * @return pressure in bara
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Get temperature.
   *
   * @return temperature in K
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Get experimental dynamic viscosity.
   *
   * @return viscosity in Pa s
   */
  public double getViscosity() {
    return viscosity;
  }

  /**
   * Get phase name.
   *
   * @return phase name for this viscosity measurement
   */
  public String getPhaseName() {
    return phaseName;
  }

  /**
   * Get the regression property index for the phase name.
   *
   * @return property index used by {@link PVTRegressionFunction}
   */
  public int getPhaseIndex() {
    if ("gas".equals(phaseName) || "vapor".equals(phaseName)) {
      return 0;
    } else if ("oil".equals(phaseName) || "liquid".equals(phaseName)) {
      return 1;
    } else if ("aqueous".equals(phaseName) || "water".equals(phaseName)) {
      return 2;
    }
    return 3;
  }
}
