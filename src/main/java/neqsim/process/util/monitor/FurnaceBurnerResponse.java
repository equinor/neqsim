package neqsim.process.util.monitor;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.reactor.FurnaceBurner;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FurnaceBurnerResponse class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FurnaceBurnerResponse extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for FurnaceBurnerResponse.
   * </p>
   *
   * @param burner a {@link neqsim.process.equipment.reactor.FurnaceBurner} object
   */
  public FurnaceBurnerResponse(FurnaceBurner burner) {
    super(burner);

    if (burner.getFuelInlet() != null) {
      data.put("fuel inlet temperature",
          new Value(Double.toString(burner.getFuelInlet().getTemperature("K")), "K"));
      data.put("fuel inlet pressure",
          new Value(Double.toString(burner.getFuelInlet().getPressure("bara")), "bara"));
      data.put("fuel inlet flow",
          new Value(Double.toString(burner.getFuelInlet().getFlowRate("kg/hr")), "kg/hr"));
    }

    if (burner.getAirInlet() != null) {
      data.put("air inlet temperature",
          new Value(Double.toString(burner.getAirInlet().getTemperature("K")), "K"));
      data.put("air inlet pressure",
          new Value(Double.toString(burner.getAirInlet().getPressure("bara")), "bara"));
      data.put("air inlet flow",
          new Value(Double.toString(burner.getAirInlet().getFlowRate("kg/hr")), "kg/hr"));
    }

    if (burner.getOutletStream() != null) {
      data.put("outlet temperature",
          new Value(Double.toString(burner.getOutletStream().getTemperature("K")), "K"));
      data.put("outlet pressure",
          new Value(Double.toString(burner.getOutletStream().getPressure("bara")), "bara"));
      data.put("outlet flow",
          new Value(Double.toString(burner.getOutletStream().getFlowRate("kg/hr")), "kg/hr"));
    }

    data.put("flame temperature", new Value(Double.toString(burner.getFlameTemperature()), "K"));
    data.put("heat release", new Value(Double.toString(burner.getHeatReleasekW()), "kW"));

    for (Map.Entry<String, Double> entry : burner.getEmissionRatesKgPerHr().entrySet()) {
      data.put("emission " + entry.getKey(), new Value(Double.toString(entry.getValue()), "kg/hr"));
      if (burner.getOutletStream() != null) {
        SystemInterface system = burner.getOutletStream().getThermoSystem();
        if (system.hasComponent(entry.getKey())) {
          double moleFraction = system.getComponent(entry.getKey()).getz();
          data.put("mole fraction " + entry.getKey(),
              new Value(Double.toString(moleFraction), "-"));
        }
      }
    }
  }
}
