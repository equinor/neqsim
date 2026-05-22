package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.process.chemistry.acid.AcidTreatmentSimulator;
import neqsim.process.chemistry.corrosion.CorrosionInhibitorPerformance;
import neqsim.process.chemistry.scale.ScaleInhibitorPerformance;
import neqsim.process.chemistry.scavenger.H2SScavengerPerformance;

/**
 * Phase 2 performance model regression tests.
 */
class ChemistryPerformanceModelsTest {

  @Test
  void scaleInhibitorMICIncreasesWithSaturationRatio() {
    ScaleInhibitorPerformance lowSr = new ScaleInhibitorPerformance();
    lowSr.setScaleType(ScaleInhibitorPerformance.ScaleType.BASO4);
    lowSr.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
    lowSr.setSaturationRatio(5.0);
    lowSr.evaluate();

    ScaleInhibitorPerformance highSr = new ScaleInhibitorPerformance();
    highSr.setScaleType(ScaleInhibitorPerformance.ScaleType.BASO4);
    highSr.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
    highSr.setSaturationRatio(50.0);
    highSr.evaluate();

    assertTrue(
        highSr.getMinimumInhibitorConcentrationMgL() > lowSr.getMinimumInhibitorConcentrationMgL());
  }

  @Test
  void scaleInhibitorPhosphonateAtHighCalciumWarns() {
    ScaleInhibitorPerformance perf = new ScaleInhibitorPerformance();
    perf.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
    perf.setCalciumMgL(5000.0);
    perf.evaluate();
    assertTrue(perf.getWarnings().containsKey("calcium_phosphonate_precipitation"));
  }

  @Test
  void scaleInhibitorEfficiencyAdequateAtRecommendedDose() {
    ScaleInhibitorPerformance perf = new ScaleInhibitorPerformance();
    perf.setSaturationRatio(10.0);
    perf.evaluate();
    perf.setAvailableDoseMgL(perf.getRecommendedDoseMgL());
    perf.evaluate();
    assertTrue(perf.isAdequate());
    assertTrue(perf.getEfficiency() > 0.85);
  }

  @Test
  void corrosionInhibitorEfficiencyIncreasesWithDose() {
    CorrosionInhibitorPerformance low = new CorrosionInhibitorPerformance();
    low.setBaseCorrosionRateMmYr(2.0);
    low.setDoseMgL(10.0);
    low.evaluate();
    CorrosionInhibitorPerformance high = new CorrosionInhibitorPerformance();
    high.setBaseCorrosionRateMmYr(2.0);
    high.setDoseMgL(50.0);
    high.evaluate();
    assertTrue(high.getEfficiency() > low.getEfficiency());
    assertTrue(high.getInhibitedCorrosionRateMmYr() < low.getInhibitedCorrosionRateMmYr());
  }

  @Test
  void corrosionInhibitorHighShearWarns() {
    CorrosionInhibitorPerformance perf = new CorrosionInhibitorPerformance();
    perf.setWallShearStressPa(180.0);
    perf.setDoseMgL(50.0);
    perf.evaluate();
    assertTrue(perf.getWarnings().containsKey("high_shear"));
  }

  @Test
  void acidStoichiometryDissolvesCalciumCarbonate() {
    AcidTreatmentSimulator sim = new AcidTreatmentSimulator();
    sim.setAcidType(AcidTreatmentSimulator.AcidType.HCL);
    sim.setAcidStrengthWtPct(15.0);
    sim.setAcidVolumeM3(1.0);
    sim.setScaleCaCO3Kg(1000.0);
    sim.setInhibitorPresent(true);
    sim.evaluate();
    assertTrue(sim.getScaleDissolvedKg() > 0.0);
    assertTrue(sim.getCO2GeneratedKg() > 0.0);
    // 1 m3 of 15% HCl ≈ 161 kg HCl ≈ 4.42 kmol → 2.21 kmol CaCO3 → ~221 kg
    assertTrue(sim.getScaleDissolvedKg() > 150.0 && sim.getScaleDissolvedKg() < 300.0);
  }

  @Test
  void acidWithoutInhibitorOnCarbonSteelWarns() {
    AcidTreatmentSimulator sim = new AcidTreatmentSimulator();
    sim.setAcidType(AcidTreatmentSimulator.AcidType.HCL);
    sim.setAcidStrengthWtPct(15.0);
    sim.setAcidVolumeM3(1.0);
    sim.setTemperatureCelsius(80.0);
    sim.setTubularMaterial("carbon_steel");
    sim.setInhibitorPresent(false);
    sim.evaluate();
    assertTrue(sim.getWarnings().containsKey("severe_corrosion"));
  }

  @Test
  void h2sScavengerCalculatesDemandAndBreakthrough() {
    H2SScavengerPerformance perf = new H2SScavengerPerformance();
    perf.setChemistry(H2SScavengerPerformance.ScavengerChemistry.MEA_TRIAZINE);
    perf.setActiveWtPct(40.0);
    perf.setGasFlowMSm3PerDay(2.0);
    perf.setH2SInletPpm(50.0);
    perf.setH2STargetPpm(4.0);
    perf.setScavengerInventoryKg(5000.0);
    perf.evaluate();
    assertTrue(perf.getH2SToRemoveKgPerDay() > 0.0);
    assertTrue(perf.getScavengerDemandKgPerDay() > 0.0);
    assertTrue(perf.getBreakthroughDays() > 0.0);
  }

  @Test
  void h2sTriazineHighTemperatureWarns() {
    H2SScavengerPerformance perf = new H2SScavengerPerformance();
    perf.setChemistry(H2SScavengerPerformance.ScavengerChemistry.MEA_TRIAZINE);
    perf.setTemperatureCelsius(95.0);
    perf.setH2SInletPpm(100.0);
    perf.evaluate();
    assertTrue(perf.getWarnings().containsKey("triazine_decomposition"));
  }

  @Test
  void scaleInhibitorMapHasInputsOutputsWarnings() {
    ScaleInhibitorPerformance perf = new ScaleInhibitorPerformance();
    perf.evaluate();
    assertTrue(perf.toMap().containsKey("inputs"));
    assertTrue(perf.toMap().containsKey("outputs"));
    assertTrue(perf.toMap().containsKey("warnings"));
    assertEquals(true, perf.isEvaluated());
    assertFalse(perf.isAdequate());
  }
}
