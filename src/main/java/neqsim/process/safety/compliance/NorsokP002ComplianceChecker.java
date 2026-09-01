package neqsim.process.safety.compliance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Point-by-point NORSOK P-002-oriented process screening checker.
 *
 * <p>
 * Evaluates calculated process values against NORSOK P-002 prescriptive design limits and collects {@link P002Finding}
 * objects. The checker is intentionally point-by-point - callers feed it the values from their NeqSim simulation
 * (header Mach numbers, blowdown momentum fluxes, carry-over fractions, etc.) and receive a structured pass/fail
 * report. Despite the legacy class and method names, a passing report covers only the supplied point checks and is not
 * a conformity assessment of the complete standard.
 * </p>
 *
 * <p>
 * Default screening values used (all configurable via the {@code check...} method overloads):
 * </p>
 *
 * <table>
 * <caption>Default P-002 limits</caption>
 * <tr>
 * <th>Criterion</th>
 * <th>Limit</th>
 * </tr>
 * <tr>
 * <td>Flare-line Mach</td>
 * <td>0.7</td>
 * </tr>
 * <tr>
 * <td>Blowdown ρv²</td>
 * <td>200 000 kg/(m·s²)</td>
 * </tr>
 * <tr>
 * <td>Vent gas velocity</td>
 * <td>60 m/s</td>
 * </tr>
 * <tr>
 * <td>Two-phase erosional ρv²</td>
 * <td>100 000 kg/(m·s²)</td>
 * </tr>
 * <tr>
 * <td>Liquid carry-over</td>
 * <td>0.001 (0.1 vol%)</td>
 * </tr>
 * </table>
 *
 * <p>
 * Projects must verify each limit against their purchased edition, project requirements, service, and approved design
 * basis before use.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class NorsokP002ComplianceChecker implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Default flare-line Mach number limit. */
  public static final double DEFAULT_FLARE_MACH_LIMIT = 0.7;
  /** Default blowdown momentum flux limit, kg/(m·s²). */
  public static final double DEFAULT_BLOWDOWN_RHO_V2_LIMIT = 200000.0;
  /** Default two-phase erosional velocity momentum flux limit, kg/(m·s²). */
  public static final double DEFAULT_EROSIONAL_RHO_V2_LIMIT = 100000.0;
  /** Default vent gas velocity limit, m/s. */
  public static final double DEFAULT_VENT_GAS_VELOCITY_LIMIT = 60.0;
  /** Default liquid carry-over fraction limit (volumetric). */
  public static final double DEFAULT_CARRY_OVER_LIMIT = 1.0e-3;

  private final List<P002Finding> findings = new ArrayList<P002Finding>();

  /**
   * Checks a flare-line segment against the Mach number limit.
   *
   * @param segmentName segment name
   * @param machNumber calculated gas Mach number (dimensionless)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkFlareLineMach(String segmentName, double machNumber) {
    return checkFlareLineMach(segmentName, machNumber, DEFAULT_FLARE_MACH_LIMIT);
  }

  /**
   * Checks a flare-line segment against a custom Mach number limit.
   *
   * @param segmentName segment name
   * @param machNumber calculated gas Mach number (dimensionless)
   * @param limit limit (default 0.7)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkFlareLineMach(String segmentName, double machNumber, double limit) {
    boolean ok = machNumber <= limit;
    findings.add(new P002Finding(segmentName, P002Criterion.FLARE_LINE_MACH_07, ok, machNumber, limit, "-",
        "Flare line Mach " + machNumber + (ok ? " <= " : " > ") + limit));
    return this;
  }

  /**
   * Checks a blowdown segment against the momentum-flux limit.
   *
   * @param segmentName segment name
   * @param rhoV2 ρv² in kg/(m·s²)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkBlowdownRhoV2(String segmentName, double rhoV2) {
    return checkBlowdownRhoV2(segmentName, rhoV2, DEFAULT_BLOWDOWN_RHO_V2_LIMIT);
  }

  /**
   * Checks a blowdown segment against a custom momentum-flux limit.
   *
   * @param segmentName segment name
   * @param rhoV2 ρv² in kg/(m·s²)
   * @param limit limit in kg/(m·s²) (default 200000)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkBlowdownRhoV2(String segmentName, double rhoV2, double limit) {
    boolean ok = rhoV2 <= limit;
    findings.add(new P002Finding(segmentName, P002Criterion.BLOWDOWN_RHO_V2, ok, rhoV2, limit, "kg/(m.s2)",
        "Blowdown rhoV2 " + rhoV2 + (ok ? " <= " : " > ") + limit));
    return this;
  }

  /**
   * Checks a two-phase pipeline segment against the erosional velocity limit.
   *
   * @param segmentName segment name
   * @param rhoV2 ρv² in kg/(m·s²)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkErosionalVelocity(String segmentName, double rhoV2) {
    return checkErosionalVelocity(segmentName, rhoV2, DEFAULT_EROSIONAL_RHO_V2_LIMIT);
  }

  /**
   * Checks a two-phase pipeline segment against a custom erosional velocity limit.
   *
   * @param segmentName segment name
   * @param rhoV2 ρv² in kg/(m·s²)
   * @param limit limit in kg/(m·s²)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkErosionalVelocity(String segmentName, double rhoV2, double limit) {
    boolean ok = rhoV2 <= limit;
    findings.add(new P002Finding(segmentName, P002Criterion.EROSIONAL_VELOCITY, ok, rhoV2, limit, "kg/(m.s2)",
        "Erosional rhoV2 " + rhoV2 + (ok ? " <= " : " > ") + limit));
    return this;
  }

  /**
   * Checks an atmospheric vent gas velocity.
   *
   * @param segmentName segment name
   * @param velocityMps gas velocity in m/s
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkVentGasVelocity(String segmentName, double velocityMps) {
    return checkVentGasVelocity(segmentName, velocityMps, DEFAULT_VENT_GAS_VELOCITY_LIMIT);
  }

  /**
   * Checks an atmospheric vent gas velocity against a custom limit.
   *
   * @param segmentName segment name
   * @param velocityMps gas velocity in m/s
   * @param limit limit in m/s (default 60)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkVentGasVelocity(String segmentName, double velocityMps, double limit) {
    boolean ok = velocityMps <= limit;
    findings.add(new P002Finding(segmentName, P002Criterion.VENT_GAS_VELOCITY, ok, velocityMps, limit, "m/s",
        "Vent gas velocity " + velocityMps + (ok ? " <= " : " > ") + limit));
    return this;
  }

  /**
   * Checks separator liquid carry-over fraction.
   *
   * @param equipmentName separator name
   * @param fraction volumetric liquid carry-over fraction (dimensionless)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkLiquidCarryOver(String equipmentName, double fraction) {
    return checkLiquidCarryOver(equipmentName, fraction, DEFAULT_CARRY_OVER_LIMIT);
  }

  /**
   * Checks separator liquid carry-over against a custom limit.
   *
   * @param equipmentName separator name
   * @param fraction volumetric liquid carry-over fraction
   * @param limit limit (default 1e-3)
   * @return this checker
   */
  public NorsokP002ComplianceChecker checkLiquidCarryOver(String equipmentName, double fraction, double limit) {
    boolean ok = fraction <= limit;
    findings.add(new P002Finding(equipmentName, P002Criterion.LIQUID_CARRY_OVER, ok, fraction, limit, "vol fr",
        "Liquid carry-over fraction " + fraction + (ok ? " <= " : " > ") + limit));
    return this;
  }

  /**
   * Records a depressurisation valve sizing check (result evaluated externally).
   *
   * @param valveName valve name
   * @param ok pass/fail flag
   * @param message free-form description
   * @return this checker
   */
  public NorsokP002ComplianceChecker recordDepressurisationValve(String valveName, boolean ok, String message) {
    findings.add(new P002Finding(valveName, P002Criterion.DEPRESSURISATION_VALVE_SIZE, ok, Double.NaN, Double.NaN, "-",
        message));
    return this;
  }

  /**
   * Records a drain slope / capacity check (evaluated externally).
   *
   * @param drainName drain name
   * @param ok pass/fail flag
   * @param message free-form description
   * @return this checker
   */
  public NorsokP002ComplianceChecker recordDrainSlope(String drainName, boolean ok, String message) {
    findings
        .add(new P002Finding(drainName, P002Criterion.DRAIN_SLOPE_CAPACITY, ok, Double.NaN, Double.NaN, "-", message));
    return this;
  }

  /**
   * @return findings in evaluation order
   */
  public List<P002Finding> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  /**
   * @return true if every finding is compliant
   */
  public boolean isCompliant() {
    for (P002Finding f : findings) {
      if (!f.isCompliant()) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return number of non-compliant findings
   */
  public int countNonCompliant() {
    int n = 0;
    for (P002Finding f : findings) {
      if (!f.isCompliant()) {
        n++;
      }
    }
    return n;
  }

  /**
   * @return findings as pretty JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(findings);
  }
}
