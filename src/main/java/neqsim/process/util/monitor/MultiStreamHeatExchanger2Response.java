package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;

/**
 * <p>
 * HXResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiStreamHeatExchanger2Response extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();
  public Double temperatureApproach;

  public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> compositeCurveResults;

  /**
   * <p>
   * Constructor for HXResponse.
   * </p>
   */
  public MultiStreamHeatExchanger2Response() {}

  /**
   * <p>
   * Constructor for HXResponse.
   * </p>
   *
   * @param inputHeatExchanger a
   *        {@link neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2} object
   */
  public MultiStreamHeatExchanger2Response(MultiStreamHeatExchanger2 inputHeatExchanger) {
    super(inputHeatExchanger);
    temperatureApproach = inputHeatExchanger.getTemperatureApproach();
    compositeCurveResults = inputHeatExchanger.getCompositeCurve();
    data.add(new String[] {"temperature approach", Double.toString(temperatureApproach),
        neqsim.util.unit.Units.getSymbol("temperature")});
  }
}
