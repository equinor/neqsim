package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;

/**
 * <p>
 * SeparatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SeparatorResponse {
  public String name;
  public Double gasLoadFactor;
  public Double massflow;
  public FluidResponse gasFluid, liquidFluid, oilFluid, waterFluid;

  /**
   * <p>
   * Constructor for SeparatorResponse.
   * </p>
   *
   * @param inputSeparator a
   *        {@link neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator} object
   */
  public SeparatorResponse(ThreePhaseSeparator inputSeparator) {
    name = inputSeparator.getName();
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")) {
      waterFluid = new FluidResponse(inputSeparator.getWaterOutStream().getFluid());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      oilFluid = new FluidResponse(inputSeparator.getOilOutStream().getFluid());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gasFluid = new FluidResponse(inputSeparator.getGasOutStream().getFluid());
    }
  }

  /**
   * <p>
   * Constructor for SeparatorResponse.
   * </p>
   *
   * @param inputSeparator a {@link neqsim.processSimulation.processEquipment.separator.Separator}
   *        object
   */
  public SeparatorResponse(Separator inputSeparator) {
    name = inputSeparator.getName();
    massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")) {
      waterFluid = new FluidResponse(inputSeparator.getThermoSystem().phaseToSystem("aqueous"));
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      oilFluid = new FluidResponse(inputSeparator.getThermoSystem().phaseToSystem("oil"));
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gasFluid = new FluidResponse(inputSeparator.getGasOutStream().getFluid());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("oil")
        || inputSeparator.getThermoSystem().hasPhaseType("aqueous")) {
      liquidFluid = new FluidResponse(inputSeparator.getLiquidOutStream().getFluid());
    }
  }
}
