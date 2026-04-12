package neqsim.process.processmodel.biorefinery;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.BiomassGasifier;
import neqsim.thermo.characterization.BiomassCharacterization;

/**
 * Tests for {@link GasificationSynthesisModule}.
 */
class GasificationSynthesisModuleTest {

  @Test
  void testFluidizedBedGasification() {
    GasificationSynthesisModule module = new GasificationSynthesisModule("GS-1");
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
    module.setBiomass(wood, 2000.0);
    module.setGasifierType(BiomassGasifier.GasifierType.FLUIDIZED_BED);
    module.setGasifierTemperatureC(850.0);
    module.setEquivalenceRatio(0.25);
    module.setFtConversion(0.40);
    module.run();

    assertNotNull(module.getFtLiquidStream(), "FT liquid stream should exist");
    assertNotNull(module.getTailGasStream(), "Tail gas stream should exist");

    Map<String, Object> results = module.getResults();
    assertTrue(((Boolean) results.get("hasRun")));
  }

  @Test
  void testDowndraftGasification() {
    GasificationSynthesisModule module = new GasificationSynthesisModule("GS-2");
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
    module.setBiomass(wood, 1500.0);
    module.setGasifierType(BiomassGasifier.GasifierType.DOWNDRAFT);
    module.setGasifierTemperatureC(900.0);
    module.setFtConversion(0.50);
    module.setFtAlpha(0.90);
    module.run();

    Map<String, Object> results = module.getResults();
    assertTrue(results.containsKey("syngasH2COratio"));
    assertTrue(results.containsKey("ftLiquidFlow_kg_per_hr"));
    assertTrue(results.containsKey("tailGasFlow_kg_per_hr"));
  }

  @Test
  void testWithSteamAddition() {
    GasificationSynthesisModule module = new GasificationSynthesisModule("GS-3");
    module.setBiomassFeedRateKgPerHr(1000.0);
    module.setSteamToBiomassRatio(0.3);
    module.setFtReactorTemperatureC(240.0);
    module.setFtReactorPressureBara(30.0);
    module.run();

    String json = module.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Gasification + Fischer-Tropsch"));
  }

  @Test
  void testResultsMap() {
    GasificationSynthesisModule module = new GasificationSynthesisModule("GS-4");
    module.setBiomassFeedRateKgPerHr(500.0);
    module.run();

    Map<String, Object> results = module.getResults();
    assertTrue(results.containsKey("moduleName"));
    assertTrue(results.containsKey("processType"));
    assertTrue(results.containsKey("gasifierType"));
    assertTrue(results.containsKey("ftConversion"));
    assertTrue(results.containsKey("ftAlpha"));
    assertTrue(results.containsKey("syngasFlow_kg_per_hr"));
  }
}
