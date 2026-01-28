package neqsim.process.costestimation.tank;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.tank.TankMechanicalDesign;

/**
 * Cost estimation class for storage tanks.
 *
 * <p>
 * This class provides tank-specific cost estimation methods using chemical engineering cost
 * correlations for atmospheric and low-pressure storage tanks per API 650/620 standards.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Turton et al. - Analysis, Synthesis and Design of Chemical Processes</li>
 * <li>Peters &amp; Timmerhaus - Plant Design and Economics</li>
 * <li>API 650 - Welded Tanks for Oil Storage</li>
 * <li>API 620 - Low-Pressure Storage Tanks</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class TankCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Tank type. */
  private String tankType = "fixed-cone-roof";

  /** Tank volume in m3. */
  private double tankVolume = 1000.0;

  /** Tank diameter in meters. */
  private double tankDiameter = 10.0;

  /** Tank height in meters. */
  private double tankHeight = 12.0;

  /** Design pressure in barg (0 for atmospheric). */
  private double designPressure = 0.0;

  /** Include floating roof. */
  private boolean floatingRoof = false;

  /** Include foundation cost. */
  private boolean includeFoundation = true;

  /** Include heating coils. */
  private boolean includeHeatingCoils = false;

  /** Include insulation. */
  private boolean includeInsulation = false;

  /** Insulation thickness in mm. */
  private double insulationThickness = 100.0;

  /**
   * Constructor for TankCostEstimate.
   *
   * @param mechanicalEquipment the tank mechanical design
   */
  public TankCostEstimate(TankMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("tank");
  }

  /**
   * Set tank type.
   *
   * @param type tank type ("fixed-cone-roof", "fixed-dome-roof", "floating-roof", "spherical",
   *        "horizontal")
   */
  public void setTankType(String type) {
    this.tankType = type;
  }

  /**
   * Get tank type.
   *
   * @return tank type
   */
  public String getTankType() {
    return tankType;
  }

  /**
   * Set tank volume.
   *
   * @param volume tank volume in m3
   */
  public void setTankVolume(double volume) {
    this.tankVolume = volume;
  }

  /**
   * Get tank volume.
   *
   * @return tank volume in m3
   */
  public double getTankVolume() {
    return tankVolume;
  }

  /**
   * Set tank diameter.
   *
   * @param diameter tank diameter in meters
   */
  public void setTankDiameter(double diameter) {
    this.tankDiameter = diameter;
  }

  /**
   * Set tank height.
   *
   * @param height tank height in meters
   */
  public void setTankHeight(double height) {
    this.tankHeight = height;
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
   * Set floating roof option.
   *
   * @param floating true for floating roof
   */
  public void setFloatingRoof(boolean floating) {
    this.floatingRoof = floating;
  }

  /**
   * Set whether to include foundation.
   *
   * @param include true to include foundation cost
   */
  public void setIncludeFoundation(boolean include) {
    this.includeFoundation = include;
  }

  /**
   * Set whether to include heating coils.
   *
   * @param include true to include heating coils
   */
  public void setIncludeHeatingCoils(boolean include) {
    this.includeHeatingCoils = include;
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
    // Update dimensions from mechanical design if available
    if (mechanicalEquipment != null) {
      TankMechanicalDesign tankMecDesign = (TankMechanicalDesign) mechanicalEquipment;
      if (tankMecDesign.getInnerDiameter() > 0) {
        this.tankDiameter = tankMecDesign.getInnerDiameter();
      }
      if (tankMecDesign.getTankHeight() > 0) {
        this.tankHeight = tankMecDesign.getTankHeight();
      }
      this.tankVolume = Math.PI / 4.0 * tankDiameter * tankDiameter * tankHeight;
    }

    // Calculate base tank cost
    double baseCost;
    if ("spherical".equalsIgnoreCase(tankType)) {
      baseCost = calcSphericalTankCost();
    } else if ("horizontal".equalsIgnoreCase(tankType)) {
      baseCost = calcHorizontalTankCost();
    } else if ("floating-roof".equalsIgnoreCase(tankType) || floatingRoof) {
      baseCost = calcFloatingRoofTankCost();
    } else {
      // Fixed roof (cone or dome)
      baseCost = calcFixedRoofTankCost();
    }

    // Apply material factor
    baseCost *= getMaterialFactor();

    // Apply pressure factor for low-pressure tanks
    if (designPressure > 0.07 && designPressure < 1.0) {
      baseCost *= (1.0 + designPressure * 0.3); // API 620 factor
    }

    // Add foundation cost
    if (includeFoundation) {
      baseCost += calcFoundationCost();
    }

    // Add heating coils
    if (includeHeatingCoils) {
      baseCost += calcHeatingCoilsCost();
    }

    // Add insulation
    if (includeInsulation) {
      baseCost += calcInsulationCost();
    }

    return baseCost;
  }

  /**
   * Calculate fixed roof tank cost (cone or dome).
   *
   * @return cost in USD
   */
  private double calcFixedRoofTankCost() {
    // API 650 tank cost correlation: Cp = a * V^b
    // For fixed roof tanks: a = 475, b = 0.507
    double volume = Math.max(tankVolume, 100.0); // min 100 m3
    double baseCost = 475.0 * Math.pow(volume, 0.507);

    // Adjust for dome roof (more expensive than cone)
    if ("fixed-dome-roof".equalsIgnoreCase(tankType)) {
      baseCost *= 1.15;
    }

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate floating roof tank cost.
   *
   * @return cost in USD
   */
  private double calcFloatingRoofTankCost() {
    // Floating roof tanks: a = 700, b = 0.52
    double volume = Math.max(tankVolume, 500.0); // min 500 m3
    double baseCost = 700.0 * Math.pow(volume, 0.52);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate spherical tank cost.
   *
   * @return cost in USD
   */
  private double calcSphericalTankCost() {
    // Spherical tanks (pressurized): Cp = 2500 * V^0.65
    double volume = Math.max(tankVolume, 50.0); // min 50 m3
    double baseCost = 2500.0 * Math.pow(volume, 0.65);

    // Apply pressure factor
    if (designPressure > 1.0) {
      baseCost *= (1.0 + 0.05 * designPressure);
    }

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate horizontal tank cost.
   *
   * @return cost in USD
   */
  private double calcHorizontalTankCost() {
    // Horizontal tanks: Cp = 600 * V^0.55
    double volume = Math.max(tankVolume, 10.0); // min 10 m3
    double baseCost = 600.0 * Math.pow(volume, 0.55);

    // Apply pressure factor
    if (designPressure > 0) {
      baseCost *= getCostCalculator().getPressureFactor(designPressure);
    }

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate foundation cost.
   *
   * @return foundation cost in USD
   */
  private double calcFoundationCost() {
    // Foundation cost based on tank footprint
    double footprintArea = Math.PI / 4.0 * tankDiameter * tankDiameter;

    // Ringwall foundation: ~$300/m2
    double baseCost = footprintArea * 300.0;

    // Adjust for tank size
    if (tankVolume > 10000) {
      baseCost *= 1.2; // Larger foundation for big tanks
    }

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate heating coils cost.
   *
   * @return heating coils cost in USD
   */
  private double calcHeatingCoilsCost() {
    // Heating coils: ~$50/m3 of tank volume
    double baseCost = tankVolume * 50.0;
    return baseCost * getMaterialFactor() * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate insulation cost.
   *
   * @return insulation cost in USD
   */
  private double calcInsulationCost() {
    // Surface area of tank
    double surfaceArea =
        Math.PI * tankDiameter * tankHeight + 2 * Math.PI / 4.0 * tankDiameter * tankDiameter;

    // Insulation cost: ~$80/m2 for mineral wool with cladding
    double baseCost = surfaceArea * 80.0;

    // Adjust for thickness
    baseCost *= (1.0 + (insulationThickness - 50.0) / 100.0);

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate annual operating cost (inspection, maintenance).
   *
   * @param operatingHoursPerYear operating hours per year
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(int operatingHoursPerYear) {
    // Tank maintenance: ~2% of PEC annually
    double maintenanceCost = getPurchasedEquipmentCost() * 0.02;

    // Add heating cost if applicable
    if (includeHeatingCoils) {
      // Assume 10 kW/1000 m3 heating duty
      double heatingDuty = tankVolume * 0.01; // kW
      double heatingCost = heatingDuty * operatingHoursPerYear * 0.08; // $0.08/kWh
      maintenanceCost += heatingCost;
    }

    return maintenanceCost;
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    breakdown.put("tankType", tankType);
    breakdown.put("tankVolume_m3", tankVolume);
    breakdown.put("tankDiameter_m", tankDiameter);
    breakdown.put("tankHeight_m", tankHeight);
    breakdown.put("designPressure_barg", designPressure);
    breakdown.put("floatingRoof", floatingRoof);

    // Calculate individual costs
    double tankCost;
    if ("spherical".equalsIgnoreCase(tankType)) {
      tankCost = calcSphericalTankCost();
    } else if ("horizontal".equalsIgnoreCase(tankType)) {
      tankCost = calcHorizontalTankCost();
    } else if ("floating-roof".equalsIgnoreCase(tankType) || floatingRoof) {
      tankCost = calcFloatingRoofTankCost();
    } else {
      tankCost = calcFixedRoofTankCost();
    }
    tankCost *= getMaterialFactor();

    breakdown.put("tankShellCost_USD", tankCost);

    if (includeFoundation) {
      breakdown.put("foundationCost_USD", calcFoundationCost());
    }
    if (includeHeatingCoils) {
      breakdown.put("heatingCoilsCost_USD", calcHeatingCoilsCost());
    }
    if (includeInsulation) {
      breakdown.put("insulationCost_USD", calcInsulationCost());
    }

    breakdown.put("materialFactor", getMaterialFactor());
    breakdown.put("totalPurchasedCost_USD", getPurchasedEquipmentCost());

    return breakdown;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("tankCostBreakdown", getCostBreakdown());
    return result;
  }
}
