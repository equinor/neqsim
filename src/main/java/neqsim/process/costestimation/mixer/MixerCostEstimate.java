package neqsim.process.costestimation.mixer;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Cost estimation class for mixers.
 *
 * <p>
 * This class provides mixer-specific cost estimation methods for static mixers, inline mixers, and
 * mixing tees used in process applications.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Vendor quotes and industry data</li>
 * <li>Peters &amp; Timmerhaus - Plant Design and Economics</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class MixerCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Mixer type. */
  private String mixerType = "static";

  /** Pipe diameter in inches. */
  private double pipeDiameter = 6.0;

  /** Number of mixing elements (for static mixers). */
  private int numberOfElements = 6;

  /** Pressure class (ASME). */
  private int pressureClass = 300;

  /** Include flanged connections. */
  private boolean flangedConnections = true;

  /**
   * Constructor for MixerCostEstimate.
   *
   * @param mechanicalEquipment the mixer mechanical design
   */
  public MixerCostEstimate(MechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("mixer");
  }

  /**
   * Set mixer type.
   *
   * @param type mixer type ("static", "inline", "tee", "vessel")
   */
  public void setMixerType(String type) {
    this.mixerType = type;
  }

  /**
   * Get mixer type.
   *
   * @return mixer type
   */
  public String getMixerType() {
    return mixerType;
  }

  /**
   * Set pipe diameter.
   *
   * @param diameter pipe diameter in inches
   */
  public void setPipeDiameter(double diameter) {
    this.pipeDiameter = diameter;
  }

  /**
   * Get pipe diameter.
   *
   * @return pipe diameter in inches
   */
  public double getPipeDiameter() {
    return pipeDiameter;
  }

  /**
   * Set number of mixing elements.
   *
   * @param elements number of elements
   */
  public void setNumberOfElements(int elements) {
    this.numberOfElements = elements;
  }

  /**
   * Set pressure class.
   *
   * @param pressClass ASME pressure class (150, 300, 600, 900, 1500, 2500)
   */
  public void setPressureClass(int pressClass) {
    this.pressureClass = pressClass;
  }

  /**
   * Set flanged connections option.
   *
   * @param flanged true for flanged connections
   */
  public void setFlangedConnections(boolean flanged) {
    this.flangedConnections = flanged;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate base mixer cost based on type
    double baseCost;
    if ("inline".equalsIgnoreCase(mixerType)) {
      baseCost = calcInlineMixerCost();
    } else if ("tee".equalsIgnoreCase(mixerType)) {
      baseCost = calcMixingTeeCost();
    } else if ("vessel".equalsIgnoreCase(mixerType)) {
      baseCost = calcMixingVesselCost();
    } else {
      // Default static mixer
      baseCost = calcStaticMixerCost();
    }

    // Apply material factor
    baseCost *= getMaterialFactor();

    // Apply pressure class factor
    baseCost *= getPressureClassFactor();

    // Add flanged connections
    if (flangedConnections) {
      baseCost += calcFlangeCost();
    }

    return baseCost;
  }

  /**
   * Calculate static mixer cost.
   *
   * @return cost in USD
   */
  private double calcStaticMixerCost() {
    // Static mixer cost: Cp = 100 * D^1.5 * N^0.5
    // where D is diameter in inches, N is number of elements
    double baseCost = 100.0 * Math.pow(pipeDiameter, 1.5) * Math.pow(numberOfElements, 0.5);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate inline mixer cost.
   *
   * @return cost in USD
   */
  private double calcInlineMixerCost() {
    // Inline mixer (powered): more expensive than static
    return calcStaticMixerCost() * 2.5;
  }

  /**
   * Calculate mixing tee cost.
   *
   * @return cost in USD
   */
  private double calcMixingTeeCost() {
    // Mixing tee: simpler, lower cost
    double baseCost = 50.0 + 20.0 * Math.pow(pipeDiameter, 1.2);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate mixing vessel cost.
   *
   * @return cost in USD
   */
  private double calcMixingVesselCost() {
    // Mixing vessel: uses vessel correlation
    // Estimate volume from diameter (assume H/D = 2)
    double diameter_m = pipeDiameter * 0.0254;
    double height_m = diameter_m * 2;
    double volume = Math.PI / 4.0 * diameter_m * diameter_m * height_m;

    double baseCost = 500.0 * Math.pow(Math.max(volume * 1000, 100), 0.55); // Volume in liters
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
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
   * Calculate flange cost.
   *
   * @return flange cost in USD
   */
  private double calcFlangeCost() {
    // Two flanges (inlet and outlet)
    double baseFlangePrice = 50.0 + 30.0 * Math.pow(pipeDiameter, 1.2);
    return 2 * baseFlangePrice * getMaterialFactor() * getPressureClassFactor()
        * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    breakdown.put("mixerType", mixerType);
    breakdown.put("pipeDiameter_in", pipeDiameter);
    breakdown.put("numberOfElements", numberOfElements);
    breakdown.put("pressureClass", pressureClass);
    breakdown.put("flangedConnections", flangedConnections);

    // Calculate individual costs
    double mixerCost;
    if ("inline".equalsIgnoreCase(mixerType)) {
      mixerCost = calcInlineMixerCost();
    } else if ("tee".equalsIgnoreCase(mixerType)) {
      mixerCost = calcMixingTeeCost();
    } else if ("vessel".equalsIgnoreCase(mixerType)) {
      mixerCost = calcMixingVesselCost();
    } else {
      mixerCost = calcStaticMixerCost();
    }

    breakdown.put("mixerBodyCost_USD", mixerCost * getMaterialFactor() * getPressureClassFactor());

    if (flangedConnections) {
      breakdown.put("flangeCost_USD", calcFlangeCost());
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
    result.put("mixerCostBreakdown", getCostBreakdown());
    return result;
  }
}
