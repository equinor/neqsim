package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;

/**
 * <p>
 * ThreePhaseSeparatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ThreePhaseSeparatorResponse {
  public String name;
  public Double gasLoadFactor;
  public Double massflow;
  public Fluid gasFluid, oilFluid;

  /**
   * <p>
   * Constructor for ThreePhaseSeparatorResponse.
   * </p>
   *
   * @param inputSeparator a {@link neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator} object
   */
  public ThreePhaseSeparatorResponse(ThreePhaseSeparator inputSeparator) {
    name = inputSeparator.getName();
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    oilFluid = new Fluid(inputSeparator.getOilOutStream().getFluid());
    gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
  }

  /**
   * <p>
   * Constructor for ThreePhaseSeparatorResponse.
   * </p>
   *
   * @param inputSeparator a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
   */
  public ThreePhaseSeparatorResponse(Separator inputSeparator) {
    name = inputSeparator.getName();
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    oilFluid = new Fluid(inputSeparator.getLiquidOutStream().getFluid());
    gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
  }
}
