package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import java.io.Serializable;

/**
 * Interface for adsorption isotherm models.
 *
 * <p>
 * Defines the contract for all gas-solid adsorption calculations in NeqSim, including surface
 * excess computation, solid material selection, and model identification.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 */
public interface AdsorptionInterface
    extends neqsim.thermo.ThermodynamicConstantsInterface, Serializable {

  /**
   * Calculate adsorption for the specified phase.
   *
   * @param phaseNum the phase number (0-based)
   */
  public void calcAdsorption(int phaseNum);

  /**
   * Get the surface excess (amount adsorbed) for a component by index.
   *
   * @param component the component index (0-based)
   * @return surface excess in mol/kg adsorbent
   */
  public double getSurfaceExcess(int component);

  /**
   * Get the surface excess (amount adsorbed) for a component by name.
   *
   * @param componentName the component name
   * @return surface excess in mol/kg adsorbent
   */
  public double getSurfaceExcess(String componentName);

  /**
   * Set the solid adsorbent material.
   *
   * @param solidM the solid material identifier (e.g. "AC", "Zeolite 13X", "Silica Gel")
   */
  public void setSolidMaterial(String solidM);

  /**
   * Get the total surface excess of all components combined.
   *
   * @return total surface excess in mol/kg adsorbent
   */
  public double getTotalSurfaceExcess();

  /**
   * Get the isotherm model type used by this implementation.
   *
   * @return the isotherm type enum value
   */
  public IsothermType getIsothermType();

  /**
   * Check whether adsorption has been calculated.
   *
   * @return true if calcAdsorption has been called successfully
   */
  public boolean isCalculated();
}
