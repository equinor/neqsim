package neqsim.process.costestimation.pipe;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Cost estimation class for pipelines and piping systems.
 *
 * <p>
 * This class provides pipe-specific cost estimation methods using chemical engineering cost
 * correlations for carbon steel, stainless steel, and other piping materials. Includes costs for
 * fittings, flanges, valves, and installation.
 * </p>
 *
 * @author AGAS
 * @version 1.0
 */
public class PipeCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Pipe nominal diameter in inches. */
  private double nominalDiameter = 6.0;

  /** Pipe length in meters. */
  private double pipeLength = 100.0;

  /** Pipe schedule. */
  private String pipeSchedule = "40";

  /** Installation type. */
  private String installationType = "above-ground";

  /** Include fittings cost. */
  private boolean includeFittings = true;

  /** Number of fittings per 100m. */
  private int fittingsPerHundredMeters = 10;

  /** Number of flanged connections. */
  private int numberOfFlanges = 2;

  /** Include insulation. */
  private boolean includeInsulation = false;

  /** Insulation thickness in mm. */
  private double insulationThickness = 50.0;

  /**
   * Constructor for PipeCostEstimate.
   *
   * @param mechanicalEquipment the pipe mechanical design
   */
  public PipeCostEstimate(MechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("piping");
  }

  /**
   * Set nominal diameter.
   *
   * @param diameter nominal diameter in inches
   */
  public void setNominalDiameter(double diameter) {
    this.nominalDiameter = diameter;
  }

  /**
   * Get nominal diameter.
   *
   * @return nominal diameter in inches
   */
  public double getNominalDiameter() {
    return nominalDiameter;
  }

  /**
   * Set pipe length.
   *
   * @param length pipe length in meters
   */
  public void setPipeLength(double length) {
    this.pipeLength = length;
  }

  /**
   * Get pipe length.
   *
   * @return pipe length in meters
   */
  public double getPipeLength() {
    return pipeLength;
  }

  /**
   * Set pipe schedule.
   *
   * @param schedule pipe schedule ("10", "40", "80", "160", "XXS")
   */
  public void setPipeSchedule(String schedule) {
    this.pipeSchedule = schedule;
  }

  /**
   * Set installation type.
   *
   * @param type installation type ("above-ground", "buried", "subsea")
   */
  public void setInstallationType(String type) {
    this.installationType = type;
  }

  /**
   * Set whether to include fittings.
   *
   * @param include true to include fittings
   */
  public void setIncludeFittings(boolean include) {
    this.includeFittings = include;
  }

  /**
   * Set fittings per 100m of pipe.
   *
   * @param count number of fittings
   */
  public void setFittingsPerHundredMeters(int count) {
    this.fittingsPerHundredMeters = count;
  }

  /**
   * Set number of flanges.
   *
   * @param count number of flanged connections
   */
  public void setNumberOfFlanges(int count) {
    this.numberOfFlanges = count;
  }

  /**
   * Set whether to include insulation.
   *
   * @param include true to include insulation
   */
  public void setIncludeInsulation(boolean include) {
    this.includeInsulation = include;
  }

  /**
   * Set insulation thickness.
   *
   * @param thickness insulation thickness in mm
   */
  public void setInsulationThickness(double thickness) {
    this.insulationThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate base piping cost
    int scheduleInt = 40;
    if ("80".equals(pipeSchedule) || "80S".equals(pipeSchedule)) {
      scheduleInt = 80;
    } else if ("160".equals(pipeSchedule)) {
      scheduleInt = 160;
    }
    double pipingCost =
        getCostCalculator().calcPipingCost(nominalDiameter * 0.0254, pipeLength, scheduleInt);

    // Apply material factor
    pipingCost *= getMaterialFactor();

    // Add fittings cost
    if (includeFittings) {
      pipingCost += calcFittingsCost();
    }

    // Add flanges cost
    pipingCost += calcFlangesCost();

    // Apply installation type factor
    pipingCost *= getInstallationTypeFactor();

    // Add insulation cost
    if (includeInsulation) {
      pipingCost += calcInsulationCost();
    }

    return pipingCost;
  }

  /**
   * Get pipe schedule factor.
   *
   * @return schedule factor
   */
  private double getScheduleFactor() {
    if ("10".equals(pipeSchedule) || "10S".equals(pipeSchedule)) {
      return 0.7;
    } else if ("40".equals(pipeSchedule) || "40S".equals(pipeSchedule)) {
      return 1.0;
    } else if ("80".equals(pipeSchedule) || "80S".equals(pipeSchedule)) {
      return 1.4;
    } else if ("160".equals(pipeSchedule)) {
      return 2.0;
    } else if ("XXS".equals(pipeSchedule)) {
      return 2.5;
    }
    return 1.0;
  }

  /**
   * Get installation type factor.
   *
   * @return installation type factor
   */
  private double getInstallationTypeFactor() {
    if ("buried".equalsIgnoreCase(installationType)) {
      return 1.5; // Trenching, backfill, coating
    } else if ("subsea".equalsIgnoreCase(installationType)) {
      return 3.0; // Laying vessel, coating, risers
    }
    return 1.0; // Above-ground
  }

  /**
   * Calculate fittings cost.
   *
   * @return fittings cost in USD
   */
  private double calcFittingsCost() {
    int totalFittings = (int) (fittingsPerHundredMeters * pipeLength / 100.0);
    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    // Average fitting cost based on size
    double avgFittingCost = 30.0 + 15.0 * Math.pow(nominalDiameter, 0.8);
    return totalFittings * avgFittingCost * getMaterialFactor() * cepciRatio;
  }

  /**
   * Calculate flanges cost.
   *
   * @return flanges cost in USD
   */
  private double calcFlangesCost() {
    if (numberOfFlanges <= 0) {
      return 0.0;
    }

    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    // Flange pair cost based on size and rating
    double baseFlangePrice = 50.0 + 30.0 * Math.pow(nominalDiameter, 1.2);
    return numberOfFlanges * baseFlangePrice * getMaterialFactor() * cepciRatio;
  }

  /**
   * Calculate insulation cost.
   *
   * @return insulation cost in USD
   */
  private double calcInsulationCost() {
    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    // Convert diameter to meters
    double outerDiam = nominalDiameter * 0.0254;
    // Surface area per meter
    double surfaceArea = Math.PI * outerDiam * pipeLength;

    // Insulation cost: ~$30/m2 for mineral wool
    double baseCost = surfaceArea * 30.0;

    // Adjust for thickness
    baseCost *= (1.0 + insulationThickness / 100.0);

    return baseCost * cepciRatio;
  }

  /**
   * Calculate pipe weight.
   *
   * @return pipe weight in kg
   */
  public double calcPipeWeight() {
    // Get wall thickness from schedule (approximate)
    double wallThickness = getScheduleWallThickness();

    // Convert to meters
    double outerDiam = nominalDiameter * 0.0254;
    double innerDiam = outerDiam - 2 * wallThickness;

    // Cross-sectional area
    double crossArea = Math.PI / 4.0 * (outerDiam * outerDiam - innerDiam * innerDiam);

    // Steel density 7850 kg/m3
    return crossArea * pipeLength * 7850.0;
  }

  /**
   * Get wall thickness from schedule (approximate values).
   *
   * @return wall thickness in meters
   */
  private double getScheduleWallThickness() {
    // Simplified - actual values depend on diameter
    double baseThickness;
    if (nominalDiameter <= 2) {
      baseThickness = 0.003;
    } else if (nominalDiameter <= 6) {
      baseThickness = 0.0055;
    } else if (nominalDiameter <= 12) {
      baseThickness = 0.0095;
    } else {
      baseThickness = 0.0125;
    }

    return baseThickness * getScheduleFactor();
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    if (totalModuleCost > 0) {
      return totalModuleCost;
    }

    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }

    return totalModuleCost;
  }

  /**
   * Get cost breakdown as map.
   *
   * @return map with cost breakdown
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    calculateCostEstimate();

    breakdown.put("nominalDiameter_inches", nominalDiameter);
    breakdown.put("pipeLength_m", pipeLength);
    breakdown.put("pipeSchedule", pipeSchedule);
    breakdown.put("materialGrade", getMaterialGrade());
    breakdown.put("installationType", installationType);
    breakdown.put("includeFittings", includeFittings);
    breakdown.put("numberOfFlanges", numberOfFlanges);
    breakdown.put("includeInsulation", includeInsulation);
    breakdown.put("pipeWeight_kg", calcPipeWeight());
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("costPerMeter_USD", purchasedEquipmentCost / pipeLength);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    return breakdown;
  }
}
