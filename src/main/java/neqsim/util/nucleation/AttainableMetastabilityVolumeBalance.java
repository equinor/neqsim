package neqsim.util.nucleation;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Predicts the attainable metastability (superheat / pressure-undershoot limit) of a depressurising liquid using the
 * <b>volume balancing method</b>.
 *
 * <p>
 * When a liquid is depressurised rapidly (for example the rarefaction wave that travels into a pipe after a full-bore
 * rupture), phase change is delayed and the liquid becomes metastable (superheated) below its saturation pressure.
 * Classical nucleation theory (CNT, see {@link ClassicalNucleationTheory}) reproduces the attainable superheat near the
 * critical point but over-predicts it strongly at low reduced temperature, where an empirical, temperature-dependent
 * correction factor is normally required.
 * </p>
 *
 * <p>
 * The volume balancing method removes that empirical factor by recognising that, away from the critical point, the
 * limit is set by <b>evaporation into pre-existing bubbles</b> (e.g. gas pockets trapped in wall crevices) rather than
 * by homogeneous nucleation. The metastability limit is the point on the depressurisation path where the rate of vapour
 * volume <i>created</i> by the growth of those bubbles first balances the rate of liquid volume <i>lost</i> through the
 * rarefaction outflow:
 * </p>
 *
 * <pre>
 *   dV/dt|_evaporation  &gt;=  dV/dt|_outflow      (metastability limit reached)
 * </pre>
 *
 * <p>
 * <b>Volume loss (outflow).</b> The depressurising liquid is treated as an isentropic rarefaction wave. The outflow
 * speed {@code u} is integrated along the metastable isentrope from the initial pressure using the Riemann invariant
 * for a simple wave,
 * </p>
 *
 * <pre>
 *   u_i = u_{i-1} - 2 (p_{i-1} - p_i) / (rho_i c_i + rho_{i-1} c_{i-1})
 *   dV/dt|_outflow = -u_i * A_pipe
 * </pre>
 *
 * <p>
 * where {@code c} is the single-phase (metastable) liquid speed of sound and {@code A_pipe} the pipe cross-sectional
 * area.
 * </p>
 *
 * <p>
 * <b>Volume creation (bubble growth).</b> Each pre-existing bubble grows by heat-transfer-limited evaporation following
 * the asymptotic Plesset &amp; Zwick (1954) solution (Collier &amp; Thome, <i>Convective Boiling and Condensation</i>,
 * Eq. 4.27). Evaluated at a short reference time {@code dt} the bubble radius and growth rate are
 * </p>
 *
 * <pre>
 *   R    = 2 dTsat k_l / (h_lg rho_g) * sqrt(3 dt / (pi alpha_l))
 *   dR/dt = 0.5 R / dt
 *   dV/dt|_evaporation = 4 pi R^2 (dR/dt) * n_bub
 * </pre>
 *
 * <p>
 * with liquid superheat {@code dTsat = T - Tsat(p)}, liquid thermal conductivity {@code k_l}, thermal diffusivity
 * {@code alpha_l = k_l/(rho_l c_pl)}, latent heat {@code h_lg}, saturated vapour density {@code rho_g}, and a single
 * temperature-independent tuning parameter {@code n_bub} (the number of pre-existing bubbles).
 * </p>
 *
 * <p>
 * All thermodynamic and transport properties are taken from the supplied NeqSim {@link SystemInterface}, so any
 * equation of state available in NeqSim (SRK, PR, GERG-2008, Span-Wagner type reference EOS, ...) can be used. The
 * metastable liquid and vapour roots are obtained with {@link SystemInterface#setForceSinglePhase(PhaseType)}. Unlike
 * the original reference implementation, the liquid thermal conductivity is taken from NeqSim's general transport
 * property models rather than a substance-specific correlation, so the method is not limited to a single fluid.
 * </p>
 *
 * <p>
 * <b>Usage:</b>
 * </p>
 *
 * <pre>
 * {@code
 * SystemInterface co2 = new SystemSrkEos(273.15, 120.0);
 * co2.addComponent("CO2", 1.0);
 * co2.setMixingRule("classic");
 *
 * AttainableMetastabilityVolumeBalance vb = new AttainableMetastabilityVolumeBalance(co2);
 * vb.setNumberOfBubbles(1.0e8);
 * vb.setPipeRadius(0.02);
 * vb.calculateLimit(273.15, 120.0e5); // T0 [K], p0 [Pa]
 *
 * if (vb.isLimitFound()) {
 *   double pLimitBar = vb.getLimitPressure() / 1e5;
 *   double superheat = vb.getSuperheat(); // K above saturation
 * }
 * }
 * </pre>
 *
 * <p>
 * <b>Scope:</b> screening / research method for a pure (or pseudo-pure) liquid. It predicts the <i>limit</i> of
 * metastability, not the subsequent two-phase flashing flow. For the equilibrium blowdown driving force use
 * {@link neqsim.process.safety.depressurization.DepressurizationSimulator}; for the metastable / spinodal region
 * classification use {@link SpinodalDecompositionDetector}.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Log, A.M. (2025). On the attainable metastability of liquids during depressurisation - effect of pre-existing
 * bubbles. <i>Journal of Fluid Mechanics</i> 1018, R2. https://doi.org/10.1017/jfm.2025.10545</li>
 * <li>Plesset, M.S. and Zwick, S.A. (1954). The growth of vapor bubbles in superheated liquids. <i>J. Appl. Phys.</i>
 * 25, 493-500.</li>
 * <li>Collier, J.G. and Thome, J.R. (1994). <i>Convective Boiling and Condensation</i>, 3rd ed., Eq. 4.27.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class AttainableMetastabilityVolumeBalance {

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(AttainableMetastabilityVolumeBalance.class);

  /** The NeqSim fluid (pure or pseudo-pure) supplied by the caller. */
  private final SystemInterface fluid;

  // ============================================================================
  // Model parameters
  // ============================================================================

  /** Number of pre-existing bubbles (free tuning parameter). Default 1.0e8. */
  private double numberOfBubbles = 1.0e8;

  /** Pipe radius in m (sets the outflow cross-sectional area). Default 0.02 m. */
  private double pipeRadius = 0.02;

  /** Lower pressure bound of the depressurisation search in Pa. Default 3.0e5 Pa. */
  private double minimumPressure = 3.0e5;

  /** Number of pressure steps used to integrate the isentrope. Default 1000. */
  private int numberOfSteps = 1000;

  /** Reference time for the asymptotic Plesset-Zwick growth evaluation in s. Default 1.0e-4 s. */
  private double bubbleReferenceTime = 1.0e-4;

  // ============================================================================
  // Results
  // ============================================================================

  /** Initial temperature in K. */
  private double initialTemperature = Double.NaN;

  /** Initial pressure in Pa. */
  private double initialPressure = Double.NaN;

  /** Whether the metastability limit was located within the search range. */
  private boolean limitFound = false;

  /** Pressure at the metastability limit in Pa. */
  private double limitPressure = Double.NaN;

  /** Temperature at the metastability limit in K. */
  private double limitTemperature = Double.NaN;

  /** Saturation temperature at the limit pressure in K. */
  private double limitSaturationTemperature = Double.NaN;

  /** Liquid superheat (T - Tsat) at the limit in K. */
  private double superheat = Double.NaN;

  /** Whether {@link #calculateLimit(double, double)} has been run. */
  private boolean calculated = false;

  /** Human-readable status message. */
  private String message = "Not calculated.";

  /**
   * Creates a volume balancing metastability solver for the given fluid.
   *
   * <p>
   * The fluid should contain a single component (or a pseudo-pure mixture) and have its mixing rule set. The fluid is
   * cloned internally, so the caller's object is not modified.
   * </p>
   *
   * @param fluid the NeqSim thermodynamic system to use as the property source
   */
  public AttainableMetastabilityVolumeBalance(SystemInterface fluid) {
    this.fluid = fluid;
  }

  /**
   * Sets the number of pre-existing bubbles (the single temperature-independent tuning parameter).
   *
   * @param numberOfBubbles number of pre-existing bubbles (must be positive)
   */
  public void setNumberOfBubbles(double numberOfBubbles) {
    this.numberOfBubbles = numberOfBubbles;
  }

  /**
   * Sets the pipe radius that defines the outflow cross-sectional area.
   *
   * @param pipeRadius pipe radius in m (must be positive)
   */
  public void setPipeRadius(double pipeRadius) {
    this.pipeRadius = pipeRadius;
  }

  /**
   * Sets the lower pressure bound of the depressurisation search.
   *
   * @param minimumPressure minimum pressure in Pa
   */
  public void setMinimumPressure(double minimumPressure) {
    this.minimumPressure = minimumPressure;
  }

  /**
   * Sets the number of pressure steps used to integrate the isentrope.
   *
   * @param numberOfSteps number of integration steps (must be &gt; 1)
   */
  public void setNumberOfSteps(int numberOfSteps) {
    this.numberOfSteps = numberOfSteps;
  }

  /**
   * Sets the reference time used to evaluate the asymptotic Plesset-Zwick bubble growth rate.
   *
   * @param bubbleReferenceTime reference time in s (must be positive)
   */
  public void setBubbleReferenceTime(double bubbleReferenceTime) {
    this.bubbleReferenceTime = bubbleReferenceTime;
  }

  /**
   * Calculates the attainable metastability limit for the given initial liquid state.
   *
   * <p>
   * The liquid is depressurised isentropically from {@code (initialTemperatureK,
   * initialPressurePa)} down to {@link #setMinimumPressure(double) the minimum pressure}. Along the metastable
   * isentrope the outflow rate and the bubble-growth rate are compared at every step; the limit is the first point
   * below saturation where bubble growth balances the outflow.
   * </p>
   *
   * @param initialTemperatureK initial (liquid) temperature in K
   * @param initialPressurePa initial pressure in Pa
   */
  public void calculateLimit(double initialTemperatureK, double initialPressurePa) {
    this.initialTemperature = initialTemperatureK;
    this.initialPressure = initialPressurePa;
    this.calculated = true;
    this.limitFound = false;
    this.limitPressure = Double.NaN;
    this.limitTemperature = Double.NaN;
    this.limitSaturationTemperature = Double.NaN;
    this.superheat = Double.NaN;

    // Forced single-phase liquid clone provides the metastable liquid root and its properties.
    SystemInterface liquid = fluid.clone();
    liquid.setForceSinglePhase(PhaseType.LIQUID);
    ThermodynamicOperations liquidOps = new ThermodynamicOperations(liquid);

    // Forced single-phase vapour clone provides the saturated vapour density and enthalpy.
    SystemInterface vapour = fluid.clone();
    vapour.setForceSinglePhase(PhaseType.GAS);
    ThermodynamicOperations vapourOps = new ThermodynamicOperations(vapour);

    // Saturation clone (normal phase behaviour) provides Tsat(p).
    SystemInterface saturation = fluid.clone();
    ThermodynamicOperations saturationOps = new ThermodynamicOperations(saturation);

    // Reference entropy of the initial liquid state (isentropic depressurisation).
    double sRef;
    try {
      liquid.setTemperature(initialTemperatureK);
      liquid.setPressure(initialPressurePa / 1.0e5);
      liquidOps.TPflash();
      sRef = liquid.getEntropy("J/molK");
    } catch (Exception ex) {
      message = "Could not evaluate the initial liquid state: " + ex.getMessage();
      logger.warn(message, ex);
      return;
    }

    double aPipe = Math.PI * pipeRadius * pipeRadius;
    double dpStep = (initialPressurePa - minimumPressure) / numberOfSteps;
    if (dpStep <= 0.0) {
      message = "Minimum pressure must be below the initial pressure.";
      logger.warn(message);
      return;
    }

    double pPrev = initialPressurePa;
    double tPrev = initialTemperatureK;
    double rhoPrev = Double.NaN;
    double cPrev = Double.NaN;
    double u = 0.0;
    double tGuess = initialTemperatureK;

    for (int i = 0; i <= numberOfSteps; i++) {
      double p = initialPressurePa - i * dpStep;
      if (p < minimumPressure) {
	p = minimumPressure;
      }

      // Solve the metastable single-phase liquid temperature on the isentrope at this pressure.
      double t;
      try {
	t = solveIsentropicLiquidTemperature(liquid, liquidOps, p, sRef, tGuess);
      } catch (Exception ex) {
	// Liquid root no longer available (likely beyond the spinodal) - stop the search.
	message = "Metastable liquid root lost near " + (p / 1e5) + " bara (spinodal reached).";
	logger.debug(message, ex);
	break;
      }
      tGuess = t;

      // Metastable liquid acoustic and density properties at the converged state.
      liquid.initProperties();
      double rho = liquid.getDensity("kg/m3");
      double c = liquid.getSoundSpeed("m/s");

      // Integrate the rarefaction outflow speed (Riemann invariant of a simple wave).
      if (i > 0 && Double.isFinite(rhoPrev) && Double.isFinite(cPrev)) {
	double dp = pPrev - p;
	u = u - 2.0 * dp / (rho * c + rhoPrev * cPrev);
      }

      // Saturation temperature at the current pressure.
      double tSat = saturationTemperature(saturation, saturationOps, p, t);
      double dTsat = t - tSat;

      if (dTsat > 0.0) {
	// Below saturation - the liquid is metastable. Compare the two volume rates.
	double dvdtLost = -u * aPipe;
	double dvdtEvap = bubbleGrowthVolumeRate(liquid, vapour, vapourOps, p, t, dTsat);

	if (dvdtEvap >= dvdtLost && dvdtLost > 0.0) {
	  limitFound = true;
	  limitPressure = p;
	  limitTemperature = t;
	  limitSaturationTemperature = tSat;
	  superheat = dTsat;
	  message = "Metastability limit located.";
	  break;
	}
      }

      pPrev = p;
      tPrev = t;
      rhoPrev = rho;
      cPrev = c;

      if (p <= minimumPressure) {
	break;
      }
    }

    if (!limitFound && message.equals("Not calculated.")) {
      message = "Metastability limit not found within the search range (pmin = " + (minimumPressure / 1e5)
	  + " bara). Consider lowering the minimum pressure.";
      logger.info(message);
    } else if (!limitFound) {
      // message already set above (spinodal reached etc.)
      logger.info(message);
    }
    // tPrev retained for clarity of the integration loop; no further use.
    if (Double.isNaN(tPrev)) {
      logger.trace("isentrope terminated with undefined temperature");
    }
  }

  /**
   * Solves the metastable single-phase liquid temperature on the reference isentrope at a given pressure using a secant
   * iteration on molar entropy.
   *
   * @param liquid the forced single-phase liquid clone
   * @param ops thermodynamic operations bound to {@code liquid}
   * @param pressurePa pressure in Pa
   * @param entropyTarget target molar entropy in J/(mol K)
   * @param temperatureGuess initial temperature guess in K
   * @return the isentropic liquid temperature in K
   */
  private double solveIsentropicLiquidTemperature(SystemInterface liquid, ThermodynamicOperations ops,
      double pressurePa, double entropyTarget, double temperatureGuess) {
    double pBara = pressurePa / 1.0e5;
    double t0 = temperatureGuess;
    double t1 = temperatureGuess - 1.0;

    liquid.setPressure(pBara);

    liquid.setTemperature(t0);
    ops.TPflash();
    double f0 = liquid.getEntropy("J/molK") - entropyTarget;

    for (int iter = 0; iter < 40; iter++) {
      liquid.setTemperature(t1);
      ops.TPflash();
      double f1 = liquid.getEntropy("J/molK") - entropyTarget;

      if (Math.abs(f1) < 1.0e-6) {
	return t1;
      }
      double denom = (f1 - f0);
      if (Math.abs(denom) < 1.0e-12) {
	break;
      }
      double tNext = t1 - f1 * (t1 - t0) / denom;
      // Keep the step bounded for robustness.
      if (!Double.isFinite(tNext)) {
	break;
      }
      if (tNext < 1.0) {
	tNext = 1.0;
      }
      t0 = t1;
      f0 = f1;
      t1 = tNext;
      if (Math.abs(t1 - t0) < 1.0e-5) {
	return t1;
      }
    }
    return t1;
  }

  /**
   * Computes the saturation (bubble point) temperature of the fluid at a given pressure.
   *
   * @param saturation the saturation clone (normal phase behaviour)
   * @param ops thermodynamic operations bound to {@code saturation}
   * @param pressurePa pressure in Pa
   * @param temperatureGuess starting temperature guess in K
   * @return saturation temperature in K
   */
  private double saturationTemperature(SystemInterface saturation, ThermodynamicOperations ops, double pressurePa,
      double temperatureGuess) {
    try {
      saturation.setPressure(pressurePa / 1.0e5);
      saturation.setTemperature(temperatureGuess);
      ops.bubblePointTemperatureFlash();
      return saturation.getTemperature();
    } catch (Exception ex) {
      logger.debug("Saturation temperature flash failed at {} bara: {}", pressurePa / 1e5, ex.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Computes the rate of vapour volume creation from heat-transfer-limited growth of the pre-existing bubbles
   * (asymptotic Plesset-Zwick solution).
   *
   * @param liquid the forced single-phase liquid clone, already flashed to (T, p)
   * @param vapour the forced single-phase vapour clone
   * @param vapourOps thermodynamic operations bound to {@code vapour}
   * @param pressurePa pressure in Pa
   * @param temperatureK metastable liquid temperature in K
   * @param superheatK liquid superheat (T - Tsat) in K
   * @return volume creation rate in m^3/s (0 if growth is not physical)
   */
  private double bubbleGrowthVolumeRate(SystemInterface liquid, SystemInterface vapour,
      ThermodynamicOperations vapourOps, double pressurePa, double temperatureK, double superheatK) {
    if (superheatK <= 0.0) {
      return 0.0;
    }
    try {
      // Liquid properties at the metastable state (liquid is already flashed at (T, p)).
      double rhoL = liquid.getDensity("kg/m3");
      double cpL = liquid.getCp("J/kgK");
      double kL = liquid.getThermalConductivity("W/mK");
      double hL = liquid.getEnthalpy("J/kg");

      // Vapour properties at the same (T, p) forced single-phase vapour root.
      vapour.setTemperature(temperatureK);
      vapour.setPressure(pressurePa / 1.0e5);
      vapourOps.TPflash();
      vapour.initProperties();
      double rhoG = vapour.getDensity("kg/m3");
      double hG = vapour.getEnthalpy("J/kg");

      double hLg = hG - hL;
      if (hLg <= 0.0 || rhoG <= 0.0 || kL <= 0.0 || cpL <= 0.0 || rhoL <= 0.0) {
	return 0.0;
      }

      double alphaL = kL / (rhoL * cpL);
      double dt = bubbleReferenceTime;

      double r = 2.0 * superheatK * kL / (hLg * rhoG) * Math.sqrt(3.0 * dt / (Math.PI * alphaL));
      double dRdt = 0.5 * r / dt;
      return 4.0 * Math.PI * r * r * dRdt * numberOfBubbles;
    } catch (Exception ex) {
      logger.debug("Bubble growth evaluation failed at {} bara: {}", pressurePa / 1e5, ex.getMessage());
      return 0.0;
    }
  }

  // ============================================================================
  // Results accessors
  // ============================================================================

  /**
   * Returns whether the metastability limit was located within the search range.
   *
   * @return true if a limit was found
   */
  public boolean isLimitFound() {
    return limitFound;
  }

  /**
   * Returns the pressure at the attainable metastability limit.
   *
   * @return limit pressure in Pa (NaN if not found)
   */
  public double getLimitPressure() {
    return limitPressure;
  }

  /**
   * Returns the temperature at the attainable metastability limit.
   *
   * @return limit temperature in K (NaN if not found)
   */
  public double getLimitTemperature() {
    return limitTemperature;
  }

  /**
   * Returns the saturation temperature at the limit pressure.
   *
   * @return saturation temperature in K (NaN if not found)
   */
  public double getLimitSaturationTemperature() {
    return limitSaturationTemperature;
  }

  /**
   * Returns the liquid superheat (T - Tsat) at the metastability limit.
   *
   * @return superheat in K (NaN if not found)
   */
  public double getSuperheat() {
    return superheat;
  }

  /**
   * Returns the initial temperature used for the calculation.
   *
   * @return initial temperature in K
   */
  public double getInitialTemperature() {
    return initialTemperature;
  }

  /**
   * Returns the initial pressure used for the calculation.
   *
   * @return initial pressure in Pa
   */
  public double getInitialPressure() {
    return initialPressure;
  }

  /**
   * Returns the number of pre-existing bubbles used as the tuning parameter.
   *
   * @return number of bubbles
   */
  public double getNumberOfBubbles() {
    return numberOfBubbles;
  }

  /**
   * Returns whether {@link #calculateLimit(double, double)} has been run.
   *
   * @return true if calculated
   */
  public boolean isCalculated() {
    return calculated;
  }

  /**
   * Returns a human-readable status message describing the outcome.
   *
   * @return status message
   */
  public String getMessage() {
    return message;
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * Returns all results as an ordered map for serialization.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("method", "Volume balancing method (Log, 2025)");
    result.put("limitFound", limitFound);
    result.put("message", message);

    Map<String, Object> input = new LinkedHashMap<String, Object>();
    input.put("initialTemperature_K", initialTemperature);
    input.put("initialPressure_bara", initialPressure / 1.0e5);
    input.put("numberOfBubbles", numberOfBubbles);
    input.put("pipeRadius_m", pipeRadius);
    input.put("minimumPressure_bara", minimumPressure / 1.0e5);
    input.put("numberOfSteps", numberOfSteps);
    input.put("bubbleReferenceTime_s", bubbleReferenceTime);
    result.put("input", input);

    if (limitFound) {
      Map<String, Object> limit = new LinkedHashMap<String, Object>();
      limit.put("limitPressure_bara", limitPressure / 1.0e5);
      limit.put("limitPressure_Pa", limitPressure);
      limit.put("limitTemperature_K", limitTemperature);
      limit.put("limitTemperature_C", limitTemperature - 273.15);
      limit.put("saturationTemperature_K", limitSaturationTemperature);
      limit.put("superheat_K", superheat);
      result.put("metastabilityLimit", limit);
    }
    return result;
  }

  /**
   * Returns the results as a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(toMap());
  }
}
