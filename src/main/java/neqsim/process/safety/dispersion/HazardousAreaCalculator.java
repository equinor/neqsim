package neqsim.process.safety.dispersion;

import java.io.Serializable;

/**
 * Hazardous-area classification per IEC 60079-10-1 / API RP 505 for gas jet releases.
 *
 * <p>
 * Estimates the hazardous distance (distance to the lower flammable limit, LFL) and the resulting zone classification
 * from a continuous, primary or secondary grade of release. The hazardous distance is computed with a turbulent
 * free-jet axial concentration-decay model (Chen &amp; Rodi 1980), which underpins the IEC 60079-10-1 Annex C jet
 * method:
 * <ul>
 * <li>Effective (notional) source diameter from the mass flow and exit conditions:
 * {@code d_e = sqrt(4·ṁ / (π·ρ_e·U_e))}</li>
 * <li>Axial mass-fraction decay: {@code Y(x) = 5.4·sqrt(ρ_e/ρ_a)·d_e / x}</li>
 * <li>Hazardous distance to a (de-rated) LFL mass fraction: {@code x_LFL = 5.4·sqrt(ρ_e/ρ_a)·d_e / (k·Y_LFL)}</li>
 * </ul>
 * The LFL volume fraction is converted to a mass fraction using the gas and air molar masses. The safety factor
 * {@code k} (typically 0.25–0.5) applies a margin below LFL per IEC 60079-10-1.
 *
 * <p>
 * The grade of release sets the zone type: continuous → Zone 0, primary → Zone 1, secondary → Zone 2.
 *
 * <p>
 * <b>References:</b> IEC 60079-10-1:2020 (Explosive atmospheres — Classification of areas — Explosive gas atmospheres),
 * API RP 505, Chen C.J. &amp; Rodi W. (1980) Vertical Turbulent Buoyant Jets.
 *
 * @author ESOL
 * @version 1.0
 */
public class HazardousAreaCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Molar mass of air [kg/mol]. */
  private static final double M_AIR = 0.028964;
  /** Chen &amp; Rodi axial-decay constant. */
  private static final double JET_DECAY_CONSTANT = 5.4;

  /** Grade of release per IEC 60079-10-1. */
  public enum ReleaseGrade {
    /** Continuous grade — present continuously or for long periods → Zone 0. */
    CONTINUOUS("Zone 0"),
    /** Primary grade — likely to occur periodically in normal operation → Zone 1. */
    PRIMARY("Zone 1"),
    /** Secondary grade — not expected in normal operation, infrequent and short → Zone 2. */
    SECONDARY("Zone 2");

    private final String zone;

    ReleaseGrade(String zone) {
      this.zone = zone;
    }

    /**
     * The zone classification corresponding to this grade of release.
     *
     * @return zone label
     */
    public String getZone() {
      return zone;
    }
  }

  private final double massFlowKgPerS;
  private final double exitDensityKgPerM3;
  private final double exitVelocityMPerS;
  private final double lflVolumeFraction;
  private final double gasMolarMassKgPerMol;

  private double ambientDensityKgPerM3 = 1.225;
  private double safetyFactor = 0.5;
  private ReleaseGrade releaseGrade = ReleaseGrade.SECONDARY;

  /**
   * Construct a hazardous-area calculator for a gas jet release.
   *
   * @param massFlowKgPerS release mass flow in kg/s
   * @param exitDensityKgPerM3 gas density at the release plane in kg/m³
   * @param exitVelocityMPerS gas velocity at the release plane in m/s
   * @param lflVolumeFraction lower flammable limit as a volume (mole) fraction (e.g. 0.044 for methane)
   * @param gasMolarMassKgPerMol gas molar mass in kg/mol
   */
  public HazardousAreaCalculator(double massFlowKgPerS, double exitDensityKgPerM3, double exitVelocityMPerS,
      double lflVolumeFraction, double gasMolarMassKgPerMol) {
    if (massFlowKgPerS <= 0.0 || exitDensityKgPerM3 <= 0.0 || exitVelocityMPerS <= 0.0) {
      throw new IllegalArgumentException("massFlow, exitDensity and exitVelocity must be positive");
    }
    if (lflVolumeFraction <= 0.0 || lflVolumeFraction >= 1.0) {
      throw new IllegalArgumentException("LFL volume fraction must be in (0,1)");
    }
    if (gasMolarMassKgPerMol <= 0.0) {
      throw new IllegalArgumentException("gas molar mass must be positive");
    }
    this.massFlowKgPerS = massFlowKgPerS;
    this.exitDensityKgPerM3 = exitDensityKgPerM3;
    this.exitVelocityMPerS = exitVelocityMPerS;
    this.lflVolumeFraction = lflVolumeFraction;
    this.gasMolarMassKgPerMol = gasMolarMassKgPerMol;
  }

  /**
   * Set the ambient air density (default 1.225 kg/m³).
   *
   * @param rho ambient density in kg/m³
   * @return this calculator for chaining
   */
  public HazardousAreaCalculator setAmbientDensityKgPerM3(double rho) {
    if (rho <= 0.0) {
      throw new IllegalArgumentException("ambient density must be positive");
    }
    this.ambientDensityKgPerM3 = rho;
    return this;
  }

  /**
   * Set the safety factor applied below the LFL (default 0.5; IEC 60079-10-1 commonly 0.25–0.5).
   *
   * @param k safety factor in (0,1]
   * @return this calculator for chaining
   */
  public HazardousAreaCalculator setSafetyFactor(double k) {
    if (k <= 0.0 || k > 1.0) {
      throw new IllegalArgumentException("safety factor must be in (0,1]");
    }
    this.safetyFactor = k;
    return this;
  }

  /**
   * Set the grade of release (default SECONDARY → Zone 2).
   *
   * @param grade the grade of release
   * @return this calculator for chaining
   */
  public HazardousAreaCalculator setReleaseGrade(ReleaseGrade grade) {
    if (grade == null) {
      throw new IllegalArgumentException("grade must not be null");
    }
    this.releaseGrade = grade;
    return this;
  }

  /**
   * Effective (notional) source diameter from the mass flow and exit conditions.
   *
   * @return effective diameter in m
   */
  public double effectiveDiameterM() {
    return Math.sqrt(4.0 * massFlowKgPerS / (Math.PI * exitDensityKgPerM3 * exitVelocityMPerS));
  }

  /**
   * LFL expressed as a mass fraction (converted from the volume fraction using gas and air molar masses).
   *
   * @return LFL mass fraction
   */
  public double lflMassFraction() {
    double y = lflVolumeFraction;
    double num = y * gasMolarMassKgPerMol;
    return num / (num + (1.0 - y) * M_AIR);
  }

  /**
   * Hazardous distance to the de-rated LFL along the jet axis.
   *
   * @return hazardous distance in m
   */
  public double hazardousDistanceM() {
    double target = safetyFactor * lflMassFraction();
    double densityRatio = Math.sqrt(exitDensityKgPerM3 / ambientDensityKgPerM3);
    return JET_DECAY_CONSTANT * densityRatio * effectiveDiameterM() / target;
  }

  /**
   * Zone classification corresponding to the configured grade of release.
   *
   * @return zone label (e.g. "Zone 2")
   */
  public String zoneClassification() {
    return releaseGrade.getZone();
  }

  /**
   * The grade of release.
   *
   * @return release grade
   */
  public ReleaseGrade getReleaseGrade() {
    return releaseGrade;
  }

  /**
   * Build a brief human-readable summary.
   *
   * @return summary string
   */
  public String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Hazardous-area classification (IEC 60079-10-1):\n");
    sb.append(String.format("  Effective source dia. : %.4f m%n", effectiveDiameterM()));
    sb.append(String.format("  LFL mass fraction     : %.4f%n", lflMassFraction()));
    sb.append(String.format("  Safety factor k       : %.2f%n", safetyFactor));
    sb.append(String.format("  Hazardous distance    : %.2f m%n", hazardousDistanceM()));
    sb.append(String.format("  Grade of release      : %s -> %s%n", releaseGrade, zoneClassification()));
    return sb.toString();
  }
}
