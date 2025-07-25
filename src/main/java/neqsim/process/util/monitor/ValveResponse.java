package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.valve.ValveInterface;

/**
 * <p>
 * StreamResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for StreamResponse.
   * </p>
   *
   * @param valve a {@link neqsim.process.equipment.valve.ValveInterface} object
   */
  public ValveResponse(ValveInterface valve) {
    super(valve);
    data.put("mass flow",
        new Value(
            Double.toString(
                valve.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("inlet temperature",
        new Value(
            Double.toString(
                valve.getInletStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("inlet pressure",
        new Value(
            Double.toString(
                valve.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("outlet temperature",
        new Value(
            Double.toString(valve.getOutletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("outlet pressure",
        new Value(
            Double.toString(
                valve.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
