package neqsim.process.costestimation.absorber;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.absorber.AbsorberMechanicalDesign;

/**
 * Cost estimation class for absorbers.
 *
 * <p>
 * This class provides absorber-specific cost estimation methods for gas absorption towers, TEG
 * contactors, amine columns, and other mass transfer equipment.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Turton et al. - Analysis, Synthesis and Design of Chemical Processes</li>
 * <li>Peters &amp; Timmerhaus - Plant Design and Economics</li>
 * <li>GPSA Engineering Data Book</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class AbsorberCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Absorber type. */
  private String absorberType = "packed";

  /** Packing type (for packed columns). */
  private String packingType = "structured";

  /** Tray type (for trayed columns). */
  private String trayType = "sieve";

  /** Column diameter in meters. */
  private double columnDiameter = 1.5;

  /** Column height in meters. */
  private double columnHeight = 15.0;

  /** Number of theoretical stages. */
  private int numberOfStages = 10;

  /** Packing height in meters. */
  private double packingHeight = 8.0;

  /** Design pressure in barg. */
  private double designPressure = 50.0;

  /** Include liquid distributor. */
  private boolean includeLiquidDistributor = true;

  /** Include mist eliminator. */
  private boolean includeMistEliminator = true;

  /** Include reboiler. */
  private boolean includeReboiler = false;

  /** Include reflux system. */
  private boolean includeRefluxSystem = false;

  /** Reboiler duty in kW. */
  private double reboilerDuty = 500.0;

  /**
   * Constructor for AbsorberCostEstimate.
   *
   * @param mechanicalEquipment the absorber mechanical design
   */
  public AbsorberCostEstimate(AbsorberMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("absorber");
  }

  /**
   * Set absorber type.
   *
   * @param type absorber type ("packed", "trayed", "spray")
   */
  public void setAbsorberType(String type) {
    this.absorberType = type;
  }

  /**
   * Get absorber type.
   *
   * @return absorber type
   */
  public String getAbsorberType() {
    return absorberType;
  }

  /**
   * Set packing type.
   *
   * @param type packing type ("structured", "random", "grid")
   */
  public void setPackingType(String type) {
    this.packingType = type;
  }

  /**
   * Set tray type.
   *
   * @param type tray type ("sieve", "valve", "bubble-cap")
   */
  public void setTrayType(String type) {
    this.trayType = type;
  }

  /**
   * Set column diameter.
   *
   * @param diameter column diameter in meters
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
  }

  /**
   * Get column diameter.
   *
   * @return column diameter in meters
   */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /**
   * Set column height.
   *
   * @param height column height in meters
   */
  public void setColumnHeight(double height) {
    this.columnHeight = height;
  }

  /**
   * Set number of stages.
   *
   * @param stages number of theoretical stages
   */
  public void setNumberOfStages(int stages) {
    this.numberOfStages = stages;
  }

  /**
   * Set packing height.
   *
   * @param height packing height in meters
   */
  public void setPackingHeight(double height) {
    this.packingHeight = height;
  }

  /**
   * Set design pressure.
   *
   * @param pressure design pressure in barg
   */
  public void setDesignPressure(double pressure) {
    this.designPressure = pressure;
  }

  /**
   * Set whether to include liquid distributor.
   *
   * @param include true to include
   */
  public void setIncludeLiquidDistributor(boolean include) {
    this.includeLiquidDistributor = include;
  }

  /**
   * Set whether to include mist eliminator.
   *
   * @param include true to include
   */
  public void setIncludeMistEliminator(boolean include) {
    this.includeMistEliminator = include;
  }

  /**
   * Set whether to include reboiler.
   *
   * @param include true to include
   */
  public void setIncludeReboiler(boolean include) {
    this.includeReboiler = include;
  }

  /**
   * Set whether to include reflux system.
   *
   * @param include true to include
   */
  public void setIncludeRefluxSystem(boolean include) {
    this.includeRefluxSystem = include;
  }

  /**
   * Set reboiler duty.
   *
   * @param duty reboiler duty in kW
   */
  public void setReboilerDuty(double duty) {
    this.reboilerDuty = duty;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate shell cost
    double shellCost = calcColumnShellCost();

    // Calculate internals cost
    double internalsCost;
    if ("trayed".equalsIgnoreCase(absorberType)) {
      internalsCost = calcTraysCost();
    } else if ("spray".equalsIgnoreCase(absorberType)) {
      internalsCost = calcSpraySystemCost();
    } else {
      // Default packed
      internalsCost = calcPackingCost();
    }

    // Apply material factor
    double totalCost = (shellCost + internalsCost) * getMaterialFactor();

    // Add liquid distributor
    if (includeLiquidDistributor) {
      totalCost += calcLiquidDistributorCost();
    }

    // Add mist eliminator
    if (includeMistEliminator) {
      totalCost += calcMistEliminatorCost();
    }

    // Add reboiler
    if (includeReboiler) {
      totalCost += calcReboilerCost();
    }

    // Add reflux system
    if (includeRefluxSystem) {
      totalCost += calcRefluxSystemCost();
    }

    return totalCost;
  }

  /**
   * Calculate column shell cost.
   *
   * @return shell cost in USD
   */
  private double calcColumnShellCost() {
    // Estimate shell weight
    double wallThickness = calcWallThickness();
    double shellWeight = Math.PI * columnDiameter * columnHeight * wallThickness * 7850.0;

    // Add heads weight (2 x 2:1 elliptical heads)
    double headWeight =
        2 * 0.9 * Math.PI / 4.0 * columnDiameter * columnDiameter * wallThickness * 7850.0;

    double totalWeight = shellWeight + headWeight;

    return getCostCalculator().calcVerticalVesselCost(totalWeight);
  }

  /**
   * Calculate wall thickness based on pressure.
   *
   * @return wall thickness in meters
   */
  private double calcWallThickness() {
    // Simplified wall thickness calculation (ASME)
    // t = P*D / (2*S*E - 1.2*P) + CA
    double p = designPressure * 0.1; // Convert barg to MPa
    double d = columnDiameter * 1000; // Convert to mm
    double s = 137.9; // Allowable stress for SA-516-70 at 100°C (MPa)
    double e = 0.85; // Joint efficiency
    double ca = 3.0; // Corrosion allowance (mm)

    double t = p * d / (2 * s * e - 1.2 * p) + ca;
    t = Math.max(t, 6.0); // Minimum 6mm

    return t / 1000.0; // Convert to meters
  }

  /**
   * Calculate packing cost.
   *
   * @return packing cost in USD
   */
  private double calcPackingCost() {
    double packingVolume = Math.PI / 4.0 * columnDiameter * columnDiameter * packingHeight;
    return getCostCalculator().calcPackingCost(packingVolume, packingType);
  }

  /**
   * Calculate trays cost.
   *
   * @return trays cost in USD
   */
  private double calcTraysCost() {
    if ("valve".equalsIgnoreCase(trayType)) {
      return getCostCalculator().calcValveTraysCost(columnDiameter, numberOfStages);
    } else if ("bubble-cap".equalsIgnoreCase(trayType)) {
      return getCostCalculator().calcBubbleCapTraysCost(columnDiameter, numberOfStages);
    } else {
      return getCostCalculator().calcSieveTraysCost(columnDiameter, numberOfStages);
    }
  }

  /**
   * Calculate spray system cost.
   *
   * @return spray system cost in USD
   */
  private double calcSpraySystemCost() {
    // Spray nozzles and support structure
    double area = Math.PI / 4.0 * columnDiameter * columnDiameter;
    double baseCost = 5000.0 + 2000.0 * area;
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate liquid distributor cost.
   *
   * @return liquid distributor cost in USD
   */
  private double calcLiquidDistributorCost() {
    double area = Math.PI / 4.0 * columnDiameter * columnDiameter;
    // Pipe-type distributor: ~$3000/m2
    double baseCost = 3000.0 * area;
    return baseCost * getMaterialFactor() * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate mist eliminator cost.
   *
   * @return mist eliminator cost in USD
   */
  private double calcMistEliminatorCost() {
    double area = Math.PI / 4.0 * columnDiameter * columnDiameter;
    // Wire mesh demister: ~$1500/m2
    double baseCost = 1500.0 * area;
    return baseCost * getMaterialFactor() * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate reboiler cost.
   *
   * @return reboiler cost in USD
   */
  private double calcReboilerCost() {
    // Estimate reboiler area from duty
    // Assume U = 800 W/m2K, LMTD = 30K
    double area = reboilerDuty * 1000 / (800.0 * 30.0);
    return getCostCalculator().calcShellTubeHeatExchangerCost(area) * getMaterialFactor();
  }

  /**
   * Calculate reflux system cost (drum, pump, piping).
   *
   * @return reflux system cost in USD
   */
  private double calcRefluxSystemCost() {
    // Reflux drum: ~10% of column volume
    double drumVolume = 0.1 * Math.PI / 4.0 * columnDiameter * columnDiameter * columnHeight;
    double drumWeight = drumVolume * 100; // Rough estimate kg

    double drumCost = getCostCalculator().calcHorizontalVesselCost(drumWeight);

    // Reflux pump: assume 10 kW
    double pumpCost = getCostCalculator().calcCentrifugalPumpCost(10.0);

    return (drumCost + pumpCost) * getMaterialFactor();
  }

  /**
   * Calculate annual operating cost.
   *
   * @param operatingHoursPerYear operating hours per year
   * @param steamCostPerTonne steam cost in $/tonne
   * @param electricityCostPerKWh electricity cost in $/kWh
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(int operatingHoursPerYear, double steamCostPerTonne,
      double electricityCostPerKWh) {
    double operatingCost = 0.0;

    // Reboiler steam consumption
    if (includeReboiler) {
      // Steam consumption from duty (latent heat ~2200 kJ/kg)
      double steamConsumption = reboilerDuty * 3600 / 2200; // kg/hr
      operatingCost += steamConsumption * operatingHoursPerYear * steamCostPerTonne / 1000.0;
    }

    // Pump power
    if (includeRefluxSystem) {
      operatingCost += 10.0 * operatingHoursPerYear * electricityCostPerKWh; // 10 kW pump
    }

    // Maintenance: ~3% of PEC
    operatingCost += getPurchasedEquipmentCost() * 0.03;

    return operatingCost;
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    breakdown.put("absorberType", absorberType);
    breakdown.put("columnDiameter_m", columnDiameter);
    breakdown.put("columnHeight_m", columnHeight);
    breakdown.put("designPressure_barg", designPressure);

    if ("packed".equalsIgnoreCase(absorberType)) {
      breakdown.put("packingType", packingType);
      breakdown.put("packingHeight_m", packingHeight);
    } else if ("trayed".equalsIgnoreCase(absorberType)) {
      breakdown.put("trayType", trayType);
      breakdown.put("numberOfStages", numberOfStages);
    }

    // Calculate individual costs
    breakdown.put("shellCost_USD", calcColumnShellCost() * getMaterialFactor());

    if ("trayed".equalsIgnoreCase(absorberType)) {
      breakdown.put("traysCost_USD", calcTraysCost() * getMaterialFactor());
    } else if ("packed".equalsIgnoreCase(absorberType)) {
      breakdown.put("packingCost_USD", calcPackingCost() * getMaterialFactor());
    }

    if (includeLiquidDistributor) {
      breakdown.put("liquidDistributorCost_USD", calcLiquidDistributorCost());
    }
    if (includeMistEliminator) {
      breakdown.put("mistEliminatorCost_USD", calcMistEliminatorCost());
    }
    if (includeReboiler) {
      breakdown.put("reboilerCost_USD", calcReboilerCost());
    }
    if (includeRefluxSystem) {
      breakdown.put("refluxSystemCost_USD", calcRefluxSystemCost());
    }

    breakdown.put("materialFactor", getMaterialFactor());
    breakdown.put("totalPurchasedCost_USD", getPurchasedEquipmentCost());

    return breakdown;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("absorberCostBreakdown", getCostBreakdown());
    return result;
  }
}
