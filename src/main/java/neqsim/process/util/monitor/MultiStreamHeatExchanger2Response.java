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
  public Double specifiedTemperatureApproach;
  public Double maximumFeasibleApproach;
  public String solverStatus;
  public String solverMessage;

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
    specifiedTemperatureApproach = inputHeatExchanger.getSpecifiedTemperatureApproach();
    double maxFeasible = inputHeatExchanger.getMaximumFeasibleApproach();
    maximumFeasibleApproach = Double.isNaN(maxFeasible) ? null : maxFeasible;
    solverStatus = inputHeatExchanger.getSolverStatus().name();
    solverMessage = inputHeatExchanger.getSolverMessage();
    compositeCurveResults = inputHeatExchanger.getCompositeCurve();
    data.put("temperature approach",
        new Value(Double.toString(temperatureApproach), neqsim.util.unit.Units.getSymbol("temperature")));
  }
}
