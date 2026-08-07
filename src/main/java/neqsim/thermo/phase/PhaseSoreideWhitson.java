package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSoreideWhitson;
import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;

/**
 * PhaseSoreideWhitson implements the Søreide-Whitson Peng-Robinson EoS with modified alpha and mixing rule.
 *
 * @author sviat
 */
public class PhaseSoreideWhitson extends PhasePrEos {
  private static final long serialVersionUID = 1L;
  private double salinityConcentration = 0.0;
  private double salinity = 0.0;
  private SoreideWhitsonParameterization aqueousCO2Parameterization = SoreideWhitsonParameterization.LEGACY;

  /**
   * Constructs a PhaseSoreideWhitson object and initializes EoS parameters.
   */
  public PhaseSoreideWhitson() {
    thermoPropertyModelName = "Soreide-Whitson-PR-EoS";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSoreideWhitson clone() {
    PhaseSoreideWhitson clonedPhase = null;
    try {
      clonedPhase = (PhaseSoreideWhitson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    // Always use the SoreideWhitson component for all components
    componentArray[compNumber] = new ComponentSoreideWhitson(name, moles, molesInPhase, compNumber);
  }

  // Set salinity for the phase (mol/kg or as used in Soreide-Whitson)
  /**
   * Setter for the field <code>salinityConcentration</code>.
   *
   * @param salinityConcentration a double
   */
  public void setSalinityConcentration(double salinityConcentration) {
    this.salinityConcentration = salinityConcentration;
  }

  /**
   * Getter for the field <code>salinityConcentration</code>.
   *
   * @return a double
   */
  public double getSalinityConcentration() {
    return this.salinityConcentration;
  }

  /**
   * Set the Soreide-Whitson binary-interaction parameterization.
   *
   * @param parameterization parameterization to use
   * @throws IllegalArgumentException if {@code parameterization} is null
   */
  public void setSoreideWhitsonParameterization(SoreideWhitsonParameterization parameterization) {
    if (parameterization == null) {
      throw new IllegalArgumentException("Soreide-Whitson parameterization cannot be null");
    }
    this.aqueousCO2Parameterization = parameterization;
  }

  /**
   * Get the Soreide-Whitson binary-interaction parameterization.
   *
   * @return selected parameterization
   */
  public SoreideWhitsonParameterization getSoreideWhitsonParameterization() {
    return aqueousCO2Parameterization;
  }

  /**
   * Set the parameterization using the historical aqueous-CO2 API name.
   *
   * @param parameterization parameterization to use
   */
  public void setAqueousCO2Parameterization(SoreideWhitsonParameterization parameterization) {
    setSoreideWhitsonParameterization(parameterization);
  }

  /**
   * Get the parameterization using the historical aqueous-CO2 API name.
   *
   * @return selected parameterization
   */
  public SoreideWhitsonParameterization getAqueousCO2Parameterization() {
    return getSoreideWhitsonParameterization();
  }

  /**
   * addSalinity.
   *
   * @param salinity a double
   */
  public void addSalinity(double salinity) {
    this.salinity += salinity;
  }

  /**
   * Getter for the field <code>salinity</code>.
   *
   * @param salinity a double
   * @return a double
   */
  public double getSalinity(double salinity) {
    return this.salinity;
  }
}
