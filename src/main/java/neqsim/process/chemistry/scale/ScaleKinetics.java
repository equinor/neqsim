package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Screening-level scale precipitation kinetics on top of a thermodynamic saturation index.
 *
 * <p>
 * A saturation index (SI) tells whether a mineral scale <em>can</em> form, but not how fast or whether the deposit is
 * limited by surface reaction or by mass transport. This class adds that missing kinetic layer to the thermodynamic
 * result produced by {@link ElectrolyteScaleCalculator} or {@link ScalePredictionCalculator}.
 * </p>
 *
 * <p>
 * From the supersaturation ratio {@code S = 10^SI = IAP / Ksp} the model estimates:
 * </p>
 * <ul>
 * <li><b>Induction time</b> via a classical nucleation form {@code t_ind = k_n * exp(B / (ln S)^2)}, so induction time
 * falls sharply as supersaturation rises and is infinite at or below saturation.</li>
 * <li><b>Surface-reaction-limited growth</b> {@code g_rxn = k_r * (S - 1)^n} (second order, n = 2 by default), the
 * classic parabolic-in-supersaturation crystal growth law.</li>
 * <li><b>Mass-transport-limited growth</b> {@code g_mt = k_m * (c_bulk - c_eq)}, the diffusion of scaling ions to the
 * surface.</li>
 * <li><b>Limiting regime</b> — the smaller of the two growth rates governs; the class reports whether deposition is
 * REACTION- or TRANSPORT-limited (or NONE below saturation).</li>
 * </ul>
 *
 * <p>
 * The rate constants are tunable and default to field-typical screening values; they are not a substitute for
 * mineral-specific laboratory kinetics. Standards informational: NACE TM0374, NORSOK M-001.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ElectrolyteScaleCalculator
 * @see ScaleDepositionAccumulator
 */
public class ScaleKinetics implements Serializable {

  private static final long serialVersionUID = 1000L;

  // ─── Inputs ─────────────────────────────────────────────

  /** Thermodynamic saturation index, log10(IAP/Ksp). */
  private double saturationIndex = 0.0;

  /** Nucleation pre-exponential constant (hours). */
  private double nucleationConstantHours = 1.0e-3;

  /** Nucleation exponential shape factor B (dimensionless). */
  private double nucleationShapeFactor = 3.0;

  /** Surface-reaction rate constant (mm/yr). */
  private double surfaceRateConstantMmYr = 5.0;

  /** Surface-reaction order in supersaturation excess (S - 1). */
  private double surfaceReactionOrder = 2.0;

  /** Mass-transfer coefficient (m/s). */
  private double massTransferCoeffMs = 1.0e-5;

  /** Bulk molar concentration of the limiting scaling ion (mol/L). */
  private double bulkConcentrationMolL = 0.0;

  /** Equilibrium molar concentration of the limiting scaling ion (mol/L). */
  private double equilibriumConcentrationMolL = 0.0;

  // ─── Outputs ────────────────────────────────────────────

  private double supersaturationRatio = 0.0;
  private double inductionTimeHours = Double.POSITIVE_INFINITY;
  private double surfaceLimitedGrowthMmYr = 0.0;
  private double transportLimitedGrowthMmYr = 0.0;
  private double effectiveGrowthRateMmYr = 0.0;
  private String limitingRegime = "NONE";
  private boolean evaluated = false;

  /**
   * Sets the thermodynamic saturation index driving the kinetics.
   *
   * @param si saturation index log10(IAP/Ksp)
   * @return this for chaining
   */
  public ScaleKinetics setSaturationIndex(double si) {
    this.saturationIndex = si;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the surface-reaction rate constant and reaction order.
   *
   * @param rateConstantMmYr surface rate constant in mm/yr (must be non-negative)
   * @param order reaction order in supersaturation excess (typically 1 to 2)
   * @return this for chaining
   */
  public ScaleKinetics setSurfaceReaction(double rateConstantMmYr, double order) {
    this.surfaceRateConstantMmYr = Math.max(0.0, rateConstantMmYr);
    this.surfaceReactionOrder = order;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the mass-transfer coefficient and the bulk and equilibrium concentrations of the limiting scaling ion.
   *
   * @param coeffMs mass-transfer coefficient in m/s (must be non-negative)
   * @param bulkMolL bulk concentration in mol/L
   * @param equilibriumMolL equilibrium concentration in mol/L
   * @return this for chaining
   */
  public ScaleKinetics setMassTransfer(double coeffMs, double bulkMolL, double equilibriumMolL) {
    this.massTransferCoeffMs = Math.max(0.0, coeffMs);
    this.bulkConcentrationMolL = Math.max(0.0, bulkMolL);
    this.equilibriumConcentrationMolL = Math.max(0.0, equilibriumMolL);
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the nucleation pre-exponential constant and exponential shape factor.
   *
   * @param constantHours nucleation pre-exponential constant in hours (must be non-negative)
   * @param shapeFactor exponential shape factor B (must be non-negative)
   * @return this for chaining
   */
  public ScaleKinetics setNucleation(double constantHours, double shapeFactor) {
    this.nucleationConstantHours = Math.max(0.0, constantHours);
    this.nucleationShapeFactor = Math.max(0.0, shapeFactor);
    this.evaluated = false;
    return this;
  }

  /**
   * Evaluates the induction time, growth rates, and limiting regime from the current inputs.
   *
   * @return this for chaining
   */
  public ScaleKinetics evaluate() {
    supersaturationRatio = Math.pow(10.0, saturationIndex);

    if (supersaturationRatio <= 1.0) {
      inductionTimeHours = Double.POSITIVE_INFINITY;
      surfaceLimitedGrowthMmYr = 0.0;
      transportLimitedGrowthMmYr = 0.0;
      effectiveGrowthRateMmYr = 0.0;
      limitingRegime = "NONE";
      evaluated = true;
      return this;
    }

    double lnS = Math.log(supersaturationRatio);
    inductionTimeHours = nucleationConstantHours * Math.exp(nucleationShapeFactor / (lnS * lnS));

    surfaceLimitedGrowthMmYr = surfaceRateConstantMmYr * Math.pow(supersaturationRatio - 1.0, surfaceReactionOrder);

    // Convert mass-transfer flux (mol/m2/s) to a wall-recession rate (mm/yr) with a nominal
    // molar volume of the scale mineral (~37 cm3/mol for CaCO3 calcite).
    double drivingConcMolM3 = Math.max(0.0, bulkConcentrationMolL - equilibriumConcentrationMolL) * 1000.0;
    double fluxMolM2S = massTransferCoeffMs * drivingConcMolM3;
    double molarVolumeM3PerMol = 37.0e-6;
    double growthMPerS = fluxMolM2S * molarVolumeM3PerMol;
    transportLimitedGrowthMmYr = growthMPerS * 1000.0 * 3.1536e7;

    if (transportLimitedGrowthMmYr <= 0.0) {
      // No transport data supplied — reaction controls by default.
      effectiveGrowthRateMmYr = surfaceLimitedGrowthMmYr;
      limitingRegime = "REACTION";
    } else if (surfaceLimitedGrowthMmYr <= transportLimitedGrowthMmYr) {
      effectiveGrowthRateMmYr = surfaceLimitedGrowthMmYr;
      limitingRegime = "REACTION";
    } else {
      effectiveGrowthRateMmYr = transportLimitedGrowthMmYr;
      limitingRegime = "TRANSPORT";
    }

    evaluated = true;
    return this;
  }

  /**
   * Gets the supersaturation ratio S = 10^SI.
   *
   * @return supersaturation ratio (dimensionless)
   */
  public double getSupersaturationRatio() {
    ensureEvaluated();
    return supersaturationRatio;
  }

  /**
   * Gets the estimated nucleation induction time.
   *
   * @return induction time in hours (positive infinity at or below saturation)
   */
  public double getInductionTimeHours() {
    ensureEvaluated();
    return inductionTimeHours;
  }

  /**
   * Gets the surface-reaction-limited growth rate.
   *
   * @return surface-limited growth rate in mm/yr
   */
  public double getSurfaceLimitedGrowthMmYr() {
    ensureEvaluated();
    return surfaceLimitedGrowthMmYr;
  }

  /**
   * Gets the mass-transport-limited growth rate.
   *
   * @return transport-limited growth rate in mm/yr (0 if no transport data supplied)
   */
  public double getTransportLimitedGrowthMmYr() {
    ensureEvaluated();
    return transportLimitedGrowthMmYr;
  }

  /**
   * Gets the effective (rate-controlling) growth rate.
   *
   * @return effective growth rate in mm/yr
   */
  public double getEffectiveGrowthRateMmYr() {
    ensureEvaluated();
    return effectiveGrowthRateMmYr;
  }

  /**
   * Gets the limiting regime label.
   *
   * @return "REACTION", "TRANSPORT", or "NONE" (below saturation)
   */
  public String getLimitingRegime() {
    ensureEvaluated();
    return limitingRegime;
  }

  /**
   * Ensures {@link #evaluate()} has been called before returning a result.
   */
  private void ensureEvaluated() {
    if (!evaluated) {
      evaluate();
    }
  }

  /**
   * Serialises the kinetic result to a JSON string.
   *
   * @return a pretty-printed JSON representation
   */
  public String toJson() {
    ensureEvaluated();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("saturationIndex", saturationIndex);
    map.put("supersaturationRatio", supersaturationRatio);
    map.put("inductionTimeHours", inductionTimeHours);
    map.put("surfaceLimitedGrowthMmYr", surfaceLimitedGrowthMmYr);
    map.put("transportLimitedGrowthMmYr", transportLimitedGrowthMmYr);
    map.put("effectiveGrowthRateMmYr", effectiveGrowthRateMmYr);
    map.put("limitingRegime", limitingRegime);
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(map);
  }
}
