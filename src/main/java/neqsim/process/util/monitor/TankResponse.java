package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.tank.Tank;

/**
 * <p>
 * TankResponse class provides a simple example of how to report information
 * from a tank unit operation.
 * </p>
 */
public class TankResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.tank.Tank}.
   *
   * @param tank the tank to create the response from
   */
  public TankResponse(Tank tank) {
    super(tank);

    data.add(new String[] {"liquid level", Double.toString(tank.getLiquidLevel()), ""});
    data.add(new String[] {"volume", Double.toString(tank.getVolume()), "m3"});

    data.add(new String[] {"gas outlet temperature",
        Double.toString(tank.getGasOutStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"gas outlet pressure",
        Double.toString(tank.getGasOutStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"gas outlet mass flow",
        Double.toString(tank.getGasOutStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});

    data.add(new String[] {"liquid outlet temperature",
        Double.toString(tank.getLiquidOutStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"liquid outlet pressure",
        Double.toString(tank.getLiquidOutStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"liquid outlet mass flow",
        Double.toString(tank.getLiquidOutStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
  }
}
