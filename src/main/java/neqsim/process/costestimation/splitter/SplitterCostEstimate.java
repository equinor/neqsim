package neqsim.process.costestimation.splitter;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Cost estimation class for splitters.
 *
 * <p>
 * This class provides splitter-specific cost estimation methods for flow dividers, manifolds, and
 * headers used to split process streams.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Vendor quotes and industry data</li>
 * <li>Piping cost correlations</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class SplitterCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Splitter type. */
  private String splitterType = "manifold";

  /** Number of outlet streams. */
  private int numberOfOutlets = 2;

  /** Inlet pipe diameter in inches. */
  private double inletDiameter = 8.0;

  /** Outlet pipe diameter in inches. */
  private double outletDiameter = 6.0;

  /** Pressure class (ASME). */
  private int pressureClass = 300;

  /** Include control valves on outlets. */
  private boolean includeControlValves = false;

  /** Include flow meters on outlets. */
  private boolean includeFlowMeters = false;

  /**
   * Constructor for SplitterCostEstimate.
   *
   * @param mechanicalEquipment the splitter mechanical design
   */
  public SplitterCostEstimate(MechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("splitter");
  }

  /**
   * Set splitter type.
   *
   * @param type splitter type ("manifold", "header", "tee", "vessel")
   */
  public void setSplitterType(String type) {
    this.splitterType = type;
  }

  /**
   * Get splitter type.
   *
   * @return splitter type
   */
  public String getSplitterType() {
    return splitterType;
  }

  /**
   * Set number of outlet streams.
   *
   * @param outlets number of outlets
   */
  public void setNumberOfOutlets(int outlets) {
    this.numberOfOutlets = outlets;
  }

  /**
   * Get number of outlets.
   *
   * @return number of outlets
   */
  public int getNumberOfOutlets() {
    return numberOfOutlets;
  }

  /**
   * Set inlet diameter.
   *
   * @param diameter inlet diameter in inches
   */
  public void setInletDiameter(double diameter) {
    this.inletDiameter = diameter;
  }

  /**
   * Set outlet diameter.
   *
   * @param diameter outlet diameter in inches
   */
  public void setOutletDiameter(double diameter) {
    this.outletDiameter = diameter;
  }

  /**
   * Set pressure class.
   *
   * @param pressClass ASME pressure class
   */
  public void setPressureClass(int pressClass) {
    this.pressureClass = pressClass;
  }

  /**
   * Set whether to include control valves.
   *
   * @param include true to include control valves
   */
  public void setIncludeControlValves(boolean include) {
    this.includeControlValves = include;
  }

  /**
   * Set whether to include flow meters.
   *
   * @param include true to include flow meters
   */
  public void setIncludeFlowMeters(boolean include) {
    this.includeFlowMeters = include;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate base splitter cost based on type
    double baseCost;
    if ("header".equalsIgnoreCase(splitterType)) {
      baseCost = calcHeaderCost();
    } else if ("tee".equalsIgnoreCase(splitterType)) {
      baseCost = calcTeeCost();
    } else if ("vessel".equalsIgnoreCase(splitterType)) {
      baseCost = calcDistributionVesselCost();
    } else {
      // Default manifold
      baseCost = calcManifoldCost();
    }

    // Apply material factor
    baseCost *= getMaterialFactor();

    // Apply pressure class factor
    baseCost *= getPressureClassFactor();

    // Add control valves
    if (includeControlValves) {
      baseCost += calcControlValvesCost();
    }

    // Add flow meters
    if (includeFlowMeters) {
      baseCost += calcFlowMetersCost();
    }

    return baseCost;
  }

  /**
   * Calculate manifold cost.
   *
   * @return cost in USD
   */
  private double calcManifoldCost() {
    // Manifold cost: base + per-outlet cost
    double baseCost = 200.0 + 50.0 * Math.pow(inletDiameter, 1.3);
    double outletCost = numberOfOutlets * 30.0 * Math.pow(outletDiameter, 1.2);
    return (baseCost + outletCost) * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate header cost.
   *
   * @return cost in USD
   */
  private double calcHeaderCost() {
    // Header is a larger manifold
    return calcManifoldCost() * 1.3;
  }

  /**
   * Calculate tee cost.
   *
   * @return cost in USD
   */
  private double calcTeeCost() {
    // Tee fitting cost (simpler, 2 outlets only)
    double baseCost = 50.0 + 25.0 * Math.pow(inletDiameter, 1.2);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate distribution vessel cost.
   *
   * @return cost in USD
   */
  private double calcDistributionVesselCost() {
    // Small vessel for flow distribution
    double diameter_m = inletDiameter * 0.0254 * 2; // Vessel diameter ~2x pipe
    double height_m = diameter_m * 1.5; // H/D = 1.5
    double volume = Math.PI / 4.0 * diameter_m * diameter_m * height_m;

    // Add nozzle costs
    double vesselCost = 400.0 * Math.pow(Math.max(volume * 1000, 50), 0.55); // Volume in liters
    double nozzleCost = (1 + numberOfOutlets) * 100.0 * Math.pow(outletDiameter, 0.8);

    return (vesselCost + nozzleCost) * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Get pressure class cost factor.
   *
   * @return pressure class factor
   */
  private double getPressureClassFactor() {
    if (pressureClass <= 150) {
      return 1.0;
    } else if (pressureClass <= 300) {
      return 1.1;
    } else if (pressureClass <= 600) {
      return 1.3;
    } else if (pressureClass <= 900) {
      return 1.5;
    } else if (pressureClass <= 1500) {
      return 1.8;
    } else {
      return 2.2;
    }
  }

  /**
   * Calculate control valves cost.
   *
   * @return control valves cost in USD
   */
  private double calcControlValvesCost() {
    // One control valve per outlet
    double valveCost = getCostCalculator().calcControlValveCost(100.0 * outletDiameter);
    return numberOfOutlets * valveCost * getMaterialFactor() * getPressureClassFactor();
  }

  /**
   * Calculate flow meters cost.
   *
   * @return flow meters cost in USD
   */
  private double calcFlowMetersCost() {
    // Orifice plate flow meters
    double meterCost = 500.0 + 100.0 * outletDiameter;
    return numberOfOutlets * meterCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    breakdown.put("splitterType", splitterType);
    breakdown.put("numberOfOutlets", numberOfOutlets);
    breakdown.put("inletDiameter_in", inletDiameter);
    breakdown.put("outletDiameter_in", outletDiameter);
    breakdown.put("pressureClass", pressureClass);

    // Calculate individual costs
    double splitterCost;
    if ("header".equalsIgnoreCase(splitterType)) {
      splitterCost = calcHeaderCost();
    } else if ("tee".equalsIgnoreCase(splitterType)) {
      splitterCost = calcTeeCost();
    } else if ("vessel".equalsIgnoreCase(splitterType)) {
      splitterCost = calcDistributionVesselCost();
    } else {
      splitterCost = calcManifoldCost();
    }

    breakdown.put("splitterBodyCost_USD",
        splitterCost * getMaterialFactor() * getPressureClassFactor());

    if (includeControlValves) {
      breakdown.put("controlValvesCost_USD", calcControlValvesCost());
    }
    if (includeFlowMeters) {
      breakdown.put("flowMetersCost_USD", calcFlowMetersCost());
    }

    breakdown.put("materialFactor", getMaterialFactor());
    breakdown.put("pressureClassFactor", getPressureClassFactor());
    breakdown.put("totalPurchasedCost_USD", getPurchasedEquipmentCost());

    return breakdown;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("splitterCostBreakdown", getCostBreakdown());
    return result;
  }
}
