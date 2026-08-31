package neqsim.process.mechanicaldesign.separator;

import java.io.Serializable;

/**
 * Breakdown of the demisting-cyclone drainage head check for a gas scrubber.
 *
 * <p>
 * Represents the total pressure drop the liquid film must overcome to drain
 * from the cyclone bank back into the vessel bulk — mesh pad pressure drop
 * plus the fraction of cyclone pressure drop acting at the drain chamber —
 * expressed as an equivalent clear-liquid column height. The available head
 * is the geometric elevation from the cyclone deck down to the High-High
 * Liquid Level (LA(HH)). The ratio of required to available head (percent)
 * is the governing conformity metric.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DrainageHeadResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final double meshDpPa;
  private final double cycloneDpToDrainPa;
  private final double totalDpPa;
  private final double requiredHeadMm;
  private final double availableHeadMm;
  private final double percentOfAvailable;
  private final double gasDensityKgM3;
  private final double liquidDensityKgM3;

  /**
   * Constructs a DrainageHeadResult.
   *
   * @param meshDpPa             mesh pad pressure drop [Pa]
   * @param cycloneDpToDrainPa   cyclone pressure drop reaching drain chamber [Pa]
   * @param requiredHeadMm       equivalent clear-liquid head required [mm]
   * @param availableHeadMm      geometric elevation cyclone deck - LA(HH) [mm]
   * @param gasDensityKgM3       gas phase density used in the calculation [kg/m3]
   * @param liquidDensityKgM3    liquid phase density used in the calculation
   *                             [kg/m3]
   */
  public DrainageHeadResult(double meshDpPa, double cycloneDpToDrainPa, double requiredHeadMm,
      double availableHeadMm, double gasDensityKgM3, double liquidDensityKgM3) {
    this.meshDpPa = meshDpPa;
    this.cycloneDpToDrainPa = cycloneDpToDrainPa;
    this.totalDpPa = meshDpPa + cycloneDpToDrainPa;
    this.requiredHeadMm = requiredHeadMm;
    this.availableHeadMm = availableHeadMm;
    this.percentOfAvailable = availableHeadMm > 0 ? requiredHeadMm / availableHeadMm * 100.0
        : Double.NaN;
    this.gasDensityKgM3 = gasDensityKgM3;
    this.liquidDensityKgM3 = liquidDensityKgM3;
  }

  /**
   * Gets the mesh pad pressure drop contribution.
   *
   * @return mesh pad dP [Pa]
   */
  public double getMeshDpPa() {
    return meshDpPa;
  }

  /**
   * Gets the fraction of cyclone pressure drop reaching the drain chamber.
   *
   * @return cyclone drain dP [Pa]
   */
  public double getCycloneDpToDrainPa() {
    return cycloneDpToDrainPa;
  }

  /**
   * Gets the total pressure drop the drainage column must balance.
   *
   * @return total dP [Pa]
   */
  public double getTotalDpPa() {
    return totalDpPa;
  }

  /**
   * Gets the required clear-liquid head (computed from total dP and two-phase
   * density difference).
   *
   * @return required head [mm]
   */
  public double getRequiredHeadMm() {
    return requiredHeadMm;
  }

  /**
   * Gets the geometric drainage elevation between cyclone deck and LA(HH).
   *
   * @return available head [mm]
   */
  public double getAvailableHeadMm() {
    return availableHeadMm;
  }

  /**
   * Gets the required head as a percentage of the available head. A value below
   * 100 % means
   * adequate drainage; above 100 % means the drainage column floods the cyclone
   * bank.
   *
   * @return required / available [%]
   */
  public double getPercentOfAvailable() {
    return percentOfAvailable;
  }

  /**
   * Gets the gas density used in the head calculation.
   *
   * @return gas density [kg/m3]
   */
  public double getGasDensityKgM3() {
    return gasDensityKgM3;
  }

  /**
   * Gets the liquid density used in the head calculation.
   *
   * @return liquid density [kg/m3]
   */
  public double getLiquidDensityKgM3() {
    return liquidDensityKgM3;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format(
        "DrainageHead[mesh=%.1f Pa, cyc=%.1f Pa, req=%.1f mm, avail=%.1f mm, %.1f %%]",
        meshDpPa, cycloneDpToDrainPa, requiredHeadMm, availableHeadMm, percentOfAvailable);
  }
}
