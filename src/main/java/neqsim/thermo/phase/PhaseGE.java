/*
 * PhaseGE.java
 *
 * Created on 11. juli 2000, 21:00
 */

package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.mixingrule.EosMixingRuleHandler;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * Abstract class PhaseGE.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class PhaseGE extends Phase implements PhaseGEInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseGE.class);

  EosMixingRuleHandler mixSelect = new EosMixingRuleHandler();
  EosMixingRulesInterface mixRule;

  /**
   * <p>
   * Constructor for PhaseGE.
   * </p>
   */
  public PhaseGE() {
    setType(PhaseType.LIQUID);
    componentArray = new ComponentGEInterface[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
    useVolumeCorrection = false;
  }

  /**
   * <p>
   * init.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   * @param totalNumberOfMoles a double
   * @param beta a double
   * @param numberOfComponents a int
   * @param pt the PhaseType of the phase
   * @param phaseNum a int
   */
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
      int numberOfComponents, PhaseType pt, int phaseNum) {
    if (totalNumberOfMoles <= 0) {
      // todo: throw this exception?
      /*
       * new neqsim.util.exception.InvalidInputException(this, "init", "totalNumberOfMoles",
       * "must be larger than zero.");
       */
    }
    for (int i = 0; i < numberOfComponents; i++) {
      // todo: Conflating init type and phase type?
      componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, pt.getValue());
    }
    this.getExcessGibbsEnergy(this, numberOfComponents, temperature, pressure, pt);

    double sumHydrocarbons = 0.0;
    double sumAqueous = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).isHydrocarbon() || getComponent(i).isInert()
          || getComponent(i).isIsTBPfraction()) {
        sumHydrocarbons += getComponent(i).getx();
      } else {
        sumAqueous += getComponent(i).getx();
      }
    }

    if (sumHydrocarbons > sumAqueous) {
      setType(PhaseType.OIL);
    } else {
      setType(PhaseType.AQUEOUS);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType != 0) {
      getExcessGibbsEnergy(this, numberOfComponents, temperature, pressure, pt);
    }

    double sumHydrocarbons = 0.0;
    double sumAqueous = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).isHydrocarbon() || getComponent(i).isInert()
          || getComponent(i).isIsTBPfraction()) {
        sumHydrocarbons += getComponent(i).getx();
      } else {
        sumAqueous += getComponent(i).getx();
      }
    }

    if (sumHydrocarbons > sumAqueous) {
      setType(PhaseType.OIL);
    } else {
      setType(PhaseType.AQUEOUS);
    }

    // calc liquid density
    if (initType > 1) {
      // Calc Cp /Cv
      // Calc enthalpy/entropys
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEModel(String name) {
    mixRule.setMixingRuleGEModel(name);
    mixSelect.setMixingRuleGEModel(name);
  }

  /** {@inheritDoc} */
  @Override
  public EosMixingRulesInterface getMixingRule() {
    return mixRule;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    if (!(mr == null) && !EosMixingRuleType.class.isInstance(mr)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "setMixingRule", "mr"));
    }
    mixingRuleType = EosMixingRuleType.CLASSIC;
    mixRule = mixSelect.getMixingRule(mixingRuleType.getValue(), this);
  }

  /** {@inheritDoc} */
  @Override
  public void resetMixingRule(MixingRuleTypeInterface mr) {
    if (!(mr == null) && !EosMixingRuleType.class.isInstance(mr)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "resetMixingRule", "mr"));
    }
    // NB! Ignores input mr
    mixingRuleType = EosMixingRuleType.CLASSIC;
    mixRule = mixSelect.resetMixingRule(mixingRuleType.getValue(), this);
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficientSymetric(int k) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /**
   * <p>
   * getActivityCoefficientInfDilWater.
   * </p>
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getActivityCoefficientInfDilWater(int k, int p) {
    if (refPhase == null) {
      initRefPhases(false, getComponentName(p));
    }
    refPhase[k].setTemperature(temperature);
    refPhase[k].setPressure(pressure);
    refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 2, 1, this.getType(), 1.0);
    ((PhaseGEInterface) refPhase[k]).getExcessGibbsEnergy(refPhase[k], 2,
        refPhase[k].getTemperature(), refPhase[k].getPressure(), refPhase[k].getType());
    return ((ComponentGEInterface) refPhase[k].getComponent(0)).getGamma();
  }

  /**
   * <p>
   * getActivityCoefficientInfDil.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficientInfDil(int k) {
    PhaseInterface dilphase = this.clone();
    dilphase.addMoles(k, -(1.0 - 1e-10) * dilphase.getComponent(k).getNumberOfMolesInPhase());
    dilphase.getComponent(k).setx(1e-10);
    dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1,
        dilphase.getType(), 1.0);
    ((PhaseGEInterface) dilphase).getExcessGibbsEnergy(dilphase, 2, dilphase.getTemperature(),
        dilphase.getPressure(), dilphase.getType());
    return ((ComponentGEInterface) dilphase.getComponent(0)).getGamma();
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return getCp() * temperature * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return getCp() * Math.log(temperature / ThermodynamicConstantsInterface.referenceTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    double tempVar = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getx() * componentArray[i].getPureComponentCpLiquid(temperature);
    }
    return tempVar;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    // Cv is assumed equal to Cp
    return getCp();
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    double densityIdealGas =
        pressure * 1e5 / ThermodynamicConstantsInterface.R / temperature * getMolarMass();
    return densityIdealGas / getDensity("kg/m3");
  }

  // return speed of sound in water constant 1470.0 m/sec
  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return 1470.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Return speed of JT coefficient of water at K/bar (assumed constant) -0.0125
   * </p>
   */
  @Override
  public double getJouleThomsonCoefficient() {
    return -0.125 / 10.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * note: at the moment return density of water (997 kg/m3)
   * </p>
   */
  @Override
  public double getDensity() {
    return 997.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume() {
    return 1.0 / (getDensity() / getMolarMass()) * 1.0e5;
  }
}
