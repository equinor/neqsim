package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSoreideWhitson;

/**
 * PhaseSoreideWhitson implements the Søreide-Whitson Peng-Robinson EoS with
 * modified alpha and mixing rule.
 */
public class PhaseSoreideWhitson extends PhasePrEos {
  private static final long serialVersionUID = 1L;
  private double salinityConcentration = 0.0;
  private double salinity = 0.0;

  public PhaseSoreideWhitson() {
    thermoPropertyModelName = "Soreide-Whitson-PR-EoS";
    uEOS = 2;
    wEOS = -1;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
  }

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

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    // Always use the SoreideWhitson component for all components
    componentArray[compNumber] = new ComponentSoreideWhitson(name, moles, molesInPhase, compNumber);
  }

  // Set salinity for the phase (mol/kg or as used in Soreide-Whitson)
  public void setSalinityConcentration(double salinityConcentration) {
    this.salinityConcentration = salinityConcentration;
  }

  public double getSalinityConcentration() {
    return this.salinityConcentration;
  }

  public void addSalinity(double salinity) {
    this.salinity += salinity;
  }

  public double getSalinity(double salinity) {
    return this.salinity;
  }

  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  public void setPressure(double pressure) {
    this.pressure = pressure;
  }
}
