package neqsim.process.costestimation.well;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Rough drilling-and-completion CAPEX estimate for a {@link neqsim.process.equipment.reservoir.WellFlow} unit.
 *
 * <p>
 * A producing or injecting well has no process-equipment shell weight, so the standard weight-based correlations return
 * zero. This class instead carries an all-in well CAPEX figure (drilling + completion + wellhead) that an agent can
 * override per case. Because the figure is already an installed, all-in number, the chemical-engineering module factors
 * (bare-module, total-module, grass-roots) are not applied on top of it.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class WellFlowCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Rough all-in well CAPEX (drilling + completion + wellhead) in USD. */
  private double wellCapexUsd = 75.0e6;

  /**
   * Constructor with mechanical equipment.
   *
   * @param mechanicalEquipment the associated mechanical design
   */
  public WellFlowCostEstimate(MechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    this.equipmentType = "well";
  }

  /**
   * Get the rough all-in well CAPEX.
   *
   * @return well CAPEX in USD
   */
  public double getWellCapexUsd() {
    return wellCapexUsd;
  }

  /**
   * Set the rough all-in well CAPEX (drilling + completion + wellhead).
   *
   * @param wellCapexUsd well CAPEX in USD (negative values are clamped to zero)
   */
  public void setWellCapexUsd(double wellCapexUsd) {
    this.wellCapexUsd = Math.max(0.0, wellCapexUsd);
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    return wellCapexUsd;
  }

  /** {@inheritDoc} */
  @Override
  public void calculateCostEstimate() {
    // Drilling and completion CAPEX is an all-in installed figure, so the bare-module,
    // total-module and grass-roots multipliers are not applied on top of it.
    purchasedEquipmentCost = wellCapexUsd;
    bareModuleCost = wellCapexUsd;
    totalModuleCost = wellCapexUsd;
    grassRootsCost = wellCapexUsd;
    installationManHours = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    return wellCapexUsd;
  }
}
