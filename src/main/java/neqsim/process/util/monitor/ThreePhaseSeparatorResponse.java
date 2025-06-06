package neqsim.process.util.monitor;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;

/**
 * <p>
 * ThreePhaseSeparatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ThreePhaseSeparatorResponse extends BaseResponse {
  public Double gasLoadFactor;
  public Double massflow;
  public FluidResponse gasFluid, oilFluid;

  /**
   * <p>
   * Constructor for ThreePhaseSeparatorResponse.
   * </p>
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
   * <p>
   * Constructor for ThreePhaseSeparatorResponse.
   * </p>
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
