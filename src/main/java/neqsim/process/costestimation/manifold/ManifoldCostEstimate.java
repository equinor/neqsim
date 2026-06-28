package neqsim.process.costestimation.manifold;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesign;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldType;

/**
 * Screening-level purchased equipment cost estimator for process manifolds.
 *
 * <p>
 * The estimate uses the dry weight and topology produced by {@link ManifoldMechanicalDesign}, with
 * allowances for valves, branch hubs, instrumentation, and location-specific fabrication
 * complexity. It covers onshore, topside, and subsea manifolds in the same process cost rollup used
 * for other unit operations.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ManifoldCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Base fabricated manifold cost in USD per dry kg for a topside carbon-steel unit. */
  private double baseCostUSDPerKg = 65.0;

  /** Base valve, hub, and instrument allowance per inlet/outlet branch in USD. */
  private double branchAllowanceUSD = 18000.0;

  /**
   * Constructor for ManifoldCostEstimate.
   *
   * @param mechanicalEquipment the manifold mechanical design
   */
  public ManifoldCostEstimate(ManifoldMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("manifold");
  }

  /**
   * Set base fabricated cost per dry kilogram.
   *
   * @param baseCostUSDPerKg cost in USD/kg, negative values are clamped to zero
   */
  public void setBaseCostUSDPerKg(double baseCostUSDPerKg) {
    this.baseCostUSDPerKg = Math.max(0.0, baseCostUSDPerKg);
  }

  /**
   * Set valve, hub, and instrument allowance per branch.
   *
   * @param branchAllowanceUSD allowance in USD per inlet/outlet branch, negative values are clamped
   *        to zero
   */
  public void setBranchAllowanceUSD(double branchAllowanceUSD) {
    this.branchAllowanceUSD = Math.max(0.0, branchAllowanceUSD);
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (!(mechanicalEquipment instanceof ManifoldMechanicalDesign)) {
      return super.calcPurchasedEquipmentCost();
    }

    ManifoldMechanicalDesign manifoldDesign = (ManifoldMechanicalDesign) mechanicalEquipment;
    int branchCount =
        Math.max(manifoldDesign.getNumberOfInlets() + manifoldDesign.getNumberOfOutlets(), 1);
    double weight = manifoldDesign.getWeightTotal();
    if (weight <= 0.0) {
      weight = estimateDryWeight(manifoldDesign, branchCount);
    }

    double fabricationCost =
        weight * baseCostUSDPerKg * locationFactor(manifoldDesign.getLocation())
            * materialFactor(manifoldDesign.getMaterialGrade())
            * typeFactor(manifoldDesign.getManifoldType());
    double branchCost =
        branchCount * branchAllowanceUSD * pressureFactor(manifoldDesign.getHeaderDiameter());
    double subseaInstallationAllowance = subseaInstallationAllowance(manifoldDesign);

    return fabricationCost + branchCost + subseaInstallationAllowance;
  }

  /**
   * Estimate dry weight when the mechanical calculator has not produced one.
   *
   * @param manifoldDesign manifold mechanical design
   * @param branchCount inlet plus outlet branch count
   * @return estimated dry weight in kg
   */
  private double estimateDryWeight(ManifoldMechanicalDesign manifoldDesign, int branchCount) {
    double headerDiameter = Math.max(manifoldDesign.getHeaderDiameter(), 0.1);
    double branchDiameter = Math.max(manifoldDesign.getBranchDiameter(), 0.05);
    double steelDensity = 7850.0;
    double headerLength = Math.max(4.0, branchCount * 1.2);
    double branchLength = 1.5;
    double headerThickness = Math.max(headerDiameter * 0.04, 0.008);
    double branchThickness = Math.max(branchDiameter * 0.04, 0.006);
    double headerWeight = Math.PI * headerDiameter * headerThickness * headerLength * steelDensity;
    double branchWeight =
        Math.PI * branchDiameter * branchThickness * branchLength * steelDensity * branchCount;
    return (headerWeight + branchWeight) * 2.2;
  }

  /**
   * Location factor for fabrication complexity.
   *
   * @param location manifold location
   * @return location multiplier
   */
  private double locationFactor(ManifoldLocation location) {
    if (ManifoldLocation.SUBSEA.equals(location)) {
      return 2.6;
    }
    if (ManifoldLocation.ONSHORE.equals(location)) {
      return 0.75;
    }
    return 1.0;
  }

  /**
   * Material factor inferred from grade string.
   *
   * @param materialGrade material grade
   * @return material multiplier
   */
  private double materialFactor(String materialGrade) {
    if (materialGrade == null) {
      return 1.0;
    }
    String grade = materialGrade.toUpperCase();
    if (grade.contains("DUPLEX") || grade.contains("22CR") || grade.contains("25CR")) {
      return 2.2;
    }
    if (grade.contains("316") || grade.contains("SS") || grade.contains("STAINLESS")) {
      return 1.8;
    }
    return 1.0;
  }

  /**
   * Manifold service/type multiplier.
   *
   * @param type manifold type
   * @return service multiplier
   */
  private double typeFactor(ManifoldType type) {
    if (ManifoldType.PIGGING.equals(type) || ManifoldType.TEST.equals(type)) {
      return 1.2;
    }
    if (ManifoldType.INJECTION.equals(type)) {
      return 1.1;
    }
    return 1.0;
  }

  /**
   * Pressure-class proxy from header diameter for valves and hubs.
   *
   * @param headerDiameter header outside diameter in m
   * @return multiplier for branch allowance
   */
  private double pressureFactor(double headerDiameter) {
    if (headerDiameter >= 0.4) {
      return 1.8;
    }
    if (headerDiameter >= 0.25) {
      return 1.4;
    }
    return 1.0;
  }

  /**
   * Adds a screening installation and protection allowance for subsea manifolds.
   *
   * @param manifoldDesign manifold mechanical design
   * @return subsea allowance in USD
   */
  private double subseaInstallationAllowance(ManifoldMechanicalDesign manifoldDesign) {
    if (!ManifoldLocation.SUBSEA.equals(manifoldDesign.getLocation())) {
      return 0.0;
    }
    return 250000.0 + Math.max(manifoldDesign.getWaterDepth(), 0.0) * 350.0;
  }
}
