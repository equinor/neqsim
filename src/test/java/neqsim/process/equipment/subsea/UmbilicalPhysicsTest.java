package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.subsea.UmbilicalElectricalCalculator;
import neqsim.process.mechanicaldesign.subsea.UmbilicalHydraulicsCalculator;
import neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign;
import neqsim.process.mechanicaldesign.subsea.UmbilicalStructuralCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Physics tests for the umbilical hydraulics, electrical, structural and design calculations.
 *
 * @author ESOL
 * @version 1.0
 */
public class UmbilicalPhysicsTest {

  /**
   * Build a typical electro-hydraulic steel tube umbilical.
   *
   * @param depthM water depth in m
   * @param lengthM length in m
   * @return umbilical
   */
  private Umbilical typicalUmbilical(double depthM, double lengthM) {
    Umbilical u = new Umbilical("Test umbilical");
    u.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
    u.setLength(lengthM);
    u.setWaterDepth(depthM);
    u.addHydraulicLine("LP Hydraulic", 12.7, 345.0);
    u.addHydraulicLine("HP Hydraulic", 9.5, 690.0);
    u.addChemicalLine("MEG", 25.4, 345.0);
    u.addElectricalCable("Power", 3, 6600.0, 35.0);
    u.addFiberOptic("Control", 12);
    return u;
  }

  @Test
  void laminarFrictionMatchesPoiseuille() {
    UmbilicalFluid fluid = UmbilicalFluid.waterGlycolHydraulic();
    double q = 0.05e-3 / 60.0; // 0.05 l/min
    double tK = 277.15;
    UmbilicalHydraulicsCalculator.LineResult r = UmbilicalHydraulicsCalculator.calculate(fluid, 9.5, 25000.0, q, tK,
        0.002, 0.0);
    double mu = fluid.getViscosityPas(tK);
    double d = 9.5e-3;
    double expectedBar = 128.0 * mu * 25000.0 * q / (Math.PI * Math.pow(d, 4)) / 1.0e5;
    assertEquals("laminar", r.getRegime());
    assertEquals(expectedBar, r.getFrictionDropBar(), 1.0e-6 * expectedBar);
  }

  @Test
  void hydrostaticHeadAndSupplyPressure() {
    UmbilicalFluid water = UmbilicalFluid.water();
    UmbilicalHydraulicsCalculator.LineResult r = UmbilicalHydraulicsCalculator.calculate(water, 20.0, 1000.0, 0.0,
        293.15, 0.002, 1000.0);
    assertEquals(1000.0 * 9.80665 * 1000.0 / 1.0e5, r.getHydrostaticBar(), 1.0e-9);
    assertEquals(0.0, r.getFrictionDropBar(), 1.0e-12);
    // Supply needs less pressure than delivery when delivery is below the supply
    assertTrue(r.getRequiredSupplyPressureBara(300.0) < 300.0);
  }

  @Test
  void turbulentFrictionIsHigherThanLaminarExtrapolation() {
    UmbilicalFluid methanol = UmbilicalFluid.methanol();
    UmbilicalHydraulicsCalculator.LineResult r = UmbilicalHydraulicsCalculator.calculate(methanol, 25.4, 10000.0,
        10.0 / 3600.0, 277.15, 0.002, 0.0);
    assertEquals("turbulent", r.getRegime());
    double laminar = 64.0 / r.getReynolds();
    assertTrue(r.getFrictionFactor() > laminar);
    assertTrue(r.getFrictionFactor() > 0.01 && r.getFrictionFactor() < 0.05);
  }

  @Test
  void flowForFrictionDropInvertsCalculate() {
    UmbilicalFluid fluid = UmbilicalFluid.meg();
    double q = UmbilicalHydraulicsCalculator.flowForFrictionDrop(fluid, 25.4, 20000.0, 100.0, 277.15, 0.002);
    double dp = UmbilicalHydraulicsCalculator.calculate(fluid, 25.4, 20000.0, q, 277.15, 0.002, 0.0)
        .getFrictionDropBar();
    assertEquals(100.0, dp, 1.0e-6);
  }

  @Test
  void stepResponseScalesWithLengthSquaredAndMatchesDiffusionSeries() {
    UmbilicalFluid fluid = UmbilicalFluid.waterGlycolHydraulic();
    UmbilicalHydraulicsCalculator.ResponseResult a = UmbilicalHydraulicsCalculator.stepResponse(fluid, 9.5, 1.2,
        200.0e9, 10000.0, 277.15, 0.9);
    UmbilicalHydraulicsCalculator.ResponseResult b = UmbilicalHydraulicsCalculator.stepResponse(fluid, 9.5, 1.2,
        200.0e9, 20000.0, 277.15, 0.9);
    assertTrue(a.isDiffusionDominated());
    assertEquals(4.0, b.getTimeToFractionS() / a.getTimeToFractionS(), 0.01);
    // 90 % of the step: 1 - 4/pi exp(-theta) = 0.9 gives theta = 2.54
    assertEquals(2.54, a.getTimeToFractionS() / a.getTimeConstantS(), 0.05);
    // Acoustic velocity is below sqrt(K/rho) because the tube wall is compliant
    double cLiquid = Math.sqrt(fluid.getBulkModulusPa() / fluid.getDensityKgM3());
    assertTrue(a.getAcousticVelocityMs() < cLiquid && a.getAcousticVelocityMs() > 0.8 * cLiquid);
    assertTrue(b.getTimeToFractionS() > b.getTransitTimeS());
  }

  @Test
  void neqsimFluidGivesLiquidProperties() {
    SystemInterface water = new SystemSrkCPAstatoil(277.15, 200.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    UmbilicalFluid fluid = UmbilicalFluid.fromNeqSim("NeqSim water", water, 277.15, 200.0);
    assertEquals(1000.0, fluid.getDensityKgM3(), 60.0);
    assertTrue(fluid.getBulkModulusPa() > 0.5e9 && fluid.getBulkModulusPa() < 6.0e9);
    assertTrue(fluid.getViscosityPas(277.15) > 0.0);
  }

  @Test
  void burstWallMatchesClosedForm() {
    double t = UmbilicalStructuralCalculator.burstWallThicknessMm(34.5, 12.7, 550.0, 0.6);
    double expected = 34.5 * 12.7 / (2.0 * (330.0 + 0.4 * 34.5 - 34.5));
    assertEquals(expected, t, 1.0e-9);
  }

  @Test
  void collapsePressureSatisfiesDnvEquationAndBounds() {
    double od = 30.0;
    double t = 2.0;
    double e = 200.0e3;
    double pc = UmbilicalStructuralCalculator.collapsePressureMPa(od, t, 550.0, e, 0.005);
    double pel = 2.0 * e * Math.pow(t / od, 3) / (1.0 - 0.09);
    double pp = 550.0 * 2.0 * t / od;
    double residual = (pc - pel) * (pc * pc - pp * pp) - pc * pel * pp * 0.005 * od / t;
    assertEquals(0.0, residual, 1.0e-6 * pel * pp * pp);
    assertTrue(pc < Math.min(pel, pp));
    double thicker = UmbilicalStructuralCalculator.collapsePressureMPa(od + 2.0, t + 1.0, 550.0, e, 0.005);
    assertTrue(thicker > pc);
    double req = UmbilicalStructuralCalculator.collapseWallThicknessMm(20.0, 25.4, 550.0, e, 0.005, 1.5);
    double check = UmbilicalStructuralCalculator.collapsePressureMPa(25.4 + 2.0 * req, req, 550.0, e, 0.005);
    assertEquals(30.0, check, 0.01);
  }

  @Test
  void catenaryIsGeometricallyConsistent() {
    double w = 150.0;
    double h = 450.0;
    double x = 225.0;
    UmbilicalStructuralCalculator.CatenaryResult c = UmbilicalStructuralCalculator.freeHangingCatenary(w, h, x);
    double a = c.getTouchdownBendRadiusM();
    assertEquals(w * (a + h) / 1000.0, c.getTopTensionKN(), 1.0e-9);
    assertEquals(x,
        a * Math.log(c.getSuspendedLengthM() / a + Math.sqrt(Math.pow(c.getSuspendedLengthM() / a, 2) + 1.0)), 1.0e-6);
    assertTrue(c.getTopTensionKN() > w * h / 1000.0);

    UmbilicalStructuralCalculator.CatenaryResult vertical = UmbilicalStructuralCalculator.freeHangingCatenary(w, h,
        1.0e-3);
    assertEquals(w * h / 1000.0, vertical.getTopTensionKN(), 0.01 * w * h / 1000.0);
    assertThrows(IllegalArgumentException.class, () -> UmbilicalStructuralCalculator.freeHangingCatenary(-1.0, h, x));
  }

  @Test
  void armorCapacityAndWireCount() {
    int n = UmbilicalStructuralCalculator.armorWireCount(74.0, 4.0, 15.0);
    assertEquals((int) Math.floor(Math.PI * 74.0 * Math.cos(Math.toRadians(15.0)) / 4.0), n);
    double one = UmbilicalStructuralCalculator.armorCapacityKN(70.0, 4.0, 15.0, 1, 560.0);
    double two = UmbilicalStructuralCalculator.armorCapacityKN(70.0, 4.0, 15.0, 2, 560.0);
    assertTrue(two > 1.9 * one);
    assertEquals(0.0, UmbilicalStructuralCalculator.armorCapacityKN(70.0, 4.0, 15.0, 0, 560.0), 0.0);
  }

  @Test
  void conductorResistanceAndSkinEffect() {
    double r = UmbilicalElectricalCalculator.conductorResistancePerMeter(35.0, 20.0);
    assertEquals(0.4926e-3, r, 0.002e-3);
    assertTrue(UmbilicalElectricalCalculator.conductorResistancePerMeter(35.0, 90.0) > r);
    double ys50 = UmbilicalElectricalCalculator.skinEffectFactor(r, 50.0);
    double ys500 = UmbilicalElectricalCalculator.skinEffectFactor(r, 500.0);
    assertTrue(ys50 >= 0.0 && ys50 < 0.01);
    assertTrue(ys500 > ys50);
  }

  @Test
  void shortCableMatchesLumpedApproximation() {
    UmbilicalElectricalCalculator.PowerResult r = UmbilicalElectricalCalculator.threePhase(95.0, 5.5, 2.5, 12000.0,
        1000.0, 50.0, 90.0, 2.0, 0.9);
    double current = r.getLoadCurrentA();
    double sinPhi = Math.sqrt(1.0 - 0.81);
    double dropV = Math.sqrt(3.0) * current * (r.getAcResistanceOhmPerKm() * 0.9 + r.getReactanceOhmPerKm() * sinPhi);
    double dropPct = dropV / 12000.0 * 100.0;
    assertEquals(dropPct, r.getVoltageDropPercent(), 0.1 * dropPct + 0.02);
    double lossLumped = 3.0 * current * current * r.getAcResistanceOhmPerKm() / 1.0e6;
    assertEquals(lossLumped, r.getLossMW(), 0.05 * lossLumped);
  }

  @Test
  void longCableChargingCurrentRaisesReceivingVoltageAtNoLoad() {
    UmbilicalElectricalCalculator.PowerResult shortCable = UmbilicalElectricalCalculator.threePhase(95.0, 8.0, 2.5,
        36000.0, 20000.0, 50.0, 90.0, 0.0, 0.9);
    UmbilicalElectricalCalculator.PowerResult longCable = UmbilicalElectricalCalculator.threePhase(95.0, 8.0, 2.5,
        36000.0, 80000.0, 50.0, 90.0, 0.0, 0.9);
    assertTrue(longCable.getChargingCurrentA() > 3.9 * shortCable.getChargingCurrentA());
    assertTrue(longCable.getNoLoadRisePercent() > shortCable.getNoLoadRisePercent());
    assertTrue(shortCable.getNoLoadRisePercent() > 0.0);
    double omega = 2.0 * Math.PI * 50.0;
    double expected = omega * longCable.getCapacitanceUfPerKm() * 1.0e-6 * 80.0 * 36000.0 / Math.sqrt(3.0);
    assertEquals(expected, longCable.getChargingCurrentA(), 1.0e-6 * expected);
  }

  @Test
  void umbilicalGeometryAndWeightsAreConsistent() {
    Umbilical u = typicalUmbilical(450.0, 25000.0);
    u.run(null);
    double od = u.getOverallDiameterMm();
    assertTrue(od > 60.0 && od < 200.0, "OD " + od);
    assertTrue(u.getDryWeightPerMeter() > 8.0 && u.getDryWeightPerMeter() < 40.0, "dry " + u.getDryWeightPerMeter());
    assertTrue(u.getFilledWeightPerMeter() > u.getDryWeightPerMeter());
    double displaced = Math.PI / 4.0 * od * od * 1.0e-6 * 1025.0;
    assertEquals(u.getFilledWeightPerMeter() - displaced, u.getSubmergedWeightPerMeter(), 1.0e-9);
    assertTrue(u.getSubmergedWeightPerMeter() > 0.0);

    double before = u.getDryWeightPerMeter();
    u.setArmorLayerCount(u.getArmorLayerCount() + 1);
    u.run(null);
    assertTrue(u.getDryWeightPerMeter() > before);
    assertTrue(u.getOverallDiameterMm() > od);

    for (Umbilical.UmbilicalElement e : u.getElements()) {
      if (e.isFluidLine()) {
        assertTrue(e.getWallThicknessMm() >= 1.0);
        assertTrue(e.getOuterDiameterMm() > e.getInnerDiameterMm());
      }
    }
  }

  @Test
  void deepWaterCollapseGovernsTubeWall() {
    Umbilical u = new Umbilical("Deep");
    u.setWaterDepth(3000.0);
    u.setLength(30000.0);
    u.addChemicalLine("MEG", 25.4, 150.0);
    u.run(null);
    double burst = UmbilicalStructuralCalculator.burstWallThicknessMm(15.0, 25.4, 550.0, 0.6);
    double wall = u.findElement("MEG").getWallThicknessMm();
    assertTrue(wall > 2.0 * burst, "collapse should govern, wall " + wall);
    UmbilicalMechanicalDesign design = u.getMechanicalDesign();
    design.calcDesign();
    assertTrue(design.getMaxCollapseUtilization() <= 1.0 + 1.0e-6);
  }

  @Test
  void lineHydraulicsApiUsesDesignFlowAndFluid() {
    Umbilical u = typicalUmbilical(450.0, 25000.0);
    u.setInjectionRate("MEG", 1.0, "m3/hr");
    double dp = u.calculatePressureDrop("MEG");
    UmbilicalFluid meg = UmbilicalFluid.meg();
    double q = 1.0 / 3600.0;
    double expected = 128.0 * meg.getViscosityPas(277.15) * 25000.0 * q / (Math.PI * Math.pow(0.0254, 4)) / 1.0e5;
    assertEquals(expected, dp, 1.0e-6 * expected);
    double maxFlow = u.calculateMaxFlowM3PerHr("MEG", dp);
    assertEquals(1.0, maxFlow, 1.0e-6);
    assertThrows(IllegalArgumentException.class, () -> u.calculatePressureDrop("Fibre"));
    assertThrows(IllegalArgumentException.class, () -> u.setInjectionRate("MEG", 1.0, "bbl/d"));
    UmbilicalHydraulicsCalculator.ResponseResult resp = u.calculateLineResponse("HP Hydraulic", 0.9);
    assertTrue(resp.getTimeToFractionS() > resp.getTransitTimeS());
  }

  @Test
  void powerTransmissionApi() {
    Umbilical u = typicalUmbilical(450.0, 25000.0);
    u.setPowerLoad(1.5);
    u.setPowerFactor(0.9);
    UmbilicalElectricalCalculator.PowerResult r = u.calculatePowerTransmission();
    assertEquals(u.calculatePowerLoss(), r.getLossMW(), 1.0e-12);
    assertTrue(r.getLossMW() > 0.0);
    assertTrue(r.getVoltageDropPercent() > 0.0);
    double drop = u.calculateControlLoopVoltageDrop("Power", 1.0);
    assertEquals(2.0 * 25000.0 * UmbilicalElectricalCalculator.conductorResistancePerMeter(35.0, 90.0), drop, 1.0e-9);

    Umbilical noCable = new Umbilical("none");
    assertThrows(IllegalStateException.class, noCable::calculatePowerTransmission);
  }

  @Test
  void mechanicalDesignSelectsArmorFromTension() {
    Umbilical u = typicalUmbilical(450.0, 25000.0);
    u.setPowerLoad(1.0);
    UmbilicalMechanicalDesign design = u.getMechanicalDesign();
    design.calcDesign();
    assertTrue(design.getRequiredArmorLayers() >= 1);
    assertTrue(design.getStaticTopTensionKN() > u.getSubmergedWeightPerMeter() * 9.80665 * 450.0 / 1000.0);
    assertEquals(design.getStaticTopTensionKN() * u.getTensionAmplificationFactor(), design.getDesignTopTensionKN(),
        1.0e-9);
    assertTrue(design.getMaxAllowableTension() >= design.getDesignTopTensionKN());
    assertTrue(design.isDesignAcceptable(), design.getDesignChecks().toString());
    assertTrue(design.getTouchdownBendRadiusM() > u.getMinimumBendRadius());
    assertTrue(design.getPowerResult() != null);

    String json = design.toJson();
    assertTrue(json.contains("elementResults") && json.contains("designChecks") && json.contains("powerTransmission")
        && json.contains("crossSection") && json.contains("hydraulicCurves") && json.contains("catenaryProfile")
        && json.contains("powerSweep"));
  }

  @Test
  void frictionBudgetCheckFlagsUndersizedChemicalLine() {
    Umbilical u = typicalUmbilical(450.0, 25000.0);
    u.setInjectionRate("MEG", 1.0, "m3/hr");
    UmbilicalMechanicalDesign design = u.getMechanicalDesign();
    design.calcDesign();
    // 1 m3/h of cold MEG through 25 km of 25.4 mm tube is about 300 bar: above half of 345 bar
    assertFalse(design.getDesignChecks().get("lineFrictionDropWithinPressureBudget"));
    assertFalse(design.isDesignAcceptable());

    u.setInjectionRate("MEG", 0.2, "m3/hr");
    design.calcDesign();
    assertTrue(design.getDesignChecks().get("lineFrictionDropWithinPressureBudget"));
  }

  @Test
  void docQuickStartRuns() {
    Umbilical umbilical = new Umbilical("Control umbilical");
    umbilical.setLength(15000.0);
    umbilical.setWaterDepth(100.0);
    umbilical.addHydraulicLine("LP hydraulic supply", 9.5, 345.0);
    umbilical.addHydraulicLine("HP hydraulic supply", 6.35, 690.0);
    umbilical.addChemicalLine("MEG", 25.4, 345.0);
    umbilical.addElectricalCable("Power", 3, 3300.0, 16.0);
    umbilical.addFiberOptic("Fibre", 12);
    umbilical.setInjectionRate("MEG", 0.5, "m3/hr");
    umbilical.setPowerLoad(0.05);

    double megDropBar = umbilical.calculatePressureDrop("MEG");
    double response90 = umbilical.calculateLineResponse("LP hydraulic supply", 0.9).getTimeToFractionS();
    double voltageDropPercent = umbilical.calculateVoltageDrop();
    UmbilicalMechanicalDesign design = umbilical.getMechanicalDesign();
    design.calcDesign();

    assertTrue(megDropBar > 50.0 && megDropBar < 172.5, "MEG drop " + megDropBar);
    assertTrue(response90 > 60.0 && response90 < 3600.0, "response " + response90);
    assertTrue(voltageDropPercent > 0.0);
    assertTrue(design.isDesignAcceptable(), design.getDesignChecks().toString());
    assertTrue(design.toJson().contains("hydraulicCurves"));
  }

  @Test
  void heavierLoadingNeedsMoreArmor() {
    Umbilical shallow = typicalUmbilical(300.0, 10000.0);
    Umbilical deep = typicalUmbilical(2500.0, 10000.0);
    shallow.getMechanicalDesign().calcDesign();
    deep.getMechanicalDesign().calcDesign();
    assertTrue(deep.getMechanicalDesign().getDesignTopTensionKN() > 3.0
        * shallow.getMechanicalDesign().getDesignTopTensionKN());
    assertTrue(
        deep.getMechanicalDesign().getRequiredArmorLayers() >= shallow.getMechanicalDesign().getRequiredArmorLayers());
  }
}
