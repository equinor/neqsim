package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link FermentationReactor}.
 */
class FermentationReactorTest {

  private Stream createSugarFeed() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 1.01325);
    fluid.addComponent("water", 90.0);
    fluid.addComponent("n-hexane", 10.0); // proxy for glucose/substrate
    fluid.setMixingRule("classic");
    Stream feed = new Stream("sugarFeed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }

  @Test
  void testMonodContinuous() {
    FermentationReactor reactor = new FermentationReactor("FR-1", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setSubstrateConcentration(100.0);
    reactor.setBiomassConcentration(1.0);
    reactor.setMaxSpecificGrowthRate(0.30);
    reactor.setMonodConstant(1.0);
    reactor.setYieldBiomass(0.10);
    reactor.setYieldProduct(0.45);
    reactor.setResidenceTime(10.0, "hr");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results, "Results should not be null");
    assertTrue(((Number) results.get("finalProductConc_g_per_L")).doubleValue() >= 0.0,
        "Product concentration should be non-negative");
    assertTrue(1.0 / ((Number) results.get("residenceTime_hr")).doubleValue() > 0.0,
        "Dilution rate should be positive");
    assertNotNull(reactor.getLiquidOutStream(), "Liquid out stream should exist");
    assertNotNull(reactor.getGasOutStream(), "Gas out stream should exist");
  }

  @Test
  void testContoisContinuous() {
    FermentationReactor reactor = new FermentationReactor("FR-2", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.CONTOIS);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setSubstrateConcentration(100.0);
    reactor.setBiomassConcentration(1.0);
    reactor.setMaxSpecificGrowthRate(0.25);
    reactor.setContoisConstant(5.0);
    reactor.setResidenceTime(12.0, "hr");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
    double substrateOut = ((Number) results.get("finalSubstrateConc_g_per_L")).doubleValue();
    assertTrue(substrateOut < 100.0, "Some substrate should be consumed");
  }

  @Test
  void testSubstrateInhibited() {
    FermentationReactor reactor = new FermentationReactor("FR-3", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.SUBSTRATE_INHIBITED);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setSubstrateConcentration(200.0); // high concentration triggers inhibition
    reactor.setBiomassConcentration(1.0);
    reactor.setMaxSpecificGrowthRate(0.30);
    reactor.setMonodConstant(2.0);
    reactor.setSubstrateInhibitionConstant(100.0);
    reactor.setResidenceTime(15.0, "hr");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
    assertNotNull(reactor.getLiquidOutStream());
  }

  @Test
  void testProductInhibited() {
    FermentationReactor reactor = new FermentationReactor("FR-4", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.PRODUCT_INHIBITED);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setSubstrateConcentration(100.0);
    reactor.setBiomassConcentration(1.0);
    reactor.setMaxSpecificGrowthRate(0.30);
    reactor.setMonodConstant(1.5);
    reactor.setProductInhibitionConcentration(80.0);
    reactor.setResidenceTime(10.0, "hr");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
  }

  @Test
  void testBatchMode() {
    FermentationReactor reactor = new FermentationReactor("FR-5", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
    reactor.setOperationMode(FermentationReactor.OperationMode.BATCH);
    reactor.setSubstrateConcentration(100.0);
    reactor.setBiomassConcentration(0.5);
    reactor.setMaxSpecificGrowthRate(0.35);
    reactor.setMonodConstant(1.0);
    reactor.setYieldBiomass(0.10);
    reactor.setYieldProduct(0.45);
    reactor.setBatchTime(24.0);
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
    double finalSubstrate = ((Number) results.get("finalSubstrateConc_g_per_L")).doubleValue();
    assertTrue(finalSubstrate < 100.0, "Substrate should be consumed in batch");
  }

  @Test
  void testFedBatchMode() {
    FermentationReactor reactor = new FermentationReactor("FR-6", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
    reactor.setOperationMode(FermentationReactor.OperationMode.FED_BATCH);
    reactor.setSubstrateConcentration(80.0);
    reactor.setBiomassConcentration(0.5);
    reactor.setMaxSpecificGrowthRate(0.30);
    reactor.setFeedingRate(5.0);
    reactor.setFeedSubstrateConcentration(500.0);
    reactor.setBatchTime(30.0);
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
    assertTrue(((String) results.get("operationMode")).equals("FED_BATCH"));
  }

  @Test
  void testGetResultsMap() {
    FermentationReactor reactor = new FermentationReactor("FR-7", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setResidenceTime(10.0, "hr");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertTrue(results.containsKey("kineticModel"));
    assertTrue(results.containsKey("operationMode"));
    assertTrue(results.containsKey("muMax_1_per_hr"));
    assertTrue(results.containsKey("yieldBiomass_g_g"));
    assertTrue(results.containsKey("yieldProduct_g_g"));
  }

  @Test
  void testToJson() {
    FermentationReactor reactor = new FermentationReactor("FR-8", createSugarFeed());
    reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setResidenceTime(10.0, "hr");
    reactor.run();

    String json = reactor.toJson();
    assertNotNull(json);
    assertTrue(json.contains("MONOD"));
    assertTrue(json.contains("CONTINUOUS"));
  }

  @Test
  void testOutletStreams() {
    FermentationReactor reactor = new FermentationReactor("FR-9", createSugarFeed());
    reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
    reactor.setResidenceTime(10.0, "hr");
    reactor.run();

    assertNotNull(reactor.getLiquidOutStream(), "Liquid outlet should exist");
    assertNotNull(reactor.getGasOutStream(), "Gas outlet should exist");
    assertEquals(2, reactor.getOutletStreams().size(), "Should have 2 outlet streams");
  }

  @Test
  void testDefaultConstructor() {
    FermentationReactor reactor = new FermentationReactor("FR-default");
    assertNotNull(reactor);
    assertEquals("FR-default", reactor.getName());
  }
}
