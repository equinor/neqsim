package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.BiomassCharacterization;

/**
 * Tests for {@link BiomassGasifier}.
 */
class BiomassGasifierTest {

  @Test
  void testDowndraftAirGasificationWoodChips() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    BiomassGasifier gasifier = new BiomassGasifier("Downdraft");
    gasifier.setBiomass(wood, 1000.0);
    gasifier.setGasifierType(BiomassGasifier.GasifierType.DOWNDRAFT);
    gasifier.setAgentType(BiomassGasifier.AgentType.AIR);
    gasifier.setEquivalenceRatio(0.25);
    gasifier.setGasificationTemperature(800.0, "C");
    gasifier.setCarbonConversionEfficiency(0.95);
    gasifier.run();

    // Syngas stream should be produced
    assertNotNull(gasifier.getSyngasOutStream(), "Syngas stream should not be null");
    assertNotNull(gasifier.getCharAshOutStream(), "Char/ash stream should not be null");

    // Key species should be present in syngas
    assertTrue(gasifier.getSyngasOutStream().getThermoSystem().hasComponent("CO"),
        "Syngas should contain CO");
    assertTrue(gasifier.getSyngasOutStream().getThermoSystem().hasComponent("hydrogen"),
        "Syngas should contain H2");
    assertTrue(gasifier.getSyngasOutStream().getThermoSystem().hasComponent("CO2"),
        "Syngas should contain CO2");

    // Performance metrics should be reasonable
    assertTrue(gasifier.getColdGasEfficiency() > 0.0, "CGE should be positive");
    assertTrue(gasifier.getColdGasEfficiency() < 1.5, "CGE should be below 1.5");
    assertTrue(gasifier.getSyngasYieldNm3PerKg() > 0.0, "Syngas yield should be positive");
    assertTrue(gasifier.getSyngasLHVMjPerNm3() > 0.0, "Syngas LHV should be positive");
    assertTrue(gasifier.getCharYieldFraction() >= 0.0, "Char yield should be non-negative");
    assertTrue(gasifier.getCharYieldFraction() < 0.5, "Char yield should be below 0.5");
  }

  @Test
  void testSteamGasification() {
    BiomassCharacterization straw = BiomassCharacterization.library("straw");

    BiomassGasifier gasifier = new BiomassGasifier("SteamGasifier");
    gasifier.setBiomass(straw, 500.0);
    gasifier.setGasifierType(BiomassGasifier.GasifierType.FLUIDIZED_BED);
    gasifier.setAgentType(BiomassGasifier.AgentType.STEAM);
    gasifier.setSteamToBiomassRatio(0.8);
    gasifier.setGasificationTemperature(850.0, "C");
    gasifier.setCarbonConversionEfficiency(0.90);
    gasifier.run();

    assertNotNull(gasifier.getSyngasOutStream());
    // Steam gasification should produce H2-rich syngas
    assertTrue(gasifier.getSyngasOutStream().getThermoSystem().hasComponent("hydrogen"));
    assertTrue(gasifier.getSyngasYieldNm3PerKg() > 0.0);
  }

  @Test
  void testOxygenGasification() {
    BiomassCharacterization cornStover = BiomassCharacterization.library("corn_stover");

    BiomassGasifier gasifier = new BiomassGasifier("O2Gasifier");
    gasifier.setBiomass(cornStover, 800.0);
    gasifier.setGasifierType(BiomassGasifier.GasifierType.UPDRAFT);
    gasifier.setAgentType(BiomassGasifier.AgentType.OXYGEN);
    gasifier.setEquivalenceRatio(0.30);
    gasifier.setGasificationTemperature(900.0, "C");
    gasifier.run();

    assertNotNull(gasifier.getSyngasOutStream());
    // Oxygen gasification should produce N2-free syngas
    assertTrue(gasifier.getSyngasYieldNm3PerKg() > 0.0);
    assertTrue(gasifier.getSyngasLHVMjPerNm3() > 0.0);
  }

  @Test
  void testAirSteamGasification() {
    BiomassCharacterization bagasse = BiomassCharacterization.library("bagasse");

    BiomassGasifier gasifier = new BiomassGasifier("AirSteam");
    gasifier.setBiomass(bagasse, 600.0);
    gasifier.setAgentType(BiomassGasifier.AgentType.AIR_STEAM);
    gasifier.setEquivalenceRatio(0.20);
    gasifier.setSteamToBiomassRatio(0.5);
    gasifier.setGasificationTemperature(780.0, "C");
    gasifier.run();

    assertNotNull(gasifier.getSyngasOutStream());
    assertTrue(gasifier.getColdGasEfficiency() > 0.0);
  }

  @Test
  void testGetResults() {
    BiomassCharacterization bc = BiomassCharacterization.library("rice_husk");

    BiomassGasifier gasifier = new BiomassGasifier("Results");
    gasifier.setBiomass(bc, 1000.0);
    gasifier.setEquivalenceRatio(0.25);
    gasifier.setGasificationTemperature(800.0, "C");
    gasifier.run();

    Map<String, Object> results = gasifier.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("coldGasEfficiency"));
    assertTrue(results.containsKey("syngasComposition_molFrac"));
    assertTrue(results.containsKey("gasifierType"));
  }

  @Test
  void testToJson() {
    BiomassCharacterization bc = BiomassCharacterization.library("msw");

    BiomassGasifier gasifier = new BiomassGasifier("JSON");
    gasifier.setBiomass(bc, 500.0);
    gasifier.setEquivalenceRatio(0.30);
    gasifier.setGasificationTemperature(850.0, "C");
    gasifier.run();

    String json = gasifier.toJson();
    assertNotNull(json);
    assertTrue(json.contains("coldGasEfficiency"));
    assertTrue(json.contains("syngasComposition_molFrac"));
  }

  @Test
  void testToStringBeforeAndAfterRun() {
    BiomassCharacterization bc = BiomassCharacterization.library("wood_chips");
    BiomassGasifier gasifier = new BiomassGasifier("Str");

    String beforeRun = gasifier.toString();
    assertTrue(beforeRun.contains("not yet run"));

    gasifier.setBiomass(bc, 1000.0);
    gasifier.setEquivalenceRatio(0.25);
    gasifier.setGasificationTemperature(800.0, "C");
    gasifier.run();

    String afterRun = gasifier.toString();
    assertTrue(afterRun.contains("Cold gas efficiency"));
    assertTrue(afterRun.contains("Syngas yield"));
  }

  @Test
  void testOutletStreams() {
    BiomassCharacterization bc = BiomassCharacterization.library("wood_chips");

    BiomassGasifier gasifier = new BiomassGasifier("Outlets");
    gasifier.setBiomass(bc, 1000.0);
    gasifier.setEquivalenceRatio(0.25);
    gasifier.setGasificationTemperature(800.0, "C");
    gasifier.run();

    assertTrue(gasifier.getOutletStreams().size() == 2, "Should have 2 outlet streams");
  }

  @Test
  void testPressurizedGasification() {
    BiomassCharacterization bc = BiomassCharacterization.library("wood_chips");

    BiomassGasifier gasifier = new BiomassGasifier("Pressurized");
    gasifier.setBiomass(bc, 1000.0);
    gasifier.setEquivalenceRatio(0.25);
    gasifier.setGasificationTemperature(900.0, "C");
    gasifier.setGasificationPressure(10.0); // 10 bara
    gasifier.run();

    assertNotNull(gasifier.getSyngasOutStream());
    assertTrue(gasifier.getSyngasYieldNm3PerKg() > 0.0);
  }
}
