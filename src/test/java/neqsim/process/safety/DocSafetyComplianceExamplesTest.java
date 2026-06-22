package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.api14c.Api14cDeviceType;
import neqsim.process.safety.api14c.Api14cEquipmentCategory;
import neqsim.process.safety.api14c.Api14cSafeChartBuilder;
import neqsim.process.safety.compliance.NorsokP002ComplianceChecker;
import neqsim.process.safety.compliance.Sts0131Gate;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy.MultiVesselBlowdownResult;
import neqsim.process.safety.dispersion.HazardousAreaCalculator;
import neqsim.process.safety.dispersion.HazardousAreaCalculator.ReleaseGrade;
import neqsim.process.safety.esd.EsdResponseTimeSimulator;
import neqsim.process.safety.esd.EsdResponseTimeSimulator.EsdResponseTimeResult;
import neqsim.process.safety.fire.Api537FlareFlameModel;
import neqsim.process.safety.fire.PfpDemandCalculator;
import neqsim.process.safety.fire.PfpDemandCalculator.FireType;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpDemandResult;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpRating;
import neqsim.process.safety.hazid.MahBowTieBuilder;
import neqsim.process.safety.hazid.MahCatalogue;
import neqsim.process.safety.hazid.MahType;
import neqsim.process.safety.risk.bowtie.BowTieModel;
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilCatalogue;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;
import neqsim.process.safety.vibration.FivLikelihoodResult;
import neqsim.process.safety.vibration.PipingFivLikelihood;
import neqsim.process.safety.vibration.PipingFivScreening;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies every code example shown in the new {@code docs/safety} reference pages compiles and behaves as documented:
 *
 * <ul>
 * <li>{@code docs/safety/nog070_sil_sts0131_esd.md}</li>
 * <li>{@code docs/safety/api14c_norsok_p002.md}</li>
 * <li>{@code docs/safety/mah_bowtie_fiv_screening.md}</li>
 * <li>{@code docs/safety/flare_flame_hazardous_area_pfp.md}</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class DocSafetyComplianceExamplesTest {

  /**
   * Builds a synthetic linear-decay depressurization result for the coupled blowdown example.
   *
   * @param peak peak mass flow in kg/s at t=0
   * @param durationS blowdown duration in s
   * @param stepS time step in s
   * @return populated depressurization result
   */
  private DepressurizationResult linearDecay(double peak, double durationS, double stepS) {
    DepressurizationResult r = new DepressurizationResult();
    for (double t = 0.0; t <= durationS + 1.0e-9; t += stepS) {
      double q = peak * Math.max(0.0, 1.0 - t / durationS);
      r.time.add(t);
      r.massFlowKgPerS.add(q);
    }
    return r;
  }

  /** Example: NOG 070 pre-determined SIL look-up and PFD-based verification. */
  @Test
  public void testNog070SilExample() {
    int minSil = Nog070SilCatalogue.getMinimumSil(Nog070SifType.HIPPS_PIPELINE);
    assertEquals(3, minSil);

    Nog070SilDetermination r = Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.0e-4);
    assertEquals(3, r.getAchievedSil());
    assertEquals(3, r.getMinimumSil());
    assertTrue(r.isCompliant());
    assertNotNull(r.toJson());
  }

  /** Example: STS-0131 technical-safety acceptance gate. */
  @Test
  public void testSts0131GateExample() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addPsvSizingMargin(1.0, 1.15, 0.10);
    gate.addMdmt(-20.0, -29.0);
    gate.addSil(Nog070SilDetermination.evaluate(Nog070SifType.PSD_PROCESS_SEGMENT, 5.0e-3));
    gate.addCustom("Fire case", true, "Heat input within API 521 envelope");

    assertTrue(gate.isAcceptable());
    assertEquals(0, gate.countFailures());
    assertNotNull(gate.toJson());
  }

  /** Example: ESD detection -> logic -> final-element response-time budget. */
  @Test
  public void testEsdResponseTimeExample() {
    EsdResponseTimeResult res = new EsdResponseTimeSimulator().setSifTag("ESD-1234")
	.addDetection("PT-1001 high-pressure", 1.0).addLogic("Logic solver scan + 2oo3 vote", 0.5)
	.addValve("ESDV-2001", 0.5, 8.0).setAllowableResponseTimeS(15.0).evaluate();

    assertEquals(1.0, res.getDetectionTimeS(), 1.0e-9);
    assertEquals(0.5, res.getLogicTimeS(), 1.0e-9);
    assertEquals(8.5, res.getFinalElementTimeS(), 1.0e-9);
    assertEquals(10.0, res.getTotalResponseTimeS(), 1.0e-9);
    assertEquals(5.0, res.getMarginS(), 1.0e-9);
    assertTrue(res.isWithinBudget());
  }

  /** Example: API RP 14C SAFE chart built from a ProcessSystem. */
  @Test
  public void testApi14cSafeChartExample() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("HP separator", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    Api14cSafeChartBuilder chart = new Api14cSafeChartBuilder()
	.declarePresent("HP separator", EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV)).build(process);

    assertNotNull(chart.getItems());
    assertNotNull(chart.getGaps());
    assertNotNull(chart.toMarkdown());
    assertNotNull(chart.toJson());
    // A separator requires more than PSH + PSV, so the chart is incomplete.
    assertEquals(false, chart.isComplete());
    assertTrue(chart.getItems().get(0).getCategory() == Api14cEquipmentCategory.PRESSURE_VESSEL
	|| chart.getItems().size() >= 1);
  }

  /** Example: NORSOK P-002 process-design compliance screen. */
  @Test
  public void testNorsokP002Example() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.5)
	.checkBlowdownRhoV2("BDV-1", 150000.0).checkVentGasVelocity("Vent", 45.0).checkLiquidCarryOver("V-100", 1.0e-4)
	.checkErosionalVelocity("Line-200", 80000.0).recordDepressurisationValve("BDV-2", true, "Sized for fire case")
	.recordDrainSlope("CD-1", true, "1:100 slope OK");

    assertTrue(c.isCompliant());
    assertEquals(0, c.countNonCompliant());
    assertNotNull(c.toJson());
  }

  /** Example: coupled multi-vessel blowdown header load. */
  @Test
  public void testMultiVesselBlowdownExample() {
    MultiVesselBlowdownStudy study = new MultiVesselBlowdownStudy().setGridStep(1.0)
	.addSourceResult("HP-sep", linearDecay(40.0, 120.0, 1.0))
	.addSourceResult("Inlet-sep", linearDecay(25.0, 90.0, 1.0)).setHeader(0.80, 1.5, 288.15, 0.020, 1.30);

    MultiVesselBlowdownResult res = study.run();
    assertEquals(65.0, res.getPeakTotalMassFlowKgPerS(), 1.0e-6);
    assertEquals(0.0, res.getPeakTimeS(), 1.0e-9);
    assertTrue(res.getHeaderMach() < 0.70);
    assertTrue(res.isHeaderMachAcceptable());
  }

  /** Example: ISO 17776 MAH bow-tie from the catalogue. */
  @Test
  public void testMahBowTieExample() {
    BowTieModel bowtie = MahBowTieBuilder.build(MahType.TOPSIDE_HYDROCARBON_RELEASE);
    assertNotNull(bowtie.getHazardId());
    assertTrue(bowtie.getThreats().size() >= 4);
    assertTrue(bowtie.getConsequences().size() >= 3);
    assertTrue(bowtie.getBarriers().size() >= 5);

    assertTrue(MahCatalogue.threatsFor(MahType.TOPSIDE_HYDROCARBON_RELEASE).size() > 0);
    assertTrue(MahCatalogue.consequencesFor(MahType.TOPSIDE_HYDROCARBON_RELEASE).size() > 0);
    assertTrue(MahCatalogue.barriersFor(MahType.TOPSIDE_HYDROCARBON_RELEASE).size() > 0);
  }

  /** Example: Energy Institute AVIFF flow-induced-vibration screening. */
  @Test
  public void testFivScreeningExample() {
    FivLikelihoodResult gas = PipingFivScreening.screenGas("Compressor discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);
    assertTrue(gas.getLofScore() >= 0.0);
    assertNotNull(gas.getLikelihood());
    assertNotNull(gas.toJson());

    FivLikelihoodResult liquid = PipingFivScreening.screenLiquid("Pump discharge", 3.5, 0.15, 0.005, 1, 1.5);
    assertNotNull(liquid.getLikelihood());

    PipingFivLikelihood band = PipingFivScreening.bandFor(0.7);
    assertEquals(PipingFivLikelihood.HIGH, band);
  }

  /** Example: API 537 flare flame geometry, radiation and noise. */
  @Test
  public void testApi537FlareFlameExample() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0)
	.setWindSpeedMPerS(10.0);

    double length = model.flameLengthM();
    assertTrue(length > 20.0 && length < 200.0);
    assertTrue(model.flameTiltRad() >= 0.0);

    double r158 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_1_58_KW);
    double r473 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_4_73_KW);
    double r946 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_9_46_KW);
    assertTrue(r158 >= r473);
    assertTrue(r473 >= r946);

    assertTrue(model.heatFluxAtGroundDistance(60.0) >= 0.0);
    assertTrue(model.soundPowerLevelDb() > 0.0);
    assertTrue(model.soundPressureLevelDb(100.0) > 0.0);
  }

  /** Example: IEC 60079-10-1 hazardous-area classification. */
  @Test
  public void testHazardousAreaExample() {
    HazardousAreaCalculator calc = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604)
	.setReleaseGrade(ReleaseGrade.SECONDARY).setSafetyFactor(0.5);

    assertTrue(calc.hazardousDistanceM() > 0.0);
    assertEquals("Zone 2", calc.zoneClassification());
  }

  /** Example: API 521 passive-fire-protection demand. */
  @Test
  public void testPfpDemandExample() {
    PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.012).setFireType(FireType.POOL).evaluate(3600.0);
    assertTrue(res.isPfpRequired());
    assertTrue(res.getRequiredPfpThicknessMm() > 0.0);
    assertEquals(PfpRating.H60, res.getRating());
  }
}
