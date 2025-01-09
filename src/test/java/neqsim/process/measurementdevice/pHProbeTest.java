package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

class pHProbeTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(pHProbeTest.class);

  static SystemInterface testFluid;
  static StreamInterface stream_1;

  @BeforeEach
  void setUp() {
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
    stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
  }

  @Test
  void testGetMeasuredValue() {
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.run();
    logger.info("pH " + phmeasurement.getMeasuredValue());
    assertEquals(4.079098133484792, phmeasurement.getMeasuredValue(), 0.01);
  }

  @Test
  void testGetMeasuredValueWithAlkalinity() {
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.setAlkalinity(50.0);
    phmeasurement.run();
    logger.info("pH " + phmeasurement.getMeasuredValue());
    assertEquals(5.629055432357595, phmeasurement.getMeasuredValue(), 0.01);
  }
}
