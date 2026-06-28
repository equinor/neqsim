package neqsim.process.costestimation.reactor;

import neqsim.process.costestimation.CostEstimationCalculator;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.reactor.ReactorMechanicalDesign;

/**
 * Screening-level purchased equipment cost estimator for reactor vessels.
 *
 * <p>
 * The estimate combines a vertical pressure-vessel cost with catalyst, bed internals, distributors,
 * and nozzles. It is intended for early reservoir-to-market and topside process screening where the
 * reactor duty is represented by a NeqSim reactor unit and {@link ReactorMechanicalDesign} has
 * produced vessel and catalyst dimensions.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ReactorCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Typical loaded catalyst purchase cost in USD per kg for screening estimates. */
  private double catalystCostUSDPerKg = 8.0;

  /**
   * Fraction of vessel cost added for distributors, support grids, nozzles, and instrumentation.
   */
  private double internalsFraction = 0.45;

  /** Fixed distributor/support allowance per catalyst bed in USD. */
  private double bedAllowanceUSD = 25000.0;

  /**
   * Constructor for ReactorCostEstimate.
   *
   * @param mechanicalEquipment the reactor mechanical design
   */
  public ReactorCostEstimate(ReactorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("reactor");
  }

  /**
   * Set catalyst cost for screening estimates.
   *
   * @param catalystCostUSDPerKg catalyst cost in USD/kg, negative values are clamped to zero
   */
  public void setCatalystCostUSDPerKg(double catalystCostUSDPerKg) {
    this.catalystCostUSDPerKg = Math.max(0.0, catalystCostUSDPerKg);
  }

  /**
   * Set the reactor internals allowance fraction.
   *
   * @param internalsFraction fraction of vessel cost, negative values are clamped to zero
   */
  public void setInternalsFraction(double internalsFraction) {
    this.internalsFraction = Math.max(0.0, internalsFraction);
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (!(mechanicalEquipment instanceof ReactorMechanicalDesign)) {
      return super.calcPurchasedEquipmentCost();
    }

    ReactorMechanicalDesign reactorDesign = (ReactorMechanicalDesign) mechanicalEquipment;
    double volume = 0.0;
    if (reactorDesign.getVesselDiameter() > 0.0 && reactorDesign.getVesselLength() > 0.0) {
      volume = Math.PI * Math.pow(reactorDesign.getVesselDiameter() / 2.0, 2.0)
          * reactorDesign.getVesselLength();
    }

    double vesselCost = getCostCalculator().calcVerticalVesselCostByVolume(volume);
    double pressureFactor = CostEstimationCalculator
        .getPressureFactor(Math.max(reactorDesign.getDesignPressureBara() - 1.01325, 0.0));
    double catalystCost = reactorDesign.getCatalystMass() * catalystCostUSDPerKg;
    double internalsCost = vesselCost * internalsFraction
        + Math.max(reactorDesign.getNumberOfBeds(), 1) * bedAllowanceUSD;
    double fallbackCost = reactorDesign.getTotalEquippedWeight() * 20.0;

    return Math.max(vesselCost * pressureFactor + catalystCost + internalsCost, fallbackCost);
  }
}
