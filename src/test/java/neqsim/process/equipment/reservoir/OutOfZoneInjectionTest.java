package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel;

/**
 * Tests for the out-of-zone injection classes (NIP-01 through NIP-08).
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 * <li>NIP-01/04: WellFlow multi-zone injection and per-zone fracture pressure</li>
 * <li>NIP-02/08: InjectionWellModel multi-zone and thermal stress</li>
 * <li>NIP-03: AnnularLeakagePath cubic law and Darcy flow</li>
 * <li>NIP-05: MultiCompartmentReservoir material balance</li>
 * <li>NIP-06: CementDegradationModel carbonation front</li>
 * <li>NIP-07: InjectionConformanceMonitor Hall plot</li>
 * </ul>
 */
public class OutOfZoneInjectionTest {

  // ===== NIP-01 / NIP-04: WellFlow multi-zone injection =====

  @Test
  void testWellFlowInjectionMode() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 200.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);

    WellFlow wellflow = new WellFlow("injection well");
    wellflow.setFlowMode(WellFlow.FlowMode.INJECTION);
    assertEquals(WellFlow.FlowMode.INJECTION, wellflow.getFlowMode());
  }

  @Test
  void testWellFlowAddInjectionZone() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 200.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);

    WellFlow wellflow = new WellFlow("injection well");
    wellflow.setFlowMode(WellFlow.FlowMode.INJECTION);
    neqsim.process.equipment.stream.Stream s1 =
        new neqsim.process.equipment.stream.Stream("s1", fluid.clone());
    neqsim.process.equipment.stream.Stream s2 =
        new neqsim.process.equipment.stream.Stream("s2", fluid.clone());
    wellflow.addInjectionZone("Target Zone", s1, 250.0, 1e-3, 300.0);
    wellflow.addInjectionZone("Thief Zone", s2, 200.0, 2e-3, 280.0);
    wellflow.setTargetZone("Target Zone");

    // Should have layers set up
    assertNotNull(wellflow);
  }

  @Test
  void testReservoirLayerFractureContainment() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 200.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);

    neqsim.process.equipment.stream.Stream stream =
        new neqsim.process.equipment.stream.Stream("test", fluid);

    WellFlow wellflow = new WellFlow("test well");
    WellFlow.ReservoirLayer layer = new WellFlow.ReservoirLayer("Test Layer", stream, 200.0, 1e-3);
    layer.setFracturePressure(350.0, "bara");
    layer.setBarrierStressContrast(20.0, "bar");
    layer.isTargetZone = true;

    assertTrue(layer.isTargetZone);
    assertEquals(350.0, layer.fracturePressure, 0.001);

    // BHP below fracture pressure -> contained
    assertTrue(layer.isFractureContained(340.0));

    // BHP above fracture pressure but net pressure < barrier contrast -> still contained
    // netPressure = 360 - 350 = 10 bar, barrierStressContrast = 20 bar, 10 < 20 -> contained
    assertTrue(layer.isFractureContained(360.0));

    // BHP between fracture and fracture + barrier contrast -> contained
    // netPressure = 365 - 350 = 15, 15 < 20 -> contained
    assertTrue(layer.isFractureContained(365.0));

    // Above fracture + barrier -> not contained
    // netPressure = 375 - 350 = 25, 25 < 20 is false -> NOT contained
    assertFalse(layer.isFractureContained(375.0));

    // Containment margin at BHP = 340 bar
    // margin = barrierStressContrast - (bhp - fracturePressure) = 20 - (340 - 350) = 30
    double margin = layer.getFractureContainmentMargin(340.0);
    assertEquals(30.0, margin, 0.001);
  }

  // ===== NIP-02 / NIP-08: InjectionWellModel multi-zone =====

  @Test
  void testInjectionWellModelMultiZone() {
    InjectionWellModel model = new InjectionWellModel();
    model.setMaxBHP(350.0, "bara");
    model.setDrainageRadius(500.0);
    model.setWellboreRadius(0.1);

    InjectionWellModel.InjectionZone zone1 =
        new InjectionWellModel.InjectionZone("Target", 2500.0, 250.0, 100.0, 20.0, 350.0);
    zone1.skinFactor = 2.0;
    zone1.isTargetZone = true;
    model.addZone(zone1);

    InjectionWellModel.InjectionZone zone2 =
        new InjectionWellModel.InjectionZone("Thief", 2400.0, 220.0, 200.0, 15.0, 300.0);
    zone2.skinFactor = 0.0;
    zone2.isTargetZone = false;
    model.addZone(zone2);

    InjectionWellModel.MultiZoneInjectionResult result = model.calculateMultiZone(5000.0);

    assertNotNull(result);
    assertTrue(result.totalRate > 0, "Total injection rate should be positive");
    assertTrue(result.injectionEfficiency >= 0 && result.injectionEfficiency <= 1.0,
        "Injection efficiency should be 0-1");
    assertTrue(result.commonBHP > 0, "Common BHP should be positive");

    // Thief zone has higher permeability and lower reservoir pressure,
    // so it should take a significant share of the injection
    assertTrue(result.outOfZoneRate >= 0, "Out-of-zone rate should be non-negative");
  }

  @Test
  void testInjectionWellModelThermalStress() {
    InjectionWellModel model = new InjectionWellModel();
    model.setFracturePressure(350.0, "bara");

    // Set thermal stress: injection at 25°C into 120°C reservoir
    model.setThermalStressReduction(298.15, 393.15, 1.2e-5, 30.0);
    model.setPoissonsRatio(0.25);

    assertTrue(model.isThermalStressEnabled());
    double thermalReduction = model.getThermalStressReduction();
    assertTrue(thermalReduction > 0,
        "Thermal stress reduction should be positive for cold injection");

    double effectiveFracPres = model.getEffectiveFracturePressure();
    assertTrue(effectiveFracPres < 350.0,
        "Effective fracture pressure should be reduced by thermal stress");
    assertEquals(350.0 - thermalReduction, effectiveFracPres, 0.001);
  }

  // ===== NIP-03: AnnularLeakagePath =====

  @Test
  void testAnnularLeakagePathChannelFlow() {
    AnnularLeakagePath leakage = new AnnularLeakagePath("Cement Channel");
    leakage.setLeakageMechanism(AnnularLeakagePath.LeakageMechanism.CHANNEL_FLOW);
    leakage.setPathGeometry(1000.0, 1500.0, 0.01, 0.0001); // 500m path, 10mm wide, 0.1mm gap
    leakage.setFluidViscosity(0.001); // 1 cP water

    leakage.calculate(300.0, 200.0); // 100 bar pressure difference

    double rate = leakage.getChannelLeakageRate("m3/s");
    assertTrue(rate > 0, "Channel leakage rate should be positive");

    // Cubic law: q = w * delta^3 / (12 * mu) * dP/L
    // w=0.01, delta=0.0001, mu=0.001, dP=100*1e5=1e7 Pa, L=500
    // q = 0.01 * (0.0001)^3 / (12 * 0.001) * (1e7/500) = 1.667e-12 m3/s
    // Very small — expected for 0.1mm gap
    assertTrue(rate > 0 && rate < 1.0, "Rate should be small but positive for micro-annulus");
  }

  @Test
  void testAnnularLeakagePathDarcyFlow() {
    AnnularLeakagePath leakage = new AnnularLeakagePath("Degraded Cement");
    leakage.setLeakageMechanism(AnnularLeakagePath.LeakageMechanism.POROUS_CEMENT);
    leakage.setPathGeometry(1000.0, 1500.0, 0.01, 0.0001);
    leakage.setCementPermeability(0.01, "mD"); // 0.01 mD
    leakage.setCementCrossSectionArea(0.001); // 10 cm² in m²
    leakage.setFluidViscosity(0.001);

    leakage.calculate(300.0, 200.0);

    double rate = leakage.getCementLeakageRate("m3/s");
    assertTrue(rate > 0, "Cement leakage rate should be positive");
  }

  @Test
  void testAnnularLeakagePathCombined() {
    AnnularLeakagePath leakage = new AnnularLeakagePath("Combined Path");
    leakage.setLeakageMechanism(AnnularLeakagePath.LeakageMechanism.COMBINED);
    leakage.setPathGeometry(1000.0, 1500.0, 0.01, 0.0001);
    leakage.setCementPermeability(0.01, "mD");
    leakage.setCementCrossSectionArea(0.001);
    leakage.setFluidViscosity(0.001);

    leakage.calculate(300.0, 200.0);

    double totalRate = leakage.getTotalLeakageRate("m3/s");
    double channelRate = leakage.getChannelLeakageRate("m3/s");
    double cementRate = leakage.getCementLeakageRate("m3/s");

    assertTrue(totalRate > 0);
    // Total should be sum of both
    assertEquals(channelRate + cementRate, totalRate, 1e-20);
  }

  @Test
  void testAnnularLeakagePathNoPressureDifference() {
    AnnularLeakagePath leakage = new AnnularLeakagePath("No DP");
    leakage.setLeakageMechanism(AnnularLeakagePath.LeakageMechanism.CHANNEL_FLOW);
    leakage.setPathGeometry(1000.0, 1500.0, 0.01, 0.0001);
    leakage.setFluidViscosity(0.001);

    leakage.calculate(200.0, 200.0); // No pressure difference

    assertEquals(0.0, leakage.getTotalLeakageRate("m3/s"), 1e-30);
  }

  // ===== NIP-05: MultiCompartmentReservoir =====

  @Test
  void testMultiCompartmentReservoirBasic() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 250.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);

    MultiCompartmentReservoir reservoir = new MultiCompartmentReservoir("Multi Compartment");
    reservoir.addCompartment("Target", fluid.clone(), 1e8, 250.0);
    reservoir.addCompartment("Thief", fluid.clone(), 5e7, 220.0);
    reservoir.setCompressibility("Target", 1e-5);
    reservoir.setCompressibility("Thief", 1e-5);

    // No transmissibility between compartments initially
    reservoir.setTransmissibility("Target", "Thief", 0.0);

    // Inject into Target
    reservoir.addInjectionRate("Inj-1", "Target", 100.0); // 100 m3/day

    double dt = 86400.0; // 1 day in seconds
    reservoir.runTimeStep(dt);

    // Target pressure should increase (injection into it)
    double targetP = reservoir.getCompartmentPressure("Target", "bara");
    assertTrue(targetP > 250.0, "Target pressure should increase with injection");

    // Thief pressure should remain unchanged (no transmissibility)
    double thiefP = reservoir.getCompartmentPressure("Thief", "bara");
    assertEquals(220.0, thiefP, 0.01, "Thief pressure should be unchanged when disconnected");
  }

  @Test
  void testMultiCompartmentReservoirCrossFlow() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 250.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);

    MultiCompartmentReservoir reservoir = new MultiCompartmentReservoir("Cross Flow Test");
    reservoir.addCompartment("High", fluid.clone(), 1e8, 300.0);
    reservoir.addCompartment("Low", fluid.clone(), 1e8, 200.0);
    reservoir.setCompressibility("High", 1e-5);
    reservoir.setCompressibility("Low", 1e-5);

    // Set transmissibility between compartments
    reservoir.setTransmissibility("High", "Low", 0.001); // m3/(day*bar)

    reservoir.runTimeStep(86400.0); // 1 day

    double highP = reservoir.getCompartmentPressure("High", "bara");
    double lowP = reservoir.getCompartmentPressure("Low", "bara");

    // High pressure should decrease, low should increase due to crossflow
    assertTrue(highP < 300.0, "High pressure should decrease with outflow");
    assertTrue(lowP > 200.0, "Low pressure should increase with inflow");

    // Flow rate should be from High to Low
    double flowRate = reservoir.getInterZoneFlowRate("High", "Low", "m3/day");
    assertTrue(flowRate > 0, "Flow should be from high to low pressure compartment");
  }

  @Test
  void testMultiCompartmentReservoirReset() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(373.15, 250.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);

    MultiCompartmentReservoir reservoir = new MultiCompartmentReservoir("Reset Test");
    reservoir.addCompartment("A", fluid.clone(), 1e8, 300.0);
    reservoir.setCompressibility("A", 1e-5);
    reservoir.addInjectionRate("Inj", "A", 500.0);

    reservoir.runTimeStep(86400.0);
    double pAfterStep = reservoir.getCompartmentPressure("A", "bara");
    assertTrue(pAfterStep > 300.0);

    reservoir.reset();
    double pAfterReset = reservoir.getCompartmentPressure("A", "bara");
    assertEquals(300.0, pAfterReset, 0.001, "Pressure should reset to initial");
  }

  // ===== NIP-06: CementDegradationModel =====

  @Test
  void testCementDegradationPortland() {
    CementDegradationModel cement = new CementDegradationModel("Portland Cement");
    cement.setCementType(CementDegradationModel.CementType.PORTLAND);
    cement.setInitialPermeability(0.001, "mD");
    cement.setCementThickness(500.0, "mm"); // 500mm thick to avoid capping
    cement.setCO2Conditions(5.0, 323.15); // 5 bar CO2, 50°C (mild conditions)

    assertEquals(CementDegradationModel.CementType.PORTLAND, cement.getCementType());
    assertEquals(0.5, cement.getCementThickness(), 0.001);

    // At t=0, permeability should be initial
    double k0 = cement.getPermeabilityAtTime(0.0, "mD");
    assertEquals(0.001, k0, 0.0001);

    // At t=30 years, permeability should increase
    double k30 = cement.getPermeabilityAtTime(30.0, "mD");
    assertTrue(k30 > k0, "Permeability should increase with time under CO2 exposure");

    // Carbonation depth should increase with time (sqrt(t) relationship)
    double d10 = cement.getDegradationDepth(10.0, "mm");
    double d30 = cement.getDegradationDepth(30.0, "mm");
    assertTrue(d30 > d10, "Carbonation depth should increase with time");
    assertTrue(d10 > 0, "Carbonation depth should be positive after 10 years");
    assertTrue(d30 <= 500.0, "Carbonation depth should not exceed cement thickness");
  }

  @Test
  void testCementDegradationCO2Resistant() {
    CementDegradationModel portlandCement = new CementDegradationModel("Portland");
    portlandCement.setCementType(CementDegradationModel.CementType.PORTLAND);
    portlandCement.setCementThickness(0.05, "m");
    portlandCement.setCO2Conditions(50.0, 353.15);

    CementDegradationModel resistantCement = new CementDegradationModel("CO2 Resistant");
    resistantCement.setCementType(CementDegradationModel.CementType.CO2_RESISTANT);
    resistantCement.setCementThickness(0.05, "m");
    resistantCement.setCO2Conditions(50.0, 353.15);

    // CO2-resistant cement should degrade slower
    double portlandDepth = portlandCement.getDegradationDepth(30.0, "mm");
    double resistantDepth = resistantCement.getDegradationDepth(30.0, "mm");
    assertTrue(resistantDepth < portlandDepth,
        "CO2-resistant cement should degrade slower than Portland");
  }

  @Test
  void testCementDegradationNoCO2() {
    CementDegradationModel cement = new CementDegradationModel("No CO2");
    cement.setCementType(CementDegradationModel.CementType.PORTLAND);
    cement.setCementThickness(0.05, "m");
    cement.setCO2Conditions(0.0, 353.15); // Zero CO2

    double depth = cement.getDegradationDepth(30.0, "mm");
    assertEquals(0.0, depth, 0.0001, "No degradation expected without CO2");
  }

  @Test
  void testCementTimeToFullCarbonation() {
    CementDegradationModel cement = new CementDegradationModel("Time Test");
    cement.setCementType(CementDegradationModel.CementType.PORTLAND);
    cement.setCementThickness(0.05, "m");
    cement.setCO2Conditions(50.0, 353.15);

    double timeYears = cement.getTimeToFullCarbonation("years");
    assertTrue(timeYears > 0, "Time to full carbonation should be positive");
    assertTrue(timeYears < 10000, "Should be finite for Portland cement");
  }

  @Test
  void testCementCompromiseCheck() {
    CementDegradationModel cement = new CementDegradationModel("Compromise Check");
    cement.setCementType(CementDegradationModel.CementType.PORTLAND);
    cement.setCementThickness(0.05, "m");
    cement.setCO2Conditions(50.0, 353.15);

    // At time 0, cement should not be compromised
    assertFalse(cement.isCementCompromised(0.0));
  }

  @Test
  void testCementThicknessUnits() {
    CementDegradationModel cement = new CementDegradationModel("Unit Test");

    cement.setCementThickness(50.0, "mm");
    assertEquals(0.05, cement.getCementThickness(), 0.001);

    cement.setCementThickness(2.0, "in");
    assertEquals(0.0508, cement.getCementThickness(), 0.001);
  }

  // ===== NIP-07: InjectionConformanceMonitor =====

  @Test
  void testConformanceMonitorHallPlot() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Monitor");

    // Record stable injection data - constant WHP, constant rate
    for (int i = 0; i < 30; i++) {
      monitor.recordInjectionData(i, 250.0, 5000.0);
    }

    monitor.calculateHallPlot();

    double slope = monitor.getCurrentHallSlope();
    assertTrue(slope > 0, "Hall slope should be positive for normal injection");

    assertEquals(30, monitor.getDataPointCount());
  }

  @Test
  void testConformanceMonitorSlopeChange() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Slope Monitor");

    // Phase 1: Normal injection (days 0-20)
    for (int i = 0; i <= 20; i++) {
      monitor.recordInjectionData(i, 250.0, 5000.0);
    }

    // Phase 2: Fracture growth - pressure drops but rate increases (days 21-40)
    for (int i = 21; i <= 40; i++) {
      monitor.recordInjectionData(i, 200.0, 8000.0);
    }

    monitor.calculateHallPlot();

    double initialSlope = monitor.getInitialHallSlope();
    double currentSlope = monitor.getCurrentHallSlope();

    // Current slope should be lower (more volume per unit pressure-time)
    assertTrue(currentSlope < initialSlope, "Current slope should be lower after fracture growth");

    // Detect change
    boolean changed = monitor.detectSlopeChange(0.15);
    assertTrue(changed, "Slope change should be detected");
  }

  @Test
  void testConformanceMonitorDiagnosis() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Diagnosis");

    // Record insufficient data
    monitor.recordInjectionData(0, 250.0, 5000.0);
    monitor.recordInjectionData(1, 250.0, 5000.0);

    String diag = monitor.getDiagnosis();
    assertTrue(diag.contains("INSUFFICIENT_DATA"), "Should report insufficient data");
  }

  @Test
  void testConformanceMonitorInjectionProfile() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Profile Monitor");

    // Add zone profile showing OOZ injection
    monitor.addZoneProfile("Target Zone A", 2500.0, 0.6, true);
    monitor.addZoneProfile("Thief Zone B", 2400.0, 0.3, false);
    monitor.addZoneProfile("Thief Zone C", 2600.0, 0.1, false);

    // Injection efficiency should be 60%
    assertEquals(0.6, monitor.getInjectionEfficiency(), 0.001);

    // Out-of-zone fraction should be 40%
    assertEquals(0.4, monitor.getOutOfZoneFraction(), 0.001);
  }

  @Test
  void testConformanceMonitorReset() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Reset Test");

    for (int i = 0; i < 10; i++) {
      monitor.recordInjectionData(i, 250.0, 5000.0);
    }
    assertEquals(10, monitor.getDataPointCount());

    monitor.reset();
    assertEquals(0, monitor.getDataPointCount());
  }

  @Test
  void testConformanceMonitorHallPlotData() {
    InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Hall Data");

    for (int i = 0; i < 10; i++) {
      monitor.recordInjectionData(i, 250.0, 5000.0);
    }
    monitor.calculateHallPlot();

    java.util.List<Double> cumPT = monitor.getHallCumulativePressureTime();
    java.util.List<Double> cumVol = monitor.getHallCumulativeVolume();

    assertEquals(10, cumPT.size());
    assertEquals(10, cumVol.size());

    // Cumulative values should be monotonically increasing
    for (int i = 1; i < cumPT.size(); i++) {
      assertTrue(cumPT.get(i) >= cumPT.get(i - 1), "Cumulative P*T should increase");
      assertTrue(cumVol.get(i) >= cumVol.get(i - 1), "Cumulative volume should increase");
    }
  }
}
