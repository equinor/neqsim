package neqsim.processSimulation.measurementDevice;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.util.monitor.WellAllocatorResponse;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 *
 */
class WellAllocatorTest extends neqsim.NeqSimTest {

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {}

  /**
   * Test method for
   * {@link neqsim.processSimulation.measurementDevice.WellAllocator#getMeasuredValue(java.lang.String)}.
   */
  @Test
  void testGetMeasuredValueString() {
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

    testFluid.addToComponentNames("_well1");

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(2500.0, "kg/hr");

    SystemInterface testFluid2 = testFluid.clone();

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(2500.0, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);

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
    // stream_1.displayResult();
    // stream_1.displayResult();
  }

}
