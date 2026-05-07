package neqsim.process.safety.mdmt;

import java.io.Serializable;

/**
 * Minimum Design Metal Temperature (MDMT) screening calculator for pressure equipment.
 *
 * <p>
 * Implements a simplified Procedure based on API 579-1 / ASME FFS-1 Part 3 (brittle fracture
 * assessment) and ASME B31.3 / Section VIII Div. 1 UCS-66 / UHA-51 impact-test exemption curves.
 * Used to verify that a vessel exposed to cold blowdown (Joule–Thomson cooling) will not become
 * susceptible to brittle fracture.
 *
 * <p>
 * Inputs: material toughness curve (A/B/C/D per UCS-66 Fig.), governing thickness, design stress
 * ratio, and minimum operating temperature. Returns: MDMT in °C and PASS/FAIL verdict.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API 579-1 / ASME FFS-1 Part 3 — Brittle Fracture</li>
 * <li>ASME Section VIII Div. 1, UCS-66 / UCS-66.1 / Fig. UCS-66</li>
 * <li>BS 7910 Annex K (toughness from Charpy correlation)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class MDMTCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * UCS-66 impact test exemption curves. Selection per Fig. UCS-66 based on material grade and
   * thickness.
   */
  public enum MaterialCurve {
    /** Curve A — most restrictive (e.g., SA-516 Gr 60 as-rolled). */
    A,
    /** Curve B — common carbon steels (e.g., SA-516 Gr 70 as-rolled, SA-105). */
    B,
    /** Curve C — normalized carbon steels (e.g., SA-516 Gr 70 normalized). */
    C,
    /** Curve D — quenched and tempered or fine-grain steels. */
    D
  }

  private final MaterialCurve curve;
  private final double governingThicknessMm;
  private double stressRatio = 1.0; // applied stress / allowable

  /**
   * Construct an MDMT calculator.
   *
   * @param curve material toughness curve from UCS-66
   * @param governingThicknessMm governing thickness per UCS-66.3 in mm
   */
  public MDMTCalculator(MaterialCurve curve, double governingThicknessMm) {
    if (curve == null) {
      throw new IllegalArgumentException("curve must not be null");
    }
    if (governingThicknessMm <= 0.0) {
      throw new IllegalArgumentException("governingThicknessMm must be positive");
    }
    this.curve = curve;
    this.governingThicknessMm = governingThicknessMm;
  }

  /**
   * Set the stress ratio (applied / allowable). Lower stress ratios permit colder MDMT per
   * UCS-66.1.
   *
   * @param ratio stress ratio in [0, 1]
   * @return this calculator for chaining
   */
  public MDMTCalculator setStressRatio(double ratio) {
    if (ratio <= 0.0 || ratio > 1.0) {
      throw new IllegalArgumentException("stressRatio must be in (0,1]");
    }
    this.stressRatio = ratio;
    return this;
  }

  /**
   * Compute the MDMT in °C without impact testing per UCS-66 Figure curves.
   *
   * <p>
   * Approximates Fig. UCS-66 with simple analytic curves of MDMT vs thickness for the four UCS-66
   * groups, then applies the stress-ratio reduction per UCS-66.1 Fig. UCS-66.1 (up to 55 °C colder
   * for low stress).
   *
   * @return MDMT in °C
   */
  public double getMDMT_C() {
    double t = Math.max(6.0, Math.min(governingThicknessMm, 100.0));
    double mdmtBase;
    switch (curve) {
      case A:
        // Curve A approximate: MDMT = -29 + 18*ln(t/6) for t in mm (warmest)
        mdmtBase = -29.0 + 18.0 * Math.log(t / 6.0);
        break;
      case B:
        mdmtBase = -48.0 + 22.0 * Math.log(t / 6.0);
        break;
      case C:
        mdmtBase = -55.0 + 20.0 * Math.log(t / 6.0);
        break;
      case D:
      default:
        mdmtBase = -65.0 + 18.0 * Math.log(t / 6.0);
        break;
    }
    // Stress-ratio reduction per UCS-66.1 — up to 55 K colder when ratio ≤ 0.35
    double reduction;
    if (stressRatio <= 0.35) {
      reduction = 55.0;
    } else if (stressRatio >= 1.0) {
      reduction = 0.0;
    } else {
      reduction = 55.0 * (1.0 - stressRatio) / 0.65;
    }
    return mdmtBase - reduction;
  }

  /**
   * Brittle-fracture screening verdict per API 579 Procedure 3-A.
   *
   * @param minOperatingTempC minimum operating metal temperature seen in blowdown / cold service
   * @return {@code true} if the operating temperature is warmer than or equal to the MDMT
   */
  public boolean isAcceptable(double minOperatingTempC) {
    return minOperatingTempC >= getMDMT_C();
  }

  /**
   * Build a multi-line text report.
   *
   * @param minOperatingTempC minimum operating metal temperature in °C
   * @return human-readable summary
   */
  public String report(double minOperatingTempC) {
    double mdmt = getMDMT_C();
    StringBuilder sb = new StringBuilder();
    sb.append("MDMT Assessment (API 579 / ASME UCS-66):\n");
    sb.append("  Material curve         : ").append(curve).append('\n');
    sb.append(String.format("  Governing thickness    : %.2f mm%n", governingThicknessMm));
    sb.append(String.format("  Stress ratio           : %.3f%n", stressRatio));
    sb.append(String.format("  MDMT (allowed coldest) : %.1f °C%n", mdmt));
    sb.append(String.format("  Min operating temp     : %.1f °C%n", minOperatingTempC));
    sb.append("  Verdict                : ")
        .append(isAcceptable(minOperatingTempC) ? "PASS" : "FAIL — brittle fracture risk")
        .append('\n');
    return sb.toString();
  }
}
