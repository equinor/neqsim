package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.util.monitor.WellAllocatorResponse;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

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
    }
    if (measurement.equals("oil export rate")) {
      return oilExportFlow;
    }
    if (measurement.equals("total export rate")) {
      return wellStream.getFluid().getFlowRate("kg/hr");
    }
    return 0.0;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 14.053);
    testFluid.setMixingRule(2);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(2500.0, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);

    SystemInterface testFluid2 = testFluid.clone();
    // testFluid2.setMolarComposition(new double[] {0.1, 0.1, 0.9, 0.1, 0.1, 0.0, 0.0, 0.0, 0.0,
    // 0.0});
    Stream stream_2 = new Stream("Stream2", testFluid2);

    Separator sep1 = new Separator("sep1", stream_1);
    sep1.addStream(stream_2);

    Stream stream_gasExp = new Stream("gasexp", sep1.getGasOutStream());

    Stream stream_oilExp = new Stream("gasexp", sep1.getLiquidOutStream());

    WellAllocator wellAlloc = new WellAllocator("alloc", stream_1);
    wellAlloc.setExportGasStream(stream_gasExp);
    wellAlloc.setExportOilStream(stream_oilExp);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(stream_2);
    operations.add(sep1);
    operations.add(stream_gasExp);
    operations.add(stream_oilExp);
    operations.add(wellAlloc);
    operations.run();

    WellAllocatorResponse responsAl = new WellAllocatorResponse(wellAlloc);

    System.out.println("name " + responsAl.name);
    System.out.println("gas flow " + responsAl.gasExportRate);
    System.out.println("oil flow " + responsAl.oilExportRate);
    System.out.println("total flow " + responsAl.totalExportRate);
  }
}
