package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow-induced pulsation (FIP) screening for closed side branches (dead legs) on a gas main line.
 *
 * <p>
 * This is the mechanism the Energy Institute AVIFF guidelines treat separately from main-line flow-induced vibration.
 * Flow past the mouth of a closed branch sheds a shear layer. When the shedding frequency coincides with a standing
 * acoustic mode of the branch the two lock in, the branch becomes a self-excited resonator, and the pulsation amplitude
 * can rise by orders of magnitude over the broadband turbulent level.
 * </p>
 *
 * <p>
 * <b>Why this matters for a line that changes from wet gas to dry gas.</b> Main-line FIV falls when the liquid is
 * removed, because both the mixture density and the two-phase fluid-viscosity factor drop. Flow-induced pulsation moves
 * the opposite way. Liquid in a branch detunes and heavily damps the acoustic mode, and the mixture speed of sound in a
 * two-phase line is a small fraction of the dry-gas value, which places the branch modes far below the shedding
 * frequency. A dry gas restores a high speed of sound and a lightly damped, high-Q resonator, so branches that were
 * quiet in wet-gas service can lock in. A wet-gas measurement campaign therefore cannot clear a line for dry-gas
 * operation, no matter how low the measured main-line vibration was.
 * </p>
 *
 * <p>
 * <b>Method.</b> A closed branch behaves as a quarter-wave tube, open at the run-pipe mouth and closed at the isolation
 * valve or blind. Its acoustic modes are
 * </p>
 *
 * <pre>
 *   f_n = (2n - 1) * c / (4 * L_eff),    L_eff = L + endCorrection * d
 * </pre>
 *
 * <p>
 * with {@code c} the speed of sound in the branch fluid, {@code L} the branch length from run-pipe wall to the closure,
 * {@code d} the branch internal diameter and an end correction of about 0.4 d for a flush branch. Lock-in is screened
 * on the Strouhal number formed with the branch diameter,
 * </p>
 *
 * <pre>
 * St_n = f_n * d / v
 * </pre>
 *
 * <p>
 * where {@code v} is the main-line velocity. Shear-layer excitation of a closed side branch occurs over roughly
 * {@code 0.3 &lt;= St &lt;= 0.6} for the first shear-layer mode; the wider band {@code 0.2 - 0.6} is used here as a
 * screening envelope. Outside that band the mode is not driven, however high the main-line velocity is.
 * </p>
 *
 * <p>
 * The severity of an excited mode is graded on the main-line momentum flux, the branch-to-run diameter ratio (a large
 * branch presents a wider mouth to the shear layer and couples more strongly) and the acoustic quality of the branch.
 * Screening only: a {@link PipingFivLikelihood#HIGH} or {@link PipingFivLikelihood#VERY_HIGH} outcome calls for a
 * pulsation study, a strain-gauge or dynamic-pressure survey, or removal of the dead leg.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowInducedPulsationScreening implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Lower Strouhal number of the shear-layer lock-in band. */
  public static final double LOCK_IN_STROUHAL_LOW = 0.2;
  /** Upper Strouhal number of the shear-layer lock-in band. */
  public static final double LOCK_IN_STROUHAL_HIGH = 0.6;
  /** End correction applied to the branch length, as a multiple of the branch internal diameter. */
  public static final double END_CORRECTION_FACTOR = 0.4;
  /** Reference gas momentum flux used to grade the severity of an excited mode, Pa. */
  public static final double REFERENCE_RHO_V2 = 20000.0;
  /** Number of quarter-wave modes evaluated. */
  public static final int MODE_COUNT = 3;
  /** Severity below which an excited mode is not worth pursuing. */
  public static final double NEGLIGIBLE_SEVERITY = 0.05;

  private FlowInducedPulsationScreening() {
    // utility class
  }

  /**
   * Screens a single closed side branch for flow-induced pulsation.
   *
   * @param branchName branch identifier, for example a drain, a vent, a closed cross-over or an instrument tapping
   * @param branchLength length from the run-pipe wall to the closed end (isolation valve, blind or plug), m; must be
   * positive
   * @param branchInternalDiameter branch internal diameter, m; must be positive
   * @param runInternalDiameter main-line internal diameter, m; must be positive
   * @param mainLineVelocity main-line flow velocity at line conditions, m/s; must be non-negative
   * @param mainLineDensity main-line fluid density at line conditions, kg/m³; must be non-negative
   * @param speedOfSound speed of sound in the branch fluid, m/s; must be positive
   * @return the screening result
   * @throws IllegalArgumentException if any geometric or acoustic input is not physical
   */
  public static FlowInducedPulsationResult screen(String branchName, double branchLength, double branchInternalDiameter,
      double runInternalDiameter, double mainLineVelocity, double mainLineDensity, double speedOfSound) {
    if (!(branchLength > 0.0)) {
      throw new IllegalArgumentException("branchLength must be positive, was " + branchLength);
    }
    if (!(branchInternalDiameter > 0.0)) {
      throw new IllegalArgumentException("branchInternalDiameter must be positive, was " + branchInternalDiameter);
    }
    if (!(runInternalDiameter > 0.0)) {
      throw new IllegalArgumentException("runInternalDiameter must be positive, was " + runInternalDiameter);
    }
    if (!(speedOfSound > 0.0)) {
      throw new IllegalArgumentException("speedOfSound must be positive, was " + speedOfSound);
    }
    if (mainLineVelocity < 0.0 || mainLineDensity < 0.0) {
      throw new IllegalArgumentException("mainLineVelocity and mainLineDensity must be non-negative");
    }

    double effectiveLength = branchLength + END_CORRECTION_FACTOR * branchInternalDiameter;
    double diameterRatio = branchInternalDiameter / runInternalDiameter;
    double rhoV2 = mainLineDensity * mainLineVelocity * mainLineVelocity;

    List<FlowInducedPulsationResult.BranchMode> modes = new ArrayList<FlowInducedPulsationResult.BranchMode>();
    boolean anyLockedIn = false;
    double lowestLockInVelocity = Double.NaN;
    double strongestModeWeight = 0.0;

    for (int n = 1; n <= MODE_COUNT; n++) {
      double frequency = (2 * n - 1) * speedOfSound / (4.0 * effectiveLength);
      double strouhal = mainLineVelocity > 0.0 ? frequency * branchInternalDiameter / mainLineVelocity
          : Double.POSITIVE_INFINITY;
      boolean lockedIn = strouhal >= LOCK_IN_STROUHAL_LOW && strouhal <= LOCK_IN_STROUHAL_HIGH;
      // St falls as velocity rises, so the high Strouhal limit gives the low velocity limit.
      double velocityLow = frequency * branchInternalDiameter / LOCK_IN_STROUHAL_HIGH;
      double velocityHigh = frequency * branchInternalDiameter / LOCK_IN_STROUHAL_LOW;
      if (lockedIn) {
        anyLockedIn = true;
        // Higher-order modes are driven far more weakly than the fundamental, so an excited third mode is not the
        // same finding as an excited first mode.
        strongestModeWeight = Math.max(strongestModeWeight, 1.0 / (2 * n - 1));
      }
      if (Double.isNaN(lowestLockInVelocity) || velocityLow < lowestLockInVelocity) {
        lowestLockInVelocity = velocityLow;
      }
      modes.add(new FlowInducedPulsationResult.BranchMode(n, frequency, strouhal, lockedIn, velocityLow, velocityHigh));
    }

    double energyTerm = rhoV2 / REFERENCE_RHO_V2;
    double couplingFactor = Math.min(1.0, diameterRatio / 0.5);
    double severity = energyTerm * couplingFactor * strongestModeWeight;

    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("effectiveBranchLength_m", effectiveLength);
    factors.put("diameterRatio", diameterRatio);
    factors.put("speedOfSound_mps", speedOfSound);
    factors.put("mainLineVelocity_mps", mainLineVelocity);
    factors.put("rhoV2_Pa", rhoV2);
    factors.put("energyTerm", energyTerm);
    factors.put("couplingFactor", couplingFactor);
    factors.put("modeWeight", strongestModeWeight);
    factors.put("severity", severity);

    PipingFivLikelihood band = bandFor(anyLockedIn, severity);
    return new FlowInducedPulsationResult(branchName, band, anyLockedIn, lowestLockInVelocity, modes, factors,
        recommendation(band, anyLockedIn));
  }

  /**
   * Maps the lock-in state and severity to a likelihood band.
   *
   * @param anyModeLockedIn true when at least one acoustic mode is excited
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
   * @param anyModeLockedIn true when at least one acoustic mode is excited
   * @return recommendation text
   */
  public static String recommendation(PipingFivLikelihood band, boolean anyModeLockedIn) {
    if (!anyModeLockedIn) {
      return "No acoustic mode of the branch is in shear-layer lock-in at the screened velocity. "
          + "Re-screen if the velocity, the gas composition or the branch length changes.";
    }
    switch (band) {
    case LOW:
      return "Acceptable - no further assessment.";
    case MEDIUM:
      return "Lock-in possible. Confirm the as-built branch length, and fit a dynamic pressure transducer "
          + "or accelerometer on the branch before the operating change.";
    case HIGH:
      return "Lock-in expected. Carry out a pulsation study and a dynamic survey of the branch, and brace or "
          + "shorten the dead leg.";
    case VERY_HIGH:
      return "Strong lock-in expected. Remove or shorten the dead leg, or detune it (change length, add a "
          + "restriction at the mouth). Do not rely on the main-line vibration measurement to detect this.";
    default:
      return "Unknown";
    }
  }
}
