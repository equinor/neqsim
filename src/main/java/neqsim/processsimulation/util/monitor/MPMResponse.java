package neqsim.processsimulation.util.monitor;

import neqsim.processsimulation.measurementdevice.MultiPhaseMeter;

/**
 * <p>
 * MPMResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MPMResponse {
  public String name;
  public Double massFLow, GOR, GOR_std, gasDensity, oilDensity, waterDensity;

  /**
   * <p>
   * Constructor for MPMResponse.
   * </p>
   *
   * @param inputMPM a {@link neqsim.processsimulation.measurementdevice.MultiPhaseMeter} object
   */
  public MPMResponse(MultiPhaseMeter inputMPM) {
    name = inputMPM.getName();
    massFLow = inputMPM.getMeasuredValue();
    GOR = inputMPM.getMeasuredValue("GOR", "");
    GOR_std = inputMPM.getMeasuredValue("GOR_std", "");
    gasDensity = inputMPM.getMeasuredValue("gasDensity", "");
    oilDensity = inputMPM.getMeasuredValue("oilDensity", "");
    waterDensity = inputMPM.getMeasuredValue("waterDensity", "");
  }
}
