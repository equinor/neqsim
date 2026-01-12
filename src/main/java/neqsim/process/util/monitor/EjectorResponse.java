package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.ejector.Ejector;

/**
 * <p>
 * EjectorResponse class for JSON serialization of Ejector equipment.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EjectorResponse extends BaseResponse {
  /** Data map containing ejector properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for EjectorResponse.
   * </p>
   *
   * @param ejector a {@link neqsim.process.equipment.ejector.Ejector} object
   */
  public EjectorResponse(Ejector ejector) {
    super(ejector);
    if (ejector.getMotiveStream() != null) {
      data.put("motive mass flow", new Value(
          Double.toString(
              ejector.getMotiveStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("motive pressure", new Value(
          Double.toString(
              ejector.getMotiveStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (ejector.getSuctionStream() != null) {
      data.put("suction mass flow",
          new Value(
              Double.toString(ejector.getSuctionStream()
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("suction pressure", new Value(
          Double.toString(
              ejector.getSuctionStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (ejector.getMixedStream() != null) {
      data.put("discharge mass flow", new Value(
          Double.toString(
              ejector.getMixedStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("discharge pressure", new Value(
          Double.toString(
              ejector.getMixedStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
      data.put("discharge temperature",
          new Value(
              Double.toString(ejector.getMixedStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
    }
    data.put("isentropic efficiency",
        new Value(Double.toString(ejector.getEfficiencyIsentropic()), ""));
    data.put("diffuser efficiency",
        new Value(Double.toString(ejector.getDiffuserEfficiency()), ""));
  }
}
