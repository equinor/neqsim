package neqsim.processSimulation.processEquipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class ComponentSplitterTest {
  static Logger logger = LogManager.getLogger(ComponentSplitterTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  @BeforeEach
  public void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    ComponentSplitter splitter = new ComponentSplitter("splitter", inletStream);
    splitter.setSplitFactors(new double[] {1.00, 0.0, 0.0});
    
    StreamInterface stream1 = new Stream("stream 1", splitter.getSplitStream(0));
    StreamInterface stream2 = new Stream("stream 2",splitter.getSplitStream(1));
    
    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);
  }

  @Test
  public void testRun() {
    processOps.run();
    //((StreamInterface)processOps.getUnit("stream 1")).displayResult();
    //((StreamInterface)processOps.getUnit("stream 2")).displayResult();
    assertEquals(((StreamInterface) processOps.getUnit("stream 1")).getFluid()
        .getComponent("methane").getx(), 1.0, 1e-6);
    assertEquals(((StreamInterface) processOps.getUnit("stream 2")).getFluid()
        .getComponent("methane").getx(), 0.0, 1e-6);
  }

  @Test
  public void testRunSplitter() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    splitter.setFlowRates(new double[] {4.0, 1.0}, "MSm3/day");
    // splitter.setFlowRates(new double[] {-1.0, 1.0}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    ThrottlingValve valve1 = new ThrottlingValve("valve", stream1);
    valve1.setCv(500.0);
    valve1.setOutletPressure(5.0);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);
    processOps.add(valve1);

    processOps.run();

    assertEquals(stream1.getFlowRate("MSm3/day"), 4.0, 1e-6);
    assertEquals(stream2.getFlowRate("MSm3/day"), 1.0, 1e-6);
    logger.info("valve opening " + valve1.getPercentValveOpening());

    splitter.setFlowRates(new double[] {-1, 4.9}, "MSm3/day");
    processOps.run();

    logger.info("valve opening " + valve1.getPercentValveOpening());
    assertEquals(0.1, splitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-6);
    assertEquals(4.9, splitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testRunSplitter2() {
    testSystem = new SystemSrkEos(298.0, 55.0);
    testSystem.addComponent("methane", 100.0);

    processOps = new ProcessSystem();

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(55.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");

    Stream streamresycl = inletStream.clone();

    Mixer mixer1 = new Mixer("mixer 1");
    mixer1.addStream(inletStream);
    mixer1.addStream(streamresycl);

    Compressor compressor1 = new Compressor("compressor 1", mixer1.getOutletStream());
    compressor1.setOutletPressure(100.0);

    Stream compressedStream = (Stream) compressor1.getOutletStream();

    Splitter splitter = new Splitter("splitter 1", compressedStream);
    splitter.setFlowRates(new double[] {5.0, 0.1}, "MSm3/day");

    StreamInterface resycStream1 = splitter.getSplitStream(1);

    ThrottlingValve valve1 = new ThrottlingValve("valve 1", resycStream1);
    valve1.setOutletPressure(55.0);
    valve1.setCv(500.0);

    Recycle recycle1 = new Recycle("recycle 1");
    recycle1.addStream(valve1.getOutletStream());
    recycle1.setOutletStream(streamresycl);

    StreamInterface exportStream = splitter.getSplitStream(0);

    processOps.add(inletStream);
    processOps.add(streamresycl);
    processOps.add(mixer1);
    processOps.add(compressor1);
    processOps.add(compressedStream);
    processOps.add(splitter);
    processOps.add(resycStream1);
    processOps.add(valve1);
    processOps.add(recycle1);
    processOps.add(exportStream);

    processOps.run();

    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.1, resycStream1.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(8.43553108874272, valve1.getPercentValveOpening(), 1e-2);


    splitter.setFlowRates(new double[] {5.0, 0.5}, "MSm3/day");
    processOps.run();

    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(0.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(41.9139926125338, valve1.getPercentValveOpening(), 1e-2);

    splitter.setFlowRates(new double[] {-1, 0.5}, "MSm3/day");
    processOps.run();
    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(0.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);

  }

}
