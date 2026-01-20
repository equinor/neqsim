package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentIdealGas;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * Phase model for an ideal gas. Compressibility is fixed to unity and thermodynamic properties are
 * calculated from ideal-gas relations.
 *
 * @author esol
 */
public class PhaseIdealGas extends Phase {

  private static final long serialVersionUID = 1000L;

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    return 1.0;
  }

  /**
   * <p>
   * Constructor for PhaseIdealGas.
   * </p>
   */
  public PhaseIdealGas() {
    thermoPropertyModelName = "ideal gas";
    Z = 1.0;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, compNumber);
    componentArray[compNumber] = new ComponentIdealGas(name, moles, molesInPhase, compNumber);
  }


  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface getMixingRule() {
    return null;
  }


  /** {@inheritDoc} */
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    Z = 1.0;
    updateMolarVolume();
  }


  /** {@inheritDoc} */
  public void setMixingRuleGEModel(String name) {}


  /**
   * <p>
   * setMixingRule.
   * </p>
   *
   * @param mr a {@link neqsim.thermo.mixingrule.MixingRuleTypeInterface} object
   */
  public void setMixingRule(MixingRuleTypeInterface mr) {}


  /** {@inheritDoc} */
  public void resetMixingRule(MixingRuleTypeInterface mr) {}


  /** {@inheritDoc} */
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return R * temperature / pressure;
  }

  private void updateMolarVolume() {
    if (pressure > 0) {
      setMolarVolume(R * temperature / pressure);
      Z = 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temp) {
    super.setTemperature(temp);
    updateMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    super.setPressure(pres);
    updateMolarVolume();
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return Math.sqrt(getGamma() * R * temperature / getMolarMass());
  }

  /** {@inheritDoc} */
  @Override
  public double getCpres() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getCvres() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTP() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return 0.0;
  }
}

