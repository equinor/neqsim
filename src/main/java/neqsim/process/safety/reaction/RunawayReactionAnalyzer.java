package neqsim.process.safety.reaction;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Screening analyzer for runaway (exothermic) reaction excursions.
 *
 * <p>
 * Implements the standard adiabatic thermal-hazard screening relations used in reaction-hazard assessment (Stoessel,
 * <i>Thermal Safety of Chemical Processes</i>; CCPS <i>Guidelines for Chemical Reactivity Evaluation</i>; DIERS):
 *
 * <ul>
 * <li><b>Adiabatic temperature rise</b>: {@code dT_ad = Q / (m · Cp)} where {@code Q = |ΔH_rxn| · n_limiting} is the
 * total heat released by complete conversion of the limiting reactant.</li>
 * <li><b>Maximum temperature of the synthesis reaction</b> (worst-case, adiabatic): {@code MTSR = T0 + dT_ad}.</li>
 * <li><b>Margin</b> to a supplied maximum-allowable temperature (decomposition onset or material limit):
 * {@code margin = MAT - MTSR}.</li>
 * <li><b>Adiabatic time-to-maximum-rate</b> (Townsend &amp; Tou): {@code TMR_ad = Cp · R · T0² / (q0 · Ea)} where
 * {@code q0} is the specific heat-release rate at {@code T0} and {@code Ea} the apparent activation energy.</li>
 * </ul>
 *
 * <p>
 * This is a HAZOP <i>Other than &rarr; runaway reaction</i> screening tool. A {@code RUNAWAY_RISK} or marginal verdict,
 * or a gas-generating ("gassy") system, flags the need for a full reaction-calorimetry study and DIERS two-phase
 * emergency-relief design — it does not size relief by itself.
 *
 * @author ESOL
 * @version 1.0
 */
public class RunawayReactionAnalyzer {
  private static final Logger logger = LogManager.getLogger(RunawayReactionAnalyzer.class);

  /** Universal gas constant [J/(mol·K)]. */
  private static final double R_GAS = 8.314462618;

  /** Default acceptable margin between the maximum-allowable temperature and MTSR [K]. */
  private static final double DEFAULT_ACCEPTABLE_MARGIN_K = 50.0;

  /** Adiabatic temperature rise above which an unrated system is treated as a runaway risk [K]. */
  private static final double HIGH_SEVERITY_DT_K = 200.0;

  /** Adiabatic temperature rise above which an unrated system is treated as marginal [K]. */
  private static final double MARGINAL_SEVERITY_DT_K = 50.0;

  /**
   * Screening verdict for a runaway-reaction scenario.
   */
  public enum RunawayVerdict {
    /** No maximum-allowable temperature supplied; verdict based on adiabatic severity only. */
    NO_RATING,
    /** MTSR is comfortably below the maximum-allowable temperature. */
    ACCEPTABLE_MARGIN,
    /** MTSR approaches the maximum-allowable temperature (small margin). */
    MARGINAL,
    /** MTSR exceeds the maximum-allowable temperature, or adiabatic severity is high. */
    RUNAWAY_RISK
  }

  private double processTemperatureK = Double.NaN;
  private double reactionEnthalpyJPerMol = Double.NaN;
  private double limitingReactantMoles = Double.NaN;
  private double totalMassKg = Double.NaN;
  private double specificHeatJPerKgK = Double.NaN;
  private double maxAllowableTemperatureK = Double.NaN;
  private boolean maxAllowableProvided = false;
  private double activationEnergyJPerMol = Double.NaN;
  private double initialHeatReleaseRateWPerKg = Double.NaN;
  private double acceptableMarginK = DEFAULT_ACCEPTABLE_MARGIN_K;
  private boolean gassySystem = false;
  private SystemInterface fluid = null;

  /**
   * Create a runaway-reaction screening analyzer with no inputs set.
   */
  public RunawayReactionAnalyzer() {
  }

  /**
   * Set the initial process temperature.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setProcessTemperature(double value, String unit) {
    this.processTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    return this;
  }

  /**
   * Set the molar reaction enthalpy of the limiting reactant. By convention an exothermic reaction has a negative
   * value; the magnitude is used for the heat-release calculation.
   *
   * @param value reaction enthalpy value
   * @param unit enthalpy unit ("J/mol" or "kJ/mol")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setReactionEnthalpy(double value, String unit) {
    if ("kJ/mol".equalsIgnoreCase(unit)) {
      this.reactionEnthalpyJPerMol = value * 1000.0;
    } else if ("J/mol".equalsIgnoreCase(unit)) {
      this.reactionEnthalpyJPerMol = value;
    } else {
      throw new IllegalArgumentException("Unsupported reaction-enthalpy unit: " + unit + " (use J/mol or kJ/mol)");
    }
    return this;
  }

  /**
   * Set the number of moles of the limiting reactant available to react.
   *
   * @param moles limiting-reactant moles [mol]
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setLimitingReactantMoles(double moles) {
    this.limitingReactantMoles = moles;
    return this;
  }

  /**
   * Set the total reacting mass explicitly.
   *
   * @param value mass value
   * @param unit mass unit ("kg")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setTotalMass(double value, String unit) {
    if (!"kg".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("Unsupported mass unit: " + unit + " (use kg)");
    }
    this.totalMassKg = value;
    return this;
  }

  /**
   * Set the specific heat of the reacting mixture explicitly.
   *
   * @param value specific-heat value
   * @param unit specific-heat unit ("J/kgK" or "kJ/kgK")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setSpecificHeat(double value, String unit) {
    if ("kJ/kgK".equalsIgnoreCase(unit)) {
      this.specificHeatJPerKgK = value * 1000.0;
    } else if ("J/kgK".equalsIgnoreCase(unit)) {
      this.specificHeatJPerKgK = value;
    } else {
      throw new IllegalArgumentException("Unsupported specific-heat unit: " + unit + " (use J/kgK or kJ/kgK)");
    }
    return this;
  }

  /**
   * Set the maximum-allowable temperature (decomposition onset or material limit) used as the rating.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setMaxAllowableTemperature(double value, String unit) {
    this.maxAllowableTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    this.maxAllowableProvided = true;
    return this;
  }

  /**
   * Set the apparent activation energy used for the adiabatic time-to-maximum-rate.
   *
   * @param value activation-energy value
   * @param unit energy unit ("J/mol" or "kJ/mol")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setActivationEnergy(double value, String unit) {
    if ("kJ/mol".equalsIgnoreCase(unit)) {
      this.activationEnergyJPerMol = value * 1000.0;
    } else if ("J/mol".equalsIgnoreCase(unit)) {
      this.activationEnergyJPerMol = value;
    } else {
      throw new IllegalArgumentException("Unsupported activation-energy unit: " + unit + " (use J/mol or kJ/mol)");
    }
    return this;
  }

  /**
   * Set the specific heat-release rate at the process temperature, used for the adiabatic time-to-maximum-rate.
   *
   * @param value heat-release-rate value
   * @param unit rate unit ("W/kg")
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setInitialHeatReleaseRate(double value, String unit) {
    if (!"W/kg".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("Unsupported heat-release-rate unit: " + unit + " (use W/kg)");
    }
    this.initialHeatReleaseRateWPerKg = value;
    return this;
  }

  /**
   * Set the acceptable margin between the maximum-allowable temperature and MTSR.
   *
   * @param marginK acceptable margin [K]
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setAcceptableMargin(double marginK) {
    this.acceptableMarginK = marginK;
    return this;
  }

  /**
   * Declare whether the reaction generates permanent (non-condensable) gas. A gassy system always triggers a DIERS
   * two-phase relief screening recommendation.
   *
   * @param gassy true if the reaction generates permanent gas
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setGassySystem(boolean gassy) {
    this.gassySystem = gassy;
    return this;
  }

  /**
   * Supply a fluid used to derive the total mass and/or specific heat when they are not set explicitly.
   *
   * @param fluid the reacting fluid
   * @return this analyzer for chaining
   */
  public RunawayReactionAnalyzer setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Run the runaway-reaction screening calculation.
   *
   * @return an immutable screening result
   * @throws IllegalStateException if required inputs are missing or physically invalid
   */
  public RunawayReactionResult analyze() {
    List<String> warnings = new ArrayList<String>();
    validateInputs();

    double mass = resolveMass(warnings);
    double cp = resolveSpecificHeat(warnings);

    double heatRelease = Math.abs(reactionEnthalpyJPerMol) * limitingReactantMoles;
    double dTad = heatRelease / (mass * cp);
    double mtsr = processTemperatureK + dTad;

    double margin = Double.NaN;
    boolean marginExceeded = false;
    if (maxAllowableProvided) {
      margin = maxAllowableTemperatureK - mtsr;
      marginExceeded = mtsr > maxAllowableTemperatureK;
    }

    boolean tmrProvided = false;
    double tmr = Double.NaN;
    if (initialHeatReleaseRateWPerKg > 0.0 && activationEnergyJPerMol > 0.0) {
      tmr = (cp * R_GAS * processTemperatureK * processTemperatureK)
          / (initialHeatReleaseRateWPerKg * activationEnergyJPerMol);
      tmrProvided = true;
    }

    RunawayVerdict verdict = classify(dTad, margin, marginExceeded);
    boolean twoPhaseScreening = gassySystem || marginExceeded;

    logger.info("Runaway-reaction screening: dT_ad={} K, MTSR={} K, margin={} K, verdict={}", dTad, mtsr, margin,
        verdict);

    return new RunawayReactionResult(processTemperatureK, dTad, mtsr, maxAllowableTemperatureK, margin,
        maxAllowableProvided, marginExceeded, heatRelease, mass, cp, tmrProvided, tmr, twoPhaseScreening, verdict,
        warnings);
  }

  /**
   * Validate that the mandatory inputs are present and physically meaningful.
   *
   * @throws IllegalStateException if any mandatory input is missing or invalid
   */
  private void validateInputs() {
    if (Double.isNaN(processTemperatureK) || processTemperatureK <= 0.0) {
      throw new IllegalStateException("Process temperature must be set to a positive value");
    }
    if (Double.isNaN(reactionEnthalpyJPerMol)) {
      throw new IllegalStateException("Reaction enthalpy must be set");
    }
    if (Double.isNaN(limitingReactantMoles) || limitingReactantMoles <= 0.0) {
      throw new IllegalStateException("Limiting-reactant moles must be set to a positive value");
    }
    if (maxAllowableProvided && maxAllowableTemperatureK <= processTemperatureK) {
      throw new IllegalStateException("Maximum-allowable temperature must exceed the process temperature");
    }
  }

  /**
   * Resolve the total reacting mass from the explicit value or the supplied fluid.
   *
   * @param warnings list to which non-fatal warnings are appended
   * @return total reacting mass [kg]
   * @throws IllegalStateException if no mass can be resolved
   */
  private double resolveMass(List<String> warnings) {
    if (!Double.isNaN(totalMassKg) && totalMassKg > 0.0) {
      return totalMassKg;
    }
    if (fluid != null) {
      double m = fluid.getFlowRate("kg/sec");
      if (m > 0.0) {
        warnings.add("Total mass derived from fluid flow rate (kg/sec) treated as a 1-second inventory basis");
        return m;
      }
    }
    throw new IllegalStateException("Total reacting mass must be set explicitly or via a fluid");
  }

  /**
   * Resolve the specific heat from the explicit value or the supplied fluid.
   *
   * @param warnings list to which non-fatal warnings are appended
   * @return specific heat [J/(kg·K)]
   * @throws IllegalStateException if no specific heat can be resolved
   */
  private double resolveSpecificHeat(List<String> warnings) {
    if (!Double.isNaN(specificHeatJPerKgK) && specificHeatJPerKgK > 0.0) {
      return specificHeatJPerKgK;
    }
    if (fluid != null) {
      try {
        SystemInterface state = fluid.clone();
        state.setTemperature(processTemperatureK);
        new neqsim.thermodynamicoperations.ThermodynamicOperations(state).TPflash();
        state.initProperties();
        double cp = state.getCp("J/kgK");
        if (cp > 0.0) {
          return cp;
        }
      } catch (RuntimeException ex) {
        warnings.add("Could not derive specific heat from fluid: " + ex.getMessage());
      }
    }
    throw new IllegalStateException("Specific heat must be set explicitly or via a fluid");
  }

  /**
   * Classify the screening verdict from the adiabatic severity and the margin to the maximum-allowable temperature.
   *
   * @param dTad adiabatic temperature rise [K]
   * @param margin margin = MAT - MTSR [K], NaN if no MAT supplied
   * @param marginExceeded true if MTSR exceeds the maximum-allowable temperature
   * @return the screening verdict
   */
  private RunawayVerdict classify(double dTad, double margin, boolean marginExceeded) {
    if (!maxAllowableProvided) {
      if (dTad >= HIGH_SEVERITY_DT_K) {
        return RunawayVerdict.RUNAWAY_RISK;
      }
      if (dTad >= MARGINAL_SEVERITY_DT_K) {
        return RunawayVerdict.MARGINAL;
      }
      return RunawayVerdict.NO_RATING;
    }
    if (marginExceeded) {
      return RunawayVerdict.RUNAWAY_RISK;
    }
    if (margin < acceptableMarginK) {
      return RunawayVerdict.MARGINAL;
    }
    return RunawayVerdict.ACCEPTABLE_MARGIN;
  }
}
