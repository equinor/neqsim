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

  /** Motive stream response. */
  public StreamResponse motiveStream;

  /** Suction stream response. */
  public StreamResponse suctionStream;

  /** Mixed (outlet) stream response. */
  public StreamResponse mixedStream;

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
      motiveStream = new StreamResponse(ejector.getMotiveStream());
    }
    if (ejector.getSuctionStream() != null) {
      suctionStream = new StreamResponse(ejector.getSuctionStream());
    }
    if (ejector.getMixedStream() != null) {
      mixedStream = new StreamResponse(ejector.getMixedStream());
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
