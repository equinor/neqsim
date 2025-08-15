package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.util.Recycle;

/**
 * RecycleResponse class provides basic reporting for a recycle unit.
 */
public class RecycleResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.util.Recycle}.
   *
   * @param recycle the recycle to create the response from
   */
  public RecycleResponse(Recycle recycle) {
    super(recycle);

    data.put("outlet mass flow",
        new Value(
            Double.toString(recycle.getOutletStream()
                .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("outlet temperature",
        new Value(
            Double.toString(recycle.getOutletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("outlet pressure",
        new Value(
            Double.toString(recycle.getOutletStream()
                .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));

    data.put("error composition",
        new Value(Double.toString(recycle.getErrorComposition()), ""));
    data.put("error flow", new Value(Double.toString(recycle.getErrorFlow()), ""));
    data.put("error temperature",
        new Value(Double.toString(recycle.getErrorTemperature()), ""));
    data.put("error pressure",
        new Value(Double.toString(recycle.getErrorPressure()), ""));
  }
}
