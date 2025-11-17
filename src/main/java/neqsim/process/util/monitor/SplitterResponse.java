package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.splitter.Splitter;

/**
 * SplitterResponse class provides basic reporting for a splitter unit.
 *
 * @author esol
 */
public class SplitterResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.splitter.Splitter}.
   *
   * @param splitter the splitter to create the response from
   */
  public SplitterResponse(Splitter splitter) {
    super(splitter);

    data.put("inlet mass flow", new Value(
        Double.toString(
            splitter.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("inlet temperature",
        new Value(
            Double.toString(splitter.getInletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("inlet pressure", new Value(
        Double.toString(
            splitter.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")));

    // Add outlet stream data for each split
    for (int i = 0; i < splitter.getSplitNumber(); i++) {
      data.put("outlet " + (i + 1) + " mass flow",
          new Value(
              Double.toString(splitter.getSplitStream(i)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("outlet " + (i + 1) + " temperature",
          new Value(
              Double.toString(splitter.getSplitStream(i)
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("outlet " + (i + 1) + " pressure", new Value(
          Double.toString(
              splitter.getSplitStream(i).getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
      data.put("outlet " + (i + 1) + " split factor",
          new Value(Double.toString(splitter.getSplitFactor(i)), ""));
    }
  }
}

