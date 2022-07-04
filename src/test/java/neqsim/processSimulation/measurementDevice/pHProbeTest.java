package neqsim.processSimulation.measurementDevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

class pHProbeTest {

  @BeforeAll
  static void setUpBeforeClass() throws Exception {}

  @Test
  void testGetMeasuredValue() {
    SystemInterface testFluid = new SystemSrkCPAstatoil(318.15, 50.0);
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
    testFluid.addComponent("water", 141.053);
    testFluid.setMixingRule(10);
    testFluid.setMultiPhaseCheck(true);
    
    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
    
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.run();
    System.out.println("pH " + phmeasurement.getMeasuredValue());
    assertEquals(4.079098133484792, phmeasurement.getMeasuredValue(),0.01);
  }
  
  @Test
  void testGetMeasuredValueWithAlkanility() {
    SystemInterface testFluid = new SystemSrkCPAstatoil(318.15, 50.0);
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
    testFluid.addComponent("water", 141.053);
    testFluid.setMixingRule(10);
    testFluid.setMultiPhaseCheck(true);
    
    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
    
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.setAlkanility(50.0);
    phmeasurement.run();
    System.out.println("pH " + phmeasurement.getMeasuredValue());
    assertEquals(5.629055432357595, phmeasurement.getMeasuredValue(),0.01);
  }

  @Test
  void testPHProbe() {
    //fail("Not yet implemented");
  }

  @Test
  void testPHProbeStreamInterface() {
    //fail("Not yet implemented");
  }

}
