package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link CompressorDeposit} and its integration with {@link Compressor}.
 */
public class CompressorDepositTest {

  private Compressor buildCompressor(double polytropicEff) {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 18.8);
    gas.addComponent("methane", 0.86);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("nitrogen", 0.02);
    gas.setMixingRule(2);
    Stream feed = new Stream("suction", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(18.8, "bara");
    feed.run();
    Compressor comp = new Compressor("KA03B", feed);
    comp.setOutletPressure(42.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(polytropicEff);
    return comp;
  }

  @Test
  public void testDepositMassToBlockage() {
    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    assertEquals(0.0, dep.getTotalDepositMass(), 1e-9);
    assertEquals(1.0, dep.getEfficiencyMultiplier(), 1e-9);

    dep.addDeposit(DepositMechanism.SULFUR_S8, 5.0);
    dep.addDeposit(DepositMechanism.SALT_NACL, 2.0);
    assertEquals(7.0, dep.getTotalDepositMass(), 1e-9);
    assertTrue(dep.getDepositVolume() > 0.0);
    assertTrue(dep.getAreaBlockageFraction() > 0.0);
    // Deposit reduces both efficiency and head.
    assertTrue(dep.getEfficiencyMultiplier() < 1.0);
    assertTrue(dep.getHeadMultiplier() < 1.0);
  }

  @Test
  public void testWashingRemovesDeposit() {
    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    dep.addDeposit(DepositMechanism.SULFUR_S8, 8.0);
    double before = dep.getTotalDepositMass();
    dep.removeFraction(0.9);
    assertEquals(before * 0.1, dep.getTotalDepositMass(), 1e-9);
    dep.clear();
    assertEquals(0.0, dep.getTotalDepositMass(), 1e-9);
  }

  @Test
  public void testInverseDepositMassForEfficiency() {
    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    double mass = dep.depositMassForEfficiencyMultiplier(DepositMechanism.SULFUR_S8, 0.77);
    assertTrue(mass > 0.0);
    // Applying that mass should reproduce (approximately) the target multiplier.
    dep.addDeposit(DepositMechanism.SULFUR_S8, mass);
    assertEquals(0.77, dep.getEfficiencyMultiplier(), 1e-3);
  }

  @Test
  public void testSizeFromCompressorInletFlow() {
    Compressor comp = buildCompressor(0.60);
    comp.run();
    CompressorDeposit dep = CompressorDeposit.fromCompressor(comp);
    assertTrue(dep.getWettedArea() > 0.0);
    assertTrue(dep.getPassageHalfHeight() > 0.0);
  }

  @Test
  public void testDegradationAppliedInRun() {
    Compressor clean = buildCompressor(0.60);
    clean.run();
    double cleanPower = clean.getPower();
    double cleanEff = clean.getPolytropicEfficiency();

    Compressor fouled = buildCompressor(0.60);
    CompressorDeposit dep = CompressorDeposit.fromCompressor(fouled);
    dep.addDeposit(DepositMechanism.SULFUR_S8, 3.0);
    fouled.setDepositModel(dep);
    fouled.run();

    // Degraded compressor has lower efficiency and needs more power for the same duty.
    assertTrue(fouled.getPolytropicEfficiency() < cleanEff);
    assertTrue(fouled.getPower() > cleanPower);
    assertTrue(fouled.getDegradationFactor() < 1.0);
    assertNotNull(fouled.getDepositModel());

    // Re-running must not compound the degradation.
    double firstEff = fouled.getPolytropicEfficiency();
    fouled.run();
    assertEquals(firstEff, fouled.getPolytropicEfficiency(), 1e-9);
  }

  @Test
  public void testRemovingDepositModelRestoresCleanEfficiency() {
    Compressor comp = buildCompressor(0.60);
    CompressorDeposit dep = CompressorDeposit.fromCompressor(comp);
    dep.addDeposit(DepositMechanism.SULFUR_S8, 3.0);
    comp.setDepositModel(dep);
    comp.run();
    assertTrue(comp.getPolytropicEfficiency() < 0.60);

    comp.setDepositModel(null);
    comp.setPolytropicEfficiency(0.60);
    comp.run();
    assertEquals(0.60, comp.getPolytropicEfficiency(), 1e-9);
    assertEquals(1.0, comp.getDegradationFactor(), 1e-9);
  }

  @Test
  public void testEntrainedSaltDepositSourceMassBalance() {
    // 10 kg/hr entrained brine water at 5 wt% salt, all evaporating -> 0.5 kg/hr salt.
    EntrainedSaltDepositSource salt = new EntrainedSaltDepositSource(10.0, 0.05);
    assertEquals(0.5, salt.getDepositRate("kg/hr"), 1e-9);
    assertEquals(12.0, salt.getDepositRate("kg/day"), 1e-9);
    assertEquals(DepositMechanism.SALT_NACL, salt.getMechanism());

    // Half the water evaporates and half the salt sticks -> 0.125 kg/hr.
    salt.setEvaporatedFraction(0.5);
    salt.setCaptureFraction(0.5);
    assertEquals(0.125, salt.getDepositRate("kg/hr"), 1e-9);

    // Accumulate into a deposit model over 30 days.
    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    double mass = dep.accumulate(salt, 30.0 * 24.0);
    assertEquals(0.125 * 30.0 * 24.0, mass, 1e-6);
    assertEquals(mass, dep.getDepositMass(DepositMechanism.SALT_NACL), 1e-6);
  }

  @Test
  public void testSolidFlashDepositSourceS8() {
    // Gas carrying free S8 above its solubility -> solid drops out on flash.
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 40.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("H2S", 0.001);
    gas.addComponent("S8", 1.0e-4);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);
    Stream feed = new Stream("s8gas", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(40.0, "bara");
    feed.run();

    SolidFlashDepositSource s8 = new SolidFlashDepositSource(feed, "S8", DepositMechanism.SULFUR_S8, 0.3);
    double rate = s8.getDepositRate("kg/hr");
    double precip = s8.getPrecipitationRate("kg/hr");
    // Free S8 far above its (mg/Sm3-level) solubility must drop out as solid.
    assertTrue(precip > 0.0);
    // Deposition rate is the capture-scaled precipitation rate and cannot exceed the feed.
    assertEquals(precip * 0.3, rate, 1e-9);
    assertTrue(rate < feed.getFlowRate("kg/hr"));

    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    double mass = dep.accumulate(s8, 24.0);
    assertEquals(rate * 24.0, mass, 1e-6);
  }

  @Test
  public void testSolidFlashUsesWarmShaftTemperatureAndTracksCondensateEvaporation() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 5.0, 40.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("n-heptane", 0.299);
    fluid.addComponent("S8", 0.001);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("entrained condensate and sulfur", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("warm shaft compressor", feed);
    CompressorThermalModel thermalModel = new CompressorThermalModel("measured local temperatures");
    thermalModel.addNode(new CompressorThermalNode(CompressorThermalModel.INLET_SHAFT,
        CompressorThermalNode.NodeType.SHAFT, 373.15, 0.0, true));
    compressor.setThermalModel(thermalModel);

    SolidFlashDepositSource source = new SolidFlashDepositSource(feed, "S8", DepositMechanism.SULFUR_S8, 1.0);
    source.setThermalNode(compressor, CompressorThermalModel.INLET_SHAFT);
    source.getPrecipitationRate("kg/hr");

    assertEquals(100.0, source.getLastEvaluationTemperatureC(), 1.0e-10);
    assertEquals(40.0, source.getLastEvaluationPressureBara(), 1.0e-10);
    assertTrue(source.getLastLiquidEvaporatedFraction() > 0.0,
        "warmer shaft conditions should evaporate some of the inlet condensate");
  }

  @Test
  public void testDepositLocationAcrossImpellers() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 40.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("H2S", 0.001);
    gas.addComponent("S8", 1.0e-4);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);
    Stream feed = new Stream("s8gas", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(40.0, "bara");
    feed.run();

    // 5-impeller compression 40 -> 120 bara, discharge ~120 C.
    java.util.List<CompressorDepositProfile.StageDeposit> profile = CompressorDepositProfile.compute(feed, 120.0, 120.0,
        5, "S8");
    assertEquals(5, profile.size());

    // Temperature and pressure rise monotonically stage to stage.
    for (int i = 1; i < profile.size(); i++) {
      assertTrue(profile.get(i).getTemperatureC() > profile.get(i - 1).getTemperatureC());
      assertTrue(profile.get(i).getPressureBara() > profile.get(i - 1).getPressureBara());
    }
    // With a monotonically rising temperature, S8 solubility increases, so the solid drop-out is
    // largest at the cold first impeller (deposits concentrate at the suction stage).
    assertTrue(profile.get(0).getSolidRateKgHr() >= profile.get(4).getSolidRateKgHr());
    assertEquals(1, CompressorDepositProfile.worstStage(profile));
  }

  @Test
  public void testDegradedChartScalesHeadAndEfficiency() {
    CompressorChart chart = new CompressorChart();
    double[] chartConditions = new double[] { 1.0, 1.0, 1.0, 1.0 };
    double[] speed = new double[] { 1000.0, 1500.0 };
    double[][] flow = new double[][] { { 500.0, 700.0, 900.0 }, { 600.0, 800.0, 1000.0 } };
    double[][] head = new double[][] { { 100.0, 90.0, 75.0 }, { 150.0, 135.0, 110.0 } };
    double[][] polyEff = new double[][] { { 78.0, 80.0, 76.0 }, { 77.0, 79.0, 75.0 } };
    chart.setCurves(chartConditions, speed, flow, head, polyEff);
    chart.setHeadUnit("kJ/kg");
    chart.setUseCompressorChart(true);

    double cleanHead = chart.getPolytropicHead(700.0, 1000.0);
    double cleanEff = chart.getPolytropicEfficiency(700.0, 1000.0);

    CompressorChart degraded = chart.getDegradedChart(0.9, 0.8);
    double degHead = degraded.getPolytropicHead(700.0, 1000.0);
    double degEff = degraded.getPolytropicEfficiency(700.0, 1000.0);

    assertEquals(cleanHead * 0.9, degHead, Math.abs(cleanHead) * 1e-6 + 1e-6);
    assertEquals(cleanEff * 0.8, degEff, Math.abs(cleanEff) * 1e-6 + 1e-6);
  }

  @Test
  public void testBuildDegradedChartFromDepositAfterOperatingHours() {
    Compressor comp = buildCompressor(0.60);
    CompressorChart chart = new CompressorChart();
    double[] chartConditions = new double[] { 1.0, 1.0, 1.0, 1.0 };
    double[] speed = new double[] { 1000.0, 1500.0 };
    double[][] flow = new double[][] { { 500.0, 700.0, 900.0 }, { 600.0, 800.0, 1000.0 } };
    double[][] head = new double[][] { { 100.0, 90.0, 75.0 }, { 150.0, 135.0, 110.0 } };
    double[][] polyEff = new double[][] { { 78.0, 80.0, 76.0 }, { 77.0, 79.0, 75.0 } };
    chart.setCurves(chartConditions, speed, flow, head, polyEff);
    chart.setHeadUnit("kJ/kg");
    chart.setUseCompressorChart(true);
    comp.setCompressorChart(chart);

    // No deposit model yet -> no degraded chart.
    assertNull(comp.buildDegradedChart());

    // Accumulate a salt deposit over 500 operating hours, then build the degraded chart.
    CompressorDeposit dep = CompressorDeposit.fromCompressor(comp);
    EntrainedSaltDepositSource salt = new EntrainedSaltDepositSource(5.0, 0.05);
    dep.accumulate(salt, 500.0);
    comp.setDepositModel(dep);

    assertTrue(dep.getTotalDepositMass() > 0.0);
    CompressorChart degraded = comp.buildDegradedChart();
    assertNotNull(degraded);
    // Degraded head and efficiency are below the clean chart at the same operating point.
    assertTrue(degraded.getPolytropicHead(700.0, 1000.0) < chart.getPolytropicHead(700.0, 1000.0));
    assertTrue(degraded.getPolytropicEfficiency(700.0, 1000.0) <= chart.getPolytropicEfficiency(700.0, 1000.0));
  }

  @Test
  public void testStageProfileFromPropertyProfileUsesRealStates() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 40.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("H2S", 0.001);
    gas.addComponent("S8", 1.0e-4);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);
    Stream feed = new Stream("s8gas", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(40.0, "bara");
    feed.run();

    Compressor comp = new Compressor("stagecomp", feed);
    comp.setOutletPressure(120.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.75);
    comp.setPolytropicMethod("detailed");
    comp.getPropertyProfile().setActive(true);
    comp.run();

    java.util.List<CompressorDepositProfile.StageDeposit> profile = CompressorDepositProfile
        .computeFromPropertyProfile(comp, 5, "S8");
    assertEquals(5, profile.size());
    // Rigorous per-step states: pressure rises monotonically to discharge.
    for (int i = 1; i < profile.size(); i++) {
      assertTrue(profile.get(i).getPressureBara() > profile.get(i - 1).getPressureBara());
    }
    // Last stage at (or near) the discharge pressure.
    assertTrue(profile.get(4).getPressureBara() > 100.0);
    // Rising temperature -> S8 solubility rises -> most solid at the cold first impeller.
    assertTrue(profile.get(0).getSolidRateKgHr() >= profile.get(4).getSolidRateKgHr());
  }

  @Test
  public void testOnlineWashRemovesMatchingDepositOnly() {
    // Mixed fouling: salt + elemental sulfur.
    CompressorDeposit dep = new CompressorDeposit(0.6, 0.02);
    dep.addDeposit(DepositMechanism.SALT_NACL, 4.0);
    dep.addDeposit(DepositMechanism.SULFUR_S8, 3.0);
    double effFouled = dep.getEfficiencyMultiplier();

    // Water dissolves salt but NOT sulfur.
    CompressorDepositWash washer = new CompressorDepositWash();
    washer.setContactEfficiency(1.0);
    CompressorDepositWash.WashResult waterWash = washer.wash(dep, WashFluid.WATER, 200.0, 2.0);
    assertTrue(waterWash.getTotalRemovedKg() > 0.0);
    assertTrue(dep.getDepositMass(DepositMechanism.SALT_NACL) < 4.0); // salt reduced
    assertEquals(3.0, dep.getDepositMass(DepositMechanism.SULFUR_S8), 1e-9); // sulfur untouched
    assertTrue(waterWash.getEfficiencyMultiplierAfter() > effFouled); // performance recovered

    // Xylene then dissolves the remaining sulfur.
    double effBeforeXylene = dep.getEfficiencyMultiplier();
    CompressorDepositWash.WashResult xyleneWash = washer.wash(dep, WashFluid.XYLENE, 200.0, 5.0);
    assertTrue(dep.getDepositMass(DepositMechanism.SULFUR_S8) < 3.0);
    assertTrue(xyleneWash.getEfficiencyMultiplierAfter() > effBeforeXylene);
  }

  @Test
  public void testWashFluidRecommendationAndPlanning() {
    // Salt-dominated deposit -> water recommended.
    CompressorDeposit saltDep = new CompressorDeposit(0.6, 0.02);
    saltDep.addDeposit(DepositMechanism.SALT_NACL, 5.0);
    assertEquals(WashFluid.WATER, CompressorDepositWash.recommend(saltDep));

    // Sulfur-dominated deposit -> aromatic solvent recommended (xylene or toluene).
    CompressorDeposit s8Dep = new CompressorDeposit(0.6, 0.02);
    s8Dep.addDeposit(DepositMechanism.SULFUR_S8, 5.0);
    WashFluid rec = CompressorDepositWash.recommend(s8Dep);
    assertTrue(rec == WashFluid.XYLENE || rec == WashFluid.TOLUENE);

    // Planning: required rate to remove 4 kg salt in 2 hours, then verify it works.
    CompressorDepositWash washer = new CompressorDepositWash();
    double rate = washer.requiredFluidRateKgHr(saltDep, WashFluid.WATER, 4.0, 2.0);
    assertTrue(rate > 0.0 && Double.isFinite(rate));
    CompressorDepositWash.WashResult r = washer.wash(saltDep, WashFluid.WATER, rate, 2.0);
    assertEquals(4.0, r.getTotalRemovedKg(), 1e-6);
  }

  @Test
  public void testCompressorWashOnlineConvenience() {
    Compressor comp = buildCompressor(0.60);
    CompressorDeposit dep = CompressorDeposit.fromCompressor(comp);
    dep.addDeposit(DepositMechanism.SALT_NACL, 2.0);
    comp.setDepositModel(dep);
    comp.run();
    double fouledPower = comp.getPower();

    // Recommend and wash online, then re-run: power should drop back toward clean.
    WashFluid fluid = CompressorDepositWash.recommend(dep);
    assertEquals(WashFluid.WATER, fluid);
    CompressorDepositWash.WashResult result = comp.washOnline(fluid, 300.0, 3.0);
    assertNotNull(result);
    assertTrue(result.getTotalRemovedKg() > 0.0);
    comp.run();
    assertTrue(comp.getPower() <= fouledPower);
  }
}
