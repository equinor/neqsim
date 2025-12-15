package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.heatexchanger.Heater;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HeaterResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for HeaterResponse.
   * </p>
   *
   * @param inputHeater a {@link neqsim.process.equipment.heatexchanger.Heater} object
   */
  public HeaterResponse(Heater inputHeater) {
    super(inputHeater);
    data.put("mass flow",
        new Value(
            Double.toString(inputHeater.getInletStream()
                .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));

    data.put("inlet temperature",
        new Value(
            Double.toString(inputHeater.getInletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));

    data.put("inlet pressure", new Value(
        Double.toString(
            inputHeater.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")));

    data.put("outlet temperature",
        new Value(
            Double.toString(inputHeater.getOutletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));

    data.put("outlet pressure",
        new Value(
            Double.toString(inputHeater.getOutletStream()
                .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));

    data.put("duty",
        new Value(Double.toString(inputHeater.getDuty(neqsim.util.unit.Units.getSymbol("duty"))),
            neqsim.util.unit.Units.getSymbol("duty")));
  }
}
