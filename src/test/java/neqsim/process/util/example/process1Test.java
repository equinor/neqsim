package neqsim.process.util.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

class process1Test extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem;
  static neqsim.process.processmodel.ProcessSystem operations;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkCPA((273.15 + 25.0), 50.00);
    testSystem.addComponent("methane", 180.00);
    testSystem.addComponent("ethane", 10.00);
    testSystem.addComponent("propane", 1.00);
    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);

    neqsim.process.equipment.compressor.Compressor compr =
        new neqsim.process.equipment.compressor.Compressor("compr", stream_1);
    compr.setOutletPressure(80.0);
    compr.setPolytropicEfficiency(0.9);
    compr.setIsentropicEfficiency(0.9);
    compr.setUsePolytropicCalc(true);

    operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(compr);
  }

  @Test
  void runTest() {
    operations.run();
  }
}
