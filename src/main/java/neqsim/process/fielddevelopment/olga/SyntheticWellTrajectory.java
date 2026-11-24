package neqsim.process.fielddevelopment.olga;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Generates a synthetic (not a specific well's real) wellbore deviation trajectory -- (MD, TVD) station arrays -- for
 * use when no real Prosper model, SMDA record, or other real trajectory source exists for a well, interpolated from a
 * caller-supplied, depth-dependent statistics basis rather than a single generic textbook default or a bare vertical
 * pipe.
 *
 * <h2>Why this exists</h2>
 * <p>
 * A well's wellbore geometry materially affects an OLGA transient multiphase result (slugging, terrain-induced liquid
 * accumulation are deviation-sensitive), so assuming a purely vertical well when no real survey exists is not a neutral
 * simplification -- it can be a materially worse approximation than a realistic deviated profile. Real well populations
 * typically show build rate and kickoff depth varying materially with total depth, so a single fixed "representative"
 * well shape is usually a poor match for the real population; this class instead interpolates a depth-dependent build
 * rate/kickoff-point from three depth tiers (shallow/medium/deep) that the caller supplies, rather than hardcoding one
 * fixed shape or any field-derived numbers in this public library itself.
 * </p>
 *
 * <h2>Data governance -- no statistics are embedded in this class</h2>
 * <p>
 * This class deliberately ships with <strong>no default {@link Statistics} basis and no embedded field/well
 * data</strong>. {@code neqsim} is a public, open-source repository; the depth-tier statistics a real caller would use
 * (derived from a real well population) may be governed by that organization's own data-handling rules and must not be
 * published here. Every caller MUST construct and supply its own {@link Statistics} (three {@link DepthTier} instances
 * plus a default max inclination, optionally with its own {@code basisDescription} provenance string) from whatever
 * internally governed data source it has rights to use -- see this class's own {@link Statistics} and {@link DepthTier}
 * Javadoc. A small, clearly-labeled example basis for experimentation is provided only in this class's own unit tests
 * (round, invented numbers -- not derived from any real well).
 * </p>
 *
 * <h2>What this is NOT</h2>
 * <ul>
 * <li>NOT a real well's actual trajectory. Every genkey or report built from this class's output MUST disclose that
 * plainly -- see {@link #syntheticTrajectoryWarning(Result)}, never silently presented as real geometry.</li>
 * <li>NOT a substitute for a real planned/as-drilled trajectory (SMDA, a real Prosper deviation survey, or a real
 * drilling-engineering well-planning tool) when one is available. Always prefer real data; use this class only when no
 * real trajectory source exists for the well in question.</li>
 * <li>NOT a full well-planning tool. It builds a simple, standard "build and hold" (or "build only", when the target is
 * reached before completing the full build) analytic circular-arc profile -- it does not model doglegs, S-shaped
 * profiles, or multi-target wells.</li>
 * </ul>
 *
 * <h2>Method</h2>
 * <p>
 * Standard "radius of curvature" analytic circular-arc geometry (unit-weight textbook directional-drilling convention):
 * </p>
 *
 * <pre>
 *     bRadPerM = radians(buildRateDegPer30m) / 30    // curvature
 *     radiusM  = 1 / bRadPerM                          // radius (m)
 *     md(inc)  = kopMd + radiusM * inc                 // along the arc
 *     tvd(inc) = kopMd + radiusM * sin(inc)             // along the arc
 * </pre>
 *
 * <p>
 * applied in up to three sections: a vertical section (0 to KOP, where MD == TVD), a build section (circular arc from 0
 * to either the statistics basis's typical max inclination or a lower inclination if the target TVD is reached first),
 * and -- only if the target TVD has NOT yet been reached at the end of the full build -- a straight tangent/hold
 * section at constant inclination for the remaining TVD. An optional reservoir-contact length then extends the well
 * further, at the terminal inclination, WITHOUT requiring further net TVD gain -- this mirrors a pattern commonly
 * observed when characterizing a real well population: the full build arc alone often already reaches a depth tier's
 * own approximate target TVD well before exhausting that tier's own typical max inclination, meaning a real well's
 * extra MD beyond the build arc is often best modeled as a reservoir-contact/lateral section, not as additional
 * vertical-depth-seeking tangent length.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see neqsim.process.fielddevelopment.olga.OlgaWellGenkeyExporter
 */
public final class SyntheticWellTrajectory {
  /**
   * Warning prefix mirroring {@code well-inflow-pi}'s own {@code [CHOKE WARNING]}/{@code [GEOMETRY WARNING]}
   * convention.
   */
  public static final String SYNTHETIC_TRAJECTORY_WARNING_PREFIX = "[SYNTHETIC TRAJECTORY WARNING]";

  /**
   * Inclination is capped below 90 degrees to keep cos(inclination) bounded away from zero in the tangent-section
   * formula ({@code md = remainingTvd / cos(inclination)}) -- an uncapped 90 degrees (or a caller-supplied value at or
   * above it) would make that division blow up or divide by zero. 89.0 degrees still represents an effectively
   * horizontal hold section (over 99.98% of unit length is still horizontal travel) while keeping the formula
   * numerically well-behaved.
   */
  private static final double MAX_SAFE_INCLINATION_DEG = 89.0;

  /**
   * A tangent/hold section longer than this, relative to the target TVD itself, is flagged as an explicit warning
   * rather than silently returned -- it usually means the max-inclination/build-rate combination in use is a poor fit
   * for this particular target TVD, not a normal result.
   */
  private static final double LONG_TANGENT_WARNING_RATIO = 3.0;

  /** One depth tier's own mean statistics, supplied by the caller (see this class's own "Data governance" Javadoc). */
  public static final class DepthTier {
    /** Tier label ("shallow", "medium", or "deep"). */
    public final String tier;
    /** Number of wells sampled in this tier (informational only; not used in the geometry calculation). */
    public final int n;
    /** Mean build rate for this tier, degrees per 30 m. */
    public final double meanBuildRateDegPer30m;
    /** Mean kickoff-point MD for this tier, meters. */
    public final double meanKopMdMetre;
    /** Approximate mean total TVD for this tier, meters (interpolation x-axis). */
    public final double approxMeanTotalTvdMetre;

    /**
     * Constructor for DepthTier.
     *
     * @param tier tier label (e.g. "shallow", "medium", "deep")
     * @param n number of wells sampled in this tier (informational/provenance only)
     * @param meanBuildRateDegPer30m mean build rate for this tier, degrees per 30 m
     * @param meanKopMdMetre mean kickoff-point MD for this tier, meters
     * @param approxMeanTotalTvdMetre approximate mean total TVD for this tier, meters (interpolation x-axis)
     */
    public DepthTier(String tier, int n, double meanBuildRateDegPer30m, double meanKopMdMetre,
        double approxMeanTotalTvdMetre) {
      this.tier = tier;
      this.n = n;
      this.meanBuildRateDegPer30m = meanBuildRateDegPer30m;
      this.meanKopMdMetre = meanKopMdMetre;
      this.approxMeanTotalTvdMetre = approxMeanTotalTvdMetre;
    }
  }

  /**
   * A depth-tier statistics basis -- the depth tiers, default max inclination, and a human-readable provenance
   * description together, mirroring the ported Python module's own {@code statistics=} parameter (an already-loaded
   * statistics dict). This class has <strong>no built-in instance</strong> -- every caller supplies its own, from
   * whatever internally governed data source it has rights to use; see this class's own "Data governance" Javadoc
   * section.
   */
  public static final class Statistics {
    /** The depth tiers to interpolate against (normally exactly three: shallow/medium/deep). */
    public final DepthTier[] depthTiers;
    /** The default max inclination (degrees) used when no override is given. */
    public final double defaultMaxInclinationDeg;
    /**
     * Human-readable provenance text for this basis, surfaced via {@link Result#basis} -- e.g. a genkey header comment.
     * Must not be left generic/misleading about the data's real nature; see
     * {@link #syntheticTrajectoryWarning(Result)}.
     */
    public final String basisDescription;

    /**
     * Constructor for Statistics.
     *
     * @param depthTiers the depth tiers to interpolate against
     * @param defaultMaxInclinationDeg the default max inclination (degrees) used when no override is given
     * @param basisDescription human-readable provenance text for this basis (surfaced via {@link Result#basis})
     */
    public Statistics(DepthTier[] depthTiers, double defaultMaxInclinationDeg, String basisDescription) {
      this.depthTiers = depthTiers;
      this.defaultMaxInclinationDeg = defaultMaxInclinationDeg;
      this.basisDescription = basisDescription;
    }
  }

  /** The specific interpolated build rate/KOP/max-inclination values a {@link Result} actually used. */
  public static final class TierInterpolation {
    /** Interpolated build rate, degrees per 30 m. */
    public final double buildRateDegPer30m;
    /** Interpolated kickoff-point MD, meters. */
    public final double kopMdMetre;
    /** Effective (possibly capped) max inclination, degrees. */
    public final double maxInclinationDeg;

    private TierInterpolation(double buildRateDegPer30m, double kopMdMetre, double maxInclinationDeg) {
      this.buildRateDegPer30m = buildRateDegPer30m;
      this.kopMdMetre = kopMdMetre;
      this.maxInclinationDeg = maxInclinationDeg;
    }
  }

  /** The generated trajectory and everything a caller needs to interpret or disclose it. */
  public static final class Result {
    /** MD station values (m), ascending. */
    public final double[] md;
    /** TVD station values (m), ascending (same convention as a real Prosper deviation survey). */
    public final double[] tvd;
    /** Always {@code "synthetic_template"} -- never confuse this with real survey data. */
    public final String provenance;
    /** Human-readable string citing the statistics basis, suitable for a genkey header comment. */
    public final String basis;
    /** The actual interpolated build rate/KOP/max-inclination values used. */
    public final TierInterpolation tierInterpolation;
    /** Warning strings (empty if none), each prefixed with {@link #SYNTHETIC_TRAJECTORY_WARNING_PREFIX}. */
    public final List<String> warnings;

    private Result(double[] md, double[] tvd, String basis, TierInterpolation tierInterpolation,
        List<String> warnings) {
      this.md = md;
      this.tvd = tvd;
      this.provenance = "synthetic_template";
      this.basis = basis;
      this.tierInterpolation = tierInterpolation;
      this.warnings = warnings;
    }
  }

  /**
   * Raised for a caller input this class refuses to build a trajectory from (e.g. a non-positive target depth), as
   * opposed to a condition that is handled with a documented fallback and a warning.
   */
  public static class SyntheticTrajectoryException extends IllegalArgumentException {
    private static final long serialVersionUID = 1000L;

    public SyntheticTrajectoryException(String message) {
      super(message);
    }
  }

  private SyntheticWellTrajectory() {
    // Utility class -- static methods only, matching the ported Python module's own module-level functions.
  }

  /**
   * Generate a synthetic (MD, TVD) wellbore trajectory for a given target TVD, with no reservoir-contact extension and
   * the statistics basis's own default max inclination. See
   * {@link #generateSyntheticTrajectory(double, double, Double, int, Statistics)} for the full set of options.
   *
   * @param targetTvdMetre target true vertical depth (m) the well must reach, must be positive
   * @param statistics the depth-tier statistics basis to interpolate against -- every caller must supply its own; see
   * this class's own "Data governance" Javadoc section
   * @return the generated trajectory
   * @throws SyntheticTrajectoryException if targetTvdMetre is not positive
   */
  public static Result generateSyntheticTrajectory(double targetTvdMetre, Statistics statistics) {
    return generateSyntheticTrajectory(targetTvdMetre, 0.0, null, 8, statistics);
  }

  /**
   * Generate a synthetic (MD, TVD) wellbore trajectory for a given target TVD, grounded in a caller-supplied depth-tier
   * statistics basis -- see this class's own Javadoc for the full method and rationale.
   *
   * @param targetTvdMetre target true vertical depth (m) the well must reach (e.g. the reservoir datum depth); must be
   * positive
   * @param reservoirContactLengthMetre optional additional MD (m) to extend the well beyond reaching
   * {@code targetTvdMetre}, at the terminal inclination, representing a reservoir-contact/lateral section. Zero means
   * stop exactly at {@code targetTvdMetre}. This is a SEPARATE concern from reaching {@code targetTvdMetre} itself --
   * see this class's own Javadoc for the reasoning. Must be zero or greater.
   * @param maxInclinationDegOverride optional override for the maximum build inclination (degrees); {@code null}
   * defaults to {@code statistics}'s own {@link Statistics#defaultMaxInclinationDeg}. Capped at
   * {@link #MAX_SAFE_INCLINATION_DEG} regardless of the value given.
   * @param buildSectionNumStations number of (MD, TVD) stations to emit across the build section (including both
   * endpoints, minimum 2). Higher values give a smoother arc at the cost of a larger array.
   * @param statistics the depth-tier statistics basis to interpolate against. This class ships with no built-in default
   * -- every caller must supply its own, derived from whatever internally governed data source it has rights to use
   * (production use), or a small fixture (testing); see this class's own "Data governance" Javadoc section and the
   * ported Python module's own {@code statistics=} parameter, which this mirrors.
   * @return the generated trajectory. May be a purely vertical profile (MD == TVD throughout, with an explanatory
   * warning in {@link Result#warnings}) rather than a curved one, if {@code targetTvdMetre} is shallower than (or equal
   * to) the depth tier's own interpolated kickoff-point depth -- that is the physically correct result in that case,
   * not an error; always inspect {@link Result#warnings} rather than assuming a curved profile.
   * @throws SyntheticTrajectoryException if targetTvdMetre is not positive, or reservoirContactLengthMetre is negative
   */
  public static Result generateSyntheticTrajectory(double targetTvdMetre, double reservoirContactLengthMetre,
      Double maxInclinationDegOverride, int buildSectionNumStations, Statistics statistics) {
    if (targetTvdMetre <= 0.0) {
      throw new SyntheticTrajectoryException("targetTvdMetre must be positive, got " + targetTvdMetre);
    }
    if (reservoirContactLengthMetre < 0.0) {
      throw new SyntheticTrajectoryException(
          "reservoirContactLengthMetre must be >= 0, got " + reservoirContactLengthMetre);
    }

    List<String> warnings = new ArrayList<>();

    double requestedMaxInclinationDeg = maxInclinationDegOverride != null ? maxInclinationDegOverride
        : statistics.defaultMaxInclinationDeg;
    double effectiveMaxInclinationDeg = Math.min(requestedMaxInclinationDeg, MAX_SAFE_INCLINATION_DEG);
    if (effectiveMaxInclinationDeg < requestedMaxInclinationDeg) {
      warnings.add(String.format(Locale.ROOT,
          "%s max_inclination_deg %s was capped at %s degrees to keep the tangent-section geometry numerically "
              + "well-behaved (see MAX_SAFE_INCLINATION_DEG's own comment for why).",
          SYNTHETIC_TRAJECTORY_WARNING_PREFIX, requestedMaxInclinationDeg, MAX_SAFE_INCLINATION_DEG));
    }

    double buildRate = interpolateTierMetric(targetTvdMetre, statistics.depthTiers,
        tier -> tier.meanBuildRateDegPer30m);
    double kopMd = interpolateTierMetric(targetTvdMetre, statistics.depthTiers, tier -> tier.meanKopMdMetre);

    String basis = statistics.basisDescription;
    TierInterpolation tierInterpolation = new TierInterpolation(buildRate, kopMd, effectiveMaxInclinationDeg);

    // Fallback: the interpolated KOP is at or beyond the target depth itself (a shallow objective, relative to
    // this depth tier's typical kickoff) -- a vertical well straight to targetTvdMetre is the correct, physically
    // sensible result here, not an error.
    if (kopMd >= targetTvdMetre || buildRate <= 0.0) {
      warnings.add(String.format(Locale.ROOT,
          "%s interpolated KOP depth (%.1f m) is at or beyond the target TVD (%.1f m), or the interpolated build "
              + "rate was non-positive -- falling back to a purely vertical synthetic trajectory rather than an "
              + "invalid deviated one.",
          SYNTHETIC_TRAJECTORY_WARNING_PREFIX, kopMd, targetTvdMetre));
      List<Double> md = new ArrayList<>(java.util.Arrays.asList(0.0, targetTvdMetre));
      List<Double> tvd = new ArrayList<>(java.util.Arrays.asList(0.0, targetTvdMetre));
      if (reservoirContactLengthMetre > 0.0) {
        md.add(targetTvdMetre + reservoirContactLengthMetre);
        tvd.add(targetTvdMetre + reservoirContactLengthMetre);
      }
      return new Result(toArray(md), toArray(tvd), basis, tierInterpolation, warnings);
    }

    double bRadPerM = Math.toRadians(buildRate) / 30.0;
    double radiusM = 1.0 / bRadPerM;
    double incMaxRad = Math.toRadians(effectiveMaxInclinationDeg);

    double tvdAtFullBuild = kopMd + radiusM * Math.sin(incMaxRad);

    List<Double> md = new ArrayList<>(java.util.Arrays.asList(0.0, kopMd));
    List<Double> tvd = new ArrayList<>(java.util.Arrays.asList(0.0, kopMd));
    double terminalInclinationRad;

    if (tvdAtFullBuild >= targetTvdMetre) {
      // Target TVD is reached WITHIN the build arc -- solve for the inclination at which TVD == targetTvdMetre,
      // build only to there, no tangent/hold section needed.
      double sinIncPartial = (targetTvdMetre - kopMd) / radiusM;
      sinIncPartial = Math.max(-1.0, Math.min(1.0, sinIncPartial));
      double incPartialRad = Math.asin(sinIncPartial);
      double[][] buildStations = buildArcStations(kopMd, radiusM, incPartialRad, buildSectionNumStations);
      appendTailExcludingFirst(md, buildStations[0]);
      appendTailExcludingFirst(tvd, buildStations[1]);
      terminalInclinationRad = incPartialRad;
    } else {
      // Target TVD requires the FULL build arc plus a straight tangent/hold section at the (capped) max
      // inclination.
      double[][] buildStations = buildArcStations(kopMd, radiusM, incMaxRad, buildSectionNumStations);
      appendTailExcludingFirst(md, buildStations[0]);
      appendTailExcludingFirst(tvd, buildStations[1]);

      double remainingTvd = targetTvdMetre - tvdAtFullBuild;
      double cosIncMax = Math.cos(incMaxRad);
      double tangentMd = remainingTvd / cosIncMax;
      md.add(md.get(md.size() - 1) + tangentMd);
      tvd.add(targetTvdMetre);
      terminalInclinationRad = incMaxRad;

      if (tangentMd > LONG_TANGENT_WARNING_RATIO * targetTvdMetre) {
        warnings.add(String.format(Locale.ROOT,
            "%s the tangent/hold section needed to reach target_tvd_m (%.1f m) is more than %sx the target depth "
                + "itself -- the max_inclination_deg/build_rate combination in use may be a poor fit for this "
                + "target depth; review before trusting this trajectory.",
            SYNTHETIC_TRAJECTORY_WARNING_PREFIX, tangentMd, LONG_TANGENT_WARNING_RATIO));
      }
    }

    if (reservoirContactLengthMetre > 0.0) {
      md.add(md.get(md.size() - 1) + reservoirContactLengthMetre);
      tvd.add(tvd.get(tvd.size() - 1) + reservoirContactLengthMetre * Math.cos(terminalInclinationRad));
    }

    return new Result(toArray(md), toArray(tvd), basis, tierInterpolation, warnings);
  }

  /**
   * Return the single, canonical warning string for a generated synthetic trajectory, suitable for a genkey header
   * comment or a warnings collector -- mirrors {@code well-inflow-pi}'s own {@code [CHOKE WARNING]}/
   * {@code [GEOMETRY WARNING]} convention.
   *
   * @param result the result from any {@code generateSyntheticTrajectory(...)} overload
   * @return a single warning string, always non-empty regardless of whether {@code result.warnings} itself is empty --
   * a synthetic trajectory is always worth disclosing, not only when something unusual happened during its generation
   */
  public static String syntheticTrajectoryWarning(Result result) {
    return SYNTHETIC_TRAJECTORY_WARNING_PREFIX + " this well's geometry is a SYNTHETIC TEMPLATE (" + result.basis
        + "), not this well's real surveyed trajectory. Review before trusting it for a design or "
        + "safety-relevant OLGA run; replace with real survey data (Prosper, SMDA, or a real "
        + "drilling-engineering trajectory) as soon as one becomes available.";
  }

  /**
   * Piecewise-linear interpolation of a tier metric against each tier's own {@code approxMeanTotalTvdMetre}, with flat
   * extrapolation beyond the sampled range (never extrapolates a trend indefinitely beyond what the real data actually
   * supports).
   *
   * @param targetTvdMetre the TVD to interpolate at
   * @param tiers the depth tiers to interpolate against
   * @param metricExtractor which tier field to interpolate
   * @return the interpolated value
   */
  private static double interpolateTierMetric(double targetTvdMetre, DepthTier[] tiers,
      ToDoubleFunction<DepthTier> metricExtractor) {
    double[][] points = new double[tiers.length][2];
    for (int i = 0; i < tiers.length; i++) {
      points[i][0] = tiers[i].approxMeanTotalTvdMetre;
      points[i][1] = metricExtractor.applyAsDouble(tiers[i]);
    }
    java.util.Arrays.sort(points, (a, b) -> Double.compare(a[0], b[0]));

    if (targetTvdMetre <= points[0][0]) {
      return points[0][1];
    }
    if (targetTvdMetre >= points[points.length - 1][0]) {
      return points[points.length - 1][1];
    }
    for (int i = 0; i < points.length - 1; i++) {
      double x0 = points[i][0];
      double y0 = points[i][1];
      double x1 = points[i + 1][0];
      double y1 = points[i + 1][1];
      if (x0 <= targetTvdMetre && targetTvdMetre <= x1) {
        if (x1 == x0) {
          return y0;
        }
        double fraction = (targetTvdMetre - x0) / (x1 - x0);
        return y0 + fraction * (y1 - y0);
      }
    }
    return points[points.length - 1][1]; // unreachable given the bounds checks above
  }

  /**
   * Discretize a circular-arc build section into (MD, TVD) station pairs, from inclination 0 (at {@code kopMd}) to
   * {@code incTargetRad}.
   *
   * @param kopMd MD (and TVD) at the start of the build section
   * @param radiusM radius of curvature (m), i.e. {@code 1 / bRadPerM}
   * @param incTargetRad inclination (radians) to build to
   * @param numStations number of stations to emit, including both endpoints (minimum 2)
   * @return a 2-row array: row 0 is MD values, row 1 is TVD values
   */
  private static double[][] buildArcStations(double kopMd, double radiusM, double incTargetRad, int numStations) {
    int stations = Math.max(2, numStations);
    double[] mdValues = new double[stations];
    double[] tvdValues = new double[stations];
    for (int i = 0; i < stations; i++) {
      double incI = incTargetRad * i / (stations - 1.0);
      mdValues[i] = kopMd + radiusM * incI;
      tvdValues[i] = kopMd + radiusM * Math.sin(incI);
    }
    return new double[][] {mdValues, tvdValues};
  }

  private static void appendTailExcludingFirst(List<Double> target, double[] source) {
    for (int i = 1; i < source.length; i++) {
      target.add(source[i]);
    }
  }

  private static double[] toArray(List<Double> values) {
    double[] array = new double[values.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = values.get(i);
    }
    return array;
  }
}
