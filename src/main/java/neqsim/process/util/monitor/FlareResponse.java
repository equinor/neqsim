package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.flare.Flare;

/**
 * <p>
 * FlareResponse class for JSON serialization of Flare equipment.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FlareResponse extends BaseResponse {
  /** Data map containing flare properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /** Inlet stream response. */
  public StreamResponse inlet;

  /**
   * <p>
   * Constructor for FlareResponse.
   * </p>
   *
   * @param flare a {@link neqsim.process.equipment.flare.Flare} object
   */
  public FlareResponse(Flare flare) {
    super(flare);
    if (flare.getInletStream() != null) {
      inlet = new StreamResponse(flare.getInletStream());
      data.put("mass flow", new Value(
          Double.toString(
              flare.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("inlet temperature",
          new Value(
              Double.toString(flare.getInletStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("inlet pressure",
          new Value(
              Double.toString(
                  flare.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
    }
    data.put("heat duty", new Value(Double.toString(flare.getHeatDuty("MW")), "MW"));
    data.put("CO2 emission", new Value(Double.toString(flare.getCO2Emission("kg/hr")), "kg/hr"));
  }
}
