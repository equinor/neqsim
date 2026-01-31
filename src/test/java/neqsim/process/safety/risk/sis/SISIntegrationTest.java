package neqsim.process.safety.risk.sis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SIS Integration package.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class SISIntegrationTest {

  private SafetyInstrumentedFunction hipps;
  private SafetyInstrumentedFunction esd;

  @BeforeEach
  void setUp() {
    // Create HIPPS (SIL 2) using builder pattern
    hipps = SafetyInstrumentedFunction.builder().id("SDV-001").name("Main HIPPS")
        .description("High Integrity Pipeline Protection System")
        .category(SafetyInstrumentedFunction.SIFCategory.HIPPS).sil(2).pfd(0.005)
        .architecture("1oo2").testIntervalHours(8760.0)
        .protectedEquipment(Arrays.asList("Export Pipeline", "Riser"))
        .initiatingEvent("Overpressure").build();

    // Create ESD (SIL 3) using simple constructor
    esd = new SafetyInstrumentedFunction("Emergency Shutdown", 3, 0.0005);
  }

  @Test
  void testSIFBuilderCreation() {
    assertNotNull(hipps, "HIPPS should not be null");
    assertEquals("SDV-001", hipps.getId());
    assertEquals("Main HIPPS", hipps.getName());
    assertEquals(SafetyInstrumentedFunction.SIFCategory.HIPPS, hipps.getCategory());
    assertEquals(2, hipps.getSil());
    assertEquals("1oo2", hipps.getArchitecture());
  }

  @Test
  void testSIFSimpleConstructor() {
    assertNotNull(esd, "ESD should not be null");
    assertEquals("Emergency Shutdown", esd.getName());
    assertEquals(3, esd.getSil());
  }

  @Test
  void testRRFCalculation() {
    double rrf = hipps.getRiskReductionFactor();

    assertTrue(rrf > 1, "RRF should be greater than 1");
    // RRF should be inverse of PFD (1/0.005 = 200)
    assertEquals(200.0, rrf, 1.0);
  }

  @Test
  void testMitigatedFrequency() {
    double unmitigatedFreq = 0.1; // 0.1 per year
    double mitigatedFreq = hipps.getMitigatedFrequency(unmitigatedFreq);

    assertTrue(mitigatedFreq < unmitigatedFreq, "Mitigated should be less than unmitigated");
    assertEquals(0.0005, mitigatedFreq, 1e-6); // 0.1 * 0.005 = 0.0005
  }

  @Test
  void testSILRequirements() {
    assertEquals(1, SafetyInstrumentedFunction.getRequiredSil(0.05)); // PFD = 0.05 -> SIL 1
    assertEquals(2, SafetyInstrumentedFunction.getRequiredSil(0.005)); // PFD = 0.005 -> SIL 2
    assertEquals(3, SafetyInstrumentedFunction.getRequiredSil(0.0005)); // PFD = 0.0005 -> SIL 3
    assertEquals(4, SafetyInstrumentedFunction.getRequiredSil(0.00005)); // PFD = 0.00005 -> SIL 4
  }

  @Test
  void testCalculateRequiredPfd() {
    double unmitigated = 1.0; // 1 per year
    double target = 0.001; // 0.001 per year
    double requiredPfd = SafetyInstrumentedFunction.calculateRequiredPfd(unmitigated, target);

    assertEquals(0.001, requiredPfd, 1e-10);
  }

  @Test
  void testPfd1oo1Calculation() {
    double lambdaDU = 1e-6; // 1e-6 per hour
    double testInterval = 8760; // 1 year in hours
    double pfd = SafetyInstrumentedFunction.calculatePfd1oo1(lambdaDU, testInterval);

    // PFD = λDU × TI / 2
    double expected = 1e-6 * 8760 / 2.0;
    assertEquals(expected, pfd, 1e-10);
  }

  @Test
  void testPfd1oo2Calculation() {
    double lambdaDU = 1e-6; // 1e-6 per hour
    double testInterval = 8760; // 1 year in hours
    double pfd = SafetyInstrumentedFunction.calculatePfd1oo2(lambdaDU, testInterval);

    // PFD = (λDU × TI)² / 3 for 1oo2
    double lambdaTI = 1e-6 * 8760;
    double expected = (lambdaTI * lambdaTI) / 3.0;
    assertEquals(expected, pfd, 1e-15);
  }

  @Test
  void testPfd2oo3Calculation() {
    double lambdaDU = 1e-6; // 1e-6 per hour
    double testInterval = 8760; // 1 year in hours
    double pfd = SafetyInstrumentedFunction.calculatePfd2oo3(lambdaDU, testInterval);

    // PFD = (λDU × TI)² for 2oo3
    double lambdaTI = 1e-6 * 8760;
    double expected = lambdaTI * lambdaTI;
    assertEquals(expected, pfd, 1e-15);
  }

  @Test
  void testSIFCategoryEnum() {
    SafetyInstrumentedFunction.SIFCategory[] categories =
        SafetyInstrumentedFunction.SIFCategory.values();
    assertEquals(6, categories.length);

    assertEquals("Emergency Shutdown", SafetyInstrumentedFunction.SIFCategory.ESD.getDescription());
    assertEquals("High Integrity Pressure Protection",
        SafetyInstrumentedFunction.SIFCategory.HIPPS.getDescription());
    assertEquals("Fire & Gas", SafetyInstrumentedFunction.SIFCategory.FIRE_GAS.getDescription());
  }

  @Test
  void testSISIntegratedRiskModelCreation() {
    SISIntegratedRiskModel model = new SISIntegratedRiskModel("Test Scenario");
    assertNotNull(model, "Model should not be null");
    assertEquals("Test Scenario", model.getName());
  }

  @Test
  void testSISIntegratedRiskModelAddSIF() {
    SISIntegratedRiskModel model = new SISIntegratedRiskModel("Test Scenario");
    model.addSIF(hipps);
    model.addSIF(esd);

    assertEquals(2, model.getSIFs().size());
  }

  @Test
  void testSISIntegratedRiskModelAddIPL() {
    SISIntegratedRiskModel model = new SISIntegratedRiskModel("Test Scenario");

    // Create IPL using the proper constructor
    SISIntegratedRiskModel.IndependentProtectionLayer ipl =
        new SISIntegratedRiskModel.IndependentProtectionLayer("PSV-001", 0.01,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.MECHANICAL);
    model.addIPL(ipl);

    assertEquals(1, model.getIPLs().size());
  }

  @Test
  void testIPLCreation() {
    SISIntegratedRiskModel.IndependentProtectionLayer ipl =
        new SISIntegratedRiskModel.IndependentProtectionLayer("PSV-001", 0.01,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.MECHANICAL);

    assertEquals("PSV-001", ipl.getName());
    assertEquals(0.01, ipl.getPfd(), 0.001);
    assertEquals(SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.MECHANICAL,
        ipl.getType());
    assertEquals(100.0, ipl.getRiskReductionFactor(), 1.0);
  }

  @Test
  void testIPLTypeEnum() {
    SISIntegratedRiskModel.IndependentProtectionLayer.IPLType[] types =
        SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.values();
    assertEquals(6, types.length);
  }

  @Test
  void testSISRiskResultCreation() {
    SISRiskResult result = new SISRiskResult("Test Study");
    assertNotNull(result, "SISRiskResult should not be null");
    assertEquals("Test Study", result.getStudyName());
  }

  @Test
  void testSISRiskResultCalculateTotals() {
    SISRiskResult result = new SISRiskResult("Test Study");
    result.addEventResult("Overpressure", 0.1, 0.001, Arrays.asList(hipps));
    result.calculateTotals();

    assertEquals(0.1, result.getTotalUnmitigatedFrequency(), 0.01);
    assertEquals(0.001, result.getTotalMitigatedFrequency(), 0.0001);
    assertTrue(result.getOverallRRF() > 1);
  }

  @Test
  void testLOPAResultCreation() {
    LOPAResult result = new LOPAResult("Test Scenario");
    assertNotNull(result, "LOPAResult should not be null");
    assertEquals("Test Scenario", result.getScenarioName());
  }

  @Test
  void testLOPAResultSetFrequencies() {
    LOPAResult result = new LOPAResult("Test Scenario");
    result.setInitiatingEventFrequency(0.1);
    result.setTargetFrequency(0.001);
    result.setMitigatedFrequency(0.0005);

    assertEquals(0.1, result.getInitiatingEventFrequency(), 0.01);
    assertEquals(0.001, result.getTargetFrequency(), 0.0001);
    assertEquals(0.0005, result.getMitigatedFrequency(), 0.0001);
    assertTrue(result.isTargetMet());
  }

  @Test
  void testLOPAResultAddLayer() {
    LOPAResult result = new LOPAResult("Test Scenario");
    result.setInitiatingEventFrequency(0.1);
    result.addLayer("HIPPS", 0.01, 0.1, 0.001);

    assertEquals(1, result.getLayers().size());
  }

  @Test
  void testSIFSettersAndGetters() {
    SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction("Test SIF", 2, 0.01);

    sif.setId("SIF-001");
    sif.setDescription("Test description");
    sif.setArchitecture("1oo2");
    sif.setTestIntervalHours(8760.0);
    sif.setMttr(24.0);
    sif.setSpuriousTripRate(0.5);
    sif.setSafeState("Shutdown");
    sif.setCategory(SafetyInstrumentedFunction.SIFCategory.ESD);

    assertEquals("SIF-001", sif.getId());
    assertEquals("Test description", sif.getDescription());
    assertEquals("1oo2", sif.getArchitecture());
    assertEquals(8760.0, sif.getTestIntervalHours(), 0.1);
    assertEquals(24.0, sif.getMttr(), 0.1);
    assertEquals(0.5, sif.getSpuriousTripRate(), 0.01);
    assertEquals("Shutdown", sif.getSafeState());
    assertEquals(SafetyInstrumentedFunction.SIFCategory.ESD, sif.getCategory());
  }

  @Test
  void testProtectedEquipment() {
    SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction("Test SIF", 2, 0.01);
    sif.addProtectedEquipment("Compressor-A");
    sif.addProtectedEquipment("Compressor-B");

    assertEquals(2, sif.getProtectedEquipment().size());
    assertTrue(sif.getProtectedEquipment().contains("Compressor-A"));
    assertTrue(sif.getProtectedEquipment().contains("Compressor-B"));
  }

  @Test
  void testSIFToMap() {
    java.util.Map<String, Object> map = hipps.toMap();

    assertNotNull(map);
    assertEquals("SDV-001", map.get("id"));
    assertEquals("Main HIPPS", map.get("name"));
    assertNotNull(map.get("silData"));
  }

  @Test
  void testSIFToJson() {
    String json = hipps.toJson();

    assertNotNull(json);
    assertTrue(json.contains("Main HIPPS"));
    assertTrue(json.contains("SDV-001"));
  }

  @Test
  void testRiskToleranceCriteria() {
    SISIntegratedRiskModel.RiskToleranceCriteria criteria =
        new SISIntegratedRiskModel.RiskToleranceCriteria();

    criteria.setTolerableFrequencyFatality(1e-5);
    criteria.setTolerableFrequencyAsset(1e-3);

    assertEquals(1e-5,
        criteria.getTolerableFrequency(SISIntegratedRiskModel.ConsequenceType.FATALITY), 1e-10);
    assertEquals(1e-3, criteria.getTolerableFrequency(SISIntegratedRiskModel.ConsequenceType.ASSET),
        1e-10);
    assertTrue(criteria.getALARP() > 0);
  }
}

