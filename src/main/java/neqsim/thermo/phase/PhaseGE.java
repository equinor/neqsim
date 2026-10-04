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
 * Abstract class PhaseGE.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class PhaseGE extends Phase implements PhaseGEInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Finite bulk modulus used for the constant-density liquid approximation, in bar. */
  private static final double INCOMPRESSIBLE_BULK_MODULUS_BAR = 1.0e12;
  /** Lower bound for internal phase volume when calculating the pressure-volume derivative. */
  private static final double MINIMUM_INTERNAL_VOLUME = 1.0e-30;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseGE.class);

  EosMixingRuleHandler mixSelect = new EosMixingRuleHandler();
  EosMixingRulesInterface mixRule;

  /**
   * Constructor for PhaseGE.
   */
  public PhaseGE() {
    setType(PhaseType.LIQUID);
    componentArray = new ComponentGEInterface[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
    useVolumeCorrection = false;
  }

  /**
   * init.
   *
   * @param temperature a double
   * @param pressure a double
   * @param totalNumberOfMoles a double
   * @param beta a double
   * @param numberOfComponents a int
   * @param pt the PhaseType of the phase
   * @param phaseNum a int
   */
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int numberOfComponents,
      PhaseType pt, int phaseNum) {
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
      if ((getComponent(i).isHydrocarbon() || getComponent(i).isInert() || getComponent(i).isIsTBPfraction())
          && !getComponent(i).getName().equals("water") && !getComponent(i).getName().equals("water_PC")
          && !getComponent(i).getComponentType().equals("alcohol")
          && !getComponent(i).getComponentType().equals("glycol") && !getComponent(i).isIsIon()) {
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
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType != 0) {
      getExcessGibbsEnergy(this, numberOfComponents, temperature, pressure, pt);
    }

    double sumHydrocarbons = 0.0;
    double sumAqueous = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if ((getComponent(i).isHydrocarbon() || getComponent(i).isInert() || getComponent(i).isIsTBPfraction())
          && !getComponent(i).getName().equals("water") && !getComponent(i).getName().equals("water_PC")
          && !getComponent(i).getComponentType().equals("aqueous") && !getComponent(i).isIsIon()) {
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
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "setMixingRule", "mr"));
    }
    mixingRuleType = EosMixingRuleType.CLASSIC;
    mixRule = mixSelect.getMixingRule(mixingRuleType.getValue(), this);
  }

  /** {@inheritDoc} */
  @Override
  public void resetMixingRule(MixingRuleTypeInterface mr) {
    if (!(mr == null) && !EosMixingRuleType.class.isInstance(mr)) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "resetMixingRule", "mr"));
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
   * getActivityCoefficientInfDilWater.
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getActivityCoefficientInfDilWater(int k, int p) {
    PhaseGE reference = createWaterInfiniteDilutionPhase(k, p);
    return ((ComponentGEInterface) reference.getComponent(k)).getGamma();
  }

  /**
   * Builds an isolated dilute reference with the owning model's parameters and component indices.
   *
   * <p>
   * A fresh binary database phase loses user-set interactions and model-specific parameter selection. Retaining the
   * full topology with numerical trace amounts of the other species preserves those parameters without transferring oil
   * or salt into the pure-water reference or modifying the source phase.
   * </p>
   *
   * @param solute solute component index
   * @param solvent water component index
   * @return evaluated dilute reference phase
   */
  protected PhaseGE createWaterInfiniteDilutionPhase(int solute, int solvent) {
    PhaseGE reference = (PhaseGE) clone();
    double totalMoles = solute == solvent ? 1.0 : 1.0 + 1.0e-10;
    for (int index = 0; index < numberOfComponents; index++) {
      double moles = index == solvent ? 1.0 : index == solute ? 1.0e-10 : 1.0e-50;
      reference.getComponent(index).setx(moles / totalMoles);
      reference.getComponent(index).setNumberOfMolesInPhase(totalMoles);
    }
    reference.init(totalMoles, numberOfComponents, 2, getType(), 1.0);
    return reference;
  }

  /**
   * Calculates the logarithmic temperature derivative of the same dilute reference used for fugacity.
   *
   * @param solute solute component index
   * @param solvent water component index
   * @return d(ln gamma infinite dilution)/dT in 1/K
   */
  public double getLnActivityCoefficientInfDilWaterTemperatureDerivative(int solute, int solvent) {
    PhaseGE reference = createWaterInfiniteDilutionPhase(solute, solvent);
    return reference.getLnActivityCoefficientTemperatureDerivative(solute);
  }

  /**
   * Calculates a model-generic activity derivative on an isolated phase at fixed composition.
   *
   * @param component component index
   * @return d(ln gamma)/dT in 1/K
   */
  public double getLnActivityCoefficientTemperatureDerivative(int component) {
    PhaseGE reference = (PhaseGE) clone();
    double step = Math.max(1.0e-3, temperature * 1.0e-6);
    reference.setTemperature(temperature + step);
    reference.getExcessGibbsEnergy(reference, numberOfComponents, reference.getTemperature(), pressure,
        reference.getType());
    double plus = Math.log(((ComponentGEInterface) reference.getComponent(component)).getGamma());
    reference.setTemperature(temperature - step);
    reference.getExcessGibbsEnergy(reference, numberOfComponents, reference.getTemperature(), pressure,
        reference.getType());
    double minus = Math.log(((ComponentGEInterface) reference.getComponent(component)).getGamma());
    return (plus - minus) / (2.0 * step);
  }

  /**
   * getActivityCoefficientInfDil.
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficientInfDil(int k) {
    PhaseGE dilphase = createInfiniteDilutionPhase(k);
    return ((ComponentGEInterface) dilphase.getComponent(k)).getGamma();
  }

  /**
   * Builds an isolated dilute reference retaining the actual mixture of other solvents.
   *
   * @param k solute component index
   * @return evaluated reference phase
   */
  private PhaseGE createInfiniteDilutionPhase(int k) {
    PhaseGE dilphase = (PhaseGE) clone();
    dilphase.addMoles(k, -(1.0 - 1e-10) * dilphase.getComponent(k).getNumberOfMolesInPhase());
    dilphase.getComponent(k).setx(1e-10);
    dilphase.normalize();
    dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1, dilphase.getType(), 1.0);
    ((PhaseGEInterface) dilphase).getExcessGibbsEnergy(dilphase, dilphase.getNumberOfComponents(),
        dilphase.getTemperature(), dilphase.getPressure(), dilphase.getType());
    return dilphase;
  }

  /**
   * Calculates the dilute-reference derivative for a mixture of solvents.
   *
   * @param component solute component index
   * @return d(ln gamma infinite dilution)/dT in 1/K
   */
  public double getLnActivityCoefficientInfDilTemperatureDerivative(int component) {
    return createInfiniteDilutionPhase(component).getLnActivityCoefficientTemperatureDerivative(component);
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return getCp() * temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return getCp() * Math.log(temperature / ThermodynamicConstantsInterface.referenceTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    double molarHeatCapacity = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      molarHeatCapacity += componentArray[i].getx() * componentArray[i].getPureComponentCpLiquid(temperature);
    }
    return molarHeatCapacity * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    // Cv is assumed equal to Cp
    return getCp();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * The current GE liquid-volume model assumes constant density and therefore zero thermal expansion.
   * </p>
   */
  @Override
  public double getdPdTVn() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * The current GE liquid-volume model is effectively incompressible. A large finite bulk modulus keeps its
   * {@code dV/dP} contribution negligible in multiphase volume flashes without propagating infinities into aggregated
   * system derivatives.
   * </p>
   */
  @Override
  public double getdPdVTn() {
    double internalVolume = Math.max(getTotalVolume(), MINIMUM_INTERNAL_VOLUME);
    return -INCOMPRESSIBLE_BULK_MODULUS_BAR / internalVolume;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * A constant-density GE phase has no pressure response to temperature at fixed volume. Returning the limiting value
   * explicitly also avoids the indeterminate zero-times-infinity form when a trace phase has zero volume.
   * </p>
   */
  @Override
  public double getCompressibilityX() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the finite constant-density limit {@code -P/K}, including when the phase volume is zero.
   * </p>
   */
  @Override
  public double getCompressibilityY() {
    return -getPressure() / INCOMPRESSIBLE_BULK_MODULUS_BAR;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the finite constant-density limit {@code 1/K}, including when the phase volume is zero.
   * </p>
   */
  @Override
  public double getIsothermalCompressibility() {
    return 1.0 / INCOMPRESSIBLE_BULK_MODULUS_BAR;
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    double densityIdealGas = pressure * 1e5 / ThermodynamicConstantsInterface.R / temperature * getMolarMass();
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
