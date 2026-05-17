package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.asphaltene.AsphalteneInhibitorPerformance;
import neqsim.process.chemistry.corrosion.CorrosionInhibitorPerformance;
import neqsim.process.chemistry.equipment.InhibitorInjectionPoint;
import neqsim.process.chemistry.hydrate.KineticHydrateInhibitorPerformance;
import neqsim.process.chemistry.hydrate.ThermodynamicHydrateInhibitorPerformance;
import neqsim.process.chemistry.rca.RootCauseAnalyser;
import neqsim.process.chemistry.scale.ScaleControlAssessor;
import neqsim.process.chemistry.util.ChemistryUncertaintyAnalyzer;
import neqsim.process.chemistry.util.StreamChemistryAdapter;
import neqsim.process.chemistry.wax.WaxInhibitorPerformance;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Coverage for the chemistry package's new advanced features (Phase 6): stream adapter, hydrate /
 * wax / asphaltene performance, injection-point equipment, and uncertainty analyser.
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemistryAdvancedFeaturesTest {

  /**
   * Builds a small produced fluid stream with water, methane, CO2 for adapter testing.
   *
   * @return a run stream
   */
  private StreamInterface buildProducedStream() {
    SystemSrkEos sys = new SystemSrkEos(298.15, 50.0);
    sys.addComponent("methane", 80.0);
    sys.addComponent("CO2", 5.0);
    sys.addComponent("water", 15.0);
    sys.setMixingRule("classic");
    sys.setMultiPhaseCheck(true);
    Stream s = new Stream("produced", sys);
    s.setFlowRate(100.0, "kg/hr");
    s.setTemperature(25.0, "C");
    s.setPressure(50.0, "bara");
    s.run();
    return s;
  }

  /** Adapter exposes T, P, partial pressures, and aqueous concentrations without crashing. */
  @Test
  public void streamAdapterExtractsScalars() {
    StreamInterface s = buildProducedStream();
    StreamChemistryAdapter ad = new StreamChemistryAdapter(s);
    assertTrue(ad.getTemperatureCelsius() > 20.0 && ad.getTemperatureCelsius() < 30.0);
    assertTrue(ad.getPressureBara() > 49.0 && ad.getPressureBara() < 51.0);
    assertTrue(ad.getPartialPressureBara("CO2") > 0.0);
    Map<String, Object> map = ad.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("temperatureC"));
    assertTrue(map.containsKey("pressureBara"));
  }

  /** ChemicalCompatibilityAssessor.fromStream populates conditions from the stream. */
  @Test
  public void chemicalCompatibilityFromStreamSeedsConditions() {
    StreamInterface s = buildProducedStream();
    ChemicalCompatibilityAssessor a = ChemicalCompatibilityAssessor.fromStream(s);
    a.addChemical(ProductionChemical.scaleInhibitor("Phosphonate-A", 25.0));
    a.addChemical(ProductionChemical.corrosionInhibitor("Imidazoline-B", 50.0));
    a.evaluate();
    Map<String, Object> result = a.toMap();
    assertNotNull(result);
    assertTrue(result.containsKey("standardsApplied"));
  }

  /** Hammerschmidt-style THI sizing returns positive injection rate and standards. */
  @Test
  public void thermodynamicHydrateInhibitorSizingHasResult() {
    ThermodynamicHydrateInhibitorPerformance thi = new ThermodynamicHydrateInhibitorPerformance();
    thi.setInhibitorChemistry(ThermodynamicHydrateInhibitorPerformance.InhibitorChemistry.MEG);
    thi.setTargetSubcoolingC(8.0);
    thi.setWaterFlowKgPerHour(500.0);
    thi.setInhibitorPurityWtPct(80.0);
    thi.setLeanInhibitorWtPctInWater(0.0);
    thi.evaluate();
    assertTrue(thi.isEvaluated());
    assertTrue(thi.getRequiredInhibitorWtPctInWater() > 0.0);
    assertTrue(thi.getRequiredInjectionKgPerHour() > 0.0);
    assertNotNull(thi.toMap().get("standardsApplied"));
    assertNotNull(thi.toJson());
  }

  /** KHI inverse model returns a finite required dose. */
  @Test
  public void kineticHydrateInhibitorReturnsRequiredDose() {
    KineticHydrateInhibitorPerformance khi = new KineticHydrateInhibitorPerformance();
    khi.setSubcoolingC(8.0);
    khi.setDoseWtPct(1.5);
    khi.setTargetInductionTimeHours(48.0);
    khi.evaluate();
    assertTrue(khi.isEvaluated());
    assertTrue(khi.getPredictedInductionTimeHours() > 0.0);
    assertTrue(Double.isFinite(khi.getRequiredDoseWtPct()));
  }

  /** Wax inhibitor depresses pour point and reduces yield stress. */
  @Test
  public void waxInhibitorReducesPourPoint() {
    WaxInhibitorPerformance wax = new WaxInhibitorPerformance();
    wax.setBasePourPointC(25.0);
    wax.setBaseWaxAppearanceTemperatureC(35.0);
    wax.setDoseMgL(300.0);
    wax.evaluate();
    assertTrue(wax.isEvaluated());
    assertTrue(wax.getPourPointDepressionC() > 0.0);
    assertTrue(wax.getInhibitedPourPointC() < 25.0);
    assertTrue(
        wax.getYieldStressReductionFraction() > 0.0 && wax.getYieldStressReductionFraction() < 1.0);
    assertNotNull(wax.toJson());
  }

  /** Asphaltene inhibitor reduces CII and shifts onset pressure downward. */
  @Test
  public void asphalteneInhibitorReducesCii() {
    AsphalteneInhibitorPerformance asp = new AsphalteneInhibitorPerformance();
    asp.setBaseColloidalInstabilityIndex(0.95);
    asp.setBaseAsphalteneOnsetPressureBara(200.0);
    asp.setDoseMgL(150.0);
    asp.evaluate();
    assertTrue(asp.isEvaluated());
    assertTrue(asp.getCiiReduction() > 0.0);
    assertTrue(asp.getInhibitedCii() < 0.95);
    assertTrue(asp.getInhibitedAopBara() < 200.0);
  }

  /** InhibitorInjectionPoint runs and produces an outlet stream and a non-zero dose. */
  @Test
  public void injectionPointRunsAndExposesDose() {
    StreamInterface s = buildProducedStream();
    InhibitorInjectionPoint inj = new InhibitorInjectionPoint("MEG-INJ", s);
    inj.setChemical(ProductionChemical.thermodynamicHydrateInhibitor("MEG-90", 100000.0));
    inj.setDoseInKgPerHour(20.0);
    inj.run();
    assertNotNull(inj.getOutletStream());
    assertEquals(20.0, inj.getInjectionRateKgPerHour(), 1e-9);
  }

  /** ChemistryUncertaintyAnalyzer: P10 < P50 < P90 and tornado is non-empty. */
  @Test
  public void uncertaintyAnalyserReturnsPercentiles() {
    final ChemistryUncertaintyAnalyzer mc = new ChemistryUncertaintyAnalyzer();
    mc.setNumberOfTrials(500);
    mc.setRandomSeed(42L);
    mc.addParameter(mc.triangular("dose_mgL", 50.0, 100.0, 200.0));
    mc.addParameter(mc.triangular("efficiency", 0.5, 0.7, 0.9));
    mc.run(new java.util.function.ToDoubleFunction<double[]>() {
      @Override
      public double applyAsDouble(double[] x) {
        // simple linear residual-rate model
        return 1.0 - x[1] * (x[0] / (x[0] + 50.0));
      }
    });
    assertTrue(mc.isEvaluated());
    assertTrue(mc.getP10() <= mc.getP50());
    assertTrue(mc.getP50() <= mc.getP90());
    assertFalse(mc.getTornado().isEmpty());
  }

  /**
   * The Bayesian RCA update must produce posteriors that sum to ~1.0 and rerank candidates
   * according to the supplied likelihoods.
   */
  @Test
  public void bayesianRcaUpdatesPosteriorsAndReranks() {
    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.setTemperatureCelsius(80.0);
    rca.setPressureBara(50.0);
    rca.setCO2PartialPressureBar(2.0);
    rca.setH2SPartialPressureBar(0.0);
    rca.setPH(5.5);
    rca.setIronMgL(8.0);
    rca.setOxygenPpb(15.0);
    rca.analyse();

    java.util.Map<String, Double> evidence = new java.util.LinkedHashMap<String, Double>();
    evidence.put("CO2_CORROSION", 5.0);
    evidence.put("O2_CORROSION", 0.5);
    rca.addEvidence(evidence);

    java.util.Map<String, Double> post = rca.getBayesianPosteriors();
    assertFalse(post.isEmpty());
    double sum = 0.0;
    for (Double v : post.values()) {
      sum += v;
    }
    assertEquals(1.0, sum, 1.0e-6);
    if (post.containsKey("CO2_CORROSION") && post.containsKey("O2_CORROSION")) {
      assertTrue(post.get("CO2_CORROSION") > post.get("O2_CORROSION"));
    }
    Map<String, Object> map = rca.toMap();
    assertTrue(map.containsKey("bayesianPosteriors"));
  }

  /** Standards traceability: every chemistry result class exposes a non-empty standards list. */
  @Test
  public void standardsTraceabilityExposedOnAllChemistryClasses() {
    StreamInterface s = buildProducedStream();
    assertFalse(ChemicalCompatibilityAssessor.fromStream(s).getStandardsApplied().isEmpty());
    assertFalse(ScaleControlAssessor.fromStream(s).getStandardsApplied().isEmpty());
    assertFalse(
        CorrosionInhibitorPerformance.fromStream(s, 0.1, 2.0).getStandardsApplied().isEmpty());
    assertFalse(new neqsim.process.chemistry.scavenger.H2SScavengerPerformance()
        .getStandardsApplied().isEmpty());
    assertFalse(new neqsim.process.chemistry.scale.ScaleInhibitorPerformance().getStandardsApplied()
        .isEmpty());
    assertFalse(
        new neqsim.process.chemistry.acid.AcidTreatmentSimulator().getStandardsApplied().isEmpty());
  }
}
