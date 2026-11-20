package neqsim.thermo.util.mercury;

import java.io.Serializable;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Phase equilibrium of a fluid with a separate elemental mercury phase.
 *
 * <p>
 * The fluid phases (gas, oil, aqueous) are in equilibrium with pure condensed mercury when the fluid holds more mercury
 * than it can dissolve. The mercury phase is treated as pure: liquid above the melting point and solid below it. Its
 * fugacity is the pure liquid fugacity from the equation of state of the fluid, corrected below the melting point with
 * the enthalpy of fusion and the pressure effect on the solid.
 * </p>
 *
 * <p>
 * Mercury is treated as a dilute solute. The split of the other components comes from a flash at trace mercury content,
 * and the mercury content of each phase is then solved so that mercury has the same fugacity in all fluid phases,
 * limited by the pure mercury fugacity. The error from this is of the order of the dissolved mercury mole fraction,
 * which is normally below 1e-5.
 * </p>
 *
 * <p>
 * The calculation works on a copy of the fluid taken when the object is created; the fluid given to the constructor is
 * not changed, and later changes to it are not seen by this object.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryPhaseEquilibrium implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Component name of elemental mercury. */
  public static final String MERCURY = "mercury";

  /** Melting point of mercury in kelvin. */
  public static final double MELTING_TEMPERATURE = 234.321;

  /** Enthalpy of fusion of mercury in J/mol. */
  private static final double HEAT_OF_FUSION = 2.29e3;

  /** Molar volume of solid mercury in cm3/mol. */
  private static final double SOLID_MOLAR_VOLUME = 14.142;

  /** Enthalpy of sublimation of mercury in J/mol. */
  private static final double HEAT_OF_SUBLIMATION = 61.39e3;

  /** Sublimation pressure of mercury in bara at {@link #SUBLIMATION_REFERENCE_TEMPERATURE}. */
  private static final double SUBLIMATION_REFERENCE_PRESSURE = 1.65e-9;

  /** Reference temperature of the sublimation pressure in kelvin. */
  private static final double SUBLIMATION_REFERENCE_TEMPERATURE = 234.22;

  /** Gas constant in J/(mol K) used in the solid mercury correlations. */
  private static final double GAS_CONSTANT = 8.314;

  /** Gas constant in cm3 bar/(mol K). */
  private static final double GAS_CONSTANT_CM3_BAR = 83.14;

  /** Mercury mole fraction of the reference flash. */
  private static final double TRACE_MOLE_FRACTION = 1.0e-10;

  /** Moles of ideal gas in one standard cubic metre at 15 C and 1.01325 bara. */
  private static final double MOLES_PER_SM3 = 42.29255785;

  /** Moles of ideal gas in one normal cubic metre at 0 C and 1.01325 bara. */
  private static final double MOLES_PER_NM3 = 44.61503;

  /** Copy of the fluid given by the caller, taken in the constructor and left unchanged. */
  private final SystemInterface inputFluid;

  /** Copy of the fluid holding the fluid phases with dissolved mercury after {@link #run()}. */
  private SystemInterface fluid = null;

  /** Moles of mercury in the feed. */
  private double feedMercuryMoles = 0.0;

  /** Moles of mercury in the separate mercury phase. */
  private double freeMercuryMoles = 0.0;

  /** Total moles of the feed, including mercury. */
  private double feedMoles = 0.0;

  /** Fugacity of pure condensed mercury in bara. */
  private double pureMercuryFugacity = Double.NaN;

  /** Fugacity of mercury with all mercury dissolved, divided by the pure mercury fugacity. */
  private double saturationRatio = Double.NaN;

  /**
   * Create a calculation for a fluid.
   *
   * @param fluid fluid with the component mercury and at least one other component, at the temperature and pressure of
   * the calculation; it is copied, so create a new calculation after changing the fluid
   * @throws IllegalArgumentException if the fluid is null, has no mercury or has only mercury
   */
  public MercuryPhaseEquilibrium(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid can not be null");
    }
    if (!fluid.getPhase(0).hasComponent(MERCURY)) {
      throw new IllegalArgumentException("fluid has no component named " + MERCURY);
    }
    if (fluid.getPhase(0).getNumberOfComponents() < 2) {
      throw new IllegalArgumentException("fluid needs at least one component in addition to mercury");
    }
    this.inputFluid = fluid.clone();
  }

  /**
   * Run the calculation at the temperature and pressure of the fluid.
   *
   * @throws IllegalStateException if the fluid has only mercury, no reference flash below mercury saturation is found
   * or the mercury distribution does not converge
   */
  public void run() {
    fluid = null;
    SystemInterface working = inputFluid.clone();
    working.setMultiPhaseCheck(true);
    int mercury = working.getPhase(0).getComponent(MERCURY).getComponentNumber();
    feedMercuryMoles = working.getPhase(0).getComponent(mercury).getNumberOfmoles();
    feedMoles = working.getTotalNumberOfMoles();
    freeMercuryMoles = 0.0;
    double otherMoles = feedMoles - feedMercuryMoles;
    if (otherMoles <= 0.0) {
      throw new IllegalStateException("fluid needs at least one component in addition to mercury");
    }
    pureMercuryFugacity = calcPureMercuryFugacity(working, mercury);

    // The reference flash must hold so little mercury that no mercury-rich liquid forms in the equation of state.
    double referenceMoles = Math.min(feedMercuryMoles, TRACE_MOLE_FRACTION * otherMoles);
    double referenceFugacity = 0.0;
    for (int attempt = 0; attempt < 12; attempt++) {
      working.addComponent(mercury, referenceMoles - working.getPhase(0).getComponent(mercury).getNumberOfmoles());
      new ThermodynamicOperations(working).TPflash();
      working.init(1);
      if (feedMercuryMoles <= 0.0) {
        fluid = working;
        saturationRatio = 0.0;
        return;
      }
      boolean mercuryRichPhase = false;
      for (int p = 0; p < working.getNumberOfPhases(); p++) {
        mercuryRichPhase = mercuryRichPhase || working.getPhase(p).getComponent(mercury).getx() > 0.01;
      }
      referenceFugacity = meanMercuryFugacity(working, mercury);
      double referenceSaturation = referenceFugacity / pureMercuryFugacity;
      if (!mercuryRichPhase && referenceSaturation <= 0.1) {
        break;
      }
      if (attempt == 11) {
        throw new IllegalStateException("could not find a mercury content below saturation for the reference flash");
      }
      referenceMoles *= mercuryRichPhase ? 1.0e-3 : 0.01 / referenceSaturation;
    }

    // Henry's law estimate of the fugacity with all mercury dissolved.
    saturationRatio = referenceFugacity * feedMercuryMoles / referenceMoles / pureMercuryFugacity;
    boolean saturated = saturationRatio > 1.0;
    boolean modeChanged = false;
    double targetMoles = saturated ? referenceMoles * pureMercuryFugacity / referenceFugacity : feedMercuryMoles;
    scaleMercury(working, mercury, targetMoles / referenceMoles);

    for (int iteration = 0; iteration < 50; iteration++) {
      int phases = working.getNumberOfPhases();
      double[] moles = new double[phases];
      double[] fugacity = new double[phases];
      double sum = 0.0;
      double total = 0.0;
      for (int p = 0; p < phases; p++) {
        moles[p] = working.getPhase(p).getComponent(mercury).getNumberOfMolesInPhase();
        fugacity[p] = mercuryFugacity(working, p, mercury);
        sum += moles[p] / fugacity[p];
        total += moles[p];
      }
      double target = saturated ? pureMercuryFugacity : total / sum;
      double error = 0.0;
      for (int p = 0; p < phases; p++) {
        error = Math.max(error, Math.abs(fugacity[p] / target - 1.0));
        moles[p] *= target / fugacity[p];
      }
      if (error < 1.0e-10) {
        // The Henry's law estimate can be on the wrong side of saturation when the feed is close to it.
        if (!modeChanged && saturated && total > feedMercuryMoles) {
          saturated = false;
          modeChanged = true;
          scaleMercury(working, mercury, feedMercuryMoles / total);
          continue;
        }
        if (!modeChanged && !saturated && target > pureMercuryFugacity) {
          saturated = true;
          modeChanged = true;
          continue;
        }
        freeMercuryMoles = saturated ? Math.max(feedMercuryMoles - total, 0.0) : 0.0;
        if (!saturated) {
          saturationRatio = target / pureMercuryFugacity;
        }
        fluid = working;
        return;
      }
      setMercuryMoles(working, mercury, moles);
    }
    throw new IllegalStateException("mercury distribution between the fluid phases did not converge");
  }

  /**
   * Calculate the fugacity of pure condensed mercury at the conditions of a fluid.
   *
   * @param system fluid giving temperature, pressure and the equation of state
   * @param mercury component index of mercury
   * @return fugacity of pure liquid mercury above the melting point and of pure solid mercury below it, in bara
   * @throws IllegalStateException if a pure mercury reference phase cannot be created for the fluid model
   */
  private static double calcPureMercuryFugacity(SystemInterface system, int mercury) {
    double temperature = system.getTemperature();
    double pressure = system.getPressure();
    PhaseInterface reference;
    try {
      reference = system.getPhase(0).getClass().getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("could not create a pure mercury reference phase", ex);
    }
    reference.setTemperature(temperature);
    reference.setPressure(pressure);
    reference.addComponent(MERCURY, 1.0, 1.0, 0);
    reference.setAttractiveTerm(system.getPhase(0).getComponent(mercury).getAttractiveTermNumber());
    reference.setMixingRule(EosMixingRuleType.CLASSIC);
    reference.init(1.0, 1, 0, PhaseType.LIQUID, 1.0);
    reference.init(1.0, 1, 1, PhaseType.LIQUID, 1.0);
    double logFugacityCoefficient = Math.log(reference.getComponent(0).fugcoef(reference));
    if (temperature < MELTING_TEMPERATURE) {
      // NeqSim molar volume is in m3/mol times 1e5.
      double liquidMolarVolume = reference.getMolarVolume() * 10.0;
      double sublimationPressure = SUBLIMATION_REFERENCE_PRESSURE * Math.exp(HEAT_OF_SUBLIMATION / GAS_CONSTANT
          / SUBLIMATION_REFERENCE_TEMPERATURE * (1.0 - SUBLIMATION_REFERENCE_TEMPERATURE / temperature));
      logFugacityCoefficient += -HEAT_OF_FUSION / GAS_CONSTANT / MELTING_TEMPERATURE
          * (MELTING_TEMPERATURE / temperature - 1.0)
          + (SOLID_MOLAR_VOLUME - liquidMolarVolume) * (pressure - sublimationPressure) / GAS_CONSTANT_CM3_BAR
              / temperature;
    }
    return Math.exp(logFugacityCoefficient) * pressure;
  }

  /**
   * Calculate the fugacity of mercury in one phase.
   *
   * @param system initialized fluid
   * @param phase phase number
   * @param mercury component index of mercury
   * @return fugacity in bara
   */
  private static double mercuryFugacity(SystemInterface system, int phase, int mercury) {
    return system.getPhase(phase).getComponent(mercury).getx()
        * system.getPhase(phase).getComponent(mercury).getFugacityCoefficient() * system.getPressure();
  }

  /**
   * Calculate the mole-weighted mean fugacity of mercury over the phases, which is the common fugacity at equilibrium.
   *
   * @param system initialized fluid
   * @param mercury component index of mercury
   * @return fugacity in bara
   */
  private static double meanMercuryFugacity(SystemInterface system, int mercury) {
    double sum = 0.0;
    double total = 0.0;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      double moles = system.getPhase(p).getComponent(mercury).getNumberOfMolesInPhase();
      sum += moles / mercuryFugacity(system, p, mercury);
      total += moles;
    }
    return total / sum;
  }

  /**
   * Multiply the mercury content of all phases by a common factor.
   *
   * @param system initialized fluid
   * @param mercury component index of mercury
   * @param factor factor on the moles of mercury in each phase
   */
  private static void scaleMercury(SystemInterface system, int mercury, double factor) {
    double[] moles = new double[system.getNumberOfPhases()];
    for (int p = 0; p < moles.length; p++) {
      moles[p] = factor * system.getPhase(p).getComponent(mercury).getNumberOfMolesInPhase();
    }
    setMercuryMoles(system, mercury, moles);
  }

  /**
   * Set the moles of mercury in each phase, keeping the moles of all other components in each phase.
   *
   * @param system initialized fluid
   * @param mercury component index of mercury
   * @param mercuryMoles new moles of mercury in each phase
   */
  private static void setMercuryMoles(SystemInterface system, int mercury, double[] mercuryMoles) {
    int phases = system.getNumberOfPhases();
    int components = system.getPhase(0).getNumberOfComponents();
    double[][] moles = new double[phases][components];
    double[] phaseMoles = new double[phases];
    double totalMoles = 0.0;
    for (int p = 0; p < phases; p++) {
      for (int i = 0; i < components; i++) {
        moles[p][i] = i == mercury ? mercuryMoles[p] : system.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
        phaseMoles[p] += moles[p][i];
      }
      totalMoles += phaseMoles[p];
    }
    system.addComponent(mercury, totalMoles - system.getTotalNumberOfMoles());
    for (int p = 0; p < phases; p++) {
      for (int i = 0; i < components; i++) {
        system.getPhase(p).getComponent(i).setx(moles[p][i] / phaseMoles[p]);
      }
      system.setBeta(p, phaseMoles[p] / totalMoles);
    }
    system.init(1);
  }

  /**
   * Check that {@link #run()} has been called.
   *
   * @throws IllegalStateException if no result is available
   */
  private void requireResult() {
    if (fluid == null) {
      throw new IllegalStateException("call run() before reading results");
    }
  }

  /**
   * Get the fluid phases with their dissolved mercury.
   *
   * @return new copy of the fluid holding the gas, oil and aqueous phases after the calculation; the separate mercury
   * phase is not part of it. Keep the returned fluid in a variable when reading several properties.
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public SystemInterface getFluid() {
    requireResult();
    return fluid.clone();
  }

  /**
   * Check whether a separate mercury phase exists.
   *
   * @return true when the feed holds more mercury than the fluid phases dissolve
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public boolean hasFreeMercury() {
    requireResult();
    return freeMercuryMoles > 0.0;
  }

  /**
   * Get the state of the separate mercury phase.
   *
   * @return "none", "liquid" or "solid"
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public String getFreeMercuryState() {
    if (!hasFreeMercury()) {
      return "none";
    }
    return fluid.getTemperature() < MELTING_TEMPERATURE ? "solid" : "liquid";
  }

  /**
   * Get the amount of mercury in the separate mercury phase.
   *
   * @return moles of mercury, on the mole basis of the input fluid
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getFreeMercuryMoles() {
    requireResult();
    return freeMercuryMoles;
  }

  /**
   * Get the amount of mercury dissolved in the fluid phases.
   *
   * @return moles of mercury, on the mole basis of the input fluid
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getDissolvedMercuryMoles() {
    requireResult();
    return feedMercuryMoles - freeMercuryMoles;
  }

  /**
   * Get the mole fraction of the feed that forms the separate mercury phase.
   *
   * @return moles of free mercury divided by the total moles of the feed
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getFreeMercuryFraction() {
    requireResult();
    return freeMercuryMoles / feedMoles;
  }

  /**
   * Get the mole fraction of the feed in a fluid phase.
   *
   * @param phase phase number in {@link #getFluid()}
   * @return moles in the phase divided by the total moles of the feed, including free mercury
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getPhaseFraction(int phase) {
    requireResult();
    return fluid.getPhase(phase).getNumberOfMolesInPhase() / feedMoles;
  }

  /**
   * Get the mole fraction of mercury in a fluid phase.
   *
   * @param phase phase number in {@link #getFluid()}
   * @return mole fraction of mercury
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getMercuryMoleFraction(int phase) {
    requireResult();
    return fluid.getPhase(phase).getComponent(MERCURY).getx();
  }

  /**
   * Get the mercury concentration in a fluid phase.
   *
   * @param phase phase number in {@link #getFluid()}
   * @param unit "ug/Sm3" (per standard cubic metre of ideal gas at 15 C and 1.01325 bara), "ug/Nm3" (0 C and 1.01325
   * bara), "ug/kg" (per kilogram of the phase) or "ppb" (mole fraction times 1e9)
   * @return mercury concentration in the given unit
   * @throws IllegalArgumentException if the unit is not supported
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getMercuryConcentration(int phase, String unit) {
    requireResult();
    PhaseInterface fluidPhase = fluid.getPhase(phase);
    return convertMercuryConcentration(fluidPhase.getComponent(MERCURY).getx(),
        fluidPhase.getComponent(MERCURY).getMolarMass(), fluidPhase.getMolarMass(), unit);
  }

  /**
   * Convert a mercury mole fraction in a phase to a concentration.
   *
   * @param moleFraction mole fraction of mercury in the phase
   * @param mercuryMolarMass molar mass of mercury in kg/mol
   * @param phaseMolarMass molar mass of the phase in kg/mol
   * @param unit "ug/Sm3" (per standard cubic metre of ideal gas at 15 C and 1.01325 bara), "ug/Nm3" (0 C and 1.01325
   * bara), "ug/kg" (per kilogram of the phase) or "ppb" (mole fraction times 1e9)
   * @return mercury concentration in the given unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  public static double convertMercuryConcentration(double moleFraction, double mercuryMolarMass, double phaseMolarMass,
      String unit) {
    if ("ug/Sm3".equals(unit)) {
      return moleFraction * mercuryMolarMass * 1.0e9 * MOLES_PER_SM3;
    } else if ("ug/Nm3".equals(unit)) {
      return moleFraction * mercuryMolarMass * 1.0e9 * MOLES_PER_NM3;
    } else if ("ug/kg".equals(unit)) {
      return moleFraction * mercuryMolarMass / phaseMolarMass * 1.0e9;
    } else if ("ppb".equals(unit)) {
      return moleFraction * 1.0e9;
    }
    throw new IllegalArgumentException("unit " + unit + " is not supported; use ug/Sm3, ug/Nm3, ug/kg or ppb");
  }

  /**
   * Get the fugacity of pure condensed mercury at the temperature and pressure of the fluid.
   *
   * @return fugacity in bara
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getPureMercuryFugacity() {
    requireResult();
    return pureMercuryFugacity;
  }

  /**
   * Get the degree of mercury saturation of the feed.
   *
   * @return fugacity of mercury with all mercury dissolved in the fluid phases divided by the fugacity of pure
   * condensed mercury; a value above one means a separate mercury phase
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public double getSaturationRatio() {
    requireResult();
    return saturationRatio;
  }

  /**
   * Convert a flow of mercury to another unit.
   *
   * @param molesPerSecond flow of mercury in mole/sec, which is the mole basis of fluids in process streams
   * @param molarMass molar mass of mercury in kg/mol
   * @param unit "mole/sec", "mole/hr", "kg/sec", "kg/min", "kg/hr", "kg/day", "kg/yr", "g/sec", "g/hr", "g/day" or
   * "g/yr"; a year is 365 days
   * @return flow of mercury in the given unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  public static double convertMercuryFlow(double molesPerSecond, double molarMass, String unit) {
    double kgPerSecond = molesPerSecond * molarMass;
    if ("mole/sec".equals(unit)) {
      return molesPerSecond;
    } else if ("mole/hr".equals(unit)) {
      return molesPerSecond * 3600.0;
    } else if ("kg/sec".equals(unit)) {
      return kgPerSecond;
    } else if ("kg/min".equals(unit)) {
      return kgPerSecond * 60.0;
    } else if ("kg/hr".equals(unit)) {
      return kgPerSecond * 3600.0;
    } else if ("kg/day".equals(unit)) {
      return kgPerSecond * 86400.0;
    } else if ("kg/yr".equals(unit)) {
      return kgPerSecond * 86400.0 * 365.0;
    } else if ("g/sec".equals(unit)) {
      return kgPerSecond * 1.0e3;
    } else if ("g/hr".equals(unit)) {
      return kgPerSecond * 3.6e6;
    } else if ("g/day".equals(unit)) {
      return kgPerSecond * 8.64e7;
    } else if ("g/yr".equals(unit)) {
      return kgPerSecond * 8.64e7 * 365.0;
    }
    throw new IllegalArgumentException("unit " + unit + " is not supported for a mercury flow; use mole/sec, mole/hr, "
        + "kg/sec, kg/min, kg/hr, kg/day, kg/yr, g/sec, g/hr, g/day or g/yr");
  }
}
