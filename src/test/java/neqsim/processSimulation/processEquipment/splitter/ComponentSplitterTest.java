package neqsim.processSimulation.processEquipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
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

}
