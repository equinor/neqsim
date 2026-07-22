package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;

/**
 * HXResponse class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiStreamHeatExchanger2Response extends BaseResponse {
  public HashMap<String, Value> data = new HashMap<String, Value>();
  public Double temperatureApproach;

  public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> compositeCurveResults;

  /**
   * Constructor for HXResponse.
   */
  public MultiStreamHeatExchanger2Response() {
  }

  /**
   * Constructor for HXResponse.
   *
   * @param inputHeatExchanger a {@link neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2} object
   */
  public MultiStreamHeatExchanger2Response(MultiStreamHeatExchanger2 inputHeatExchanger) {
    super(inputHeatExchanger);
    temperatureApproach = inputHeatExchanger.getTemperatureApproach();
    compositeCurveResults = inputHeatExchanger.getCompositeCurve();
    data.put("temperature approach",
        new Value(Double.toString(temperatureApproach), neqsim.util.unit.Units.getSymbol("temperature")));
  }
}
