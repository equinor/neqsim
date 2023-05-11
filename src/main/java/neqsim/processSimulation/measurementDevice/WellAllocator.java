package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * WellAllocator class.
 * </p>
 *
 * @author ASMF
 * @version $Id: $Id
 */
public class WellAllocator extends MeasurementDeviceBaseClass {

  private static final long serialVersionUID = 1L;
  protected StreamInterface wellStream = null;
  protected StreamInterface exportGasStream = null;
  protected StreamInterface exportOilStream = null;

  /**
   * <p>
   * Constructor for WellAllocator.
   * </p>
   */
  public WellAllocator() {
    name = "Well Allocator";
  }

  /**
   * <p>
   * Constructor for WellAllocator.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public WellAllocator(StreamInterface stream) {
    name = "Well Allocator";
    this.wellStream = stream;
  }

  /**
   * <p>
   * Constructor for WellAllocator.
   * </p>
   *
   * @param streamname a {@link java.lang.String} object
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public WellAllocator(String streamname, StreamInterface stream) {
    this(stream);
    name = streamname;
  }

  /**
   * <p>
   * Setter for the field <code>exportGasStream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public void setExportGasStream(StreamInterface stream) {
    this.exportGasStream = stream;
  }

  /**
   * <p>
   * Setter for the field <code>exportOilStream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public void setExportOilStream(StreamInterface stream) {
    this.exportOilStream = stream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return wellStream.getThermoSystem().getFlowRate("kg/hr");
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String measurement) {
    int numberOfComps = wellStream.getThermoSystem().getNumberOfComponents();
    double[] splitFactors = new double[numberOfComps];
    double gasExportFlow = 0.0;
    double oilExportFlow = 0.0;
    for (int i = 0; i < numberOfComps; i++) {
      splitFactors[i] = exportGasStream.getFluid().getComponent(i).getFlowRate("kg/hr")
          / (exportGasStream.getFluid().getComponent(i).getFlowRate("kg/hr")
              + exportOilStream.getFluid().getComponent(i).getFlowRate("kg/hr"));
      gasExportFlow +=
          wellStream.getFluid().getComponent(i).getTotalFlowRate("kg/hr") * splitFactors[i];
      oilExportFlow +=
          wellStream.getFluid().getComponent(i).getTotalFlowRate("kg/hr") * (1.0 - splitFactors[i]);
    }

    if (measurement.equals("gas export rate")) {
      return gasExportFlow;
    } else if (measurement.equals("oil export rate")) {
      return oilExportFlow;
    } else if (measurement.equals("total export rate")) {
      return wellStream.getFluid().getFlowRate("kg/hr");
    }
    return 0.0;
  }
}
