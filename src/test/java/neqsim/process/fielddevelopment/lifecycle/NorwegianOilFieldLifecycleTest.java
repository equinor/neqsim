package neqsim.process.fielddevelopment.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.lifecycle.FacilityLifecycleStrategy.DevelopmentMode;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionLoad;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;

/** Tests the integrated Norwegian oil-field lifecycle workflow. */
class NorwegianOilFieldLifecycleTest extends neqsim.NeqSimTest {

  @Test
  void gasInjectionCaseRunsFromReservoirToEconomics() {
    FieldLifecycleConcept concept = NorwegianOilFieldCase.createCase("short gas injection", 0.85, 5.0e6, 2.0);
    concept.getModel().setProductionPotentialProvider((model, configuration, ageYears, pressureBara) ->
        new FacilityProductionRate(18000.0, 0.0, 1500.0));
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
    assertEquals(DevelopmentMode.GREENFIELD, result.getFacilityDesignResult().getDevelopmentMode());
    assertEquals(18000.0, result.getAnnualResults().get(0).getPotentialOilRateSm3PerDay(), 1.0e-6);
    assertTrue(result.getFacilityDesignResult().getAutoSizedEquipmentCount() > 0);
    assertTrue(result.getPeakFacilityUtilization() <= 1.0 + 1.0e-6);
  }

  @Test
  void naturalDepletionDoesNotInjectGas() {
    FieldLifecycleResult result = new FieldLifecycleEvaluator()
        .evaluate(NorwegianOilFieldCase.createCase("short depletion", 0.0, 0.0, 2.0));

    assertEquals(0.0, result.getCumulativeGasInjectedSm3(), 1.0);
    assertTrue(result.getCumulativeOilSm3() > 0.0);
  }

  @Test
  void evaluatorRanksIndependentConceptsAndExportsComparisonTable() {
    FieldLifecycleEvaluator evaluator = new FieldLifecycleEvaluator();
    List<FieldLifecycleResult> results = evaluator.evaluateAll(Arrays
        .asList(NorwegianOilFieldCase.createCase("short injection", 0.85, 5.0e6, 1.0),
            NorwegianOilFieldCase.createCase("short depletion", 0.0, 0.0, 1.0)));

    assertEquals(2, results.size());
    assertTrue(results.get(0).getNpvMusd() >= results.get(1).getNpvMusd());
    String table = evaluator.toMarkdownTable(results);
    assertTrue(table.contains("Break-even oil"));
    assertTrue(table.contains("Gas injected"));
    assertTrue(table.contains("Peak facility utilization"));
  }

  @Test
  void allocatorHandlesDynamicHostCapacityPriorityAndHoldback() {
    HostFacility host = HostFacility.builder("test host").oilCapacity(125796.0).gasCapacity(6.0)
        .waterCapacity(30000.0).liquidCapacity(45000.0).build();
    ProductionProfileSeries profile = new ProductionProfileSeries("host profile")
        .addPeriod(2030, 3.0, 15000.0 / ProductionLoad.BARREL_TO_M3, 10000.0, 0.0)
        .addPeriod(2040, 1.0, 5000.0 / ProductionLoad.BARREL_TO_M3, 18000.0, 0.0);
    FacilityLifecycleStrategy strategy = FacilityLifecycleStrategy.tieback("shared host", host, profile)
        .allocationPolicy(CapacityAllocationPolicy.BASE_FIRST).holdback(0.0, 0.10)
        .useDetailedProcessConstraints(false)
        .build();

    FacilityCapacityAllocator.AllocationResult early = new FacilityCapacityAllocator().allocate(strategy, 2030,
        new FacilityProductionRate(12000.0, 4.0e6, 8000.0));
    FacilityCapacityAllocator.AllocationResult late = new FacilityCapacityAllocator().allocate(strategy, 2040,
        new FacilityProductionRate(12000.0, 4.0e6, 8000.0));

    assertEquals(15000.0, early.getHostAllocated().getOilSm3PerDay(), 1.0e-9);
    assertTrue(early.getSatelliteAllocated().getOilSm3PerDay()
        < early.getSatelliteRequested().getOilSm3PerDay());
    assertTrue(late.getSatelliteAllocated().getOilSm3PerDay()
        > early.getSatelliteAllocated().getOilSm3PerDay());
    assertEquals(1.0, early.getMaximumUtilization(), 1.0e-6);
  }

  @Test
  void detailedTiebackReportsHostLoadDeferredProductionAndBottleneck() {
    FieldLifecycleConcept concept = NorwegianOilFieldCase.createTiebackCase("short tieback",
        CapacityAllocationPolicy.BASE_FIRST, 0.0, 0.0, 2.0);
    FieldLifecycleResult result = new FieldLifecycleEvaluator().evaluate(concept);

    assertEquals(DevelopmentMode.BROWNFIELD_TIEBACK, result.getFacilityDesignResult().getDevelopmentMode());
    assertFalse(result.getAnnualResults().isEmpty());
    assertTrue(result.getAnnualResults().get(0).getHostOilRateSm3PerDay() > 0.0);
    assertTrue(result.getCumulativeDeferredOilSm3() > 0.0);
    assertTrue(result.getPeakFacilityUtilization() <= 1.0 + 1.0e-6);
    assertTrue(result.getAnnualResults().get(0).getPrimaryBottleneck().contains("capacity"));
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
