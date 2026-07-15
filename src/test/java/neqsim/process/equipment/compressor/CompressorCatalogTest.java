package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for compressor catalog selection and process-equipment integration. */
public class CompressorCatalogTest {

  @Test
  void defaultCatalogDocumentsCalibrationInputs() {
    CompressorCatalog catalog = CompressorCatalog.createDefaultCatalog();

    assertEquals(2, catalog.getIds().size());
    CompressorCatalogEntry entry = catalog.get("generic-centrifugal-single-stage");
    assertNotNull(entry);
    assertTrue(entry.getRequiredParameters().containsKey("thermal conductances W/K"));
    assertTrue(entry.getReferences().containsKey("API 617"));
    assertNotNull(entry.getThermalModel().getNode(CompressorThermalModel.INLET_SHAFT));
  }

  @Test
  void catalogJsonRoundTripsAndApplicationCopiesTemplate() {
    CompressorCatalog original = CompressorCatalog.createDefaultCatalog();
    CompressorCatalog restored = CompressorCatalog.fromJson(original.toJson());
    Compressor first = new Compressor("first");
    Compressor second = new Compressor("second");

    restored.apply("generic-centrifugal-multistage", first);
    restored.apply("generic-centrifugal-multistage", second);

    assertNotSame(first.getThermalModel(), second.getThermalModel());
    first.getThermalModel().getNode(CompressorThermalModel.AMBIENT).setTemperatureK(315.0);
    assertEquals(298.15, second.getThermalModel().getTemperature(CompressorThermalModel.AMBIENT, "K"), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> restored.apply("unknown", first));
  }

  @Test
  void attachedModelAutoSolvesAndAppearsInEquipmentState() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");
    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.run();

    Compressor compressor = new Compressor("thermal compressor", inlet);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.78);
    compressor.initMechanicalLosses(120.0);
    compressor.applyCatalogEntry(CompressorCatalog.createDefaultCatalog(), "generic-centrifugal-single-stage");

    compressor.run();

    CompressorThermalModel model = compressor.getThermalModel();
    assertEquals(inlet.getTemperature("K"), model.getTemperature(CompressorThermalModel.SUCTION_GAS, "K"), 1.0e-10);
    assertEquals(compressor.getOutletStream().getTemperature("K"),
        model.getTemperature(CompressorThermalModel.DISCHARGE_GAS, "K"), 1.0e-10);
    assertTrue(model.getTemperature(CompressorThermalModel.INLET_SHAFT, "K") > 250.0);

    Map<String, Map<String, Object>> state = compressor.getEquipmentState("C", "bara", "kg/hr");
    assertTrue(state.containsKey("thermal_inlet-shaft"));
    assertTrue(state.containsKey("thermal_dry-gas-seal"));
  }
}
