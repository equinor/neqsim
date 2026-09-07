package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.asphaltene.AsphalteneInhibitorPerformance;
import neqsim.process.chemistry.corrosion.CorrosionInhibitorPerformance;
import neqsim.process.chemistry.equipment.InhibitorInjectionPoint;
import neqsim.process.chemistry.hydrate.KineticHydrateInhibitorPerformance;
import neqsim.process.chemistry.hydrate.ThermodynamicHydrateInhibitorPerformance;
import neqsim.process.chemistry.rca.RootCauseAnalyser;
import neqsim.process.chemistry.rca.RootCauseCandidate;
import neqsim.process.chemistry.rca.Symptom;
import neqsim.process.chemistry.scale.ProductionChemicalScaleScenario;
import neqsim.process.chemistry.scale.ScaleControlAssessor;
import neqsim.process.chemistry.scale.ScaleInhibitorPerformance;
import neqsim.process.chemistry.scale.ScaleRemediationAdvisor;
import neqsim.process.chemistry.scavenger.H2SScavengerPerformance;
import neqsim.process.chemistry.util.ChemistryUncertaintyAnalyzer;
import neqsim.process.chemistry.util.StreamChemistryAdapter;
import neqsim.process.chemistry.wax.WaxInhibitorPerformance;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.watertreatment.DemulsifierDoseResponseModel;
import neqsim.process.equipment.watertreatment.OilInWaterDoseOptimizer;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executes every code example published in the {@code neqsim-production-chemistry} skill so that a documented API
 * cannot silently drift away from the implementation.
 *
 * @author ESOL
 * @version 1.0
 */
public class ProductionChemistrySkillExamplesTest {

  /**
   * Builds the scale inhibitor used by several examples.
   *
   * @return an evaluated barite scale-inhibitor performance model
   */
  private ScaleInhibitorPerformance bariteInhibitor() {
    ScaleInhibitorPerformance sip = new ScaleInhibitorPerformance();
    sip.setScaleType(ScaleInhibitorPerformance.ScaleType.BASO4);
    sip.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
    sip.setTemperatureCelsius(95.0);
    sip.setSaturationRatio(12.0);
    sip.setTdsMgL(90000.0);
    sip.setCalciumMgL(1200.0);
    sip.setAvailableDoseMgL(15.0);
    sip.evaluate();
    return sip;
  }

  /**
   * Builds a produced-fluid stream used by the stream-adapter examples.
   *
   * @return a run stream carrying gas, CO2 and water
   */
  private Stream producedStream() {
    SystemSrkEos sys = new SystemSrkEos(298.15, 50.0);
    sys.addComponent("methane", 80.0);
    sys.addComponent("CO2", 5.0);
    sys.addComponent("water", 15.0);
    sys.setMixingRule("classic");
    sys.setMultiPhaseCheck(true);
    Stream s = new Stream("produced", sys);
    s.setFlowRate(1000.0, "kg/hr");
    s.setTemperature(25.0, "C");
    s.setPressure(50.0, "bara");
    s.run();
    return s;
  }

  @Test
  void testChemicalInventoryExample() {
    ProductionChemical si = ProductionChemical.scaleInhibitor("SI-A", 20.0);
    ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0);
    ProductionChemical meg = ProductionChemical.thermodynamicHydrateInhibitor("MEG", 0.0);
    ProductionChemical scav = ProductionChemical.h2sScavenger("Triazine", 100.0);

    si.setActiveIngredient("phosphonate");
    si.setActiveWtPct(35.0);
    si.setIonicNature(ProductionChemical.IonicNature.ANIONIC);
    si.setTemperatureRangeC(4.0, 150.0);
    si.setPH(3.5);

    assertEquals(ProductionChemical.ChemicalType.SCALE_INHIBITOR, si.getType());
    assertEquals(ProductionChemical.ChemicalType.CORROSION_INHIBITOR, ci.getType());
    assertEquals(ProductionChemical.ChemicalType.HYDRATE_INHIBITOR_THERMODYNAMIC, meg.getType());
    assertEquals(ProductionChemical.ChemicalType.H2S_SCAVENGER, scav.getType());
    assertTrue(si.isStableAt(120.0));
    assertFalse(si.isStableAt(200.0));
    assertNotNull(si.toMap());
  }

  @Test
  void testCompatibilityExample() {
    ProductionChemical si = ProductionChemical.scaleInhibitor("SI-A", 20.0);
    si.setActiveIngredient("phosphonate");
    si.setIonicNature(ProductionChemical.IonicNature.ANIONIC);
    ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0);
    ci.setIonicNature(ProductionChemical.IonicNature.CATIONIC);
    ProductionChemical scav = ProductionChemical.h2sScavenger("Triazine", 100.0);

    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(si);
    assessor.addChemical(ci);
    assessor.addChemical(scav);
    assessor.setTemperatureCelsius(85.0);
    assessor.setPressureBara(90.0);
    assessor.setCalciumMgL(1200.0);
    assessor.setIronMgL(5.0);
    assessor.setBicarbonateMgL(300.0);
    assessor.setMaterial("carbon steel");
    assessor.evaluate();

    assertTrue(assessor.isEvaluated());
    assertNotNull(assessor.getVerdict());
    assertNotNull(assessor.getIssues());
    assertNotNull(assessor.getInteractionMatrix());
    assertNotNull(assessor.getThermalStability());
    assertNotNull(assessor.getStandardsApplied());
    assertTrue(assessor.toJson().length() > 0);
  }

  @Test
  void testCompatibilityFromStreamExample() {
    ChemicalCompatibilityAssessor assessor = ChemicalCompatibilityAssessor.fromStream(producedStream());
    assessor.addChemical(ProductionChemical.scaleInhibitor("SI-A", 20.0));
    assessor.evaluate();
    assertTrue(assessor.isEvaluated());
  }

  @Test
  void testScaleInhibitorAndControlExample() {
    ScaleInhibitorPerformance sip = bariteInhibitor();
    assertTrue(sip.isEvaluated());
    assertTrue(sip.getMinimumInhibitorConcentrationMgL() > 0.0);
    assertTrue(sip.getRecommendedDoseMgL() > 0.0);
    assertTrue(sip.getEfficiency() >= 0.0);
    assertNotNull(sip.getWarnings());

    ScalePredictionCalculator pred = new ScalePredictionCalculator();
    pred.setTemperatureCelsius(95.0);
    pred.setPressureBara(150.0);
    pred.setCalciumConcentration(1200.0);
    pred.setBariumConcentration(250.0);
    pred.setSulphateConcentration(1800.0);
    pred.setBicarbonateConcentration(300.0);
    pred.setTotalDissolvedSolids(90000.0);
    pred.setPH(6.2);

    ScaleControlAssessor control = new ScaleControlAssessor(pred);
    control.addInhibitor(ScaleInhibitorPerformance.ScaleType.BASO4, sip);
    control.evaluate();

    assertTrue(control.isEvaluated());
    assertTrue(Double.isFinite(control.getResidualSI(ScaleInhibitorPerformance.ScaleType.BASO4)));
    assertTrue(Double.isFinite(control.getWorstResidualSI()));
    control.isControlled(0.0);
    control.isKineticallyControlled(1.0);
    assertNotNull(control.getStandardsApplied());
  }

  @Test
  void testCorrosionInhibitorExample() {
    CorrosionInhibitorPerformance cip = new CorrosionInhibitorPerformance();
    cip.setChemistry(CorrosionInhibitorPerformance.InhibitorChemistry.IMIDAZOLINE);
    cip.setDoseMgL(50.0);
    cip.setBaseCorrosionRateMmYr(2.4);
    cip.setTemperatureCelsius(70.0);
    cip.setWallShearStressPa(35.0);
    cip.setOrganicAcidPpm(200.0);
    cip.setH2SPartialPressureBar(0.02);
    cip.setOxygenPpb(10.0);
    cip.evaluate();

    assertTrue(cip.isEvaluated());
    assertTrue(cip.getEfficiency() > 0.0 && cip.getEfficiency() <= 1.0);
    assertTrue(cip.getInhibitedCorrosionRateMmYr() < 2.4);
    assertTrue(cip.getMinimumEffectiveDoseMgL() > 0.0);
    assertNotNull(cip.getWarnings());

    CorrosionInhibitorPerformance fromStream = CorrosionInhibitorPerformance.fromStream(producedStream(), 0.2, 4.0);
    assertNotNull(fromStream);
  }

  @Test
  void testHydrateInhibitorExamples() {
    ThermodynamicHydrateInhibitorPerformance thi = new ThermodynamicHydrateInhibitorPerformance();
    thi.setInhibitorChemistry(ThermodynamicHydrateInhibitorPerformance.InhibitorChemistry.MEG);
    thi.setTargetSubcoolingC(8.0);
    thi.setWaterFlowKgPerHour(1500.0);
    thi.setInhibitorPurityWtPct(90.0);
    thi.setLeanInhibitorWtPctInWater(0.0);
    thi.evaluate();

    assertTrue(thi.getRequiredInhibitorWtPctInWater() > 0.0);
    assertTrue(thi.getRequiredInjectionKgPerHour() > 0.0);
    assertNotNull(thi.getWarnings());
    assertTrue(thi.toJson().length() > 0);

    KineticHydrateInhibitorPerformance khi = new KineticHydrateInhibitorPerformance();
    khi.setSubcoolingC(6.0);
    khi.setDoseWtPct(0.5);
    khi.setTargetInductionTimeHours(24.0);
    khi.evaluate();

    assertTrue(khi.getPredictedInductionTimeHours() >= 0.0);
    assertTrue(khi.getRequiredDoseWtPct() >= 0.0);
  }

  @Test
  void testWaxAndAsphalteneInhibitorExamples() {
    WaxInhibitorPerformance wax = new WaxInhibitorPerformance();
    wax.setInhibitorChemistry(WaxInhibitorPerformance.InhibitorChemistry.EVA);
    wax.setBasePourPointC(24.0);
    wax.setBaseWaxAppearanceTemperatureC(32.0);
    wax.setDoseMgL(300.0);
    wax.evaluate();
    assertTrue(wax.getPourPointDepressionC() > 0.0);
    assertTrue(wax.getInhibitedWaxAppearanceTemperatureC() <= 32.0);

    AsphalteneInhibitorPerformance asp = new AsphalteneInhibitorPerformance();
    asp.setInhibitorChemistry(AsphalteneInhibitorPerformance.InhibitorChemistry.ALKYLPHENOL_RESIN);
    asp.setBaseColloidalInstabilityIndex(1.2);
    asp.setBaseAsphalteneOnsetPressureBara(280.0);
    asp.setDoseMgL(150.0);
    asp.evaluate();
    assertTrue(asp.getInhibitedCii() < 1.2);
    assertTrue(asp.getEfficacyFraction() > 0.0);
  }

  @Test
  void testH2sScavengerExample() {
    H2SScavengerPerformance scv = new H2SScavengerPerformance();
    scv.setChemistry(H2SScavengerPerformance.ScavengerChemistry.MEA_TRIAZINE);
    scv.setActiveWtPct(40.0);
    scv.setScavengerInventoryKg(20000.0);
    scv.setGasFlowMSm3PerDay(4.0);
    scv.setH2SInletPpm(35.0);
    scv.setH2STargetPpm(4.0);
    scv.setTemperatureCelsius(40.0);
    scv.setPressureBara(70.0);
    scv.evaluate();

    assertTrue(scv.getH2SToRemoveKgPerDay() > 0.0);
    assertTrue(scv.getScavengerDemandKgPerDay() > 0.0);
    assertTrue(scv.getCapacityKgH2SPerKgActive() > 0.0);
    assertTrue(scv.getBreakthroughDays() > 0.0);
    assertNotNull(scv.getStandardsApplied());
  }

  @Test
  void testInjectionPointExample() {
    Stream feed = producedStream();
    ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0);

    InhibitorInjectionPoint inj = new InhibitorInjectionPoint("CI injection", feed);
    inj.setChemical(ci);
    inj.setDoseInPpmOnWater(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inj);
    process.run();

    assertTrue(inj.getActiveIngredientPpmInWater() >= 0.0);
    assertTrue(inj.getInjectionRateKgPerHour() >= 0.0);
    assertNotNull(inj.getOutletStream());
  }

  @Test
  void testDemulsifierDoseExample() {
    DemulsifierDoseResponseModel dr = new DemulsifierDoseResponseModel();
    double[] dose = new double[] { 2.0, 5.0, 10.0, 20.0, 30.0 };
    double[] observed = new double[] { 180.0, 110.0, 60.0, 35.0, 40.0 };
    double rms = dr.calibrate(dose, observed, 250.0);
    assertTrue(rms >= 0.0);
    assertTrue(dr.predictOilInWater(250.0, 12.0) > 0.0);

    OilInWaterDoseOptimizer opt = new OilInWaterDoseOptimizer();
    opt.setDoseResponseModel(dr);
    opt.setDoseRange(2.0, 40.0, 0.5);
    opt.setSafetyMarginMgL(3.0);
    OilInWaterDoseOptimizer.DoseRecommendation rec = opt.recommendDose(250.0, 900.0, 18);
    assertNotNull(rec);
    assertTrue(rec.getSetpointDosePpm() >= 2.0);
    assertNotNull(rec.getStatus());
  }

  @Test
  void testTreatmentScenarioExample() {
    ProductionChemicalScaleScenario sc = new ProductionChemicalScaleScenario();
    sc.addChemical(ProductionChemical.causticPHAdjuster("NaOH", 500.0)).setTemperatureCelsius(80.0)
        .setPressureBara(60.0).setPH(5.8).setCalciumMgL(1200.0).setBicarbonateMgL(300.0).setSulphateMgL(1800.0)
        .setBariumMgL(250.0).setTotalDissolvedSolidsMgL(90000.0).setCO2PartialPressureBar(1.5);
    sc.evaluate();

    assertTrue(sc.isEvaluated());
    assertTrue(sc.getTreatedPH() >= 5.8);
    assertTrue(Double.isFinite(sc.getBaselineSaturationIndex("CaCO3")));
    assertTrue(Double.isFinite(sc.getTreatedSaturationIndex("CaCO3")));
    assertTrue(Double.isFinite(sc.getSaturationIndexChange("CaCO3")));
  }

  @Test
  void testRootCauseExample() {
    ProductionChemical si = ProductionChemical.scaleInhibitor("SI-A", 20.0);
    ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0);

    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT, "hard white deposit in choke")
        .withMeasurement("deposit_thickness_mm", 3.0).withConfidence(0.9));
    rca.addSymptom(new Symptom(Symptom.Category.FLOW_RESTRICTION, "choke Cv down 25%"));
    rca.addChemical(si);
    rca.addChemical(ci);
    rca.setTemperatureCelsius(85.0);
    rca.setPH(5.8);
    rca.setCalciumMgL(1200.0);
    rca.setBariumMgL(250.0);
    rca.setSulphateMgL(1800.0);
    rca.setOxygenPpb(15.0);
    rca.setMaterial("carbon steel");
    rca.analyse();

    assertTrue(rca.isEvaluated());
    RootCauseCandidate primary = rca.getPrimary();
    assertNotNull(primary);
    List<RootCauseCandidate> ranked = rca.getCandidates();
    assertFalse(ranked.isEmpty());
    assertNotNull(rca.getDataGaps());

    Map<String, Double> likelihoods = new LinkedHashMap<String, Double>();
    likelihoods.put("BASO4_SCALE", 0.8);
    likelihoods.put("CACO3_SCALE", 0.2);
    rca.addEvidence(likelihoods);
    assertNotNull(rca.getBayesianPosteriors());

    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("BaSO4");
    assertFalse(options.isEmpty());
  }

  @Test
  void testStreamChemistryAdapterExample() {
    StreamChemistryAdapter ad = new StreamChemistryAdapter(producedStream());
    assertTrue(Double.isFinite(ad.getTemperatureCelsius()));
    assertTrue(ad.getPartialPressureBara("CO2") >= 0.0);
    assertTrue(ad.getCalciumMgL() >= 0.0);
    assertTrue(ad.getTdsMgL() >= 0.0);
    assertTrue(ad.getH2SInGasPpm() >= 0.0);
    assertTrue(ad.getGasFlowSm3PerDay() >= 0.0);
    assertTrue(ad.estimateWallShearStressPa(0.2, 4.0) >= 0.0);
    assertNotNull(ad.toMap());
  }

  @Test
  void testUncertaintyExample() {
    ChemistryUncertaintyAnalyzer unc = new ChemistryUncertaintyAnalyzer();
    unc.setNumberOfTrials(200);
    unc.setRandomSeed(42L);
    unc.addParameter(unc.triangular("baseRateMmYr", 1.5, 2.4, 4.0));
    unc.addParameter(unc.triangular("shearPa", 20.0, 35.0, 60.0));
    unc.run(new ToDoubleFunction<double[]>() {
      @Override
      public double applyAsDouble(double[] x) {
        CorrosionInhibitorPerformance m = new CorrosionInhibitorPerformance();
        m.setChemistry(CorrosionInhibitorPerformance.InhibitorChemistry.IMIDAZOLINE);
        m.setDoseMgL(50.0);
        m.setBaseCorrosionRateMmYr(x[0]);
        m.setWallShearStressPa(x[1]);
        m.setTemperatureCelsius(70.0);
        m.evaluate();
        return m.getInhibitedCorrosionRateMmYr();
      }
    });

    assertTrue(unc.isEvaluated());
    assertTrue(unc.getP10() <= unc.getP50());
    assertTrue(unc.getP50() <= unc.getP90());
    assertNotNull(unc.getTornado());
  }
}
