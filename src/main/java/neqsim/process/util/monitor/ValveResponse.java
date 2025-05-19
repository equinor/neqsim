package neqsim.process.util.monitor;

import java.util.ArrayList;
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
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * <p>
   * Constructor for StreamResponse.
   * </p>
   *
   * @param valve a {@link neqsim.process.equipment.valve.ValveInterface} object
   */
  public ValveResponse(ValveInterface valve) {
    super(valve);
    data.add(new String[] {"mass flow",
        Double.toString(
            valve.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    data.add(new String[] {"inlet temperature",
        Double.toString(
            valve.getInletStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"inlet pressure",
        Double.toString(
            valve.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"outlet temperature",
        Double.toString(valve.getOutletStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"outlet pressure",
        Double.toString(
            valve.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
