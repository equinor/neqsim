package neqsim.process.util.heattransfer;

import java.io.Serializable;

/**
 * Steady-state boil-off (heat-ingress) calculator for insulated cryogenic or refrigerated vessels.
 *
 * <p>
 * Heat leaking through the insulation evaporates the stored liquid and sets the minimum relief (boil-off) rate that the
 * vent or pressure-relief system must accommodate. The series thermal resistance of the outer film and the insulation
 * layer gives an effective heat-transfer coefficient
 * </p>
 *
 * $$ h_{eff} = \left( \frac{1}{h_o} + \frac{t_{ins}}{k_{ins}} \right)^{-1} $$
 *
 * <p>
 * from which the heat ingress {@code Q = h_eff * A * (T_amb - T_fluid)} and the latent-heat boil-off rate
 * {@code m_dot = Q / L} follow. Sweeping the insulation thickness reproduces the familiar BoilFAST-style trade-off
 * between insulation thickness and relief load.
 * </p>
 *
 * <p>
 * <b>References:</b> ISO 21013; API 521 §5.15 (heat leak); BoilFAST methodology.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BoilOffCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  private double outerFilmCoefficient = 10.0;
  private double insulationConductivity = 0.025;
  private double surfaceAreaM2 = 1.0;
  private double ambientTemperatureK = 288.15;
  private double fluidTemperatureK = 111.65;
  private double latentHeatJPerKg = 510000.0;

  /**
   * Creates a boil-off calculator with default values representative of an LNG storage application.
   */
  public BoilOffCalculator() {
  }

  /**
   * Sets the outer-surface film heat-transfer coefficient.
   *
   * @param outerFilmCoefficient outer film coefficient in W/(m²·K); must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code outerFilmCoefficient} is not positive
   */
  public BoilOffCalculator setOuterFilmCoefficient(double outerFilmCoefficient) {
    if (outerFilmCoefficient <= 0.0) {
      throw new IllegalArgumentException("outerFilmCoefficient must be positive");
    }
    this.outerFilmCoefficient = outerFilmCoefficient;
    return this;
  }

  /**
   * Sets the insulation thermal conductivity.
   *
   * @param insulationConductivity insulation conductivity in W/(m·K); must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code insulationConductivity} is not positive
   */
  public BoilOffCalculator setInsulationConductivity(double insulationConductivity) {
    if (insulationConductivity <= 0.0) {
      throw new IllegalArgumentException("insulationConductivity must be positive");
    }
    this.insulationConductivity = insulationConductivity;
    return this;
  }

  /**
   * Sets the heat-transfer surface area.
   *
   * @param surfaceAreaM2 surface area in m²; must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code surfaceAreaM2} is not positive
   */
  public BoilOffCalculator setSurfaceArea(double surfaceAreaM2) {
    if (surfaceAreaM2 <= 0.0) {
      throw new IllegalArgumentException("surfaceAreaM2 must be positive");
    }
    this.surfaceAreaM2 = surfaceAreaM2;
    return this;
  }

  /**
   * Sets the ambient temperature.
   *
   * @param ambientTemperatureK ambient temperature in K; must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code ambientTemperatureK} is not positive
   */
  public BoilOffCalculator setAmbientTemperatureK(double ambientTemperatureK) {
    if (ambientTemperatureK <= 0.0) {
      throw new IllegalArgumentException("ambientTemperatureK must be positive");
    }
    this.ambientTemperatureK = ambientTemperatureK;
    return this;
  }

  /**
   * Sets the stored-fluid (boiling) temperature.
   *
   * @param fluidTemperatureK fluid temperature in K; must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code fluidTemperatureK} is not positive
   */
  public BoilOffCalculator setFluidTemperatureK(double fluidTemperatureK) {
    if (fluidTemperatureK <= 0.0) {
      throw new IllegalArgumentException("fluidTemperatureK must be positive");
    }
    this.fluidTemperatureK = fluidTemperatureK;
    return this;
  }

  /**
   * Sets the latent heat of vaporization of the stored fluid.
   *
   * @param latentHeatJPerKg latent heat in J/kg; must be positive
   * @return this calculator for chaining
   * @throws IllegalArgumentException if {@code latentHeatJPerKg} is not positive
   */
  public BoilOffCalculator setLatentHeat(double latentHeatJPerKg) {
    if (latentHeatJPerKg <= 0.0) {
      throw new IllegalArgumentException("latentHeatJPerKg must be positive");
    }
    this.latentHeatJPerKg = latentHeatJPerKg;
    return this;
  }

  /**
   * Computes the effective heat-transfer coefficient for a given insulation thickness.
   *
   * @param insulationThicknessM insulation thickness in m; must be non-negative
   * @return effective heat-transfer coefficient in W/(m²·K)
   * @throws IllegalArgumentException if {@code insulationThicknessM} is negative
   */
  public double effectiveHeatTransferCoefficient(double insulationThicknessM) {
    if (insulationThicknessM < 0.0) {
      throw new IllegalArgumentException("insulationThicknessM must be non-negative");
    }
    double resistance = 1.0 / outerFilmCoefficient + insulationThicknessM / insulationConductivity;
    return 1.0 / resistance;
  }

  /**
   * Computes the steady heat ingress through the insulation.
   *
   * @param insulationThicknessM insulation thickness in m; must be non-negative
   * @return heat ingress in W (positive into the cold fluid)
   * @throws IllegalArgumentException if {@code insulationThicknessM} is negative
   */
  public double heatIngressW(double insulationThicknessM) {
    double hEff = effectiveHeatTransferCoefficient(insulationThicknessM);
    return hEff * surfaceAreaM2 * (ambientTemperatureK - fluidTemperatureK);
  }

  /**
   * Computes the latent-heat boil-off (relief) rate.
   *
   * @param insulationThicknessM insulation thickness in m; must be non-negative
   * @return boil-off rate in kg/s
   * @throws IllegalArgumentException if {@code insulationThicknessM} is negative
   */
  public double boilOffRateKgPerS(double insulationThicknessM) {
    return heatIngressW(insulationThicknessM) / latentHeatJPerKg;
  }

  /**
   * Computes the latent-heat boil-off (relief) rate per hour.
   *
   * @param insulationThicknessM insulation thickness in m; must be non-negative
   * @return boil-off rate in kg/h
   * @throws IllegalArgumentException if {@code insulationThicknessM} is negative
   */
  public double boilOffRateKgPerH(double insulationThicknessM) {
    return boilOffRateKgPerS(insulationThicknessM) * 3600.0;
  }

  /**
   * Sweeps a range of insulation thicknesses and returns the corresponding boil-off rates.
   *
   * @param minThicknessM minimum insulation thickness in m; must be non-negative
   * @param maxThicknessM maximum insulation thickness in m; must be greater than {@code minThicknessM}
   * @param points number of evenly spaced points; must be at least two
   * @return a two-row array: {@code [0]} thicknesses in m, {@code [1]} boil-off rates in kg/h
   * @throws IllegalArgumentException if the range or point count is invalid
   */
  public double[][] sweepInsulationThickness(double minThicknessM, double maxThicknessM, int points) {
    if (minThicknessM < 0.0) {
      throw new IllegalArgumentException("minThicknessM must be non-negative");
    }
    if (maxThicknessM <= minThicknessM) {
      throw new IllegalArgumentException("maxThicknessM must be greater than minThicknessM");
    }
    if (points < 2) {
      throw new IllegalArgumentException("points must be at least 2");
    }
    double[] thickness = new double[points];
    double[] rate = new double[points];
    double step = (maxThicknessM - minThicknessM) / (points - 1);
    for (int i = 0; i < points; i++) {
      thickness[i] = minThicknessM + i * step;
      rate[i] = boilOffRateKgPerH(thickness[i]);
    }
    return new double[][] { thickness, rate };
  }
}
