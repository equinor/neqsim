package neqsim.process.mechanicaldesign.well;

import neqsim.process.costestimation.well.WellFlowCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical-design wrapper for a {@link neqsim.process.equipment.reservoir.WellFlow} unit.
 *
 * <p>
 * A well has no process-vessel shell to size, so this class carries no weight and instead exposes a rough, configurable
 * all-in drilling-and-completion CAPEX through a {@link WellFlowCostEstimate}. This lets the whole-system cost rollups
 * ({@code ProcessCostEstimate} and {@code SystemMechanicalDesign}) include the subsurface CAPEX when estimating cost
 * from reservoir to market.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class WellFlowMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for WellFlowMechanicalDesign.
   *
   * @param processEquipment the well equipment
   */
  public WellFlowMechanicalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    costEstimate = new WellFlowCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // Rough well CAPEX is independent of detailed mechanical sizing; the well carries no shell
    // weight.
    setWeightTotal(0.0);
  }

  /**
   * Get the rough all-in well CAPEX.
   *
   * @return well CAPEX in USD
   */
  public double getWellCapexUsd() {
    return ((WellFlowCostEstimate) costEstimate).getWellCapexUsd();
  }

  /**
   * Set the rough all-in well CAPEX (drilling + completion + wellhead).
   *
   * @param wellCapexUsd well CAPEX in USD
   */
  public void setWellCapexUsd(double wellCapexUsd) {
    ((WellFlowCostEstimate) costEstimate).setWellCapexUsd(wellCapexUsd);
  }
}
