package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.mixer.Mixer;

/**
 * MixerResponse class provides basic reporting for a mixer unit.
 *
 * @author esol
 */
public class MixerResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.mixer.Mixer}.
   *
   * @param mixer the mixer to create the response from
   */
  public MixerResponse(Mixer mixer) {
    super(mixer);

    data.put("feed mass flow",
        new Value(
            Double.toString(
                mixer.getOutletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("outlet mass flow",
        new Value(
            Double.toString(
                mixer.getOutletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    data.put("outlet temperature",
        new Value(
            Double.toString(mixer.getOutletStream()
                .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    data.put("outlet pressure",
        new Value(
            Double.toString(
                mixer.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
  }
}
