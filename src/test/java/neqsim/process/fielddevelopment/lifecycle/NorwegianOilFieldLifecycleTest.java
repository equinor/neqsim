package neqsim.process.fielddevelopment.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the integrated Norwegian oil-field lifecycle workflow. */
class NorwegianOilFieldLifecycleTest extends neqsim.NeqSimTest {

  @Test
  void gasInjectionCaseRunsFromReservoirToEconomics() {
    FieldLifecycleConcept concept = NorwegianOilFieldCase.createGasInjectionCase();
    FieldLifecycleResult result = new FieldLifecycleEvaluator().evaluate(concept);

    assertFalse(result.getAnnualResults().isEmpty());
    assertTrue(result.getCumulativeOilSm3() > 0.0);
    assertTrue(result.getCumulativeGasInjectedSm3() > 0.0);
    assertTrue(result.getLifecycleEnergyMWh() > 0.0);
    assertTrue(result.getLifecycleCo2Tonnes() >= 0.0);
    assertTrue(Double.isFinite(result.getNpvMusd()));
    assertTrue(result.getBreakevenOilPriceUsdPerBbl() > 0.0);
    assertTrue(result.getBreakevenOilPriceUsdPerBbl() < 200.0);
    assertTrue(result.getFinalReservoirPressureBara() > 0.0);
    assertTrue(result.getInitialReservoirPressureBara() >= result.getFinalReservoirPressureBara());
  }

  @Test
  void naturalDepletionDoesNotInjectGas() {
    FieldLifecycleResult result = new FieldLifecycleEvaluator()
        .evaluate(NorwegianOilFieldCase.createNaturalDepletionCase());

    assertEquals(0.0, result.getCumulativeGasInjectedSm3(), 1.0);
    assertTrue(result.getCumulativeOilSm3() > 0.0);
  }

  @Test
  void evaluatorRanksIndependentConceptsAndExportsComparisonTable() {
    FieldLifecycleEvaluator evaluator = new FieldLifecycleEvaluator();
    List<FieldLifecycleResult> results = evaluator.evaluateAll(Arrays
        .asList(NorwegianOilFieldCase.createGasInjectionCase(), NorwegianOilFieldCase.createNaturalDepletionCase()));

    assertEquals(2, results.size());
    assertTrue(results.get(0).getNpvMusd() >= results.get(1).getNpvMusd());
    assertEquals("NCS subsea oil with gas injection", results.get(0).getConceptName());
    String table = evaluator.toMarkdownTable(results);
    assertTrue(table.contains("Break-even oil"));
    assertTrue(table.contains("Gas injected"));
  }

  @Test
  void configurationAppliesWaterBreakthroughAndValidatesFractions() {
    FieldLifecycleConfiguration configuration = FieldLifecycleConfiguration.builder().waterCut(0.05, 0.65, 2.0, 10.0)
        .build();

    assertEquals(0.05, configuration.getWaterCut(1.0), 1.0e-12);
    assertEquals(0.35, configuration.getWaterCut(7.0), 1.0e-12);
    assertEquals(0.65, configuration.getWaterCut(20.0), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> FieldLifecycleConfiguration.builder().gasInjection(0.0, 1.1, 1.0e6).build());
  }
}
