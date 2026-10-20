package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression evidence for deep copies of layered wells, without changing their legacy rate basis.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WellFlowCopyTest {
  /**
   * Build a synthetic gas stream with a declared SRK/classic model.
   *
   * @param name stream identity
   * @param pressureBara pressure in bara
   * @return initialized stream
   */
  private Stream stream(String name, double pressureBara) {
    SystemSrkEos gas = new SystemSrkEos(298.15, pressureBara);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream stream = new Stream(name, gas);
    stream.setFlowRate(0.001, "MSm3/day");
    stream.run();
    return stream;
  }

  /**
   * Create the existing squared-pressure injection definition, with two different reservoir boundaries.
   *
   * @return configured injector
   */
  private WellFlow injector() {
    WellFlow well = new WellFlow("injector");
    well.setInletStream(stream("feed", 150.0));
    well.addInjectionZone("target", stream("target fluid", 100.0), 100.0, 7.0e-8, 160.0);
    well.addInjectionZone("secondary", stream("secondary fluid", 110.0), 110.0, 3.0e-8, 140.0);
    well.getLayer(0).setBarrierStressContrast(12.0, "bar");
    well.setTargetZone("target");
    well.setOutletPressure(150.0, "bara");
    return well;
  }

  /** Copy before execution must retain zones, constraints and target metadata but share no mutable fluid. */
  @Test
  void copiesUnrunInjectionDefinition() {
    WellFlow original = injector();
    WellFlow copy = (WellFlow) original.copy();
    assertEquals(2, copy.getNumberOfLayers());
    assertEquals(WellFlow.FlowMode.INJECTION, copy.getFlowMode());
    assertNotSame(original.getLayer(0), copy.getLayer(0));
    assertNotSame(original.getLayer(0).stream, copy.getLayer(0).stream);
    assertNotSame(original.getLayer(0).stream.getThermoSystem(), copy.getLayer(0).stream.getThermoSystem());
    assertEquals("target", copy.getLayer(0).name);
    assertEquals(7.0e-8, copy.getLayer(0).productivityIndex, 0.0);
    assertEquals(160.0, copy.getLayer(0).fracturePressure, 0.0);
    assertEquals(12.0, copy.getLayer(0).barrierStressContrast, 0.0);
    assertTrue(copy.getLayer(0).isTargetZone);
    assertFalse(copy.getLayer(1).isTargetZone);
    original.run(UUID.randomUUID());
    copy.run(UUID.randomUUID());
    assertEquals(original.getOutletStream().getFlowRate("kg/sec"), copy.getOutletStream().getFlowRate("kg/sec"),
        1.0e-12);
    assertEquals(7.0e-8 * (150.0 * 150.0 - 100.0 * 100.0), copy.getLayer(0).calculatedRate, 1.0e-15);
    assertArrayEquals(original.getZoneAllocationFractions(), copy.getZoneAllocationFractions(), 1.0e-12);
    assertArrayEquals(new boolean[] {false, true}, copy.getZoneFractureRisk());
  }

  /** Copy after execution must retain results and permit independent reservoir updates and restoration. */
  @Test
  void copiesCalculatedInjectionStateIndependently() {
    WellFlow original = injector();
    original.run(UUID.randomUUID());
    double baseline = original.getOutletStream().getFlowRate("kg/sec");
    WellFlow copy = (WellFlow) original.copy();
    assertEquals(original.getLayer(0).calculatedRate, copy.getLayer(0).calculatedRate, 0.0);
    assertEquals(original.getInjectionEfficiency(), copy.getInjectionEfficiency(), 0.0);
    copy.getLayer(0).setReservoirPressure(120.0, "bara");
    copy.setTargetZone("secondary");
    copy.run(UUID.randomUUID());
    assertTrue(copy.getOutletStream().getFlowRate("kg/sec") < baseline);
    assertEquals(100.0, original.getLayer(0).stream.getPressure("bara"), 0.0);
    assertEquals(baseline, original.getOutletStream().getFlowRate("kg/sec"), 0.0);
    assertTrue(original.getLayer(0).isTargetZone);
    copy.getLayer(0).setReservoirPressure(100.0, "bara");
    copy.run(UUID.randomUUID());
    assertEquals(baseline, copy.getOutletStream().getFlowRate("kg/sec"), 1.0e-12);
  }

  /** Layered production retains its stream aliases inside a copied process but not aliases to the source. */
  @Test
  void processCopyPreservesProductionLayerAliases() {
    Stream reservoir = stream("reservoir", 200.0);
    WellFlow well = new WellFlow("producer");
    well.setInletStream(reservoir);
    well.addLayer("upper", reservoir, 200.0, 1.0e-7);
    well.addLayer("lower", stream("lower reservoir", 180.0), 180.0, 2.0e-7);
    well.setOutletPressure(150.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(reservoir);
    process.add(well);
    process.run();
    ProcessSystem copied = process.copy();
    WellFlow copiedWell = (WellFlow) copied.getUnit("producer");
    assertEquals(2, copiedWell.getNumberOfLayers());
    assertSame(copied.getUnit("reservoir"), copiedWell.getLayer(0).stream);
    assertSame(copiedWell.getInletStream(), copiedWell.getLayer(0).stream);
    assertNotSame(reservoir, copiedWell.getLayer(0).stream);
    copied.run();
    assertEquals(well.getOutletStream().getFlowRate("kg/sec"), copiedWell.getOutletStream().getFlowRate("kg/sec"),
        1.0e-12);
    copiedWell.getLayer(0).setReservoirPressure(190.0, "bara");
    copied.run();
    assertTrue(copiedWell.getOutletStream().getFlowRate("kg/sec") < well.getOutletStream().getFlowRate("kg/sec"));
    assertEquals(200.0, reservoir.getPressure("bara"), 0.0);
  }

  /** Empty single-zone definitions must retain an extensible layer list after copying. */
  @Test
  void copiedSingleLayerWellCanAddZones() {
    WellFlow original = new WellFlow("empty");
    original.setInletStream(stream("feed", 150.0));
    WellFlow copy = (WellFlow) original.copy();
    assertEquals(0, copy.getNumberOfLayers());
    copy.addInjectionZone("new", stream("new fluid", 100.0), 100.0, 1.0e-7, 160.0);
    assertEquals(1, copy.getNumberOfLayers());
    assertEquals(0, original.getNumberOfLayers());
  }

  /**
   * A missing optional list in an old non-layered definition remains extensible after reading.
   *
   * @throws Exception if reflection cannot reproduce a missing serialized field
   */
  @Test
  void restoresMissingNonLayeredList() throws Exception {
    WellFlow original = new WellFlow("legacy single-layer");
    original.setInletStream(stream("feed", 150.0));
    Field layers = WellFlow.class.getDeclaredField("layers");
    layers.setAccessible(true);
    layers.set(original, null);
    WellFlow copy = (WellFlow) original.copy();
    assertEquals(0, copy.getNumberOfLayers());
    copy.addLayer("zone", stream("zone fluid", 100.0), 100.0, 1.0e-7);
    assertEquals(1, copy.getNumberOfLayers());
  }

  /**
   * Missing zones cannot be reconstructed from a saved outlet; fail while reading with migration guidance.
   *
   * @throws Exception if reflection cannot reproduce a missing serialized field
   */
  @Test
  void rejectsMissingLayeredDefinition() throws Exception {
    WellFlow original = injector();
    Field layers = WellFlow.class.getDeclaredField("layers");
    layers.setAccessible(true);
    layers.set(original, null);
    RuntimeException failure = assertThrows(RuntimeException.class, original::copy);
    assertTrue(failure.getCause() instanceof InvalidObjectException);
    assertTrue(failure.getCause().getMessage().contains("Rebuild"));
  }
}
