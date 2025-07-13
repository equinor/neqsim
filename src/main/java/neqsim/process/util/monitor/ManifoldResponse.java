package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.manifold.Manifold;

/**
 * ManifoldResponse class provides basic reporting for a manifold unit.
 */
public class ManifoldResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.manifold.Manifold}.
   *
   * @param manifold the manifold to create the response from
   */
  public ManifoldResponse(Manifold manifold) {
    super(manifold);

    data.add(new String[] {"mixed mass flow",
        Double.toString(manifold.getMixedStream()
            .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    data.add(new String[] {"mixed temperature",
        Double.toString(manifold.getMixedStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"mixed pressure",
        Double.toString(manifold.getMixedStream()
            .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});

    for (int i = 0; i < manifold.getNumberOfOutputStreams(); i++) {
      data.add(new String[] {"split mass flow " + (i + 1),
          Double.toString(manifold.getSplitStream(i)
              .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")});
    }
  }
}
