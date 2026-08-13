package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow-induced pulsation (FIP) screening for side branches (dead legs) on a gas main line.
 *
 * <p>
 * This is the tonal, acoustically resonant mechanism that the Energy Institute AVIFF guidelines treat separately from
 * main-line flow-induced vibration. Flow past the mouth of a side branch sheds a shear layer at the upstream edge of
 * the tee. When the shedding frequency coincides with a standing acoustic mode of the branch the two lock in, the
 * branch becomes a self-excited resonator, and the pulsation amplitude can rise by orders of magnitude over the
 * broadband turbulent level.
 * </p>
 *
 * <p>
 * <b>Why this matters when a line changes from wet gas to dry gas.</b> Main-line FIV <i>falls</i> when the liquid is
 * removed, because both the mixture density and the two-phase fluid-viscosity factor drop. Flow-induced pulsation moves
 * the other way: even a small amount of a second phase affects not only the vortex shedding but also the acoustic
 * damping and the speed of sound in the branch. A dry gas restores a high speed of sound and a lightly damped, high-Q
 * resonator, and a liquid-filled leg that drains changes its acoustic length scale entirely. A wet-gas measurement
 * campaign therefore cannot clear a line for dry-gas operation, however low the measured main-line vibration was.
 * </p>
 *
 * <h2>Method</h2>
 *
 * <p>
 * <b>Step 1 - acoustic eigenfrequencies of the branch.</b> The branch is treated as a single pipe with a pulsation
 * source at the tee and an acoustic termination at the far end. The acoustic length {@code L} runs along the centreline
 * from the tee to the <i>first acoustic boundary</i> - a normally closed valve, a blind, or a large volume such as a
 * separator, cooler or knock-out drum. For a closed termination (reflection coefficient R = +1) the branch is a
 * quarter-wave tube:
 * </p>
 *
 * <pre>
 *   f_n = (2n + 1) * c / (4 L),      n = 0, 1, 2, ...
 * </pre>
 *
 * <p>
 * and for an open termination (R = -1) a half-wave tube:
 * </p>
 *
 * <pre>
 *   f_n = (n + 1) * c / (2 L),       n = 0, 1, 2, ...
 * </pre>
 *
 * <p>
 * <b>Step 2 - excitation frequency.</b> The vortex shedding frequency follows from the Strouhal number formed with the
 * <i>effective width</i> of the branch mouth rather than with the branch diameter:
 * </p>
 *
 * <pre>
 *   W_eff = pi * d_s / 4 + r_eff,        f_s = Sr * U0 / W_eff
 * </pre>
 *
 * <p>
 * where {@code d_s} is the branch internal diameter, {@code r_eff} the edge radius of the tee (zero for a sharp-edged
 * branch) and {@code U0} the mean main-line velocity. Peak acoustic energy is produced for roughly
 * {@code 0.2 < Sr < 0.6} at common tees; {@value #DEFAULT_STROUHAL_MODE_A} is the recommended screening value for the
 * side-branch modes, and {@value #DEFAULT_STROUHAL_MODE_C} for the main-header mode.
 * </p>
 *
 * <p>
 * <b>Step 3 - resonance check.</b> A resonance condition is possible when the shedding frequency falls within
 * &plusmn;{@value #LOCK_IN_ENVELOPE_FRACTION} of an acoustic eigenfrequency:
 * </p>
 *
 * <pre>
 *   0.8 f_n &lt;= f_s &lt;= 1.2 f_n
 * </pre>
 *
 * <p>
 * The envelope accounts for the lock-in effect itself plus uncertainty in the speed of sound and in the geometry.
 * </p>
 *
 * <p>
 * Screening only: it identifies branches where a resonance condition is possible, it does not predict amplitudes. A
 * {@link PipingFivLikelihood#HIGH} or {@link PipingFivLikelihood#VERY_HIGH} outcome calls for a pulsation study, a
 * dynamic-pressure or strain-gauge survey, or removal or shortening of the dead leg.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 */
public class FlowInducedPulsationScreening implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Acoustic termination of the far end of the side branch.
   */
  public enum AcousticTermination {
    /** Closed end - closed valve, blind flange, plug. Reflection coefficient R = +1, quarter-wave modes. */
    CLOSED,
    /** Open end - separator, vessel at constant pressure. Reflection coefficient R = -1, half-wave modes. */
    OPEN
  }

  /** Recommended screening Strouhal number for the side-branch (A) acoustic modes. */
  public static final double DEFAULT_STROUHAL_MODE_A = 0.37;
  /** Recommended screening Strouhal number for the main-header (C) acoustic mode. */
  public static final double DEFAULT_STROUHAL_MODE_C = 0.20;
  /** Half-width of the lock-in envelope around each acoustic eigenfrequency, as a fraction. */
  public static final double LOCK_IN_ENVELOPE_FRACTION = 0.20;
  /** Reference gas momentum flux used to grade the severity of an excited mode, Pa. */
  public static final double REFERENCE_RHO_V2 = 20000.0;
  /** Number of acoustic modes evaluated by default. */
  public static final int DEFAULT_MODE_COUNT = 5;
  /** Severity below which an excited mode is not worth pursuing. */
  public static final double NEGLIGIBLE_SEVERITY = 0.05;

  private FlowInducedPulsationScreening() {
    // utility class
  }

  /**
   * Effective width of the branch mouth, the length scale of the Strouhal number.
   *
   * @param branchInternalDiameter branch internal diameter, m
   * @param edgeRadius edge radius of the tee, m; zero for a sharp-edged branch
   * @return effective width, m
   */
  public static double effectiveWidth(double branchInternalDiameter, double edgeRadius) {
    return Math.PI * branchInternalDiameter / 4.0 + edgeRadius;
  }

  /**
   * Acoustic eigenfrequency of a single-pipe side branch.
   *
   * @param modeIndex mode index n, starting at 0 for the fundamental
   * @param speedOfSound speed of sound in the branch fluid, m/s
   * @param acousticLength length from the tee to the first acoustic boundary, m
   * @param termination acoustic termination of the far end
   * @return eigenfrequency, Hz
   */
  public static double eigenFrequency(int modeIndex, double speedOfSound, double acousticLength,
      AcousticTermination termination) {
    if (termination == AcousticTermination.OPEN) {
      return (modeIndex + 1) * speedOfSound / (2.0 * acousticLength);
    }
    return (2 * modeIndex + 1) * speedOfSound / (4.0 * acousticLength);
  }

  /**
   * Screens a side branch for flow-induced pulsation using the recommended screening Strouhal number for the
   * side-branch acoustic modes and a closed termination.
   *
   * @param branchName branch identifier, for example a drain, a vent, a closed cross-over or an instrument tapping
   * @param acousticLength length along the centreline from the tee to the first acoustic boundary, m; must be positive
   * @param branchInternalDiameter branch internal diameter, m; must be positive
   * @param runInternalDiameter main-line internal diameter, m; must be positive
   * @param mainLineVelocity mean main-line flow velocity at line conditions, m/s; must be non-negative
   * @param mainLineDensity main-line fluid density at line conditions, kg/m³; must be non-negative
   * @param speedOfSound speed of sound in the branch fluid, m/s; must be positive
   * @return the screening result
   */
  public static FlowInducedPulsationResult screen(String branchName, double acousticLength,
      double branchInternalDiameter, double runInternalDiameter, double mainLineVelocity, double mainLineDensity,
      double speedOfSound) {
    return screen(branchName, acousticLength, branchInternalDiameter, 0.0, runInternalDiameter, mainLineVelocity,
        mainLineDensity, speedOfSound, AcousticTermination.CLOSED, DEFAULT_STROUHAL_MODE_A, DEFAULT_MODE_COUNT);
  }

  /**
   * Screens a side branch for flow-induced pulsation.
   *
   * @param branchName branch identifier
   * @param acousticLength length along the centreline from the tee to the first acoustic boundary, m; must be positive
   * @param branchInternalDiameter branch internal diameter, m; must be positive
   * @param edgeRadius edge radius of the tee, m; zero for a sharp-edged branch; must be non-negative
   * @param runInternalDiameter main-line internal diameter, m; must be positive
   * @param mainLineVelocity mean main-line flow velocity at line conditions, m/s; must be non-negative
   * @param mainLineDensity main-line fluid density at line conditions, kg/m³; must be non-negative
   * @param speedOfSound speed of sound in the branch fluid, m/s; must be positive
   * @param termination acoustic termination of the far end of the branch
   * @param strouhalNumber screening Strouhal number; must be positive
   * @param modeCount number of acoustic modes to evaluate; must be at least one
   * @return the screening result
   * @throws IllegalArgumentException if any geometric, acoustic or numerical input is not physical
   */
  public static FlowInducedPulsationResult screen(String branchName, double acousticLength,
      double branchInternalDiameter, double edgeRadius, double runInternalDiameter, double mainLineVelocity,
      double mainLineDensity, double speedOfSound, AcousticTermination termination, double strouhalNumber,
      int modeCount) {
    if (!(acousticLength > 0.0)) {
      throw new IllegalArgumentException("acousticLength must be positive, was " + acousticLength);
    }
    if (!(branchInternalDiameter > 0.0)) {
      throw new IllegalArgumentException("branchInternalDiameter must be positive, was " + branchInternalDiameter);
    }
    if (edgeRadius < 0.0) {
      throw new IllegalArgumentException("edgeRadius must be non-negative, was " + edgeRadius);
    }
    if (!(runInternalDiameter > 0.0)) {
      throw new IllegalArgumentException("runInternalDiameter must be positive, was " + runInternalDiameter);
    }
    if (!(speedOfSound > 0.0)) {
      throw new IllegalArgumentException("speedOfSound must be positive, was " + speedOfSound);
    }
    if (!(strouhalNumber > 0.0)) {
      throw new IllegalArgumentException("strouhalNumber must be positive, was " + strouhalNumber);
    }
    if (modeCount < 1) {
      throw new IllegalArgumentException("modeCount must be at least one, was " + modeCount);
    }
    if (mainLineVelocity < 0.0 || mainLineDensity < 0.0) {
      throw new IllegalArgumentException("mainLineVelocity and mainLineDensity must be non-negative");
    }
    if (termination == null) {
      throw new IllegalArgumentException("termination must not be null");
    }

    double effectiveWidth = effectiveWidth(branchInternalDiameter, edgeRadius);
    double sheddingFrequency = strouhalNumber * mainLineVelocity / effectiveWidth;
    double diameterRatio = branchInternalDiameter / runInternalDiameter;
    double rhoV2 = mainLineDensity * mainLineVelocity * mainLineVelocity;

    List<FlowInducedPulsationResult.BranchMode> modes = new ArrayList<FlowInducedPulsationResult.BranchMode>();
    boolean anyLockedIn = false;
    double strongestModeWeight = 0.0;
    double lowestResonanceVelocity = Double.NaN;

    for (int n = 0; n < modeCount; n++) {
      double frequency = eigenFrequency(n, speedOfSound, acousticLength, termination);
      double envelopeLow = (1.0 - LOCK_IN_ENVELOPE_FRACTION) * frequency;
      double envelopeHigh = (1.0 + LOCK_IN_ENVELOPE_FRACTION) * frequency;
      boolean lockedIn = sheddingFrequency >= envelopeLow && sheddingFrequency <= envelopeHigh;
      // Main-line velocity that would place the shedding frequency exactly on this mode.
      double resonanceVelocity = frequency * effectiveWidth / strouhalNumber;
      if (lockedIn) {
        anyLockedIn = true;
        // Higher-order modes are driven far more weakly than the fundamental.
        strongestModeWeight = Math.max(strongestModeWeight, 1.0 / (2 * n + 1));
      }
      if (Double.isNaN(lowestResonanceVelocity) || resonanceVelocity < lowestResonanceVelocity) {
        lowestResonanceVelocity = resonanceVelocity;
      }
      modes.add(new FlowInducedPulsationResult.BranchMode(n, frequency, envelopeLow, envelopeHigh, lockedIn,
          resonanceVelocity, (1.0 - LOCK_IN_ENVELOPE_FRACTION) * resonanceVelocity,
          (1.0 + LOCK_IN_ENVELOPE_FRACTION) * resonanceVelocity));
    }

    double energyTerm = rhoV2 / REFERENCE_RHO_V2;
    double couplingFactor = Math.min(1.0, diameterRatio / 0.5);
    double severity = energyTerm * couplingFactor * strongestModeWeight;

    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("acousticLength_m", acousticLength);
    factors.put("effectiveWidth_m", effectiveWidth);
    factors.put("strouhalNumber", strouhalNumber);
    factors.put("sheddingFrequency_Hz", sheddingFrequency);
    factors.put("diameterRatio", diameterRatio);
    factors.put("speedOfSound_mps", speedOfSound);
    factors.put("mainLineVelocity_mps", mainLineVelocity);
    factors.put("rhoV2_Pa", rhoV2);
    factors.put("energyTerm", energyTerm);
    factors.put("couplingFactor", couplingFactor);
    factors.put("modeWeight", strongestModeWeight);
    factors.put("severity", severity);

    PipingFivLikelihood band = bandFor(anyLockedIn, severity);
    return new FlowInducedPulsationResult(branchName, band, anyLockedIn, sheddingFrequency, lowestResonanceVelocity,
        modes, factors, recommendation(band, anyLockedIn));
  }

  /**
   * Maps the resonance state and severity to a likelihood band.
   *
   * @param anyModeLockedIn true when the shedding frequency falls inside the envelope of at least one acoustic mode
   * @param severity normalised severity of an excited mode
   * @return likelihood band
   */
  public static PipingFivLikelihood bandFor(boolean anyModeLockedIn, double severity) {
    if (!anyModeLockedIn || severity < NEGLIGIBLE_SEVERITY) {
      return PipingFivLikelihood.LOW;
    }
    if (severity < 0.3) {
      return PipingFivLikelihood.MEDIUM;
    }
    if (severity < 1.0) {
      return PipingFivLikelihood.HIGH;
    }
    return PipingFivLikelihood.VERY_HIGH;
  }

  /**
   * Returns the screening recommendation for a band.
   *
   * @param band likelihood band
   * @param anyModeLockedIn true when a resonance condition is possible
   * @return recommendation text
   */
  public static String recommendation(PipingFivLikelihood band, boolean anyModeLockedIn) {
    if (!anyModeLockedIn) {
      return "The shedding frequency falls outside the plus/minus 20 % envelope of every evaluated acoustic mode. "
          + "Re-screen if the velocity, the gas composition or the acoustic length changes.";
    }
    switch (band) {
    case LOW:
      return "Acceptable - no further assessment.";
    case MEDIUM:
      return "A resonance condition is possible. Confirm the as-built acoustic length, and fit a dynamic pressure "
          + "transducer or accelerometer on the branch before the operating change.";
    case HIGH:
      return "A resonance condition is expected. Carry out a pulsation study and a dynamic survey of the branch, and "
          + "shorten or brace the dead leg.";
    case VERY_HIGH:
      return "A strong resonance condition is expected. Remove or shorten the dead leg, or detune it by changing the "
          + "acoustic length. Do not rely on main-line vibration measurement to detect this.";
    default:
      return "Unknown";
    }
  }
}
