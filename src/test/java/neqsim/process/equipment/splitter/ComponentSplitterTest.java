package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class ComponentSplitterTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentSplitterTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    ComponentSplitter splitter = new ComponentSplitter("splitter", inletStream);
    splitter.setSplitFactors(new double[] {1.00, 0.0, 0.0});

    StreamInterface stream1 = new Stream("stream 1", splitter.getSplitStream(0));
    StreamInterface stream2 = new Stream("stream 2", splitter.getSplitStream(1));

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);
  }

  @Test
  public void configSplitter() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    Splitter splitter = new Splitter("splitter", inletStream, 3);
    splitter.setSplitFactors(new double[] {0.8, 0.2, 0.0});
    splitter.run();
    assertEquals(0.815104472498348, splitter.getSplitStream(0).getFluid().getPhase(0).getZ(), 0.01);
    assertEquals(0.815104472498348, splitter.getSplitStream(1).getFluid().getPhase(0).getZ(), 0.01);
    assertEquals(0.815104472498348, splitter.getSplitStream(2).getFluid().getPhase(0).getZ(), 0.01);
  }

  @Test
  public void testRun() {
    processOps.run();
    // ((StreamInterface)processOps.getUnit("stream 1")).displayResult();
    // ((StreamInterface)processOps.getUnit("stream 2")).displayResult();
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
    Stream inletStream = new Stream("inlet stream", testSystem);
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
    testSystem.addComponent("ethane", 10.0);

    processOps = new ProcessSystem();

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(55.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");

    Stream streamresycl = inletStream.clone("recycle stream");

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
    recycle1.setTolerance(1e-6);

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
    // assertEquals(8.43553108874272, valve1.getPercentValveOpening(), 1e-2);

    splitter.setFlowRates(new double[] {5.0, 0.5}, "MSm3/day");
    processOps.run();

    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(0.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);
    // assertEquals(41.9139926125338, valve1.getPercentValveOpening(), 1e-2);

    splitter.setFlowRates(new double[] {-1, 2.5}, "MSm3/day");
    processOps.run();
    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(2.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);

    splitter.setFlowRates(new double[] {5.0, 0.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);

    splitter.setFlowRates(new double[] {5.0, 3.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(3.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);

    splitter.setFlowRates(new double[] {-1, 0.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOnePositionFirst() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    // -1 in first position
    splitter.setFlowRates(new double[] {-1, 1.0}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);

    processOps.run();

    // stream1 should get 4.0 MSm3/day (5.0 - 1.0)
    assertEquals(4.0, stream1.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(1.0, stream2.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOnePositionLast() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    // -1 in last position
    splitter.setFlowRates(new double[] {1.0, -1}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);

    processOps.run();

    // stream1 should get 1.0 MSm3/day, stream2 should get 4.0 (5.0 - 1.0)
    assertEquals(1.0, stream1.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(4.0, stream2.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOneArbitraryPosition() {
    // Test that -1 produces identical results regardless of position
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);

    // Test with -1 first
    ProcessSystem processOps1 = new ProcessSystem();
    neqsim.thermo.system.SystemInterface testSystem1 = new SystemSrkEos(298.0, 10.0);
    testSystem1.addComponent("methane", 100.0);
    testSystem1.addComponent("ethane", 10.0);
    testSystem1.addComponent("propane", 10.0);
    Stream inletStream1 = new Stream("inlet stream", testSystem1);
    inletStream1.setPressure(pressure_inlet, "bara");
    inletStream1.setTemperature(temperature_inlet, "C");
    inletStream1.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter1 = new Splitter("splitter", inletStream1);
    splitter1.setSplitNumber(2);
    splitter1.setFlowRates(new double[] {-1, 2.5}, "MSm3/day");

    processOps1.add(inletStream1);
    processOps1.add(splitter1);
    processOps1.run();

    double result1Stream0 = splitter1.getSplitStream(0).getFlowRate("MSm3/day");
    double result1Stream1 = splitter1.getSplitStream(1).getFlowRate("MSm3/day");

    // Test with -1 last
    ProcessSystem processOps2 = new ProcessSystem();
    neqsim.thermo.system.SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);
    testSystem2.addComponent("methane", 100.0);
    testSystem2.addComponent("ethane", 10.0);
    testSystem2.addComponent("propane", 10.0);
    Stream inletStream2 = new Stream("inlet stream", testSystem2);
    inletStream2.setPressure(pressure_inlet, "bara");
    inletStream2.setTemperature(temperature_inlet, "C");
    inletStream2.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter2 = new Splitter("splitter", inletStream2);
    splitter2.setSplitNumber(2);
    splitter2.setFlowRates(new double[] {2.5, -1}, "MSm3/day");

    processOps2.add(inletStream2);
    processOps2.add(splitter2);
    processOps2.run();

    double result2Stream0 = splitter2.getSplitStream(0).getFlowRate("MSm3/day");
    double result2Stream1 = splitter2.getSplitStream(1).getFlowRate("MSm3/day");

    // Both configurations should produce the same results
    assertEquals(result1Stream0, result2Stream0, 1e-6);
    assertEquals(result1Stream1, result2Stream1, 1e-6);
    // Verify the actual values
    assertEquals(2.5, result1Stream0, 1e-6);
    assertEquals(2.5, result2Stream0, 1e-6);
    assertEquals(2.5, result1Stream1, 1e-6);
    assertEquals(2.5, result2Stream1, 1e-6);
  }
}
