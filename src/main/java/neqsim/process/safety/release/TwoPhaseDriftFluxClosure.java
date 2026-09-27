package neqsim.process.safety.release;

import java.util.Map;
import java.util.TreeMap;

/**
 * Shared bounded Zuber-Findlay/Harmathy closure for one gas and one liquid phase.
 *
 * @author esol
 * @version 1.0
 */
final class TwoPhaseDriftFluxClosure {
  private static final double DISTRIBUTION_PARAMETER = 1.2;
  private static final double HARMATHY_COEFFICIENT = 1.53;
  private static final double GRAVITY_MS2 = 9.80665;
  private static final double MAX_GAS_AREA_FRACTION = 0.80;
  private static final double MAX_SLIP_RATIO = 128.0;

  /** Utility class. */
  private TwoPhaseDriftFluxClosure() {
  }

  /**
   * Solves phase-area, kinetic-energy and drift-flux closure.
   *
   * @param reference thermodynamic state with exactly one gas and one liquid phase
   * @param surfaceTensionNm caller-declared interfacial tension in N/m
   * @return immutable closure result
   */
  static Result solve(ReleaseState reference, double surfaceTensionNm) {
    ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    PhaseBasis basis = phaseBasis(reference);
    double driftVelocity = HARMATHY_COEFFICIENT * Math.pow(GRAVITY_MS2 * surfaceTensionNm
        * (basis.liquidDensityKgM3 - basis.gasDensityKgM3) / (basis.liquidDensityKgM3 * basis.liquidDensityKgM3), 0.25);
    ReleaseFlowRequest.positive(driftVelocity, "Harmathy drift velocity");
    double lower = 1.0;
    double lowerResidual = residual(reference, basis, lower, driftVelocity);
    double upper = 2.0;
    double upperResidual = residual(reference, basis, upper, driftVelocity);
    while (upperResidual <= 0.0 && upper < MAX_SLIP_RATIO) {
      upper *= 2.0;
      upperResidual = residual(reference, basis, upper, driftVelocity);
    }
    if (!(lowerResidual < 0.0) || !(upperResidual > 0.0)) {
      throw new IllegalStateException("Unable to bracket positive drift-flux slip solution");
    }
    for (int iteration = 0; iteration < 80; iteration++) {
      double middle = 0.5 * (lower + upper);
      double middleResidual = residual(reference, basis, middle, driftVelocity);
      if (middleResidual > 0.0) {
        upper = middle;
      } else {
        lower = middle;
      }
    }
    double slipRatio = 0.5 * (lower + upper);
    Result result = closure(reference, basis, slipRatio, surfaceTensionNm, driftVelocity);
    if (result.gasAreaFraction > MAX_GAS_AREA_FRACTION) {
      throw new UnsupportedOperationException("Predicted gas area fraction " + result.gasAreaFraction
          + " exceeds the bounded bubbly/dispersed screening limit " + MAX_GAS_AREA_FRACTION);
    }
    return result;
  }

  /**
   * Evaluates the drift-flux residual at one slip ratio.
   *
   * @param reference thermodynamic state
   * @param basis validated two-phase basis
   * @param slipRatio gas velocity divided by liquid velocity
   * @param driftVelocity Harmathy drift velocity in m/s
   * @return drift-flux residual in m/s
   */
  private static double residual(ReleaseState reference, PhaseBasis basis, double slipRatio, double driftVelocity) {
    return closure(reference, basis, slipRatio, Double.NaN, driftVelocity).driftResidualMs;
  }

  /**
   * Evaluates all closure equations for one slip ratio.
   *
   * @param reference thermodynamic state
   * @param basis validated two-phase basis
   * @param slipRatio gas velocity divided by liquid velocity
   * @param surfaceTensionNm interfacial tension in N/m, or NaN during root iteration
   * @param driftVelocity Harmathy drift velocity in m/s
   * @return immutable closure result
   */
  private static Result closure(ReleaseState reference, PhaseBasis basis, double slipRatio, double surfaceTensionNm,
      double driftVelocity) {
    double homogeneousVelocity = ReleaseFlowRequest.positive(reference.getVelocityMs(), "homogeneous velocity");
    double denominator = basis.gasMassFraction * slipRatio * slipRatio + basis.liquidMassFraction;
    double liquidVelocity = homogeneousVelocity / Math.sqrt(denominator);
    double gasVelocity = slipRatio * liquidVelocity;
    double specificArea = basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity)
        + basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double massFlux = 1.0 / specificArea;
    double gasAreaFraction = massFlux * basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity);
    double liquidAreaFraction = massFlux * basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double phaseAreaSum = gasAreaFraction + liquidAreaFraction;
    double volumetricFlux = massFlux
        * (basis.gasMassFraction / basis.gasDensityKgM3 + basis.liquidMassFraction / basis.liquidDensityKgM3);
    double driftResidual = gasVelocity - DISTRIBUTION_PARAMETER * volumetricFlux - driftVelocity;
    double phaseKineticEnergy = 0.5 * (basis.gasMassFraction * gasVelocity * gasVelocity
        + basis.liquidMassFraction * liquidVelocity * liquidVelocity);
    double homogeneousKineticEnergy = 0.5 * homogeneousVelocity * homogeneousVelocity;
    double kineticEnergyError = phaseKineticEnergy - homogeneousKineticEnergy;
    if (!Double.isFinite(massFlux) || massFlux <= 0.0 || !Double.isFinite(driftResidual)
        || Math.abs(phaseAreaSum - 1.0) > 1e-10
        || Math.abs(kineticEnergyError) > 1e-9 * Math.max(1.0, homogeneousKineticEnergy)) {
      throw new IllegalStateException("Phase area, drift-flux or kinetic-energy closure failed");
    }
    Map<String, Double> velocities = new TreeMap<String, Double>();
    velocities.put("GAS", gasVelocity);
    velocities.put(basis.liquidPhase, liquidVelocity);
    double bulkVelocity = massFlux / reference.getDensityKgM3();
    return new Result(reference.withPhaseVelocities(bulkVelocity, velocities), massFlux, gasAreaFraction, phaseAreaSum,
        kineticEnergyError, driftResidual, driftVelocity, slipRatio, surfaceTensionNm);
  }

  /**
   * Validates and extracts the one-gas/one-liquid phase basis.
   *
   * @param reference thermodynamic state
   * @return validated phase basis
   */
  private static PhaseBasis phaseBasis(ReleaseState reference) {
    if (reference == null || reference.getPhaseMassFractions().size() != 2
        || !reference.getPhaseMassFractions().containsKey("GAS")) {
      throw new UnsupportedOperationException("Exactly one gas and one liquid phase are required at the opening");
    }
    String liquid = null;
    for (String phase : reference.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported drift-flux phase: " + phase);
        }
        liquid = phase;
      }
    }
    if (liquid == null
        || !reference.getPhaseDensitiesKgM3().keySet().equals(reference.getPhaseMassFractions().keySet())) {
      throw new IllegalStateException("Phase density and mass-fraction bases do not match");
    }
    double gasMassFraction = reference.getPhaseMassFractions().get("GAS");
    double liquidMassFraction = reference.getPhaseMassFractions().get(liquid);
    if (gasMassFraction <= 1e-10 || liquidMassFraction <= 1e-10) {
      throw new UnsupportedOperationException("Both phases must have positive resolved mass fraction");
    }
    double gasDensity = ReleaseFlowRequest.positive(reference.getPhaseDensitiesKgM3().get("GAS"), "gas density");
    double liquidDensity = ReleaseFlowRequest.positive(reference.getPhaseDensitiesKgM3().get(liquid), "liquid density");
    if (liquidDensity <= gasDensity) {
      throw new UnsupportedOperationException("Harmathy closure requires liquid density greater than gas density");
    }
    return new PhaseBasis(liquid, gasMassFraction, liquidMassFraction, gasDensity, liquidDensity);
  }

  /** @return Zuber-Findlay distribution parameter */
  static double getDistributionParameter() {
    return DISTRIBUTION_PARAMETER;
  }

  /** @return maximum accepted bubbly/dispersed gas area fraction */
  static double getMaximumGasAreaFraction() {
    return MAX_GAS_AREA_FRACTION;
  }

  /** Validated phase basis. */
  private static final class PhaseBasis {
    private final String liquidPhase;
    private final double gasMassFraction;
    private final double liquidMassFraction;
    private final double gasDensityKgM3;
    private final double liquidDensityKgM3;

    /**
     * Creates a phase basis.
     *
     * @param liquidPhase native liquid phase name
     * @param gasMassFraction gas mass fraction
     * @param liquidMassFraction liquid mass fraction
     * @param gasDensityKgM3 gas density in kg/m3
     * @param liquidDensityKgM3 liquid density in kg/m3
     */
    private PhaseBasis(String liquidPhase, double gasMassFraction, double liquidMassFraction, double gasDensityKgM3,
        double liquidDensityKgM3) {
      this.liquidPhase = liquidPhase;
      this.gasMassFraction = gasMassFraction;
      this.liquidMassFraction = liquidMassFraction;
      this.gasDensityKgM3 = gasDensityKgM3;
      this.liquidDensityKgM3 = liquidDensityKgM3;
    }
  }

  /** Immutable closure result shared by release models. */
  static final class Result {
    final ReleaseState state;
    final double massFluxKgM2s;
    final double gasAreaFraction;
    final double phaseAreaSum;
    final double kineticEnergyErrorJkg;
    final double driftResidualMs;
    final double driftVelocityMs;
    final double slipRatio;
    final double surfaceTensionNm;

    /**
     * Creates a checked closure result.
     *
     * @param state state carrying phase velocities
     * @param massFluxKgM2s mass flux in kg/(m2 s)
     * @param gasAreaFraction gas area fraction
     * @param phaseAreaSum sum of phase area fractions
     * @param kineticEnergyErrorJkg energy closure error in J/kg
     * @param driftResidualMs drift-flux residual in m/s
     * @param driftVelocityMs Harmathy drift velocity in m/s
     * @param slipRatio gas velocity divided by liquid velocity
     * @param surfaceTensionNm interfacial tension in N/m
     */
    private Result(ReleaseState state, double massFluxKgM2s, double gasAreaFraction, double phaseAreaSum,
        double kineticEnergyErrorJkg, double driftResidualMs, double driftVelocityMs, double slipRatio,
        double surfaceTensionNm) {
      this.state = state;
      this.massFluxKgM2s = massFluxKgM2s;
      this.gasAreaFraction = gasAreaFraction;
      this.phaseAreaSum = phaseAreaSum;
      this.kineticEnergyErrorJkg = kineticEnergyErrorJkg;
      this.driftResidualMs = driftResidualMs;
      this.driftVelocityMs = driftVelocityMs;
      this.slipRatio = slipRatio;
      this.surfaceTensionNm = surfaceTensionNm;
    }
  }
}
