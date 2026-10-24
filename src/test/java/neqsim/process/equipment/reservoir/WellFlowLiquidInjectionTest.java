package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Analytical liquid-injection acceptance, separate from legacy gas-standard coefficients.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WellFlowLiquidInjectionTest {
  /**
   * Create a synthetic single-component stream.
   *
   * @param name stream name
   * @param component component identity
   * @param pressure pressure in bara
   * @return flashed stream at 298.15 K
   */
  private Stream stream(String name, String component, double pressure) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressure);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(1.0, "kg/sec");
    stream.run();
    return stream;
  }

  /**
   * Build the documented two-zone mass-basis injector.
   *
   * @return configured well
   */
  private WellFlow injector() {
    WellFlow well = new WellFlow("liquid injector");
    well.setInletStream(stream("injection supply", "water", 180.0));
    // The injected composition is water, even when the reservoir boundary stream describes oil.
    well.addLiquidInjectionZone("target", stream("target", "n-decane", 100.0), 100.0, 0.01, 170.0);
    well.addLiquidInjectionZone("secondary", stream("secondary", "water", 110.0), 110.0, 0.005, 160.0);
    well.setTargetZone("target");
    well.setOutletPressure(150.0, "bara");
    return well;
  }

  /**
   * Build a two-zone live radial-Darcy ordinary-water injector.
   *
   * @return configured well
   */
  private WellFlow radialInjector() {
    WellFlow well = new WellFlow("radial injector");
    well.setInletStream(stream("radial supply", "water", 180.0));
    well.addRadialDarcyWaterInjectionZone("target", stream("radial target", "water", 100.0), 100.0,
        RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 1.0, 500.0, 0.1, 300.0), 200.0);
    well.addRadialDarcyWaterInjectionZone("secondary", stream("radial secondary", "water", 110.0), 110.0,
        RadialDarcyWaterInjectivity.iapwsPureWater(50.0, 10.0, 2.0, 500.0, 0.1, 300.0), 195.0);
    well.setTargetZone("target");
    well.setOutletPressure(150.0, "bara");
    return well;
  }

  /** Check the analytical Darcy mass law, allocation, units and injected-fluid ownership. */
  @Test
  void closesAnalyticalMassAllocation() {
    WellFlow well = injector();
    well.run();
    // rho k A / (mu L) * 1e5 = 0.01 kg/(s bar), for the declared synthetic constant-property case.
    double darcyMassIndex = 1000.0 * 1.0e-12 * 10.0 / (0.001 * 100.0) * 1.0e5;
    assertEquals(darcyMassIndex * 50.0, well.getLayerFlowRates("kg/sec")[0], 1.0e-12);
    assertEquals(0.7, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    assertArrayEquals(new double[] {0.5, 0.2}, well.getLayerFlowRates("kg/s"), 1.0e-12);
    assertArrayEquals(new double[] {1800.0, 720.0}, well.getLayerFlowRates("kg/hr"), 1.0e-9);
    assertArrayEquals(new double[] {43200.0, 17280.0}, well.getLayerFlowRates("kg/day"), 1.0e-9);
    assertEquals(0.2, well.getOutOfZoneRate("kg/sec"), 1.0e-12);
    assertEquals(720.0, well.getOutOfZoneRate("kg/hr"), 1.0e-9);
    assertEquals(5.0 / 7.0, well.getInjectionEfficiency(), 1.0e-12);
    assertArrayEquals(new double[] {5.0 / 7.0, 2.0 / 7.0}, well.getZoneAllocationFractions(), 1.0e-12);
    assertFalse(well.getOutletStream().getThermoSystem().hasComponent("n-decane"));
    assertEquals(1.0, well.getInletStream().getFlowRate("kg/sec"), 1.0e-12);
    assertEquals(180.0, well.getInletStream().getPressure("bara"), 1.0e-12);
    assertEquals(298.15, well.getOutletStream().getTemperature("K"), 1.0e-8);
  }

  /** Repeated boundary changes retain the linear slope, unit-aware BHP, shut-in and recovery. */
  @Test
  void respondsToPressureAndRecoversFromShutIn() {
    WellFlow well = injector();
    for (double pressure : new double[] {140.0, 150.0, 160.0}) {
      well.setOutletPressure(pressure * 1.0e5, "Pa");
      well.run();
      assertEquals(0.01 * (pressure - 100.0) + 0.005 * (pressure - 110.0), well.getOutletStream().getFlowRate("kg/sec"),
          1.0e-10);
      assertFalse(well.getZoneFractureRisk()[0]);
      assertFalse(well.getZoneFractureRisk()[1]);
    }
    well.setOutletPressure(161.0, "bara");
    well.run();
    assertTrue(well.getZoneFractureRisk()[1]);
    assertEquals(0.865, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    well.setOutletPressure(105.0, "bara");
    well.run();
    assertArrayEquals(new double[] {0.05, 0.0}, well.getLayerFlowRates("kg/sec"), 1.0e-12);
    well.setOutletPressure(90.0, "bara");
    well.run();
    assertEquals(0.0, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-12);
    assertArrayEquals(new double[] {0.0, 0.0}, well.getZoneAllocationFractions(), 0.0);
    well.setOutletPressure(150.0, "bara");
    well.run();
    assertEquals(0.7, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    well.getLayer(0).setReservoirPressure(120.0, "bara");
    well.run();
    assertEquals(0.5, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
  }

  /** The live bridge refreshes zone PVT and closes its standard-volume-to-mass conversion. */
  @Test
  void evaluatesRadialDarcyWaterOnEverySolve() {
    WellFlow well = radialInjector();
    well.run();
    double expectedTotal = 0.0;
    for (int i = 0; i < well.getNumberOfLayers(); i++) {
      RadialDarcyWaterInjectivity.Result evidence = well.getLayer(i).getRadialDarcyWaterInjectivityResult();
      double expected = evidence.getStandardVolumeRateSm3PerDay() * evidence.getStandardDensityKgPerSm3() / 86400.0;
      assertEquals(expected, well.getLayerFlowRates("kg/sec")[i], 1.0e-12);
      expectedTotal += expected;
    }
    assertEquals(expectedTotal, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    RadialDarcyWaterInjectivity law = well.getLayer(0).getRadialDarcyWaterInjectivity();
    double firstRate = well.getLayerFlowRates("kg/sec")[0];
    well.getLayer(0).setReservoirPressure(120.0, "bara");
    well.run();
    assertSame(law, well.getLayer(0).getRadialDarcyWaterInjectivity());
    assertEquals(120.0, well.getLayer(0).getRadialDarcyWaterInjectivityResult().getReservoirPressureBara(), 0.0);
    assertTrue(well.getLayerFlowRates("kg/sec")[0] < firstRate);
  }

  /** The live radial-Darcy API accepts only its declared pure-water PVT and composition basis. */
  @Test
  void rejectsUnqualifiedRadialDarcyBasisAndFeed() {
    WellFlow well = new WellFlow("invalid radial injector");
    well.setInletStream(stream("oil supply", "n-decane", 180.0));
    RadialDarcyWaterInjectivity configured = RadialDarcyWaterInjectivity.configured(100.0, 20.0, 0.0, 500.0, 0.1, 0.5,
        1.0);
    assertThrows(IllegalArgumentException.class,
        () -> well.addRadialDarcyWaterInjectionZone("zone", stream("zone", "water", 100.0), 100.0, configured, 200.0));
    well.addRadialDarcyWaterInjectionZone("zone", stream("zone", "water", 100.0), 100.0,
        RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 0.0, 500.0, 0.1, 300.0), 200.0);
    well.setOutletPressure(150.0, "bara");
    assertThrows(IllegalStateException.class, () -> well.run());
  }

  /** Units, law selection and invalid configuration fail before changing the existing zone list. */
  @Test
  void rejectsAmbiguousUnitsAndMixedBases() {
    WellFlow well = injector();
    well.run();
    assertThrows(IllegalArgumentException.class, () -> well.getLayerFlowRates("Sm3/day"));
    assertThrows(IllegalArgumentException.class, () -> well.getOutOfZoneRate("MSm3/day"));
    assertThrows(IllegalArgumentException.class, () -> well.getLayerFlowRates(null));
    assertThrows(IllegalStateException.class,
        () -> well.addInjectionZone("gas", well.getInletStream(), 100.0, 1.0e-8, 170.0));
    assertThrows(IllegalStateException.class, () -> well.addLayer("production", well.getInletStream(), 100.0, 1.0e-8));
    assertThrows(IllegalArgumentException.class,
        () -> well.addLiquidInjectionZone("target", well.getInletStream(), 100.0, 0.01, 170.0));
    assertThrows(IllegalArgumentException.class,
        () -> well.addLiquidInjectionZone("invalid", well.getInletStream(), 100.0, -0.01, 170.0));
    assertEquals(2, well.getNumberOfLayers());
    WellFlow legacy = new WellFlow("legacy");
    legacy.addInjectionZone("legacy", well.getInletStream(), 100.0, 1.0e-8, 170.0);
    assertThrows(IllegalStateException.class,
        () -> legacy.addLiquidInjectionZone("liquid", well.getInletStream(), 100.0, 0.01, 170.0));
    assertFalse(legacy.isLiquidMassInjection());
    assertEquals(1, legacy.getNumberOfLayers());
  }

  /** Existing mutable fields cannot bypass validation or leave accepted layer-rate evidence. */
  @Test
  void rejectsInvalidRuntimeStateAndRecovers() {
    WellFlow well = injector();
    assertThrows(IllegalStateException.class, () -> well.getInjectionEfficiency());
    well.run();
    well.getLayer(0).productivityIndex = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> well.run());
    assertTrue(Double.isNaN(well.getLayerFlowRates("kg/sec")[0]));
    assertThrows(IllegalStateException.class, () -> well.getZoneAllocationFractions());
    assertThrows(IllegalStateException.class, () -> well.getOutOfZoneRate("kg/sec"));
    well.getLayer(0).productivityIndex = 0.01;
    well.setOutletPressure(Double.NaN, "bara");
    assertThrows(IllegalArgumentException.class, () -> well.run());
    well.setOutletPressure(150.0, "bara");
    well.setFlowMode(WellFlow.FlowMode.PRODUCTION);
    assertThrows(IllegalStateException.class, () -> well.run());
    well.setFlowMode(WellFlow.FlowMode.INJECTION);
    well.getLayer(0).productivityIndex = Double.MAX_VALUE;
    assertThrows(IllegalArgumentException.class, () -> well.run());
    well.getLayer(0).productivityIndex = 0.01;
    well.run();
    assertEquals(0.7, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
  }

  /** A gas or two-phase inlet cannot be silently treated as a linear liquid. */
  @Test
  void rejectsGasAndMultiphaseInlets() {
    WellFlow well = injector();
    well.setInletStream(stream("gas feed", "methane", 180.0));
    assertThrows(IllegalStateException.class, () -> well.run());
    SystemSrkEos mixture = new SystemSrkEos(298.15, 150.0);
    mixture.addComponent("methane", 0.5);
    mixture.addComponent("n-decane", 0.5);
    mixture.setMixingRule("classic");
    Stream feed = new Stream("two-phase feed", mixture);
    feed.run();
    assertTrue(feed.getThermoSystem().getNumberOfPhases() > 1);
    well.setInletStream(feed);
    assertThrows(IllegalStateException.class, () -> well.run());
  }

  /** An unqualified transient path must not fall back to the legacy squared-pressure equation. */
  @Test
  void rejectsDynamicModeButAllowsQuasiSteadyEvaluation() {
    WellFlow well = injector();
    well.setCalculateSteadyState(false);
    assertThrows(UnsupportedOperationException.class, () -> well.runTransient(1.0, UUID.randomUUID()));
    well.setCalculateSteadyState(true);
    well.runTransient(1.0, UUID.randomUUID());
    assertEquals(0.7, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
  }

  /** Copies retain the selected basis and aliases but not mutable ownership across models. */
  @Test
  void copiesMassBasisAndIndependentLayerState() {
    WellFlow original = injector();
    WellFlow coldCopy = (WellFlow) original.copy();
    assertTrue(coldCopy.isLiquidMassInjection());
    coldCopy.run();
    assertEquals(0.7, coldCopy.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    original.run();
    ProcessSystem process = new ProcessSystem();
    process.add(original.getInletStream());
    process.add(original);
    ProcessSystem copy = process.copy();
    WellFlow copied = (WellFlow) copy.getUnit("liquid injector");
    assertTrue(copied.isLiquidMassInjection());
    assertSame(copy.getUnit("injection supply"), copied.getInletStream());
    assertNotSame(original.getLayer(0), copied.getLayer(0));
    assertNotSame(original.getLayer(0).stream, copied.getLayer(0).stream);
    copied.getLayer(0).setReservoirPressure(120.0, "bara");
    copied.run();
    assertEquals(0.5, copied.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    assertEquals(100.0, original.getLayer(0).reservoirPressure, 0.0);
    assertEquals(0.7, original.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
  }

  /** Serialized copies retain an independent radial law and regenerate evidence from copied boundaries. */
  @Test
  void copiesRadialDarcyDefinitionAndEvidence() {
    WellFlow original = radialInjector();
    original.run();
    WellFlow copied = (WellFlow) original.copy();
    assertNotSame(original.getLayer(0), copied.getLayer(0));
    assertNotSame(original.getLayer(0).getRadialDarcyWaterInjectivity(),
        copied.getLayer(0).getRadialDarcyWaterInjectivity());
    copied.getLayer(0).setReservoirPressure(120.0, "bara");
    copied.run();
    assertEquals(120.0, copied.getLayer(0).getRadialDarcyWaterInjectivityResult().getReservoirPressureBara(), 0.0);
    assertEquals(100.0, original.getLayer(0).getRadialDarcyWaterInjectivityResult().getReservoirPressureBara(), 0.0);
  }

  /** Preserve the old coefficient and rate-getter behavior, including its documented legacy unit defect. */
  @Test
  void preservesLegacyInjectionExactly() {
    WellFlow well = new WellFlow("legacy gas");
    Stream gas = stream("gas", "methane", 100.0);
    well.setInletStream(gas);
    well.addInjectionZone("zone", gas, 100.0, 7.0e-8, 200.0);
    well.setOutletPressure(150.0, "bara");
    well.run();
    double expected = 7.0e-8 * (150.0 * 150.0 - 100.0 * 100.0);
    assertFalse(well.isLiquidMassInjection());
    assertEquals(expected, well.getOutletStream().getFlowRate("MSm3/day"), 1.0e-12);
    assertEquals(expected, well.getLayerFlowRates("Sm3/day")[0], 1.0e-12);
    assertEquals(expected / 1.0e6, well.getLayerFlowRates("MSm3/day")[0], 1.0e-15);
    assertEquals(expected, well.getOutOfZoneRate("Sm3/day"), 1.0e-12);
  }
}
