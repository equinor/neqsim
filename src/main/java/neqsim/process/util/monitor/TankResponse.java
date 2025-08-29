package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.tank.Tank;

/**
 * <p>
 * TankResponse class provides a simple example of how to report information from a tank unit
 * operation.
 * </p>
 *
 * @author esol
 */
public class TankResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.tank.Tank}.
   *
   * @param tank the tank to create the response from
   */
  public TankResponse(Tank tank) {
    super(tank);

    data.put("liquid level", new Value(Double.toString(tank.getLiquidLevel()), ""));
    data.put("volume", new Value(Double.toString(tank.getVolume()), "m3"));

    data.put("gas outlet temperature", new Value(

        Double.toString(
            tank.getGasOutStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("gas outlet pressure",
        new Value(
            Double.toString(
                tank.getGasOutStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("gas outlet mass flow",
        new Value(
            Double.toString(
                tank.getGasOutStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),

            neqsim.util.unit.Units.getSymbol("mass flow")));

    data.put("liquid outlet temperature",
        new Value(
            Double.toString(tank.getLiquidOutStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("liquid outlet pressure", new Value(
        Double.toString(
            tank.getLiquidOutStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("liquid outlet mass flow", new Value(
        Double.toString(
            tank.getLiquidOutStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),

        neqsim.util.unit.Units.getSymbol("mass flow")));
  }
}
