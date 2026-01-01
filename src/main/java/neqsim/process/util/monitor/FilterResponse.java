package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.filter.Filter;

/**
 * <p>
 * FilterResponse class for JSON serialization of Filter equipment.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FilterResponse extends BaseResponse {
  /** Data map containing filter properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for FilterResponse.
   * </p>
   *
   * @param filter a {@link neqsim.process.equipment.filter.Filter} object
   */
  public FilterResponse(Filter filter) {
    super(filter);
    if (filter.getInletStream() != null) {
      data.put("mass flow", new Value(
          Double.toString(
              filter.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("inlet temperature",
          new Value(
              Double.toString(filter.getInletStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("inlet pressure", new Value(
          Double.toString(
              filter.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (filter.getOutletStream() != null) {
      data.put("outlet temperature",
          new Value(
              Double.toString(filter.getOutletStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("outlet pressure", new Value(
          Double.toString(
              filter.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    data.put("pressure drop", new Value(Double.toString(filter.getDeltaP()),
        neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("Cv factor", new Value(Double.toString(filter.getCvFactor()), ""));
  }
}
