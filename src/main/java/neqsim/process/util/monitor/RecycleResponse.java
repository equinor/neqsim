package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.util.Recycle;

/**
 * RecycleResponse class provides basic reporting for a recycle unit.
 */
public class RecycleResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.util.Recycle}.
   *
   * @param recycle the recycle to create the response from
   */
  public RecycleResponse(Recycle recycle) {
    super(recycle);

    data.add(new String[] {"outlet mass flow",
        Double.toString(recycle.getOutletStream()
            .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    data.add(new String[] {"outlet temperature",
        Double.toString(recycle.getOutletStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"outlet pressure",
        Double.toString(recycle.getOutletStream()
            .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});

    data.add(new String[] {"error composition", Double.toString(recycle.getErrorComposition()), ""});
    data.add(new String[] {"error flow", Double.toString(recycle.getErrorFlow()), ""});
    data.add(new String[] {"error temperature", Double.toString(recycle.getErrorTemperature()), ""});
    data.add(new String[] {"error pressure", Double.toString(recycle.getErrorPressure()), ""});
  }
}
