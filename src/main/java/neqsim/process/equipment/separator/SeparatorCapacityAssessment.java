package neqsim.process.equipment.separator;

import java.io.Serializable;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable evidence snapshot for a separator Souders-Brown gas-capacity assessment.
 *
 * <p>
 * The snapshot is detached from the separator and fails closed when geometry, thermodynamic state, design K-factor, or
 * design-basis provenance is unavailable. It does not size equipment or approve operation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class SeparatorCapacityAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Assessment outcome. */
  public enum Status {
    /** Complete evidence and utilization no greater than one. */
    FEASIBLE,
    /** Complete evidence and utilization greater than one. */
    INFEASIBLE,
    /** Required evidence is missing or non-finite. */
    UNASSESSED
  }

  private final String separatorName;
  private final Status status;
  private final String diagnostic;
  private final String designBasisProvenance;
  private final String designBasisSource;
  private final double designBasisConfidence;
  private final String governingCapacityBasis;
  private final String orientation;
  private final double internalDiameterM;
  private final double designLiquidLevelFraction;
  private final double gasAreaM2;
  private final double gasDensityKgM3;
  private final double liquidDensityKgM3;
  private final double designGasLoadFactorMPerS;
  private final double maximumGasVelocityMPerS;
  private final double currentGasFlowM3PerS;
  private final double mechanicalDesignMaximumGasFlowM3PerS;
  private final double soudersBrownMaximumGasFlowM3PerS;
  private final double maximumGasFlowM3PerS;
  private final double utilization;

  /**
   * Creates an immutable snapshot.
   *
   * @param separatorName separator name
   * @param status assessment status
   * @param diagnostic status explanation
   * @param designBasisProvenance source of the design capacity basis
   * @param designBasisSource NeqSim object that supplied the governing design limit
   * @param designBasisConfidence confidence assigned to the governing design constraint
   * @param governingCapacityBasis name of the governing capacity calculation
   * @param orientation separator orientation
   * @param internalDiameterM internal diameter in metres
   * @param designLiquidLevelFraction design liquid level fraction
   * @param gasAreaM2 available gas flow area in square metres
   * @param gasDensityKgM3 gas density in kilograms per cubic metre
   * @param liquidDensityKgM3 liquid density in kilograms per cubic metre
   * @param designGasLoadFactorMPerS design K-factor in metres per second
   * @param maximumGasVelocityMPerS maximum Souders-Brown gas velocity in metres per second
   * @param currentGasFlowM3PerS current gas volumetric flow in cubic metres per second
   * @param mechanicalDesignMaximumGasFlowM3PerS mechanical-design gas limit in cubic metres per second
   * @param soudersBrownMaximumGasFlowM3PerS Souders-Brown gas limit in cubic metres per second
   * @param maximumGasFlowM3PerS maximum gas volumetric flow in cubic metres per second
   * @param utilization current flow divided by maximum flow
   */
  private SeparatorCapacityAssessment(String separatorName, Status status, String diagnostic,
      String designBasisProvenance, String designBasisSource, double designBasisConfidence,
      String governingCapacityBasis, String orientation, double internalDiameterM, double designLiquidLevelFraction,
      double gasAreaM2, double gasDensityKgM3, double liquidDensityKgM3, double designGasLoadFactorMPerS,
      double maximumGasVelocityMPerS, double currentGasFlowM3PerS, double mechanicalDesignMaximumGasFlowM3PerS,
      double soudersBrownMaximumGasFlowM3PerS, double maximumGasFlowM3PerS, double utilization) {
    this.separatorName = separatorName;
    this.status = status;
    this.diagnostic = diagnostic;
    this.designBasisProvenance = designBasisProvenance;
    this.designBasisSource = designBasisSource;
    this.designBasisConfidence = designBasisConfidence;
    this.governingCapacityBasis = governingCapacityBasis;
    this.orientation = orientation;
    this.internalDiameterM = internalDiameterM;
    this.designLiquidLevelFraction = designLiquidLevelFraction;
    this.gasAreaM2 = gasAreaM2;
    this.gasDensityKgM3 = gasDensityKgM3;
    this.liquidDensityKgM3 = liquidDensityKgM3;
    this.designGasLoadFactorMPerS = designGasLoadFactorMPerS;
    this.maximumGasVelocityMPerS = maximumGasVelocityMPerS;
    this.currentGasFlowM3PerS = currentGasFlowM3PerS;
    this.mechanicalDesignMaximumGasFlowM3PerS = mechanicalDesignMaximumGasFlowM3PerS;
    this.soudersBrownMaximumGasFlowM3PerS = soudersBrownMaximumGasFlowM3PerS;
    this.maximumGasFlowM3PerS = maximumGasFlowM3PerS;
    this.utilization = utilization;
  }

  /**
   * Captures current separator gas-capacity evidence without changing separator state.
   *
   * @param separator separator to assess
   * @param designBasisProvenance non-blank source for geometry and K-factor limits
   * @return detached immutable assessment
   */
  static SeparatorCapacityAssessment from(Separator separator, String designBasisProvenance) {
    String provenance = designBasisProvenance == null ? "" : designBasisProvenance.trim();
    String name = separator == null ? "" : separator.getName();
    if (separator == null) {
      return unavailable(name, provenance, "Separator must not be null");
    }
    if (provenance.isEmpty()) {
      return unavailable(name, provenance, "Design-basis provenance is required");
    }
    String orientation = separator.getOrientation();
    double diameter = separator.getInternalDiameter();
    double liquidLevel = separator.getDesignLiquidLevelFraction();
    double kFactor = separator.getDesignGasLoadFactor();
    SystemInterface fluid = separator.getThermoSystem();
    if (!("horizontal".equals(orientation) || "vertical".equals(orientation))) {
      return unavailable(name, provenance, "Separator orientation must be horizontal or vertical");
    }
    if (!finitePositive(diameter) || !finitePositive(kFactor) || fluid == null || !fluid.hasPhaseType("gas")) {
      return unavailable(name, provenance, "Finite geometry, design K-factor, and a calculated gas phase are required");
    }
    if (!Double.isFinite(liquidLevel) || liquidLevel < 0.0 || liquidLevel >= 1.0) {
      return unavailable(name, provenance, "Design liquid level fraction must be in [0, 1)");
    }

    fluid.initPhysicalProperties();
    double gasDensity = fluid.getPhase("gas").getPhysicalProperties().getDensity();
    double liquidDensity = Separator.DEFAULT_LIQUID_DENSITY_FOR_SIZING;
    if (fluid.hasPhaseType("oil")) {
      liquidDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();
    } else if (fluid.hasPhaseType("aqueous")) {
      liquidDensity = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
    }
    double totalArea = Math.PI * diameter * diameter / 4.0;
    double gasArea = "horizontal".equals(orientation) ? totalArea * (1.0 - liquidLevel) : totalArea;
    double maximumVelocity = separator.getMaxAllowableGasVelocity();
    double currentFlow = fluid.getPhase("gas").getFlowRate("m3/sec");
    double soudersBrownMaximumFlow = separator.getMaxAllowableGasFlowRate();
    SeparatorMechanicalDesign mechanicalDesign = separator.getMechanicalDesign();
    double mechanicalDesignMaximumFlow = mechanicalDesign.getMaxDesignGassVolumeFlow() / 3600.0;
    CapacityConstraint designConstraint = findMechanicalDesignGasConstraint(separator, mechanicalDesign);
    String designBasisSource = designConstraint == null ? "equipment" : designConstraint.getDataSource();
    double designBasisConfidence = designConstraint != null && designConstraint.hasConfidence()
        ? designConstraint.getConfidence()
        : Double.NaN;
    boolean mechanicalDesignGoverns = finitePositive(mechanicalDesignMaximumFlow)
        && mechanicalDesignMaximumFlow <= soudersBrownMaximumFlow;
    double maximumFlow = mechanicalDesignGoverns ? mechanicalDesignMaximumFlow : soudersBrownMaximumFlow;
    String governingCapacityBasis = mechanicalDesignGoverns ? "mechanicalDesignGasFlow" : "soudersBrown";
    double utilization = maximumFlow > 0.0 ? currentFlow / maximumFlow : Double.NaN;
    if (!finitePositive(gasDensity) || !finitePositive(liquidDensity) || !finitePositive(gasArea)
        || !finitePositive(maximumVelocity) || !Double.isFinite(currentFlow) || currentFlow < 0.0
        || !finitePositive(soudersBrownMaximumFlow) || !finitePositive(maximumFlow) || !Double.isFinite(utilization)
        || utilization < 0.0) {
      return unavailable(name, provenance, "Separator capacity calculation returned non-finite evidence");
    }
    Status status = utilization <= 1.0 ? Status.FEASIBLE : Status.INFEASIBLE;
    String diagnostic = status == Status.FEASIBLE ? "Gas capacity is within the evidenced design limit"
        : "Gas capacity exceeds the evidenced design limit";
    return new SeparatorCapacityAssessment(name, status, diagnostic, provenance, designBasisSource,
        designBasisConfidence, governingCapacityBasis, orientation, diameter, liquidLevel, gasArea, gasDensity,
        liquidDensity, kFactor, maximumVelocity, currentFlow, mechanicalDesignMaximumFlow, soudersBrownMaximumFlow,
        maximumFlow, utilization);
  }

  /**
   * Finds the existing or derived mechanical-design gas-flow constraint.
   *
   * @param separator separator whose registered constraints are inspected
   * @param mechanicalDesign separator mechanical design
   * @return mechanical-design gas-flow constraint, or {@code null} when no positive limit exists
   */
  private static CapacityConstraint findMechanicalDesignGasConstraint(Separator separator,
      SeparatorMechanicalDesign mechanicalDesign) {
    CapacityConstraint registered = separator.getCapacityConstraints().get("design gas volume flow");
    if (registered != null) {
      return registered;
    }
    List<CapacityConstraint> derived = mechanicalDesign.getDesignCapacityConstraints();
    for (CapacityConstraint constraint : derived) {
      if ("design gas volume flow".equals(constraint.getName())) {
        return constraint;
      }
    }
    return null;
  }

  /**
   * Creates an unassessed snapshot with unavailable numeric evidence.
   *
   * @param name separator name
   * @param provenance design-basis provenance
   * @param diagnostic reason the assessment is unavailable
   * @return unassessed snapshot
   */
  private static SeparatorCapacityAssessment unavailable(String name, String provenance, String diagnostic) {
    return new SeparatorCapacityAssessment(name, Status.UNASSESSED, diagnostic, provenance, "", Double.NaN, "", "",
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN);
  }

  /**
   * Tests whether a value is finite and positive.
   *
   * @param value value to test
   * @return true when the value is finite and greater than zero
   */
  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  /** @return separator name */
  public String getSeparatorName() {
    return separatorName;
  }

  /** @return assessment status */
  public Status getStatus() {
    return status;
  }

  /** @return true only for complete evidence within capacity */
  public boolean isFeasible() {
    return status == Status.FEASIBLE;
  }

  /** @return status explanation */
  public String getDiagnostic() {
    return diagnostic;
  }

  /** @return source for geometry and K-factor limits */
  public String getDesignBasisProvenance() {
    return designBasisProvenance;
  }

  /** @return NeqSim source of the governing design limit */
  public String getDesignBasisSource() {
    return designBasisSource;
  }

  /** @return confidence assigned to the design constraint, or NaN when unset */
  public double getDesignBasisConfidence() {
    return designBasisConfidence;
  }

  /** @return name of the governing capacity calculation */
  public String getGoverningCapacityBasis() {
    return governingCapacityBasis;
  }

  /** @return separator orientation */
  public String getOrientation() {
    return orientation;
  }

  /** @return internal diameter in metres */
  public double getInternalDiameterM() {
    return internalDiameterM;
  }

  /** @return design liquid level fraction */
  public double getDesignLiquidLevelFraction() {
    return designLiquidLevelFraction;
  }

  /** @return available gas area in square metres */
  public double getGasAreaM2() {
    return gasAreaM2;
  }

  /** @return gas density in kilograms per cubic metre */
  public double getGasDensityKgM3() {
    return gasDensityKgM3;
  }

  /** @return liquid density in kilograms per cubic metre */
  public double getLiquidDensityKgM3() {
    return liquidDensityKgM3;
  }

  /** @return design K-factor in metres per second */
  public double getDesignGasLoadFactorMPerS() {
    return designGasLoadFactorMPerS;
  }

  /** @return maximum gas velocity in metres per second */
  public double getMaximumGasVelocityMPerS() {
    return maximumGasVelocityMPerS;
  }

  /** @return current gas flow in cubic metres per second */
  public double getCurrentGasFlowM3PerS() {
    return currentGasFlowM3PerS;
  }

  /** @return mechanical-design maximum gas flow in cubic metres per second, or NaN when unset */
  public double getMechanicalDesignMaximumGasFlowM3PerS() {
    return mechanicalDesignMaximumGasFlowM3PerS;
  }

  /** @return Souders-Brown maximum gas flow in cubic metres per second */
  public double getSoudersBrownMaximumGasFlowM3PerS() {
    return soudersBrownMaximumGasFlowM3PerS;
  }

  /** @return maximum gas flow in cubic metres per second */
  public double getMaximumGasFlowM3PerS() {
    return maximumGasFlowM3PerS;
  }

  /** @return current-to-maximum flow ratio */
  public double getUtilization() {
    return utilization;
  }

  /**
   * Serializes this assessment for detached reporting.
   *
   * @return JSON representation with explicit special floating-point values
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}