package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.splitter.ComponentSplitter;

/**
 * ComponentSplitterResponse class provides basic reporting for a component splitter unit.
 */
public class ComponentSplitterResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.splitter.ComponentSplitter}.
   *
   * @param splitter the component splitter to create the response from
   */
  public ComponentSplitterResponse(ComponentSplitter splitter) {
    super(splitter);

    data.put("feed mass flow",
        new Value(
            Double.toString(
                splitter.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("feed temperature",
        new Value(
            Double.toString(splitter.getInletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("feed pressure",
        new Value(
            Double.toString(
                splitter.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));

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
      data.put("outlet " + (i + 1) + " pressure",
          new Value(
              Double.toString(
                  splitter.getSplitStream(i).getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
    }
  }
}

