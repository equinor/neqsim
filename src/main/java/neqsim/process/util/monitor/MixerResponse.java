package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.mixer.Mixer;

/**
 * MixerResponse class provides basic reporting for a mixer unit.
 */
public class MixerResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * Create a response based on a {@link neqsim.process.equipment.mixer.Mixer}.
   *
   * @param mixer the mixer to create the response from
   */
  public MixerResponse(Mixer mixer) {
    super(mixer);

    data.add(new String[] {"mass flow",
        Double.toString(mixer.getOutletStream()
            .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    data.add(new String[] {"outlet temperature",
        Double.toString(mixer.getOutletStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"outlet pressure",
        Double.toString(
            mixer.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
  }
}
