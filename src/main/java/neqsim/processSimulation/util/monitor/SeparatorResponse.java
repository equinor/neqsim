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
  public StreamResponse feed, gas, liquid, oil, water;

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
    gasLoadFactor = inputSeparator.getGasLoadFactor();

    feed = new StreamResponse(inputSeparator.getFeedStream());
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")) {
      water = new StreamResponse(inputSeparator.getWaterOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      oil = new StreamResponse(inputSeparator.getOilOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gas = new StreamResponse(inputSeparator.getGasOutStream());
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
    gasLoadFactor = inputSeparator.getGasLoadFactor();
    feed = new StreamResponse(inputSeparator.getFeedStream());
    if (inputSeparator.getThermoSystem().hasPhaseType("aqueous")
        || inputSeparator.getThermoSystem().hasPhaseType("liquid")
        || inputSeparator.getThermoSystem().hasPhaseType("oil")) {
      liquid = new StreamResponse(inputSeparator.getLiquidOutStream());
    }
    if (inputSeparator.getThermoSystem().hasPhaseType("gas")) {
      gas = new StreamResponse(inputSeparator.getGasOutStream());
    }
  }
}
