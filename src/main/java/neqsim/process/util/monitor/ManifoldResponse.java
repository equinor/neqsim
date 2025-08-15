package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.manifold.Manifold;

/**
 * ManifoldResponse class provides basic reporting for a manifold unit.
 */
public class ManifoldResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.manifold.Manifold}.
   *
   * @param manifold the manifold to create the response from
   */
  public ManifoldResponse(Manifold manifold) {
    super(manifold);

    data.put("mixed mass flow",
        new Value(
            Double.toString(manifold.getMixedStream()
                .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("mixed temperature",
        new Value(
            Double.toString(manifold.getMixedStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("mixed pressure",
        new Value(
            Double.toString(manifold.getMixedStream()
                .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));

    for (int i = 0; i < manifold.getNumberOfOutputStreams(); i++) {
      data.put("split mass flow " + (i + 1),
          new Value(
              Double.toString(manifold.getSplitStream(i)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
    }
  }
}
