package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiStreamHeatExchangerResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();
  public Double[] feedTemperature;
  public Double[] dischargeTemperature;
  public Double[] duty;
  public Double[] flowRate;

  /**
   * <p>
   * Constructor for MultiStreamHeatExchangerResponse.
   * </p>
   *
   * @param inputHX a {@link neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger} object
   */
  public MultiStreamHeatExchangerResponse(MultiStreamHeatExchanger inputHX) {
    super(inputHX);

    // Initialize arrays based on the number of feed streams
    int numberOfStreams = inputHX.numerOfFeedStreams();
    feedTemperature = new Double[numberOfStreams];
    dischargeTemperature = new Double[numberOfStreams];
    duty = new Double[numberOfStreams];
    flowRate = new Double[numberOfStreams];

    for (int i = 0; i < numberOfStreams; i++) {
      feedTemperature[i] = inputHX.getInStream(i).getTemperature("C");
      dischargeTemperature[i] = inputHX.getOutStream(i).getTemperature("C");
      duty[i] = inputHX.getDuty(i);
      flowRate[i] = inputHX.getInStream(i).getFlowRate("kg/hr");

      String streamId = Integer.toString(i + 1);
      data.add(new String[] {"feed temperature stream " + streamId,
          Double.toString(feedTemperature[i]), neqsim.util.unit.Units.getSymbol("temperature")});
      data.add(new String[] {"discharge temperature stream " + streamId,
          Double.toString(dischargeTemperature[i]), neqsim.util.unit.Units.getSymbol("temperature")});
      data.add(new String[] {"duty stream " + streamId, Double.toString(duty[i]),
          neqsim.util.unit.Units.getSymbol("duty")});
      data.add(new String[] {"mass flow stream " + streamId, Double.toString(flowRate[i]),
          neqsim.util.unit.Units.getSymbol("mass flow")});
    }

    data.add(new String[] {"UA value", Double.toString(inputHX.getUAvalue()), "W/K"});
  }
}
