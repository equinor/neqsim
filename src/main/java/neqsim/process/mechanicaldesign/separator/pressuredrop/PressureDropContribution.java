package neqsim.process.mechanicaldesign.separator.pressuredrop;

import java.io.Serializable;

/**
 * One line item in a separator pressure-drop breakdown.
 *
 * <p>
 * Each contribution represents a single physical mechanism that takes a fraction of the gas-side
 * static pressure (inlet expansion, an internals device, an outlet contraction, etc.). The fields
 * are intentionally diagnostic: they record the loss coefficient, the velocity and density used,
 * and the resulting pressure drop in Pascal so reports can show how each number was obtained.
 * </p>
 *
 * <p>
 * Instances are immutable value objects produced by
 * {@link SeparatorPressureDropCalculator}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PressureDropContribution implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final String source;
  private final double lossCoefficient;
  private final double velocityMs;
  private final double densityKgM3;
  private final double areaM2;
  private final double dpPa;

  /**
   * Creates a new contribution.
   *
   * @param name short identifier (e.g. {@code "inletExpansion"}, {@code "mesh"})
   * @param source where the loss coefficient came from (e.g. {@code "Borda-Carnot"},
   *               {@code "user"}, {@code "default"})
   * @param lossCoefficient dimensionless loss coefficient (Eu or K)
   * @param velocityMs reference velocity used in the velocity head, m/s
   * @param densityKgM3 fluid density used in the velocity head, kg/m3
   * @param areaM2 reference flow area, m2 (NaN when not applicable)
   * @param dpPa resulting pressure drop in Pa (always non-negative)
   */
  public PressureDropContribution(String name, String source, double lossCoefficient,
      double velocityMs, double densityKgM3, double areaM2, double dpPa) {
    this.name = name;
    this.source = source;
    this.lossCoefficient = lossCoefficient;
    this.velocityMs = velocityMs;
    this.densityKgM3 = densityKgM3;
    this.areaM2 = areaM2;
    this.dpPa = dpPa;
  }

  /**
   * Gets the short identifier.
   *
   * @return identifier string
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the source / provenance of the loss coefficient.
   *
   * @return source string
   */
  public String getSource() {
    return source;
  }

  /**
   * Gets the dimensionless loss coefficient.
   *
   * @return loss coefficient
   */
  public double getLossCoefficient() {
    return lossCoefficient;
  }

  /**
   * Gets the reference velocity.
   *
   * @return velocity in m/s
   */
  public double getVelocityMs() {
    return velocityMs;
  }

  /**
   * Gets the reference density.
   *
   * @return density in kg/m3
   */
  public double getDensityKgM3() {
    return densityKgM3;
  }

  /**
   * Gets the reference flow area.
   *
   * @return area in m2 (NaN when not applicable)
   */
  public double getAreaM2() {
    return areaM2;
  }

  /**
   * Gets the resulting pressure drop.
   *
   * @return pressure drop in Pa
   */
  public double getDpPa() {
    return dpPa;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format("%s[K=%.3f, v=%.2f m/s, rho=%.2f kg/m3, dp=%.1f Pa, src=%s]", name,
        lossCoefficient, velocityMs, densityKgM3, dpPa, source);
  }
}
