package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Native equipment/port contract tests; real finite-rate physics is checked by the optional Cantera integration suite.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MultiBurnerFiredHeaterTest {
  /** Verify supplied fuel/air are actual ports and switching does not delete total fuel. */
  @Test
  void exposesPortsAndPreservesTotalSupplyDuringSwitching() {
    MultiBurnerFiredHeater heater = heater();
    Stream oil = stream("oil", "n-decane", 100.0, 500.0, 20.0);
    oil.setFlowRate(100.0, "kg/sec");
    heater.setHotOilInlet(oil);
    StreamInterface originalOilOutlet = heater.getHotOilOutlet();
    StreamInterface originalGasOutlet = heater.getOutletStream();
    assertEquals(3, heater.getInletStreams().size());
    assertEquals(2, heater.getOutletStreams().size());
    ProcessSystem process = new ProcessSystem();
    for (StreamInterface supply : heater.getInletStreams()) {
      process.add(supply);
    }
    process.add(heater);
    process.run();
    assertTrue(heater.isOutletProjectionValid());
    assertEquals(0.08, heater.getFiringDutyMW(), 1.0e-12);
    assertEquals(0.02, heater.getUsefulHeatMW(), 1.0e-12);
    assertTrue(heater.getHotOilOutlet().getTemperature() > 500.0);
    assertSame(originalOilOutlet, heater.getHotOilOutlet());
    assertSame(originalGasOutlet, heater.getOutletStream());
    for (int i = 0; i < 7; i++) {
      heater.configureBurner(i, i < 5, 1.0, 0.2, 0.1);
    }
    process.run();
    assertSame(originalOilOutlet, heater.getHotOilOutlet());
    assertEquals(1.0, heater.getSpeciesMolarFlow("CH4"), 1.0e-10);
    assertEquals(0.08, heater.getFiringDutyMW(), 1.0e-12);
  }

  /** An all-off burner configuration with supplied fuel must fail rather than create fictitious useful heat. */
  @Test
  void rejectsAllOffAndInvalidAirCapture() {
    MultiBurnerFiredHeater heater = heater();
    for (int i = 0; i < 7; i++) {
      heater.setBurnerEnabled(i, false);
    }
    assertFalse(heater.validateSetup().isValid());
    assertThrows(IllegalStateException.class, () -> heater.run(UUID.randomUUID()));
    assertThrows(IllegalStateException.class, () -> heater.getOutletStream());
    assertThrows(IllegalArgumentException.class, () -> heater.configureBurner(0, true, 1.0, 1.1, 0.1));
    assertThrows(IllegalArgumentException.class, () -> heater.setChamberGeometry(Double.NaN, 10.0));
  }

  /**
   * Construct the documented native API with an identity fake backend to isolate equipment and stream semantics.
   *
   * @return configured heater
   */
  private static MultiBurnerFiredHeater heater() {
    Stream fuel = stream("fuel", "methane", 1.0, 300.0, 1.01325);
    SystemSrkEos airFluid = new SystemSrkEos(300.0, 1.01325);
    airFluid.addComponent("oxygen", 2.0, "mole/sec");
    airFluid.addComponent("nitrogen", 7.52, "mole/sec");
    airFluid.createDatabase(true);
    airFluid.setMixingRule(2);
    Stream air = new Stream("air", airFluid);
    MultiBurnerFiredHeater heater = new MultiBurnerFiredHeater("heater", fuel, air, 7);
    heater.setKineticsBackend(MultiBurnerFiredHeaterTest::identityResult);
    for (int i = 0; i < 7; i++) {
      heater.configureBurner(i, true, 1.0, 1.0 / 7.0, 0.1);
    }
    heater.setChamberGeometry(2.0, 3.0);
    heater.setTubeHeatTransfer(10.0, 15.0, 0.1, 0.1, 550.0);
    heater.setRefractory(0.2, 1.0, 0.8, 15.0, 300.0);
    return heater;
  }

  /**
   * Create a known nonreactive test stream.
   *
   * @param name stream name
   * @param component component name
   * @param flow flow [mol/s]
   * @param temperature temperature [K]
   * @param pressure pressure [bara]
   * @return stream
   */
  private static Stream stream(String name, String component, double flow, double temperature, double pressure) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent(component, flow, "mole/sec");
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return new Stream(name, fluid);
  }

  /**
   * Return explicit fixture metadata and an identity component result. This fixture is not a chemistry model.
   *
   * @param requestJson native equipment request
   * @return identity result JSON
   */
  private static String identityResult(String requestJson) {
    JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", 1);
    result.addProperty("converged", true);
    result.addProperty("reactorModel", request.get("reactorModel").getAsString());
    result.addProperty("temperatureK", 600.0);
    result.addProperty("pressurePa", request.get("pressurePa").getAsDouble());
    result.addProperty("heatTransferToSurroundingsW", 21000.0);
    result.addProperty("mechanismInletEnthalpyJPerKg", 0.0);
    result.addProperty("mechanismOutletEnthalpyJPerKg", 0.0);
    result.addProperty("energyReference", "Identity boundary fixture; not a mechanism energy calculation");
    result.addProperty("energyBalanceRelativeResidual", 0.0);
    result.addProperty("fullEnergyBalanceRelativeResidual", 0.0);
    result.addProperty("fuelChemicalPowerW", 80000.0);
    result.addProperty("usefulHeatToOilW", 20000.0);
    result.addProperty("shellHeatLossW", 1000.0);
    result.addProperty("stackSensibleHeatW", 59000.0);
    result.addProperty("residualChemicalPowerW", 0.0);
    JsonObject mapping = new JsonObject();
    mapping.addProperty("methane", "CH4");
    mapping.addProperty("oxygen", "O2");
    mapping.addProperty("nitrogen", "N2");
    result.add("componentToSpecies", mapping);
    JsonObject species = new JsonObject();
    for (java.util.Map.Entry<String, JsonElement> entry : request.getAsJsonObject("componentMolarFlows").entrySet()) {
      species.add(mapping.get(entry.getKey()).getAsString(), entry.getValue());
    }
    result.add("speciesMolarFlows", species);
    JsonObject masses = new JsonObject();
    masses.addProperty("CH4", 0.016043);
    masses.addProperty("O2", 0.031998);
    masses.addProperty("N2", 0.028014);
    result.add("speciesMolecularMassesKgPerMol", masses);
    JsonObject counts = new JsonObject();
    JsonObject methane = new JsonObject();
    methane.addProperty("C", 1.0);
    methane.addProperty("H", 4.0);
    counts.add("CH4", methane);
    JsonObject oxygen = new JsonObject();
    oxygen.addProperty("O", 2.0);
    counts.add("O2", oxygen);
    JsonObject nitrogen = new JsonObject();
    nitrogen.addProperty("N", 2.0);
    counts.add("N2", nitrogen);
    result.add("speciesAtomCounts", counts);
    JsonObject provenance = new JsonObject();
    provenance.addProperty("solver", "Identity test fixture");
    provenance.addProperty("solverVersion", "1");
    provenance.addProperty("mechanism", "No physical mechanism");
    provenance.addProperty("resolvedMechanismSha256",
        "0000000000000000000000000000000000000000000000000000000000000000");
    provenance.addProperty("speciesCount", 3);
    provenance.addProperty("reactionCount", 1);
    result.add("provenance", provenance);
    return result.toString();
  }
}
