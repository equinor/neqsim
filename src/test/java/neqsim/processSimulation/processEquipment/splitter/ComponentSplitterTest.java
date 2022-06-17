package neqsim.processSimulation.processEquipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class ComponentSplitterTest {
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
    assertEquals(((StreamInterface)processOps.getUnit("stream 1")).getFluid().getComponent("methane").getx(), 1.0, 1e-6);
    assertEquals(((StreamInterface)processOps.getUnit("stream 2")).getFluid().getComponent("methane").getx(), 0.0, 1e-6);
  }

}
