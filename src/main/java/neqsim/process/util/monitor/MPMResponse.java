package neqsim.process.util.monitor;

import neqsim.process.measurementdevice.MultiPhaseMeter;

/**
 * MPMResponse class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MPMResponse extends BaseResponse {
  public Double massFLow, GOR, GOR_std, gasDensity, oilDensity, waterDensity;

  /**
   * Constructor for MPMResponse.
   *
   * @param inputMPM a {@link neqsim.process.measurementdevice.MultiPhaseMeter} object
   */
  public MPMResponse(MultiPhaseMeter inputMPM) {
    super(inputMPM);
    massFLow = inputMPM.getMeasuredValue();
    GOR = inputMPM.getMeasuredValue("GOR", "");
    GOR_std = inputMPM.getMeasuredValue("GOR_std", "");
    gasDensity = inputMPM.getMeasuredValue("gasDensity", "");
    oilDensity = inputMPM.getMeasuredValue("oilDensity", "");
    waterDensity = inputMPM.getMeasuredValue("waterDensity", "");
  }
}
