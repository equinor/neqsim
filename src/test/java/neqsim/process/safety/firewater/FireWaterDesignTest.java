package neqsim.process.safety.firewater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.firewater.ActiveFireProtectionScreening.FireScenario;
import neqsim.process.safety.firewater.FireWaterCoverageAssessment.Verdict;

/** Tests for the fire-water coverage design and screening classes. */
public class FireWaterDesignTest {

  @Test
  void areaDemandFollowsApplicationRate() {
    FireWaterDemandCalculator calc = new FireWaterDemandCalculator(510.0)
        .setAreaRate(FireWaterDemandCalculator.NORSOK_PROCESS_AREA_LPM_M2).setDurationMin(30.0);
    assertEquals(5100.0, calc.areaDemandLpm(), 1e-6);
    assertEquals(306.0, calc.totalDemandM3PerHour(), 1e-6);
    assertEquals(153.0, calc.waterVolumeM3(), 1e-6);
  }

  @Test
  void wellheadRateIsDoubleTheProcessAreaRate() {
    assertEquals(2.0,
        FireWaterDemandCalculator.NORSOK_WELLHEAD_LPM_M2 / FireWaterDemandCalculator.NORSOK_PROCESS_AREA_LPM_M2, 1e-12);
  }

  @Test
  void horizontalVesselSurfaceUsesShellPlusEnds() {
    FireWaterDemandCalculator calc = new FireWaterDemandCalculator(510.0).setAreaRate(10.0).addHorizontalVessel("HX-1",
        0.72, 6.606, 10.0);
    double expected = Math.PI * 0.72 * 6.606 + 2.0 * Math.PI * 0.72 * 0.72 / 4.0;
    assertEquals(expected, calc.getObjects().get(0).surfaceAreaM2, 1e-9);
    assertEquals(expected * 10.0, calc.objectDemandLpm(), 1e-9);
  }

  @Test
  void selectiveObjectProtectionIsAsmallFractionOfBlanketAreaCoverage() {
    FireWaterDemandCalculator calc = new FireWaterDemandCalculator(510.0).setAreaRate(10.0).addHorizontalVessel("HA-1",
        0.72, 6.61, 10.0);
    assertTrue(calc.objectToAreaDemandRatio() < 0.10,
        "dedicated protection of one exchanger should cost well under 10% of blanket area coverage");
  }

  @Test
  void foamConcentrateScalesWithWaterVolume() {
    FireWaterDemandCalculator calc = new FireWaterDemandCalculator(100.0).setAreaRate(10.0).setDurationMin(30.0)
        .setFoamConcentratePercent(3.0);
    assertEquals(30.0, calc.waterVolumeM3(), 1e-9);
    assertEquals(0.9, calc.foamConcentrateVolumeM3(), 1e-9);
  }

  @Test
  void simultaneousReleaseFactorMultipliesDemand() {
    FireWaterDemandCalculator calc = new FireWaterDemandCalculator(200.0).setAreaRate(10.0)
        .setSimultaneousAreaFactor(2.0);
    assertEquals(4000.0, calc.totalDemandLpm(), 1e-9);
  }

  @Test
  void nozzleFlowFollowsOrificeLaw() {
    DelugeNozzleLayout.Nozzle hv = DelugeNozzleLayout.highVelocity();
    assertEquals(hv.kFactorLpmPerSqrtBar * Math.sqrt(4.0), hv.flowLpm(4.0), 1e-9);
    assertEquals(hv.flowLpm(hv.minPressureBarg), hv.minFlowLpm(), 1e-12);
  }

  @Test
  void nozzleCountIsGovernedByTheLargerOfFlowAndCoverage() {
    DelugeNozzleLayout layout = new DelugeNozzleLayout(510.0, 10.0, DelugeNozzleLayout.highVelocity())
        .setOperatingPressureBarg(5.0);
    assertEquals(5100.0, layout.requiredFlowLpm(), 1e-9);
    assertTrue(layout.nozzleCount() >= layout.nozzleCountFromFlow());
    assertTrue(layout.nozzleCount() >= layout.nozzleCountFromCoverage());
    assertEquals(Math.max(layout.nozzleCountFromFlow(), layout.nozzleCountFromCoverage()), layout.nozzleCount());
    assertTrue(layout.isDensityMet());
    assertTrue(layout.isPressureAdequate());
    assertTrue(layout.gridSpacingM() > 0.0 && layout.gridSpacingM() < 10.0);
  }

  @Test
  void coverageGovernsWhenNozzlesAreHighCapacity() {
    DelugeNozzleLayout layout = new DelugeNozzleLayout(510.0, 10.0, DelugeNozzleLayout.highCapacity())
        .setOperatingPressureBarg(5.0);
    assertEquals("coverage", layout.governingCriterion(),
        "a high-capacity nozzle meets the flow with few nozzles, so spray overlap must govern");
    assertTrue(layout.deliveredDensityLpmPerM2() > 10.0);
  }

  @Test
  void pressureBelowNozzleMinimumIsFlagged() {
    DelugeNozzleLayout layout = new DelugeNozzleLayout(100.0, 10.0, DelugeNozzleLayout.highVelocity())
        .setOperatingPressureBarg(2.0);
    assertFalse(layout.isPressureAdequate());
  }

  @Test
  void clearanceCheckUsesTheNozzleRequirement() {
    DelugeNozzleLayout layout = new DelugeNozzleLayout(100.0, 10.0, DelugeNozzleLayout.highVelocity());
    assertTrue(layout.isClearanceAdequate(1.2));
    assertFalse(layout.isClearanceAdequate(0.5));
  }

  @Test
  void monitorCannotDeliverFixedSystemDensityOverALargeDeck() {
    FireMonitorCoverage mon = new FireMonitorCoverage(510.0, 10.0).setMonitors(2, 2400.0).setGeometry(40.0, 90.0)
        .setWind(0.0, 10.0);
    assertEquals(4800.0, mon.totalFlowLpm(), 1e-9);
    assertTrue(mon.nominalDensityLpmPerM2() < 10.0);
    assertFalse(mon.meetsDensityStillAir());
    assertTrue(mon.verdict().startsWith("NOT SUITABLE"));
  }

  @Test
  void windDriftReducesMonitorCoverage() {
    FireMonitorCoverage still = new FireMonitorCoverage(510.0, 10.0).setMonitors(3, 2000.0).setGeometry(40.0, 180.0)
        .setWind(0.0, 12.0);
    FireMonitorCoverage windy = new FireMonitorCoverage(510.0, 10.0).setMonitors(3, 2000.0).setGeometry(40.0, 180.0)
        .setWind(15.0, 12.0);
    assertEquals(1.0, still.windCoverageFraction(), 1e-12);
    assertEquals(12.0 * 15.0 / FireMonitorCoverage.DEFAULT_DROPLET_TERMINAL_VELOCITY_M_PER_S,
        windy.driftDisplacementM(), 1e-9);
    assertTrue(windy.windCoverageFraction() < 0.02,
        "a 22.5 m drift across a 22.6 m deck leaves essentially nothing wetted");
    assertTrue(windy.effectiveDensityLpmPerM2() < still.effectiveDensityLpmPerM2());
  }

  @Test
  void obstructedLineOfSightDisqualifiesAMonitor() {
    FireMonitorCoverage mon = new FireMonitorCoverage(100.0, 10.0).setMonitors(4, 4000.0).setGeometry(40.0, 360.0)
        .setLineOfSightObstructed(true);
    assertTrue(mon.meetsDensityStillAir());
    assertTrue(mon.verdict().startsWith("NOT SUITABLE"));
  }

  @Test
  void negativePressureMarginRoutesToADedicatedSupply() {
    FireWaterCoverageAssessment a = new FireWaterCoverageAssessment(510.0, 5100.0).setCoveredAreaM2(0.0)
        .setExistingDeliveredFlowLpm(0.0).setSpareSupplyFlowLpm(-1000.0)
        .setWorstCasePressureMarginBar(-0.9, "XX-71-0010B").setRequiredPressureMarginBar(0.5);
    assertEquals(510.0, a.coverageGapM2(), 1e-9);
    assertEquals(5100.0, a.flowDeficitLpm(), 1e-9);
    assertFalse(a.isFlowFeasible());
    assertFalse(a.isPressureFeasible());
    assertEquals(Verdict.DEDICATED_SUPPLY_REQUIRED, a.verdict());
  }

  @Test
  void pressureDeficitAloneIsDistinguishedFromFlowDeficit() {
    FireWaterCoverageAssessment press = new FireWaterCoverageAssessment(510.0, 5100.0).setSpareSupplyFlowLpm(20000.0)
        .setWorstCasePressureMarginBar(-0.9, "XX-71-0010B").setRequiredPressureMarginBar(0.5);
    assertEquals(Verdict.SUPPLY_PRESSURE_DEFICIT, press.verdict());
    assertTrue(press.recommendation().contains("XX-71-0010B"));

    FireWaterCoverageAssessment flow = new FireWaterCoverageAssessment(510.0, 5100.0).setSpareSupplyFlowLpm(100.0)
        .setWorstCasePressureMarginBar(2.5, "XX-71-0011A").setRequiredPressureMarginBar(0.5);
    assertEquals(Verdict.SUPPLY_FLOW_DEFICIT, flow.verdict());
  }

  @Test
  void fullyCoveredAreaIsCompliant() {
    FireWaterCoverageAssessment a = new FireWaterCoverageAssessment(510.0, 5100.0).setCoveredAreaM2(510.0)
        .setExistingDeliveredFlowLpm(5200.0).setSpareSupplyFlowLpm(1000.0)
        .setWorstCasePressureMarginBar(2.0, "XX-71-0011A");
    assertEquals(Verdict.COMPLIANT, a.verdict());
    assertEquals(1.0, a.coverageFraction(), 1e-12);
  }

  @Test
  void waterDoesNotExtinguishAGasJetFireButIsCreditableForEscalationControl() {
    ActiveFireProtectionScreening jet = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE)
        .setIncidentHeatFluxKWPerM2(250.0);
    assertFalse(jet.isWaterExtinguishing());
    assertTrue(jet.isWaterCreditableForEscalationControl());

    ActiveFireProtectionScreening pool = new ActiveFireProtectionScreening(FireScenario.LIQUID_POOL_FIRE);
    assertTrue(pool.isWaterExtinguishing());
  }

  @Test
  void passiveProtectionIsNeverAsubstituteForTheAreaFireWaterRequirement() {
    ActiveFireProtectionScreening s = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE)
        .setPassiveFireProtectionPresent(true).setLoadBearingStructureExposed(true);
    assertFalse(s.isPassiveSubstitutionPermitted());
    assertTrue(s.passiveSubstitutionReason().length() > 50);
    assertTrue(s.verdict().contains("no fire-water cooling credit"));
  }

  @Test
  void blowdownMarginDecidesWhetherInventoryOrProtectionGoverns() {
    ActiveFireProtectionScreening fast = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE)
        .setBlowdownTimeToTargetS(600.0).setBareSteelTimeToCriticalS(900.0);
    assertTrue(fast.isBlowdownFasterThanFailure());
    assertEquals(300.0, fast.blowdownMarginS(), 1e-9);

    ActiveFireProtectionScreening slow = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE)
        .setBlowdownTimeToTargetS(1200.0).setBareSteelTimeToCriticalS(400.0);
    assertFalse(slow.isBlowdownFasterThanFailure());
    assertEquals(-800.0, slow.blowdownMarginS(), 1e-9);
  }

  @Test
  void excludedOverheadNozzlesAddASideMountedMeasureRatherThanRemovingCoverage() {
    ActiveFireProtectionScreening s = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE)
        .setNozzlesCanBePlacedAboveEquipment(false);
    boolean found = false;
    for (int i = 0; i < s.rankedMeasures().size(); i++) {
      if (s.rankedMeasures().get(i).contains("side-mounted")) {
        found = true;
      }
    }
    assertTrue(found, "excluding overhead nozzles must produce an alternative, not a gap");
  }

  @Test
  void selectiveProtectionSavingIsReported() {
    ActiveFireProtectionScreening s = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE).setDemands(170.0,
        5100.0);
    assertEquals(1.0 - 170.0 / 5100.0, s.selectiveProtectionSavingFraction(), 1e-12);
  }

  @Test
  void jsonOutputsAreWellFormedAndCarryTheSchemaVersion() {
    String demand = new FireWaterDemandCalculator(510.0).setAreaRate(10.0).addHorizontalVessel("HA-1", 0.72, 6.61, 10.0)
        .toJson();
    String layout = new DelugeNozzleLayout(510.0, 10.0, DelugeNozzleLayout.highVelocity()).toJson();
    String monitor = new FireMonitorCoverage(510.0, 10.0).setMonitors(2, 2400.0).toJson();
    String assess = new FireWaterCoverageAssessment(510.0, 5100.0).toJson();
    String screen = new ActiveFireProtectionScreening(FireScenario.GAS_JET_FIRE).toJson();
    String[] docs = new String[] { demand, layout, monitor, assess, screen };
    for (int i = 0; i < docs.length; i++) {
      assertTrue(docs[i].contains("\"schemaVersion\""));
      com.google.gson.JsonParser.parseString(docs[i]);
    }
  }
}
