package neqsim.processSimulation.util.monitor;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.valve.ValveInterface;

/**
 * <p>
 * StreamResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveResponse {
  public String name;
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * <p>
   * Constructor for StreamResponse.
   * </p>
   *
   * @param valve a {@link neqsim.processSimulation.processEquipment.valve.ValveInterface} object
   */
  public ValveResponse(ValveInterface valve) {

    name = valve.getName();

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
