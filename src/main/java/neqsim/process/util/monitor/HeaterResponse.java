package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.heatexchanger.Heater;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HeaterResponse extends BaseResponse {

  public ArrayList<String[]> data = new ArrayList<String[]>();

  /**
   * <p>
   * Constructor for HeaterResponse.
   * </p>
   *
   * @param inputHeater a {@link neqsim.process.equipment.heatexchanger.Heater} object
   */
  public HeaterResponse(Heater inputHeater) {
    super(inputHeater);

    data.add(new String[] {"flow rate",
        Double.toString(inputHeater.getInletStream()
            .getFlowRate(neqsim.util.unit.Units.getSymbol("flow rate"))),
        neqsim.util.unit.Units.getSymbol("flow rate")});


    data.add(new String[] {"inlet temperature",
        Double.toString(inputHeater.getInletStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});

    data.add(new String[] {"inlet pressure",
        Double.toString(
            inputHeater.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"outlet temperature",
        Double.toString(inputHeater.getOutletStream()
            .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"outlet pressure",
        Double.toString(inputHeater.getOutletStream()
            .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});

    data.add(new String[] {"duty",
        Double.toString(inputHeater.getDuty(neqsim.util.unit.Units.getSymbol("duty"))),
        neqsim.util.unit.Units.getSymbol("duty")});
  }
}
