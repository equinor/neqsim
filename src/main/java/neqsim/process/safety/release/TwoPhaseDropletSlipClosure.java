package neqsim.process.safety.release;

import java.util.Map;
import java.util.TreeMap;

/**
 * Bounded prescribed-slip closure for spherical liquid droplets dispersed in a continuous gas.
 *
 * <p>
 * The caller supplies the droplet Sauter mean diameter and the gas-to-droplet relative speed. The closure solves the
 * two positive phase velocities that retain the homogeneous-state kinetic energy, then derives phase areas and mass
 * flux from continuity. It accepts only dilute spherical droplets with {@code Eo <= 4}, {@code We <= 12}, and liquid
 * area fraction at most 0.20. It does not predict droplet size, slip, breakup, coalescence, entrainment, or annular
 * flow.
 *
 * @author esol
 * @version 1.0
 */
final class TwoPhaseDropletSlipClosure {
  private static final double GRAVITY_MS2 = 9.80665;
  private static final double MAX_EOTVOS_NUMBER = 4.0;
  private static final double MAX_WEBER_NUMBER = 12.0;
  private static final double MAX_LIQUID_AREA_FRACTION = 0.20;

  /** Utility class. */
  private TwoPhaseDropletSlipClosure() {
  }

  /**
   * Solves the prescribed liquid-droplet-in-gas phase velocities and area fractions.
   *
   * @param reference thermodynamic state with exactly one gas and one liquid phase
   * @param surfaceTensionNm caller-declared interfacial tension in N/m
   * @param dropletDiameterM caller-declared Sauter mean diameter in m
   * @param relativeVelocityMs caller-declared gas-to-droplet relative speed in m/s
   * @return immutable checked closure result
   */
  static Result solve(ReleaseState reference, double surfaceTensionNm, double dropletDiameterM,
      double relativeVelocityMs) {
    ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    ReleaseFlowRequest.positive(dropletDiameterM, "dropletDiameterM");
    ReleaseFlowRequest.positive(relativeVelocityMs, "relativeVelocityMs");
    PhaseBasis basis = phaseBasis(reference);
    double homogeneousVelocity = ReleaseFlowRequest.positive(reference.getVelocityMs(), "homogeneous velocity");
    double radicand = homogeneousVelocity * homogeneousVelocity
        - basis.gasMassFraction * basis.liquidMassFraction * relativeVelocityMs * relativeVelocityMs;
    if (!(radicand > 0.0)) {
      throw new UnsupportedOperationException(
          "Declared droplet slip exceeds the kinetic-energy available at the opening");
    }
    double root = Math.sqrt(radicand);
    double gasVelocity = basis.liquidMassFraction * relativeVelocityMs + root;
    double liquidVelocity = gasVelocity - relativeVelocityMs;
    if (!(liquidVelocity > 0.0)) {
      throw new UnsupportedOperationException("Declared droplet slip produces non-forward liquid-droplet velocity");
    }
    double specificArea = basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity)
        + basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double massFlux = ReleaseFlowRequest.positive(1.0 / specificArea, "droplet-slip mass flux");
    double gasAreaFraction = massFlux * basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity);
    double liquidAreaFraction = massFlux * basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double phaseAreaSum = gasAreaFraction + liquidAreaFraction;
    double phaseKineticEnergy = 0.5 * (basis.gasMassFraction * gasVelocity * gasVelocity
        + basis.liquidMassFraction * liquidVelocity * liquidVelocity);
    double homogeneousKineticEnergy = 0.5 * homogeneousVelocity * homogeneousVelocity;
    double kineticEnergyError = phaseKineticEnergy - homogeneousKineticEnergy;
    double eotvos = GRAVITY_MS2 * (basis.liquidDensityKgM3 - basis.gasDensityKgM3) * dropletDiameterM * dropletDiameterM
        / surfaceTensionNm;
    double weber = basis.gasDensityKgM3 * relativeVelocityMs * relativeVelocityMs * dropletDiameterM / surfaceTensionNm;
    if (liquidAreaFraction > MAX_LIQUID_AREA_FRACTION) {
      throw new UnsupportedOperationException("Predicted liquid-droplet area fraction " + liquidAreaFraction
          + " exceeds the dilute-dispersion limit " + MAX_LIQUID_AREA_FRACTION);
    }
    if (eotvos > MAX_EOTVOS_NUMBER) {
      throw new UnsupportedOperationException(
          "Droplet Eotvos number " + eotvos + " exceeds the spherical-droplet limit " + MAX_EOTVOS_NUMBER);
    }
    if (weber > MAX_WEBER_NUMBER) {
      throw new UnsupportedOperationException(
          "Droplet Weber number " + weber + " exceeds the bounded non-breakup limit " + MAX_WEBER_NUMBER);
    }
    if (!Double.isFinite(phaseAreaSum) || Math.abs(phaseAreaSum - 1.0) > 1e-10
        || Math.abs(kineticEnergyError) > 1e-9 * Math.max(1.0, homogeneousKineticEnergy)) {
      throw new IllegalStateException("Droplet phase-area or kinetic-energy closure failed");
    }
    Map<String, Double> velocities = new TreeMap<String, Double>();
    velocities.put("GAS", gasVelocity);
    velocities.put(basis.liquidPhase, liquidVelocity);
    double bulkVelocity = massFlux / reference.getDensityKgM3();
    return new Result(reference.withPhaseVelocities(bulkVelocity, velocities), massFlux, gasAreaFraction,
        liquidAreaFraction, phaseAreaSum, kineticEnergyError, eotvos, weber, relativeVelocityMs, surfaceTensionNm,
        dropletDiameterM);
  }

  /**
   * Validates and extracts the one-gas/one-liquid basis.
   *
   * @param reference state to validate
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
          throw new UnsupportedOperationException("Unsupported droplet phase: " + phase);
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
      throw new UnsupportedOperationException("Droplet closure requires liquid density greater than gas density");
    }
    return new PhaseBasis(liquid, gasMassFraction, liquidMassFraction, gasDensity, liquidDensity);
  }

  /** @return maximum accepted dispersed-liquid area fraction */
  static double getMaximumLiquidAreaFraction() {
    return MAX_LIQUID_AREA_FRACTION;
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

  /** Immutable closure result. */
  static final class Result {
    final ReleaseState state;
    final double massFluxKgM2s;
    final double gasAreaFraction;
    final double liquidAreaFraction;
    final double phaseAreaSum;
    final double kineticEnergyErrorJkg;
    final double eotvosNumber;
    final double weberNumber;
    final double relativeVelocityMs;
    final double surfaceTensionNm;
    final double dropletDiameterM;

    /**
     * Creates a checked result.
     *
     * @param state state carrying phase velocities
     * @param massFluxKgM2s mass flux in kg/(m2 s)
     * @param gasAreaFraction gas area fraction
     * @param liquidAreaFraction liquid-droplet area fraction
     * @param phaseAreaSum sum of phase area fractions
     * @param kineticEnergyErrorJkg kinetic-energy closure error in J/kg
     * @param eotvosNumber droplet Eotvos number
     * @param weberNumber droplet Weber number
     * @param relativeVelocityMs gas-to-droplet relative speed in m/s
     * @param surfaceTensionNm interfacial tension in N/m
     * @param dropletDiameterM Sauter mean diameter in m
     */
    private Result(ReleaseState state, double massFluxKgM2s, double gasAreaFraction, double liquidAreaFraction,
        double phaseAreaSum, double kineticEnergyErrorJkg, double eotvosNumber, double weberNumber,
        double relativeVelocityMs, double surfaceTensionNm, double dropletDiameterM) {
      this.state = state;
      this.massFluxKgM2s = massFluxKgM2s;
      this.gasAreaFraction = gasAreaFraction;
      this.liquidAreaFraction = liquidAreaFraction;
      this.phaseAreaSum = phaseAreaSum;
      this.kineticEnergyErrorJkg = kineticEnergyErrorJkg;
      this.eotvosNumber = eotvosNumber;
      this.weberNumber = weberNumber;
      this.relativeVelocityMs = relativeVelocityMs;
      this.surfaceTensionNm = surfaceTensionNm;
      this.dropletDiameterM = dropletDiameterM;
    }
  }
}
