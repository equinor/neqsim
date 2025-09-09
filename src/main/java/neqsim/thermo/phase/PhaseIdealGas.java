package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentIdealGas;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Phase model for an ideal gas. Compressibility is fixed to unity and
 * thermodynamic properties are calculated from ideal-gas relations.
 */
public class PhaseIdealGas extends Phase implements ThermodynamicConstantsInterface {

  private static final long serialVersionUID = 1000L;

  @Override
  public double getZ() {
    return 1.0;
  }

  public PhaseIdealGas() {
    thermoPropertyModelName = "ideal gas";
    Z = 1.0;
  }

  @Override
  public PhaseIdealGas clone() {
    PhaseIdealGas cloned = null;
    try {
      cloned = (PhaseIdealGas) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, compNumber);
    componentArray[compNumber] = new ComponentIdealGas(name, moles, molesInPhase, compNumber);
  }

  
  public EosMixingRulesInterface getMixingRule() {
    return null;
  }

  
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    Z = 1.0;
    updateMolarVolume();
  }

  
  public void setMixingRuleGEModel(String name) {}

  
  public void setMixingRule(MixingRuleTypeInterface mr) {}

  
  public void resetMixingRule(MixingRuleTypeInterface mr) {}

  
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    return R * temperature / pressure;
  }

  private void updateMolarVolume() {
    if (pressure > 0) {
      setMolarVolume(R * temperature / pressure);
      Z = 1.0;
    }
  }

  @Override
  public void setTemperature(double temp) {
    super.setTemperature(temp);
    updateMolarVolume();
  }

  @Override
  public void setPressure(double pres) {
    super.setPressure(pres);
    updateMolarVolume();
  }

  @Override
  public double getDensity(String unit) {
    double rho = getDensity();
    switch (unit) {
      case "kg/m3":
        return rho;
      case "mol/m3":
        return rho / getMolarMass();
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
  }

  @Override
  public double getSoundSpeed() {
    return Math.sqrt(getGamma() * R * temperature / getMolarMass());
  }

  @Override
  public double getCpres() {
    return 0.0;
  }

  @Override
  public double getCvres() {
    return 0.0;
  }

  @Override
  public double getHresTP() {
    return 0.0;
  }

  @Override
  public double getSresTP() {
    return 0.0;
  }

  @Override
  public double getJouleThomsonCoefficient() {
    return 0.0;
  }
}

