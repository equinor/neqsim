package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import neqsim.process.engineering.SafetyFunctionDesign;

/** Seeded uncertainty propagation around the analytical {@link SafetyFunctionDesign} reliability screen. */
public final class SafetyFunctionReliabilityStudy {
  private SafetyFunctionReliabilityStudy() {
  }

  /** Non-negative triangular distribution used for reliability inputs and multipliers. */
  public static final class TriangularDistribution implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double low;
    private final double mode;
    private final double high;

    private TriangularDistribution(double low, double mode, double high) {
      if (!Double.isFinite(low) || !Double.isFinite(mode) || !Double.isFinite(high) || low < 0.0 || low > mode
          || mode > high) {
        throw new IllegalArgumentException("triangular distribution requires 0 <= low <= mode <= high");
      }
      this.low = low;
      this.mode = mode;
      this.high = high;
    }

    public static TriangularDistribution of(double low, double mode, double high) {
      return new TriangularDistribution(low, mode, high);
    }

    public static TriangularDistribution constant(double value) {
      return new TriangularDistribution(value, value, value);
    }

    double sample(Random random) {
      if (low == high) {
        return low;
      }
      double u = random.nextDouble();
      double fractionAtMode = (mode - low) / (high - low);
      if (u <= fractionAtMode) {
        return low + Math.sqrt(u * (high - low) * (mode - low));
      }
      return high - Math.sqrt((1.0 - u) * (high - low) * (high - mode));
    }

    boolean isStrictlyPositive() {
      return low > 0.0;
    }

    boolean isFraction() {
      return high <= 1.0;
    }

    Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("distribution", "TRIANGULAR");
      map.put("low", Double.valueOf(low));
      map.put("mode", Double.valueOf(mode));
      map.put("high", Double.valueOf(high));
      return map;
    }
  }

  /** Uncertainty definition for one named SIF subsystem. */
  public static final class SubsystemUncertainty implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String subsystemName;
    private TriangularDistribution failureRateFactor = TriangularDistribution.constant(1.0);
    private TriangularDistribution diagnosticCoverage;
    private TriangularDistribution proofTestIntervalFactor = TriangularDistribution.constant(1.0);
    private TriangularDistribution repairTimeFactor = TriangularDistribution.constant(1.0);
    private TriangularDistribution betaFactor;
    private TriangularDistribution bypassProbability;

    public SubsystemUncertainty(String subsystemName) {
      if (subsystemName == null || subsystemName.trim().isEmpty()) {
        throw new IllegalArgumentException("subsystemName must not be blank");
      }
      this.subsystemName = subsystemName.trim();
    }

    public SubsystemUncertainty setFailureRateFactor(TriangularDistribution value) {
      failureRateFactor = requirePositiveDistribution(value, "failureRateFactor");
      return this;
    }

    public SubsystemUncertainty setDiagnosticCoverage(TriangularDistribution value) {
      diagnosticCoverage = requireFractionDistribution(value, "diagnosticCoverage");
      return this;
    }

    public SubsystemUncertainty setProofTestIntervalFactor(TriangularDistribution value) {
      proofTestIntervalFactor = requirePositiveDistribution(value, "proofTestIntervalFactor");
      return this;
    }

    public SubsystemUncertainty setRepairTimeFactor(TriangularDistribution value) {
      repairTimeFactor = requirePositiveDistribution(value, "repairTimeFactor");
      return this;
    }

    public SubsystemUncertainty setBetaFactor(TriangularDistribution value) {
      betaFactor = requireFractionDistribution(value, "betaFactor");
      return this;
    }

    public SubsystemUncertainty setBypassProbability(TriangularDistribution value) {
      bypassProbability = requireFractionDistribution(value, "bypassProbability");
      return this;
    }

    private SafetyFunctionDesign.Subsystem sample(SafetyFunctionDesign.Subsystem subsystem, Random random) {
      double coverage = diagnosticCoverage == null ? subsystem.getDiagnosticCoverage()
          : diagnosticCoverage.sample(random);
      double beta = betaFactor == null ? subsystem.getBetaFactor() : betaFactor.sample(random);
      double bypass = bypassProbability == null ? subsystem.getBypassProbability()
          : bypassProbability.sample(random);
      return subsystem.copyWithReliabilityInputs(
          subsystem.getDangerousFailureRatePerHour() * failureRateFactor.sample(random), coverage,
          subsystem.getProofTestIntervalHours() * proofTestIntervalFactor.sample(random),
          subsystem.getMeanRepairTimeHours() * repairTimeFactor.sample(random), beta, bypass);
    }

    private Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("subsystemName", subsystemName);
      map.put("failureRateFactor", failureRateFactor.toMap());
      map.put("diagnosticCoverage", diagnosticCoverage == null ? "DESIGN_VALUE" : diagnosticCoverage.toMap());
      map.put("proofTestIntervalFactor", proofTestIntervalFactor.toMap());
      map.put("repairTimeFactor", repairTimeFactor.toMap());
      map.put("betaFactor", betaFactor == null ? "DESIGN_VALUE" : betaFactor.toMap());
      map.put("bypassProbability", bypassProbability == null ? "DESIGN_VALUE" : bypassProbability.toMap());
      return map;
    }
  }

  /** Immutable Monte Carlo reliability result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String sifTag;
    private final SafetyFunctionDesign.DemandMode demandMode;
    private final int targetSil;
    private final int iterations;
    private final long seed;
    private final double deterministicPfdAverage;
    private final double deterministicPfh;
    private final double p10PfdAverage;
    private final double p50PfdAverage;
    private final double p90PfdAverage;
    private final double p10Pfh;
    private final double p50Pfh;
    private final double p90Pfh;
    private final double targetMetProbability;
    private final List<Map<String, Object>> uncertaintyDefinitions;

    private Result(SafetyFunctionDesign design, int iterations, long seed, double[] pfdSamples,
        double[] pfhSamples, int targetMetCount, List<Map<String, Object>> uncertaintyDefinitions) {
      sifTag = design.getSifTag();
      demandMode = design.getDemandMode();
      targetSil = design.getTargetSil();
      this.iterations = iterations;
      this.seed = seed;
      deterministicPfdAverage = design.calculatePfdAverage();
      deterministicPfh = design.calculatePfh();
      p10PfdAverage = percentile(pfdSamples, 0.10);
      p50PfdAverage = percentile(pfdSamples, 0.50);
      p90PfdAverage = percentile(pfdSamples, 0.90);
      p10Pfh = percentile(pfhSamples, 0.10);
      p50Pfh = percentile(pfhSamples, 0.50);
      p90Pfh = percentile(pfhSamples, 0.90);
      targetMetProbability = ((double) targetMetCount) / iterations;
      this.uncertaintyDefinitions = Collections
          .unmodifiableList(new ArrayList<Map<String, Object>>(uncertaintyDefinitions));
    }

    public double getP10PfdAverage() {
      return p10PfdAverage;
    }

    public double getP50PfdAverage() {
      return p50PfdAverage;
    }

    public double getP90PfdAverage() {
      return p90PfdAverage;
    }

    public double getP10Pfh() {
      return p10Pfh;
    }

    public double getP50Pfh() {
      return p50Pfh;
    }

    public double getP90Pfh() {
      return p90Pfh;
    }

    public double getDeterministicPfdAverage() {
      return deterministicPfdAverage;
    }

    public double getDeterministicPfh() {
      return deterministicPfh;
    }

    public int getIterations() {
      return iterations;
    }

    public long getSeed() {
      return seed;
    }

    public double getTargetMetProbability() {
      return targetMetProbability;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "sif_reliability_uncertainty.v1");
      map.put("sifTag", sifTag);
      map.put("demandMode", demandMode.name());
      map.put("targetSil", Integer.valueOf(targetSil));
      map.put("iterations", Integer.valueOf(iterations));
      map.put("seed", Long.valueOf(seed));
      map.put("deterministicPfdAverage", Double.valueOf(deterministicPfdAverage));
      map.put("deterministicPfhPerHour", Double.valueOf(deterministicPfh));
      map.put("p10PfdAverage", Double.valueOf(p10PfdAverage));
      map.put("p50PfdAverage", Double.valueOf(p50PfdAverage));
      map.put("p90PfdAverage", Double.valueOf(p90PfdAverage));
      map.put("p10PfhPerHour", Double.valueOf(p10Pfh));
      map.put("p50PfhPerHour", Double.valueOf(p50Pfh));
      map.put("p90PfhPerHour", Double.valueOf(p90Pfh));
      map.put("targetMetProbability", Double.valueOf(targetMetProbability));
      map.put("uncertaintyDefinitions", uncertaintyDefinitions);
      map.put("silTargetInferred", Boolean.FALSE);
      map.put("engineeringApprovalRequired", Boolean.TRUE);
      map.put("method", "SEEDED_TRIANGULAR_MONTE_CARLO_AROUND_SAFETY_FUNCTION_DESIGN");
      return map;
    }
  }

  /**
   * Runs a seeded uncertainty study.
   *
   * @param design analytical SIF design
   * @param uncertainties subsystem uncertainty definitions
   * @param iterations number of samples, at least 100
   * @param seed reproducibility seed
   * @return percentile and target-probability evidence
   */
  public static Result run(SafetyFunctionDesign design, List<SubsystemUncertainty> uncertainties, int iterations,
      long seed) {
    if (design == null || uncertainties == null) {
      throw new IllegalArgumentException("design and uncertainties are required");
    }
    if (iterations < 100) {
      throw new IllegalArgumentException("iterations must be at least 100");
    }
    Map<String, SubsystemUncertainty> definitions = index(design, uncertainties);
    List<Map<String, Object>> definitionMaps = new ArrayList<Map<String, Object>>();
    for (SubsystemUncertainty uncertainty : uncertainties) {
      definitionMaps.add(uncertainty.toMap());
    }
    Random random = new Random(seed);
    double[] pfdSamples = new double[iterations];
    double[] pfhSamples = new double[iterations];
    int targetMetCount = 0;
    for (int sampleIndex = 0; sampleIndex < iterations; sampleIndex++) {
      double success = 1.0;
      double pfh = 0.0;
      for (SafetyFunctionDesign.Subsystem subsystem : design.getSubsystems()) {
        SubsystemUncertainty uncertainty = definitions.get(subsystem.getName());
        SafetyFunctionDesign.Subsystem sampled = uncertainty == null ? subsystem
            : uncertainty.sample(subsystem, random);
        success *= 1.0 - sampled.calculatePfdAverage();
        pfh += sampled.calculatePfh();
      }
      pfdSamples[sampleIndex] = 1.0 - success;
      pfhSamples[sampleIndex] = pfh;
      if (achievedSil(design.getDemandMode(), pfdSamples[sampleIndex], pfh) >= design.getTargetSil()) {
        targetMetCount++;
      }
    }
    return new Result(design, iterations, seed, pfdSamples, pfhSamples, targetMetCount, definitionMaps);
  }

  private static Map<String, SubsystemUncertainty> index(SafetyFunctionDesign design,
      List<SubsystemUncertainty> uncertainties) {
    Map<String, SafetyFunctionDesign.Subsystem> designSubsystems = new LinkedHashMap<String, SafetyFunctionDesign.Subsystem>();
    for (SafetyFunctionDesign.Subsystem subsystem : design.getSubsystems()) {
      designSubsystems.put(subsystem.getName(), subsystem);
    }
    Map<String, SubsystemUncertainty> result = new LinkedHashMap<String, SubsystemUncertainty>();
    for (SubsystemUncertainty uncertainty : uncertainties) {
      if (uncertainty == null || !designSubsystems.containsKey(uncertainty.subsystemName)) {
        throw new IllegalArgumentException("uncertainty references an unknown subsystem");
      }
      if (result.put(uncertainty.subsystemName, uncertainty) != null) {
        throw new IllegalArgumentException("duplicate uncertainty for " + uncertainty.subsystemName);
      }
    }
    return result;
  }

  private static int achievedSil(SafetyFunctionDesign.DemandMode demandMode, double pfd, double pfh) {
    double measure = demandMode == SafetyFunctionDesign.DemandMode.LOW_DEMAND ? pfd : pfh;
    double firstLimit = demandMode == SafetyFunctionDesign.DemandMode.LOW_DEMAND ? 1.0e-1 : 1.0e-5;
    if (measure <= 0.0 || measure >= firstLimit) {
      return 0;
    }
    double[] limits = demandMode == SafetyFunctionDesign.DemandMode.LOW_DEMAND
        ? new double[] {1.0e-2, 1.0e-3, 1.0e-4, 1.0e-5}
        : new double[] {1.0e-6, 1.0e-7, 1.0e-8, 1.0e-9};
    for (int sil = 1; sil <= limits.length; sil++) {
      if (measure >= limits[sil - 1]) {
        return sil;
      }
    }
    return demandMode == SafetyFunctionDesign.DemandMode.LOW_DEMAND || measure >= 1.0e-9 ? 4 : 0;
  }

  private static double percentile(double[] values, double fraction) {
    double[] sorted = values.clone();
    java.util.Arrays.sort(sorted);
    double index = fraction * (sorted.length - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);
    if (lower == upper) {
      return sorted[lower];
    }
    return sorted[lower] + (index - lower) * (sorted[upper] - sorted[lower]);
  }

  private static TriangularDistribution requirePositiveDistribution(TriangularDistribution value, String name) {
    if (value == null || !value.isStrictlyPositive()) {
      throw new IllegalArgumentException(name + " must be strictly positive");
    }
    return value;
  }

  private static TriangularDistribution requireFractionDistribution(TriangularDistribution value, String name) {
    if (value == null || !value.isFraction()) {
      throw new IllegalArgumentException(name + " must remain in [0, 1]");
    }
    return value;
  }
}
