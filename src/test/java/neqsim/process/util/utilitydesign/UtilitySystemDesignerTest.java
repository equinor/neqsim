package neqsim.process.util.utilitydesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link UtilitySystemDesigner}.
 */
public class UtilitySystemDesignerTest {
  /**
   * Builds a small process system with a heater, a cooler and a compressor.
   *
   * @param name the process-system name
   * @return a fully-run process system
   */
  private ProcessSystem buildProcess(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(50.0, "bara");

    Heater heater = new Heater("process heater", feed);
    heater.setOutTemperature(393.15); // 120 C

    Cooler cooler = new Cooler("process cooler", heater.getOutletStream());
    cooler.setOutTemperature(313.15); // 40 C -> cooling water

    Compressor compressor = new Compressor("export compressor", cooler.getOutletStream());
    compressor.setOutletPressure(90.0);

    ProcessSystem process = new ProcessSystem();
    process.setName(name);
    process.add(feed);
    process.add(heater);
    process.add(cooler);
    process.add(compressor);
    process.run();
    return process;
  }

  @Test
  void testHarvestAndDesignFromProcessSystem() {
    ProcessSystem process = buildProcess("Area 1");
    UtilitySystemDesigner designer = UtilitySystemDesigner.fromProcessSystem(process);
    designer.design();

    assertTrue(designer.getTotalHeatingDutyKW() > 0.0, "expected positive heating duty");
    assertTrue(designer.getTotalCoolingDutyKW() > 0.0, "expected positive cooling duty");
    assertTrue(designer.getTotalShaftPowerKW() > 0.0, "expected positive shaft power");

    // Cooler outlet at 40 C is below the 45 C cut-over -> cooling water.
    assertTrue(designer.getCoolingWaterDutyKW() > 0.0, "cooler should use cooling water");
    assertEquals(0.0, designer.getAirCoolerDutyKW(), 1.0e-6, "no air-cooler duty expected");
    assertTrue(designer.getCoolingWaterFlowM3h() > 0.0, "expected cooling-water circulation");

    assertTrue(designer.getInstrumentAirDemandNm3h() > 0.0, "expected base instrument-air demand");
    assertTrue(designer.getInstrumentAirCompressorKW() > 0.0, "expected air-compressor power");

    assertTrue(designer.getFuelMassDemandKgh() > 0.0, "expected fuel-gas demand");
    assertTrue(designer.getTotalCo2TonnePerYear() > 0.0, "expected CO2 emissions");
    assertTrue(designer.getTotalOpex() > 0.0, "expected operating cost");

    assertTrue(designer.validate().isEmpty(), "expected no validation warnings");
  }

  @Test
  void testJsonStructure() {
    ProcessSystem process = buildProcess("Area 1");
    UtilitySystemDesigner designer = UtilitySystemDesigner.fromProcessSystem(process);
    Map<String, Object> results = designer.toResultsMap();

    assertEquals(UtilitySystemDesigner.SCHEMA_VERSION, results.get("schemaVersion"));
    assertNotNull(results.get("demand"));
    assertNotNull(results.get("steamSystem"));
    assertNotNull(results.get("coolingWater"));
    assertNotNull(results.get("instrumentAir"));
    assertNotNull(results.get("fuelGas"));
    assertNotNull(results.get("emissions"));
    assertNotNull(results.get("opex"));

    String json = designer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("schemaVersion"));
  }

  @Test
  void testFromProcessModelAggregatesAreas() {
    ProcessModel model = new ProcessModel();
    model.add("Area 1", buildProcess("Area 1"));
    model.add("Area 2", buildProcess("Area 2"));

    UtilitySystemDesigner single = UtilitySystemDesigner.fromProcessSystem(buildProcess("Single")).design();
    UtilitySystemDesigner combined = UtilitySystemDesigner.fromProcessModel(model).design();

    // Two identical areas should harvest roughly double the single-area heating duty.
    assertEquals(2.0 * single.getTotalHeatingDutyKW(), combined.getTotalHeatingDutyKW(),
        Math.abs(single.getTotalHeatingDutyKW()) * 0.05 + 1.0, "model should aggregate both areas");
    assertTrue(combined.getTotalShaftPowerKW() > single.getTotalShaftPowerKW(),
        "model shaft power should exceed single area");
  }

  @Test
  void testNullArgumentsRejected() {
    assertThrows(IllegalArgumentException.class, () -> UtilitySystemDesigner.fromProcessSystem(null));
    assertThrows(IllegalArgumentException.class, () -> UtilitySystemDesigner.fromProcessModel(null));
  }
}
