package neqsim.process.util.monitor;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;

/**
 * ThreePhaseSeparatorResponse class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ThreePhaseSeparatorResponse extends BaseResponse {
  public Double gasLoadFactor;
  public Double massflow;
  public FluidResponse gasFluid, oilFluid;

  /**
   * Constructor for ThreePhaseSeparatorResponse.
   *
   * @param inputSeparator a {@link neqsim.process.equipment.separator.ThreePhaseSeparator} object
   */
  public ThreePhaseSeparatorResponse(ThreePhaseSeparator inputSeparator) {
    super(inputSeparator);
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    oilFluid = new FluidResponse(inputSeparator.getOilOutStream().getFluid());
    gasFluid = new FluidResponse(inputSeparator.getGasOutStream().getFluid());
  }

  /**
   * Constructor for ThreePhaseSeparatorResponse.
   *
   * @param inputSeparator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public ThreePhaseSeparatorResponse(Separator inputSeparator) {
    name = inputSeparator.getName();
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    oilFluid = new FluidResponse(inputSeparator.getLiquidOutStream().getFluid());
    gasFluid = new FluidResponse(inputSeparator.getGasOutStream().getFluid());
  }
}
