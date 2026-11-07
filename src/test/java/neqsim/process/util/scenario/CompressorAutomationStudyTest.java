package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.SensorFaultType;

/**
 * Native source-identity, finite-inventory and safety-response regressions for the compressor template.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorAutomationStudyTest {
  /** Rejects unusable user composition rather than silently replacing the declared basis. */
  @Test
  void rejectsInvalidCompositionAndCopiesGovernedInputs() {
    CompressorAutomationStudy.Design invalid = new CompressorAutomationStudy.Design();
    Map<String, Double> fractions = new LinkedHashMap<String, Double>();
    fractions.put("methane", Double.NaN);
    invalid.setComposition(fractions);
    assertThrows(IllegalArgumentException.class, () -> CompressorAutomationStudy.create(invalid));
    CompressorAutomationStudy.Design design = new CompressorAutomationStudy.Design();
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(design);
    design.setFeedTemperatureK(400.0);
    assertEquals(333.15, train.getDesign().getFeedTemperatureK(), 0.0);
    assertTrue((Boolean) train.snapshot().get("gasCvApplicable"));
  }

  /** Verifies actual recycle source references survive native deep copy without sharing mutable hardware. */
  @Test
  void copiedTrainRetainsActualRecycleAndIndependentHardware() {
    CompressorAutomationStudy.Train original = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    CompressorAutomationStudy.Train copy = original.copy();
    assertNotSame(original.getAntiSurgeValve(), copy.getAntiSurgeValve());
    assertSame(copy.getCompressor(), copy.getAntiSurgeController().getCompressor());
    assertSame(copy.getAntiSurgeValve(), copy.getAntiSurgeController().getRecycleValve());
    assertEquals("30-COLD-RECYCLE", copy.getAntiSurgeValve().getOutletStream().getName());
    copy.getAntiSurgeValve().setPercentValveOpening(25.0);
    assertEquals(0.0, original.getAntiSurgeValve().getPercentValveOpening(), 0.0);
  }

  /** Checks exact consumed ledger rates, independent energy arithmetic and physical UUID idempotency. */
  @Test
  @SuppressWarnings("unchecked")
  void finiteStepExportsConsumedEnergyAndDoesNotRepeatPhysicalUuid() {
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    train.initializeDynamics();
    UUID id = UUID.randomUUID();
    train.getProcess().runTransient(0.1, id);
    Map<String, Object> boundary = train.getLastBoundary();
    SeparatorTransientEnergyBalanceReport ledger = train.getDischargeScrubber().getLastTransientEnergyBalanceReport();
    Map<String, Object> inlet = (Map<String, Object>) boundary.get("dischargeInlet");
    Map<String, Object> gas = (Map<String, Object>) boundary.get("dischargeGasOutlet");
    Map<String, Object> liquid = (Map<String, Object>) boundary.get("dischargeLiquidOutlet");
    assertEquals(ledger.getInletEnthalpyRateW(), ((Number) inlet.get("enthalpyW")).doubleValue(), 0.0);
    assertEquals(ledger.getGasOutletEnthalpyRateW(), ((Number) gas.get("enthalpyW")).doubleValue(), 0.0);
    assertEquals(ledger.getLiquidOutletEnthalpyRateW(), ((Number) liquid.get("enthalpyW")).doubleValue(), 0.0);
    Map<String, Object> before = (Map<String, Object>) boundary.get("dischargeBefore");
    Map<String, Object> after = (Map<String, Object>) boundary.get("dischargeAfter");
    double observed = ((Number) after.get("internalEnergyJ")).doubleValue()
        - ((Number) before.get("internalEnergyJ")).doubleValue();
    double transfer = 0.1 * (ledger.getInletEnthalpyRateW() - ledger.getGasOutletEnthalpyRateW()
        - ledger.getLiquidOutletEnthalpyRateW() + ledger.getHeatInputRateW());
    assertEquals(transfer + ledger.getLocalVuDefectJ(), observed, 1.0e-5);
    Map<String, Object> compressorInlet = (Map<String, Object>) boundary.get("compressorInlet");
    Map<String, Object> compressorOutlet = (Map<String, Object>) boundary.get("compressorOutlet");
    double fluidEnthalpyRise = ((Number) compressorOutlet.get("enthalpyW")).doubleValue()
        - ((Number) compressorInlet.get("enthalpyW")).doubleValue();
    assertEquals(((Number) boundary.get("compressorPowerConsumedW")).doubleValue(), fluidEnthalpyRise, 1.0e-5);
    assertTrue((Boolean) boundary.get("compressorFluidWorkApplicable"));
    double time = train.getProcess().getTime();
    assertTrue(train.getSuctionScrubber().getLastTransientEnergyBalanceReport().isComplete());
    long count = ledger.getAcceptedStepCount();
    train.getProcess().runTransient(0.1, id);
    assertEquals(time, train.getProcess().getTime(), 0.0);
    assertEquals(count, train.getDischargeScrubber().getLastTransientEnergyBalanceReport().getAcceptedStepCount());
    assertTrue((Boolean) train.snapshot().get("gasCvApplicable"));
    assertTrue(new Gson().toJson(train.snapshot()).startsWith("{"));
  }

  /** Explicit level units retain native sample-and-hold and independently injected SIS faults. */
  @Test
  void fractionAliasPreservesHeldLevelAndSeparatesSisObject() {
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    LevelTransmitter bpcs = (LevelTransmitter) train.getProcess().getMeasurementDevice("30-LT-100");
    LevelTransmitter sis = (LevelTransmitter) train.getProcess().getMeasurementDevice("30-LT-102");
    assertNotSame(bpcs, sis);
    double originalBpcs = bpcs.getMeasuredValue("fraction");
    sis.setFault(SensorFaultType.STUCK_AT_VALUE, 0.9);
    UUID id = UUID.randomUUID();
    sis.sampleTransient(0.1, id);
    assertEquals(0.9, sis.getTransientSample("fraction"), 0.0);
    assertEquals(sis.getTransientSample(""), sis.getTransientSample("fraction"), 0.0);
    assertEquals(originalBpcs, bpcs.getMeasuredValue("fraction"), 0.0);
    assertThrows(RuntimeException.class, () -> sis.getTransientSample("metres"));
  }

  /** Shutdown must traverse actual zero flow and retain a complete native successful hold window. */
  @Test
  void manualShutdownCompletesAfterNativeNrvSeats() {
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    CompressorAutomationStudy.Result result = CompressorAutomationStudy.run(train,
        new CompressorAutomationStudy.CaseDefinition(CompressorAutomationStudy.Profile.MANUAL_ESD, 0.1, 6.2, 1.0));
    assertTrue(result.getNativeResult().isPassed(), result.getNativeResult().toMap().toString());
    assertEquals(0.0, result.getConfiguredTrain().getCompressor().getSpeed(), 0.0);
    assertEquals(100.0, result.getConfiguredTrain().getAntiSurgeValve().getPercentValveOpening(), 0.0);
    assertEquals(0.0, result.getConfiguredTrain().getCompressor().getInletStream().getFlowRate("kg/sec"), 0.0);
    assertEquals(0.0,
        ((Number) result.getConfiguredTrain().getLastBoundary().get("compressorPowerConsumedW")).doubleValue(), 0.0);
    assertFalse((Boolean) result.getConfiguredTrain().getLastBoundary().get("compressorFluidWorkApplicable"));
    assertTrue(result.getConfiguredTrain().getLastBoundary().containsKey("compressorNativePowerDiagnosticW"));
  }

  /** Fault preflight uses the exact runner-owned copy and retains native failed-case evidence. */
  @Test
  void declaredStuckActuatorSurvivesConfiguredCopyInspection() {
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    CompressorAutomationStudy.CaseDefinition definition = new CompressorAutomationStudy.CaseDefinition(
        CompressorAutomationStudy.Profile.ASV_TRIP_FAILED, 0.2, 6.2, 1.0);
    boolean[] observed = {false};
    CompressorAutomationStudy.CaseBundle bundle = CompressorAutomationStudy.createCase(train, definition,
        (configured, declared) -> {
          assertFalse(configured.getAntiSurgeValve().isActuatorAvailable());
          assertEquals(CompressorAutomationStudy.Profile.ASV_TRIP_FAILED, declared.getProfile());
          observed[0] = true;
        });
    CompressorAutomationStudy.Result result = bundle.run();
    assertTrue(observed[0]);
    assertSame(bundle.getNativeResult(), result.getNativeResult());
    assertFalse(result.getSamples().isEmpty());
    assertFalse(result.getNativeResult().isPassed());
    assertTrue(((java.util.List<?>) result.getNativeResult().toMap().get("errors")).isEmpty());
    assertFalse(result.getNativeResult().getCriterionResults().get("RECYCLE_FAIL_OPEN").isPassed());
    assertTrue(new Gson().toJson(result.toMap()).contains("RECYCLE_FAIL_OPEN"));
    assertFalse(train.getAntiSurgeValve().getPercentValveOpening() > 0.0);
  }
}
