package neqsim.process.mechanicaldesign.valve.choke;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.mechanicaldesign.valve.ValveCharacteristic;

/**
 * Screening of a production choke against recommended sizing and operating limits.
 *
 * <p>
 * The checks cover the points that decide whether a choke is acceptable in service:
 * </p>
 * <ul>
 * <li><b>Operating range</b>: operate above about 20 % of maximum Cv for plug-and-cage trims and above about 60 % for
 * rotating-disc trims. A minimum position calculated from throttling velocity, pressure drop and sand load replaces the
 * 20 % default for plug-and-cage trims when it is supplied; without a calculation, keep at least 20 % of maximum Cv in
 * sand service.</li>
 * <li><b>Clean-up</b>: the smallest port in the trim should be at least three times the largest particle expected.</li>
 * <li><b>Collapse capacity</b>: the pressure-protection system must be able to handle the flow of a collapsed (fully
 * open) choke. Supply the collapsed flow, for example from the choke model with the maximum Cv, and the protection
 * capacity.</li>
 * <li><b>Noise</b>: optional comparison of a predicted level with a limit.</li>
 * <li><b>Opening speed</b>: the choke should open more slowly than the upstream block valve.</li>
 * </ul>
 *
 * <p>
 * The limits are screening recommendations. A WARN is a deviation that needs an explicit justification; a FAIL is a
 * violated hard criterion. Checks without input are reported as NOT_EVALUATED and never count as a pass.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ChokeOperatingEnvelope implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Recommended minimum fraction of maximum Cv for plug-and-cage trims. */
  public static final double PLUG_AND_CAGE_MIN_CV_FRACTION = 0.20;
  /** Recommended minimum fraction of maximum Cv for rotating-disc trims. */
  public static final double ROTATING_DISC_MIN_CV_FRACTION = 0.60;
  /** Minimum ratio of the smallest port size to the largest expected particle. */
  public static final double MIN_PORT_TO_PARTICLE_RATIO = 3.0;

  /** Trim families with different recommended operating ranges. */
  public enum TrimType {
    /** Plug and cage trim. */
    PLUG_AND_CAGE(PLUG_AND_CAGE_MIN_CV_FRACTION),
    /** Rotating disc trim. */
    ROTATING_DISC(ROTATING_DISC_MIN_CV_FRACTION),
    /** Trim not identified; the more permissive plug-and-cage fraction is used. */
    OTHER(PLUG_AND_CAGE_MIN_CV_FRACTION);

    /** Recommended minimum fraction of maximum Cv. */
    private final double minimumCvFraction;

    /**
     * Creates a trim type.
     *
     * @param minimumCvFraction recommended minimum fraction of maximum Cv
     */
    TrimType(double minimumCvFraction) {
      this.minimumCvFraction = minimumCvFraction;
    }

    /**
     * Gets the recommended minimum fraction of maximum Cv.
     *
     * @return minimum fraction, 0 to 1
     */
    public double getMinimumCvFraction() {
      return minimumCvFraction;
    }
  }

  /** Outcome of one check. */
  public enum Status {
    /** Criterion met. */
    PASS,
    /** Recommended limit not met; needs justification. */
    WARN,
    /** Hard criterion violated. */
    FAIL,
    /** Input missing, check not performed. */
    NOT_EVALUATED
  }

  /** Trim family. */
  private TrimType trimType = TrimType.PLUG_AND_CAGE;
  /** Cv at full opening [US Cv]. */
  private double maximumCv = Double.NaN;
  /** Cv at the operating point [US Cv]. */
  private double currentCv = Double.NaN;
  /** Calculated minimum fraction of maximum Cv for sand service, NaN if not calculated. */
  private double calculatedMinimumFraction = Double.NaN;
  /** Whether solids are produced through the choke. */
  private boolean sandService = false;
  /** Largest expected particle [mm], NaN if unknown. */
  private double particleSizeMm = Double.NaN;
  /** Smallest port in the trim [mm], NaN if unknown. */
  private double smallestPortMm = Double.NaN;
  /** Flow of a collapsed choke [kg/s], NaN if unknown. */
  private double collapsedChokeFlowKgPerS = Double.NaN;
  /** Flow the pressure-protection system can handle [kg/s], NaN if unknown. */
  private double protectionCapacityKgPerS = Double.NaN;
  /** Predicted noise level [dB(A)], NaN if unknown. */
  private double predictedNoiseDbA = Double.NaN;
  /** Noise limit [dB(A)], NaN if unknown. */
  private double noiseLimitDbA = Double.NaN;
  /** Choke full-stroke opening time [s], NaN if unknown. */
  private double chokeOpeningTimeS = Double.NaN;
  /** Block valve full-stroke opening time [s], NaN if unknown. */
  private double blockValveOpeningTimeS = Double.NaN;

  /**
   * Sets the trim family.
   *
   * @param trimType trim family, not null
   * @return this envelope
   * @throws IllegalArgumentException if trimType is null
   */
  public ChokeOperatingEnvelope setTrimType(TrimType trimType) {
    if (trimType == null) {
      throw new IllegalArgumentException("trimType must not be null");
    }
    this.trimType = trimType;
    return this;
  }

  /**
   * Sets the operating point by Cv values.
   *
   * @param maximumCv Cv at full opening [US Cv], positive
   * @param currentCv Cv at the operating point [US Cv], not negative
   * @return this envelope
   * @throws IllegalArgumentException if maximumCv is not positive or currentCv is negative or non-finite
   */
  public ChokeOperatingEnvelope setCv(double maximumCv, double currentCv) {
    if (!Double.isFinite(maximumCv) || maximumCv <= 0.0 || !Double.isFinite(currentCv) || currentCv < 0.0) {
      throw new IllegalArgumentException("maximumCv must be positive and currentCv not negative");
    }
    this.maximumCv = maximumCv;
    this.currentCv = currentCv;
    return this;
  }

  /**
   * Sets the operating point by valve opening and trim characteristic.
   *
   * @param maximumCv Cv at full opening [US Cv], positive
   * @param percentOpening opening [percent], 0 to 100
   * @param characteristic trim characteristic that maps opening to the fraction of maximum Cv
   * @return this envelope
   * @throws IllegalArgumentException if maximumCv is not positive or the characteristic is null
   */
  public ChokeOperatingEnvelope setOperatingPoint(double maximumCv, double percentOpening,
      ValveCharacteristic characteristic) {
    return setCv(maximumCv, ChokeFlowCoefficient.cvAtOpening(maximumCv, percentOpening, characteristic));
  }

  /**
   * Declares solids in the produced stream and optionally the minimum position calculated for the throttling velocity,
   * pressure drop and sand load.
   *
   * @param calculatedMinimumFraction calculated minimum fraction of maximum Cv, 0 to 1, or NaN when no calculation is
   * available
   * @return this envelope
   * @throws IllegalArgumentException if a finite fraction is outside 0 to 1
   */
  public ChokeOperatingEnvelope setSandService(double calculatedMinimumFraction) {
    if (Double.isFinite(calculatedMinimumFraction)
        && (calculatedMinimumFraction < 0.0 || calculatedMinimumFraction > 1.0)) {
      throw new IllegalArgumentException("calculatedMinimumFraction must be within 0 to 1");
    }
    this.sandService = true;
    this.calculatedMinimumFraction = calculatedMinimumFraction;
    return this;
  }

  /**
   * Sets the clean-up debris and trim port sizes.
   *
   * @param particleSizeMm largest particle expected [mm], positive
   * @param smallestPortMm smallest port in the trim [mm], positive
   * @return this envelope
   * @throws IllegalArgumentException if a size is not positive and finite
   */
  public ChokeOperatingEnvelope setCleanUpSizes(double particleSizeMm, double smallestPortMm) {
    if (!Double.isFinite(particleSizeMm) || particleSizeMm <= 0.0 || !Double.isFinite(smallestPortMm)
        || smallestPortMm <= 0.0) {
      throw new IllegalArgumentException("particle and port sizes must be positive and finite");
    }
    this.particleSizeMm = particleSizeMm;
    this.smallestPortMm = smallestPortMm;
    return this;
  }

  /**
   * Sets the collapsed-choke flow and the capacity of the pressure-protection system.
   *
   * @param collapsedChokeFlowKgPerS flow of the fully open or collapsed choke [kg/s], positive
   * @param protectionCapacityKgPerS flow the protection system can handle [kg/s], positive
   * @return this envelope
   * @throws IllegalArgumentException if a flow is not positive and finite
   */
  public ChokeOperatingEnvelope setCollapseCapacity(double collapsedChokeFlowKgPerS, double protectionCapacityKgPerS) {
    if (!Double.isFinite(collapsedChokeFlowKgPerS) || collapsedChokeFlowKgPerS <= 0.0
        || !Double.isFinite(protectionCapacityKgPerS) || protectionCapacityKgPerS <= 0.0) {
      throw new IllegalArgumentException("flows must be positive and finite");
    }
    this.collapsedChokeFlowKgPerS = collapsedChokeFlowKgPerS;
    this.protectionCapacityKgPerS = protectionCapacityKgPerS;
    return this;
  }

  /**
   * Sets the predicted noise level and its limit.
   *
   * @param predictedNoiseDbA predicted level [dB(A)]
   * @param noiseLimitDbA acceptable level [dB(A)]
   * @return this envelope
   * @throws IllegalArgumentException if a value is not finite
   */
  public ChokeOperatingEnvelope setNoise(double predictedNoiseDbA, double noiseLimitDbA) {
    if (!Double.isFinite(predictedNoiseDbA) || !Double.isFinite(noiseLimitDbA)) {
      throw new IllegalArgumentException("noise values must be finite");
    }
    this.predictedNoiseDbA = predictedNoiseDbA;
    this.noiseLimitDbA = noiseLimitDbA;
    return this;
  }

  /**
   * Sets the full-stroke opening times of the choke and the upstream block valve.
   *
   * @param chokeOpeningTimeS choke opening time from closed to open [s], positive
   * @param blockValveOpeningTimeS block valve opening time [s], positive
   * @return this envelope
   * @throws IllegalArgumentException if a time is not positive and finite
   */
  public ChokeOperatingEnvelope setOpeningTimes(double chokeOpeningTimeS, double blockValveOpeningTimeS) {
    if (!Double.isFinite(chokeOpeningTimeS) || chokeOpeningTimeS <= 0.0 || !Double.isFinite(blockValveOpeningTimeS)
        || blockValveOpeningTimeS <= 0.0) {
      throw new IllegalArgumentException("opening times must be positive and finite");
    }
    this.chokeOpeningTimeS = chokeOpeningTimeS;
    this.blockValveOpeningTimeS = blockValveOpeningTimeS;
    return this;
  }

  /**
   * Evaluates all checks.
   *
   * @return result with one finding per check and the worst status
   */
  public Result evaluate() {
    List<Finding> findings = new ArrayList<Finding>();
    findings.add(checkOperatingRange());
    findings.add(checkCleanUp());
    findings.add(checkCollapseCapacity());
    findings.add(checkNoise());
    findings.add(checkOpeningSpeed());
    return new Result(findings);
  }

  /**
   * Checks the operating point against the minimum Cv fraction.
   *
   * @return finding for the operating range
   */
  private Finding checkOperatingRange() {
    String name = "operating range";
    if (!Double.isFinite(maximumCv)) {
      return new Finding(name, Status.NOT_EVALUATED, "no Cv or opening supplied");
    }
    double fraction = currentCv / maximumCv;
    double recommended = trimType.getMinimumCvFraction();
    String basis = trimType + " recommended minimum";
    if (sandService && trimType != TrimType.ROTATING_DISC && Double.isFinite(calculatedMinimumFraction)) {
      if (fraction < calculatedMinimumFraction) {
        return new Finding(name, Status.FAIL,
            String.format("%.1f %% of maximum Cv is below the %.1f %% minimum " + "calculated for the sand load",
                100.0 * fraction, 100.0 * calculatedMinimumFraction));
      }
      return new Finding(name, Status.PASS,
          String.format("%.1f %% of maximum Cv is above the %.1f %% minimum " + "calculated for the sand load",
              100.0 * fraction, 100.0 * calculatedMinimumFraction));
    }
    if (fraction < recommended) {
      return new Finding(name, Status.WARN, String.format("%.1f %% of maximum Cv is below the %.0f %% (%s)",
          100.0 * fraction, 100.0 * recommended, basis));
    }
    return new Finding(name, Status.PASS,
        String.format("%.1f %% of maximum Cv is above the %.0f %% (%s)", 100.0 * fraction, 100.0 * recommended, basis));
  }

  /**
   * Checks the port size against the expected debris size.
   *
   * @return finding for clean-up
   */
  private Finding checkCleanUp() {
    String name = "clean-up port size";
    if (!Double.isFinite(particleSizeMm)) {
      return new Finding(name, Status.NOT_EVALUATED, "no particle or port size supplied");
    }
    double ratio = smallestPortMm / particleSizeMm;
    if (ratio < MIN_PORT_TO_PARTICLE_RATIO) {
      return new Finding(name, Status.FAIL,
          String.format("smallest port is %.1f times the largest particle, " + "at least %.0f times is recommended",
              ratio, MIN_PORT_TO_PARTICLE_RATIO));
    }
    return new Finding(name, Status.PASS, String.format("smallest port is %.1f times the largest particle", ratio));
  }

  /**
   * Checks the protection capacity against the collapsed choke flow.
   *
   * @return finding for the collapse capacity
   */
  private Finding checkCollapseCapacity() {
    String name = "collapse capacity";
    if (!Double.isFinite(collapsedChokeFlowKgPerS)) {
      return new Finding(name, Status.NOT_EVALUATED, "no collapsed-choke flow or protection capacity supplied");
    }
    double ratio = collapsedChokeFlowKgPerS / protectionCapacityKgPerS;
    if (ratio > 1.0) {
      return new Finding(name, Status.FAIL, String.format("collapsed choke flow is %.0f %% of the protection "
          + "capacity; reduce the maximum Cv or increase the capacity", 100.0 * ratio));
    }
    return new Finding(name, Status.PASS,
        String.format("collapsed choke flow is %.0f %% of the protection capacity", 100.0 * ratio));
  }

  /**
   * Checks the noise level against its limit.
   *
   * @return finding for noise
   */
  private Finding checkNoise() {
    String name = "noise";
    if (!Double.isFinite(predictedNoiseDbA)) {
      return new Finding(name, Status.NOT_EVALUATED, "no noise prediction supplied");
    }
    if (predictedNoiseDbA > noiseLimitDbA) {
      return new Finding(name, Status.WARN,
          String.format("predicted %.1f dB(A) exceeds the %.1f dB(A) limit", predictedNoiseDbA, noiseLimitDbA));
    }
    return new Finding(name, Status.PASS,
        String.format("predicted %.1f dB(A) is within the %.1f dB(A) limit", predictedNoiseDbA, noiseLimitDbA));
  }

  /**
   * Checks that the choke opens more slowly than the block valve.
   *
   * @return finding for the opening speed
   */
  private Finding checkOpeningSpeed() {
    String name = "opening speed";
    if (!Double.isFinite(chokeOpeningTimeS)) {
      return new Finding(name, Status.NOT_EVALUATED, "no opening times supplied");
    }
    if (chokeOpeningTimeS <= blockValveOpeningTimeS) {
      return new Finding(name, Status.FAIL,
          String.format("choke opens in %.0f s, not slower than the block valve " + "(%.0f s)", chokeOpeningTimeS,
              blockValveOpeningTimeS));
    }
    return new Finding(name, Status.PASS, String.format("choke opens in %.0f s, slower than the block valve (%.0f s)",
        chokeOpeningTimeS, blockValveOpeningTimeS));
  }

  /** One check outcome. */
  public static final class Finding implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Check name. */
    private final String check;
    /** Outcome. */
    private final Status status;
    /** Explanation with the numbers behind the outcome. */
    private final String message;

    /**
     * Creates a finding.
     *
     * @param check check name
     * @param status outcome
     * @param message explanation
     */
    Finding(String check, Status status, String message) {
      this.check = check;
      this.status = status;
      this.message = message;
    }

    /**
     * Gets the check name.
     *
     * @return check name
     */
    public String getCheck() {
      return check;
    }

    /**
     * Gets the outcome.
     *
     * @return status
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Gets the explanation.
     *
     * @return message with the numbers behind the outcome
     */
    public String getMessage() {
      return message;
    }
  }

  /** Result of all checks. */
  public static final class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Findings in evaluation order. */
    private final List<Finding> findings;

    /**
     * Creates a result.
     *
     * @param findings findings in evaluation order
     */
    Result(List<Finding> findings) {
      this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
    }

    /**
     * Gets the findings.
     *
     * @return unmodifiable list in evaluation order
     */
    public List<Finding> getFindings() {
      return findings;
    }

    /**
     * Gets one finding by check name.
     *
     * @param check check name, for example {@code "operating range"}
     * @return the finding, or null if there is no such check
     */
    public Finding get(String check) {
      for (Finding finding : findings) {
        if (finding.getCheck().equals(check)) {
          return finding;
        }
      }
      return null;
    }

    /**
     * Gets the worst status of the evaluated checks.
     *
     * @return FAIL, WARN or PASS; NOT_EVALUATED when no check could be performed
     */
    public Status getOverallStatus() {
      Status worst = Status.NOT_EVALUATED;
      for (Finding finding : findings) {
        Status status = finding.getStatus();
        if (status == Status.NOT_EVALUATED) {
          continue;
        }
        if (worst == Status.NOT_EVALUATED || status.ordinal() > worst.ordinal()) {
          worst = status;
        }
      }
      return worst;
    }
  }
}
