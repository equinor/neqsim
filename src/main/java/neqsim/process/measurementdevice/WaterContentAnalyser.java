package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * WaterContentAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterContentAnalyser extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for WaterContentAnalyser.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterContentAnalyser(StreamInterface stream) {
    this("water analyser", stream);
  }

  /**
   * <p>
   * Constructor for WaterContentAnalyser.
   * </p>
   *
   * @param name Name of WaterContentAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterContentAnalyser(String name, StreamInterface stream) {
    super(name, "kg/day", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      System.out.println("total water production [kg/dag]"
          + stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
              * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600
              * 24);
      System.out.println("water in phase 1 (ppm) "
          + stream.getThermoSystem().getPhase(0).getComponent("water").getx() * 1e6);
    } finally {
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
        * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600 * 24;
  }
}
