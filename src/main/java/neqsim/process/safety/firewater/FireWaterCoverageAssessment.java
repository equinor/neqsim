package neqsim.process.safety.firewater;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Gate that compares a required fire-water demand against what an existing fire-water system can actually deliver to an
 * area.
 *
 * <p>
 * Adding deluge coverage to an existing installation fails on one of three things, and the order matters because they
 * have different costs:
 * </p>
 * <ol>
 * <li><b>Coverage</b> — is any part of the area unprotected today?</li>
 * <li><b>Flow</b> — can the pumps and ring main deliver the extra water at the same time as the governing simultaneous
 * demand case?</li>
 * <li><b>Pressure</b> — is there margin between the pressure available at the deluge-skid inlet and the pressure the
 * new section needs? On a mature installation this is usually the binding constraint, and a negative margin in any
 * design case means the new load cannot simply be added to an existing skid.</li>
 * </ol>
 *
 * <p>
 * The verdict distinguishes these cases so a modification can be routed correctly: a flow deficit points at pump
 * capacity, a pressure deficit points at a new dedicated skid or a ring-main modification, and a pure coverage gap with
 * margin in hand points at simply extending the nozzle net.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FireWaterCoverageAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Outcome of the coverage and hydraulic screening. */
  public enum Verdict {
    /** Area is covered and the supply has margin. */
    COMPLIANT,
    /** Coverage gap exists and the supply can absorb the extra demand. */
    COVERAGE_GAP_SUPPLY_ADEQUATE,
    /** Supply cannot deliver the extra flow together with the governing simultaneous case. */
    SUPPLY_FLOW_DEFICIT,
    /** Pressure margin at the serving skid is insufficient or negative in a design case. */
    SUPPLY_PRESSURE_DEFICIT,
    /** Both flow and pressure are short; a stand-alone supply is required. */
    DEDICATED_SUPPLY_REQUIRED
  }

  private final double requiredFlowLpm;
  private final double protectedAreaM2;
  private double coveredAreaM2 = 0.0;
  private double existingDeliveredFlowLpm = 0.0;
  private double spareSupplyFlowLpm = Double.NaN;
  private double worstCasePressureMarginBar = Double.NaN;
  private double requiredPressureMarginBar = 0.0;
  private String servingSection = "";

  /**
   * Create a coverage assessment.
   *
   * @param protectedAreaM2 area that the governing philosophy requires to be covered, in m2, must be greater than zero
   * @param requiredFlowLpm fire-water flow the philosophy requires for that area, in l/min, must be greater than zero
   */
  public FireWaterCoverageAssessment(double protectedAreaM2, double requiredFlowLpm) {
    if (protectedAreaM2 <= 0.0) {
      throw new IllegalArgumentException("protectedAreaM2 must be > 0, got " + protectedAreaM2);
    }
    if (requiredFlowLpm <= 0.0) {
      throw new IllegalArgumentException("requiredFlowLpm must be > 0, got " + requiredFlowLpm);
    }
    this.protectedAreaM2 = protectedAreaM2;
    this.requiredFlowLpm = requiredFlowLpm;
  }

  /**
   * Set the area that existing fixed protection actually covers.
   *
   * @param coveredAreaM2 covered area in m2, must be zero or greater
   * @return this assessment, for chaining
   */
  public FireWaterCoverageAssessment setCoveredAreaM2(double coveredAreaM2) {
    if (coveredAreaM2 < 0.0) {
      throw new IllegalArgumentException("coveredAreaM2 must be >= 0, got " + coveredAreaM2);
    }
    this.coveredAreaM2 = coveredAreaM2;
    return this;
  }

  /**
   * Set the fire-water flow that existing fixed protection already delivers into the area.
   *
   * @param flowLpm delivered flow in l/min, must be zero or greater
   * @return this assessment, for chaining
   */
  public FireWaterCoverageAssessment setExistingDeliveredFlowLpm(double flowLpm) {
    if (flowLpm < 0.0) {
      throw new IllegalArgumentException("flow must be >= 0, got " + flowLpm);
    }
    this.existingDeliveredFlowLpm = flowLpm;
    return this;
  }

  /**
   * Set the spare pump and ring-main flow available on top of the governing simultaneous case.
   *
   * @param flowLpm spare flow in l/min; may be negative when the governing case already exceeds the installed capacity
   * @return this assessment, for chaining
   */
  public FireWaterCoverageAssessment setSpareSupplyFlowLpm(double flowLpm) {
    this.spareSupplyFlowLpm = flowLpm;
    return this;
  }

  /**
   * Set the pressure margin at the serving deluge section in its worst design case.
   *
   * @param marginBar available pressure minus required pressure in bar; negative means the existing section already
   * fails to make its design pressure in that case
   * @param servingSection identifier of the deluge section or skid, for traceability
   * @return this assessment, for chaining
   */
  public FireWaterCoverageAssessment setWorstCasePressureMarginBar(double marginBar, String servingSection) {
    this.worstCasePressureMarginBar = marginBar;
    this.servingSection = servingSection;
    return this;
  }

  /**
   * Set the pressure margin that must remain after the new load is added.
   *
   * @param marginBar required residual margin in bar, must be zero or greater
   * @return this assessment, for chaining
   */
  public FireWaterCoverageAssessment setRequiredPressureMarginBar(double marginBar) {
    if (marginBar < 0.0) {
      throw new IllegalArgumentException("required margin must be >= 0, got " + marginBar);
    }
    this.requiredPressureMarginBar = marginBar;
    return this;
  }

  /**
   * Uncovered area.
   *
   * @return coverage gap in m2
   */
  public double coverageGapM2() {
    double gap = protectedAreaM2 - coveredAreaM2;
    return gap < 0.0 ? 0.0 : gap;
  }

  /**
   * Fraction of the required area that is covered today.
   *
   * @return coverage fraction between 0 and 1
   */
  public double coverageFraction() {
    double f = coveredAreaM2 / protectedAreaM2;
    return f > 1.0 ? 1.0 : f;
  }

  /**
   * Additional fire-water flow needed to close the gap.
   *
   * @return flow deficit in l/min, zero when already satisfied
   */
  public double flowDeficitLpm() {
    double d = requiredFlowLpm - existingDeliveredFlowLpm;
    return d < 0.0 ? 0.0 : d;
  }

  /**
   * Whether the supply has enough spare flow for the deficit.
   *
   * @return true when spare flow is unknown or covers the deficit
   */
  public boolean isFlowFeasible() {
    if (Double.isNaN(spareSupplyFlowLpm)) {
      return true;
    }
    return spareSupplyFlowLpm >= flowDeficitLpm();
  }

  /**
   * Whether the serving section has the required residual pressure margin.
   *
   * @return true when the margin is unknown or at or above the required residual margin
   */
  public boolean isPressureFeasible() {
    if (Double.isNaN(worstCasePressureMarginBar)) {
      return true;
    }
    return worstCasePressureMarginBar >= requiredPressureMarginBar;
  }

  /**
   * Screening verdict.
   *
   * @return the verdict
   */
  public Verdict verdict() {
    boolean gap = coverageGapM2() > 0.0 || flowDeficitLpm() > 0.0;
    boolean flowOk = isFlowFeasible();
    boolean pressOk = isPressureFeasible();
    if (!gap) {
      return Verdict.COMPLIANT;
    }
    if (!flowOk && !pressOk) {
      return Verdict.DEDICATED_SUPPLY_REQUIRED;
    }
    if (!pressOk) {
      return Verdict.SUPPLY_PRESSURE_DEFICIT;
    }
    if (!flowOk) {
      return Verdict.SUPPLY_FLOW_DEFICIT;
    }
    return Verdict.COVERAGE_GAP_SUPPLY_ADEQUATE;
  }

  /**
   * Routing advice matching the verdict.
   *
   * @return a short recommendation string
   */
  public String recommendation() {
    switch (verdict()) {
    case COMPLIANT:
      return "No modification required on coverage or hydraulic grounds.";
    case COVERAGE_GAP_SUPPLY_ADEQUATE:
      return "Extend the nozzle net from the existing serving section; supply has margin.";
    case SUPPLY_FLOW_DEFICIT:
      return "Nozzle net alone is not enough: pump or ring-main capacity must be increased, or the "
          + "simultaneous-release philosophy revisited.";
    case SUPPLY_PRESSURE_DEFICIT:
      return "Do not add load to section " + servingSection
          + ": its worst-case pressure margin is already inadequate. Route the new demand to a "
          + "dedicated skid or upgrade the supply pressure.";
    case DEDICATED_SUPPLY_REQUIRED:
    default:
      return "Neither flow nor pressure margin exists. A stand-alone fire-water package with its "
          + "own ring-main tie-in is the only route that does not degrade existing protection.";
    }
  }

  /**
   * Serialise the assessment to JSON.
   *
   * @return pretty-printed JSON document
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("protectedAreaM2", protectedAreaM2);
    root.addProperty("coveredAreaM2", coveredAreaM2);
    root.addProperty("coverageGapM2", coverageGapM2());
    root.addProperty("coverageFraction", coverageFraction());
    root.addProperty("requiredFlowLpm", requiredFlowLpm);
    root.addProperty("existingDeliveredFlowLpm", existingDeliveredFlowLpm);
    root.addProperty("flowDeficitLpm", flowDeficitLpm());
    root.addProperty("spareSupplyFlowLpm", spareSupplyFlowLpm);
    root.addProperty("servingSection", servingSection);
    root.addProperty("worstCasePressureMarginBar", worstCasePressureMarginBar);
    root.addProperty("requiredPressureMarginBar", requiredPressureMarginBar);
    root.addProperty("flowFeasible", isFlowFeasible());
    root.addProperty("pressureFeasible", isPressureFeasible());
    root.addProperty("verdict", verdict().name());
    root.addProperty("recommendation", recommendation());
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
