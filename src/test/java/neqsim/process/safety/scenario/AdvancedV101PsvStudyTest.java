package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork;
import neqsim.process.safety.scenario.AdvancedV101PsvStudy.CaseBundle;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Real connected PSV response, backup interaction, negative controls, cycle chronology and refinement.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AdvancedV101PsvStudyTest {
  /**
   * Functioning upstream shutdown contains demand while the PSV remains physically closed throughout the sampled
   * window.
   */
  @Test
  void normalAndJointProtectionDoNotInventReliefCredit() throws IOException {
    for (String mode : new String[] {"PSV_NORMAL", "SIS_STANDBY"}) {
      CaseBundle result = run(mode, 0.1);
      assertTrue(passed(result));
      for (Map<String, Object> row : result.getRows()) {
        assertEquals(0.0, number(row, "relievingMassFlowKgPerS"));
        assertEquals(0.0, number(row, "liftFraction"));
      }
      assertEquals(0L,
          ((SafetyReliefValve) result.getConfiguredProcess().getUnit("20-PSV-001")).getOpeningCycleCount());
      if (mode.equals("SIS_STANDBY")) {
        assertEquals(0.0,
            ((ThrottlingValve) result.getConfiguredProcess().getUnit("20-HIPPS-XV-001")).getPercentValveOpening());
        Map<?, ?> energy = (Map<?, ?>) ((Map<?, ?>) result.toMap().get("energy")).get("10-V-100");
        double debt = ((Number) energy.get("maxRelativeCumulativeResidual")).doubleValue();
        assertEquals(debt <= 1.0e-5 ? "PASS" : "FAIL", energy.get("verdict"));
        assertEquals("HOLD", result.toMap().get("engineeringQualification"));
      }
    }
  }

  /** A native pressure demand produces measured lift and positive actual discharge in PSV-only and backup-ESD cases. */
  @Test
  void realReliefContainsPressureWithAndWithoutBackupShutdown() throws IOException {
    for (String mode : new String[] {"PSV_RELIEF", "HIPPS_FAILED_PSV"}) {
      CaseBundle result = run(mode, 0.1);
      assertTrue(passed(result));
      assertTrue(max(result, "vesselPressureBara") < 43.0);
      assertTrue(max(result, "relievingMassFlowKgPerS") > 1.0);
      boolean actualDemandThenFlow = false;
      for (Map<String, Object> row : result.getRows()) {
        if (number(row, "pressureBara") >= 42.2 && number(row, "liftFraction") > 0.0
            && number(row, "relievingMassFlowKgPerS") > 0.0) {
          actualDemandThenFlow = true;
        }
      }
      assertTrue(actualDemandThenFlow);
    }
  }

  /**
   * Failed, undersized, backpressured and slow relief stay failed; stuck-open relief fails even at low vessel pressure.
   */
  @Test
  void negativeControlsRetainActualFunctionalFailures() throws IOException {
    for (String mode : new String[] {"PSV_FAILED_CLOSED", "PSV_STUCK_OPEN", "PSV_UNDERSIZED", "PSV_HIGH_BACKPRESSURE",
        "PSV_SLOW"}) {
      CaseBundle result = run(mode, 0.1);
      assertFalse(passed(result));
      if (mode.equals("PSV_FAILED_CLOSED")) {
        assertEquals(0.0, max(result, "relievingMassFlowKgPerS"));
      } else if (mode.equals("PSV_STUCK_OPEN")) {
        assertTrue(max(result, "vesselPressureBara") < 43.0);
        assertTrue(max(result, "relievingMassFlowKgPerS") > 0.0);
        Map<?, ?> nativeResult = (Map<?, ?>) result.toMap().get("nativeResult");
        Map<?, ?> criterion = (Map<?, ?>) ((Map<?, ?>) nativeResult.get("criteria")).get("NO-UNWANTED-RELIEF");
        assertFalse((Boolean) criterion.get("passed"));
      } else {
        assertTrue(max(result, "vesselPressureBara") > 43.0);
      }
    }
  }

  /** Reseat requires an observed pop followed by below-reseat exact seating, then a later actual reopening. */
  @Test
  void cycleEvidenceRequiresOrderedPopExactReseatAndReopening() throws IOException {
    CaseBundle result = run("PSV_CYCLE", 0.1);
    assertTrue(passed(result));
    boolean opened = false;
    boolean seated = false;
    boolean reopened = false;
    double reseat = ((SafetyReliefValve) result.getConfiguredProcess().getUnit("20-PSV-001")).getReseatPressureBar();
    for (Map<String, Object> row : result.getRows()) {
      double lift = number(row, "liftFraction");
      if (!opened && lift > 0.0 && number(row, "relievingMassFlowKgPerS") > 0.0) {
        opened = true;
      } else if (opened && !seated && lift == 0.0 && number(row, "pressureBara") <= reseat) {
        seated = true;
      } else if (seated && lift > 0.0 && number(row, "relievingMassFlowKgPerS") > 0.0) {
        reopened = true;
      }
    }
    assertTrue(opened && seated && reopened);
    SafetyReliefValve psv = (SafetyReliefValve) result.getConfiguredProcess().getUnit("20-PSV-001");
    assertEquals(2L, psv.getOpeningCycleCount());
    assertEquals(1L, psv.getReseatCycleCount());
  }

  /**
   * Frozen inputs and energy policy are retained at half timestep and a copied source replays the same native result.
   */
  @Test
  void reliefRefinesAndReplaysWithoutMutatingCanonicalSource() throws IOException {
    CaseBundle coarse = run("PSV_RELIEF", 0.1);
    CaseBundle fine = run("PSV_RELIEF", 0.05);
    assertTrue(passed(coarse));
    assertTrue(passed(fine));
    assertSteadyPeakBoundary(coarse, 0.1);
    assertSteadyPeakBoundary(fine, 0.05);
    assertTrue(Math.abs(max(coarse, "vesselPressureBara") - max(fine, "vesselPressureBara")) < 0.15);
    Map<String, Double> settings = AdvancedV101PsvStudy.defaultSettings();
    ProcessSystem source = AdvancedV101PsvStudy.buildSource(settings);
    SafetyReliefValve sourcePsv = (SafetyReliefValve) source.getUnit("20-PSV-001");
    double initial = sourcePsv.getOpenFraction();
    CaseBundle replay = AdvancedV101PsvStudy.createCase(source, settings, "PSV_RELIEF", 0.1);
    assertEquals(coarse.toMap().get("nativeResult"), replay.runStrict().toMap());
    assertEquals(initial, sourcePsv.getOpenFraction());
  }

  /**
   * Rate the recorded PSV-only peak as a dry-gas snapshot with zero line and tip pressure loss. The fixture specifies
   * Cv, so the SI nozzle comparison uses an explicitly assumed water-equivalent CdA.
   *
   * @param reference completed native case
   * @param dt source and handoff sampling step in seconds
   */
  private static void assertSteadyPeakBoundary(CaseBundle reference, double dt) {
    String tag = "20-PSV-001";
    DepressurizationResult source = new DepressurizationResult();
    Map<String, Object> peak = reference.getRows().get(0);
    for (Map<String, Object> row : reference.getRows()) {
      source.time.add(number(row, "timeSeconds"));
      double flow = number(row, "relievingMassFlowKgPerS");
      source.massFlowKgPerS.add(flow);
      if (flow > number(peak, "relievingMassFlowKgPerS")) {
        peak = row;
      }
    }
    MultiVesselBlowdownStudy.MultiVesselBlowdownResult history = new MultiVesselBlowdownStudy().setGridStep(dt)
        .addSourceResult(tag, source).run();
    assertEquals(number(peak, "timeSeconds"), history.getPeakTimeS(), 1e-9);
    assertEquals(number(peak, "relievingMassFlowKgPerS"), history.getPeakTotalMassFlowKgPerS(), 1e-9);

    // Reconstruct peak vessel gas from the nine observed component inventories and the native SRK basis.
    SystemInterface fluid = new SystemSrkEos(number(peak, "20-V-101.temperatureK"), number(peak, "vesselPressureBara"));
    Separator vessel = (Separator) reference.getConfiguredProcess().getUnit("20-V-101");
    for (String component : vessel.getThermoSystem().getComponentNames()) {
      fluid.addComponent(component, number(peak, "20-V-101." + component + "Mol"));
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    PhaseInterface gas = fluid.getPhase("gas");
    SteadyFlareGasNetwork.Pipe line = new SteadyFlareGasNetwork.Pipe(1.0, 0.0, 0.02);
    double boundary = reference.getSettings().get("psvBackpressureBara");
    SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("header", line, "tip", boundary, boundary, 100.0,
        fluid.getTemperature(), gas.getMolarMass(), gas.getZ(), gas.getGamma());
    // US gallon/min at one psi with 999 kg/m3 water. The native effective Cv already includes lift and Kd.
    double cdArea = number(peak, "effectiveCapacityCv") * (0.003785411784 / 60.0)
        / Math.sqrt(2.0 * 6894.757293168 / 999.0);
    double inlet = number(peak, "pressureBara");
    network.addDevice(tag, inlet, line, cdArea, 1.0, line, null);
    SteadyFlareGasNetwork.Result rating = history.ratePeakGasCapacity(network);
    assertEquals(number(peak, "relievingMassFlowKgPerS"), rating.devices.get(tag).demandKgS, 1e-9);
    assertEquals(inlet, rating.devices.get(tag).inletBara, 1e-9);
    assertEquals(number(peak, "backpressureBara"), rating.devices.get(tag).outletBara, 1e-9);
    assertEquals(boundary, rating.headerBara, 1e-9);
    assertEquals(boundary, rating.tipBara, 1e-9);
    assertEquals(0.0, rating.massResidualKgS, 1e-8);
    double gamma = gas.getGamma();
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    assertTrue(boundary / inlet < criticalRatio);
    double sonic = Math.sqrt(gamma * gas.getMolarMass() / (gas.getZ() * 8.3144621 * fluid.getTemperature()));
    double idealNozzle = cdArea * inlet * 100000.0 * sonic
        * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
    assertEquals(idealNozzle, rating.totalKgS, 1e-7);
  }

  /** The runtime-only helper participates honestly with the native splitter in a complete branch-subgraph rollback. */
  @Test
  void algebraicRefreshHelperHasCompleteBranchTransactionOwnership() {
    SystemInterface fluid = new SystemSrkEos(300.0, 45.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(5.0, "kg/sec");
    Stream source = new Stream("SOURCE", fluid);
    source.run();
    Splitter tee = new Splitter("TEE", source, 2);
    tee.setSplitFactors(new double[] {0.5, 0.5});
    tee.run();
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS);
    ThrottlingValve pcv = new ThrottlingValve("PCV", tee.getSplitStream(0));
    pcv.setCv(2.0);
    pcv.setOutletPressure(35.0, "bara");
    pcv.setCalculateSteadyState(false);
    SafetyReliefValve psv = new SafetyReliefValve("PSV", tee.getSplitStream(1));
    psv.configureConventionalSnap(42.2, .1, .03, 5.0);
    psv.setOutletPressure(1.2, "bara");
    psv.setCalculateSteadyState(false);
    pcv.setAllowChoked(true);
    psv.setAllowChoked(true);
    pcv.runTransient(0.0, UUID.randomUUID());
    psv.runTransient(0.0, UUID.randomUUID());
    AdvancedV101PsvStudy.BranchStateRefresh helper = new AdvancedV101PsvStudy.BranchStateRefresh(tee);
    ProcessSystem graph = new ProcessSystem();
    graph.add(source);
    graph.add(helper);
    graph.add(pcv);
    graph.add(psv);
    graph.add(tee);
    assertTrue(graph.getTransientTransactionCoverage().isComplete());
    Stream branch = (Stream) tee.getSplitStream(1);
    UUID id = UUID.randomUUID();
    TransientStepTransaction transaction = graph.beginTransientStepTransaction();
    graph.runTransient(0.1, id);
    double expected = psv.getOutletStream().getFlowRate("kg/sec");
    transaction.rollback();
    graph.runTransient(0.1, id);
    assertSame(branch, psv.getInletStream());
    assertSame(branch, tee.getSplitStream(1));
    assertEquals(expected, psv.getOutletStream().getFlowRate("kg/sec"));
  }

  /**
   * @param mode explicit case
   * @param dt seconds
   * @return actual completed case
   * @throws IOException if requested export fails
   */
  private static CaseBundle run(String mode, double dt) throws IOException {
    Map<String, Double> settings = AdvancedV101PsvStudy.defaultSettings();
    CaseBundle result = AdvancedV101PsvStudy.createCase(AdvancedV101PsvStudy.buildSource(settings), settings, mode, dt);
    DynamicSafetyScenarioResult nativeResult = result.runStrict();
    assertTrue(((List<?>) nativeResult.toMap().get("errors")).isEmpty(), nativeResult.toJson());
    for (String tag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001", "20-PCV-001", "20-PSV-001"}) {
      assertTrue(((ThrottlingValve) result.getConfiguredProcess().getUnit(tag)).isAllowChoked());
    }
    assertFalse(result.getRows().isEmpty());
    assertEquals(1.0, number(result.getRows().get(0), "timeSeconds"), 1e-9);
    for (Map<String, Object> row : result.getRows()) {
      assertTrue((Boolean) row.get("allHeldSamplesCurrentStep"));
      assertTrue(number(row, "liftFraction") >= 0.0 && number(row, "liftFraction") <= 1.0);
      assertTrue(number(row, "relievingMassFlowKgPerS") >= 0.0);
      for (Object value : row.values()) {
        if (value instanceof Number) {
          assertTrue(Double.isFinite(((Number) value).doubleValue()));
        }
      }
    }
    String output = System.getProperty("neqsim.psvStudy.output");
    if (output != null && !output.isEmpty()) {
      result.export(Paths.get(output));
    }
    return result;
  }

  /**
   * @param result actual case
   * @return native required-response verdict
   */
  private static boolean passed(CaseBundle result) {
    return (Boolean) ((Map<?, ?>) result.toMap().get("nativeResult")).get("passed");
  }

  /**
   * @param row actual sample
   * @param key actual field
   * @return scalar value
   */
  private static double number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).doubleValue();
  }

  /**
   * @param result actual case
   * @param key actual field
   * @return observed sampled maximum
   */
  private static double max(CaseBundle result, String key) {
    double value = Double.NEGATIVE_INFINITY;
    for (Map<String, Object> row : result.getRows()) {
      value = Math.max(value, number(row, key));
    }
    return value;
  }
}
