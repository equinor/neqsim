package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * WellAllocator class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WellAllocator extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected StreamInterface exportGasStream = null;
  protected StreamInterface exportOilStream = null;

  /**
   * <p>
   * Constructor for WellAllocator.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WellAllocator(StreamInterface stream) {
    this("Well Allocator", stream);
  }

  /**
   * <p>
   * Constructor for WellAllocator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WellAllocator(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  /**
   * <p>
   * Setter for the field <code>exportGasStream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setExportGasStream(StreamInterface stream) {
    this.exportGasStream = stream;
  }

  /**
   * <p>
   * Setter for the field <code>exportOilStream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setExportOilStream(StreamInterface stream) {
    this.exportOilStream = stream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return stream.getThermoSystem().getFlowRate(unit);
  }

  /**
   * Get specific measurement type. Supports "gas export rate", "oil export rate" and "total export
   * rate".
   *
   * @param measurement Measurement value to get
   * @param unit Unit to get value in
   * @return Measured value
   */
  public double getMeasuredValue(String measurement, String unit) {
    int numberOfComps = stream.getThermoSystem().getNumberOfComponents();
    double[] splitFactors = new double[numberOfComps];
    double gasExportFlow = 0.0;
    double oilExportFlow = 0.0;
    for (int i = 0; i < numberOfComps; i++) {
      splitFactors[i] = exportGasStream.getFluid().getComponent(i).getFlowRate("kg/hr")
          / (exportGasStream.getFluid().getComponent(i).getFlowRate("kg/hr")
              + exportOilStream.getFluid().getComponent(i).getFlowRate("kg/hr"));
      gasExportFlow += stream.getFluid().getComponent(i).getTotalFlowRate(unit) * splitFactors[i];
      oilExportFlow +=
          stream.getFluid().getComponent(i).getTotalFlowRate(unit) * (1.0 - splitFactors[i]);
    }

    if (measurement.equals("gas export rate")) {
      return gasExportFlow;
    } else if (measurement.equals("oil export rate")) {
      return oilExportFlow;
    } else if (measurement.equals("total export rate")) {
      return stream.getFluid().getFlowRate(unit);
    }
    return 0.0;
  }
}
