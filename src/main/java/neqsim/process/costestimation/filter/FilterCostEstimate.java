package neqsim.process.costestimation.filter;

import neqsim.process.costestimation.CostEstimationCalculator;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.filter.FilterMechanicalDesign;

/**
 * Screening-level purchased equipment cost estimator for filter vessels.
 *
 * <p>
 * The estimate combines a horizontal pressure-vessel correlation with cartridge element, nozzle, and instrumentation
 * allowances. It intentionally uses the dimensions and element count produced by {@link FilterMechanicalDesign} so the
 * process-level cost rollup reflects the same mechanical screening basis as the unit design.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FilterCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Typical filter element purchase cost in USD per installed element. */
  private double elementCostUSD = 450.0;

  /**
   * Nozzle, skid, instrumentation, and element-retainer allowance as a fraction of vessel and element cost.
   */
  private double auxiliariesFraction = 0.35;

  /**
   * Constructor for FilterCostEstimate.
   *
   * @param mechanicalEquipment the filter mechanical design
   */
  public FilterCostEstimate(FilterMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("filter");
  }

  /**
   * Set the typical filter element cost.
   *
   * @param elementCostUSD element cost in USD per installed element, negative values are clamped to zero
   */
  public void setElementCostUSD(double elementCostUSD) {
    this.elementCostUSD = Math.max(0.0, elementCostUSD);
  }

  /**
   * Set the auxiliary-cost fraction for nozzles, skid, retainers, and instrumentation.
   *
   * @param auxiliariesFraction auxiliary fraction of vessel and element cost, negative values are clamped to zero
   */
  public void setAuxiliariesFraction(double auxiliariesFraction) {
    this.auxiliariesFraction = Math.max(0.0, auxiliariesFraction);
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (!(mechanicalEquipment instanceof FilterMechanicalDesign)) {
      return super.calcPurchasedEquipmentCost();
    }

    FilterMechanicalDesign filterDesign = (FilterMechanicalDesign) mechanicalEquipment;
    double diameter = filterDesign.getInnerDiameter();
    double length = filterDesign.getVesselLength();
    double volume = 0.0;
    if (diameter > 0.0 && length > 0.0) {
      volume = Math.PI * Math.pow(diameter / 2.0, 2.0) * length;
    }

    double vesselCost = getCostCalculator().calcHorizontalVesselCostByVolume(volume);
    double elementCost = Math.max(filterDesign.getRequiredElements(), 1) * elementCostUSD;
    double correlationCost = (vesselCost + elementCost) * (1.0 + auxiliariesFraction);

    double fallbackCost = filterDesign.getEquipmentCostUSD();
    double purchasedCost = Math.max(correlationCost, fallbackCost);
    purchasedCost *= materialFactor(filterDesign.getMaterialGrade());
    purchasedCost *= CostEstimationCalculator
        .getPressureFactor(Math.max(filterDesign.getDesignPressure() - 1.01325, 0.0));

    return purchasedCost;
  }

  /**
   * Estimate material multiplier from the mechanical design material grade.
   *
   * @param materialGrade material grade or material family string
   * @return material purchase-cost multiplier
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
}
