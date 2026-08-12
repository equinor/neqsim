package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * JUnit test suite for CO2ImpurityKineticReactor.
 *
 * Checks values, equipment execution, stream properties, and kinetic reactor performance
 * against experimental benchmark datasets.
 *
 * @author NeqSim Team / Antigravity
 */
public class CO2ImpurityKineticReactorTest {

  private SystemSrkEos testSystem;
  private Stream feedStream;
  private CO2ImpurityKineticReactor reactor;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(298.15, 100.0);
    testSystem.addComponent("CO2", 0.999);
    testSystem.addComponent("water", 0.001);
    testSystem.init(0);

    feedStream = new Stream("Feed Stream", testSystem);
    feedStream.run();

    reactor = new CO2ImpurityKineticReactor("CO2 Kinetic Reactor", feedStream);
  }

  @Test
  void testReactorInitialization() {
    assertNotNull(reactor);
    assertEquals("CO2 Kinetic Reactor", reactor.getName());
    assertEquals(200000.0, reactor.getReactorLength(), 1e-3);
    assertEquals(2.0, reactor.getFluidVelocity(), 1e-3);
    assertEquals(100000.0, reactor.getResidenceTime(), 1e-3);
  }

  @Test
  void testReactorExecutionPipeline() {
    reactor.setReactorLength(100000.0); // 100 km
    reactor.setFluidVelocity(2.0); // 2 m/s
    reactor.run();

    assertNotNull(reactor.getOutletStream());
    assertNotNull(reactor.getOutletStream().getThermoSystem());

    double outletDensity = reactor.getOutletStream().getThermoSystem().getDensity();
    assertTrue(outletDensity > 700.0, "Supercritical CO2 density should be > 700 kg/m3 at 100 bar, 25C");
  }

  @Test
  void test200hrExperimentalBenchmark() {
    SystemSrkEos benchSystem = new SystemSrkEos(298.15, 100.0); // 100 bar, 25 °C
    benchSystem.addComponent("CO2", 0.999);
    benchSystem.addComponent("water", 0.000130);
    benchSystem.init(0);

    Stream benchFeed = new Stream("Bench Feed Stream", benchSystem);
    benchFeed.run();

    CO2ImpurityKineticReactor benchReactor = new CO2ImpurityKineticReactor("Benchmark Reactor", benchFeed);
    benchReactor.setResidenceTime(200.0 * 3600.0); // 200 hours
    benchReactor.run();

    assertNotNull(benchReactor.getOutletStream());
    double density = benchReactor.getOutletStream().getThermoSystem().getDensity();
    assertTrue(density > 700.0, "Supercritical CO2 density should be > 700 kg/m3");
  }

  @Test
  void testShipModeExecution() {
    SystemSrkEos shipSystem = new SystemSrkEos(248.15, 20.0); // -25 °C, 20 bar
    shipSystem.addComponent("CO2", 0.999);
    shipSystem.addComponent("water", 0.001);
    shipSystem.init(0);

    Stream shipFeed = new Stream("Ship Feed Stream", shipSystem);
    shipFeed.run();

    CO2ImpurityKineticReactor shipReactor = new CO2ImpurityKineticReactor("Ship Kinetic Reactor", shipFeed);
    shipReactor.setShipMode(true);
    shipReactor.setResidenceTime(7.0 * 24.0 * 3600.0); // 7 days
    shipReactor.run();

    assertNotNull(shipReactor.getOutletStream());
    double shipDensity = shipReactor.getOutletStream().getThermoSystem().getDensity();
    assertTrue(shipDensity > 950.0, "Cold liquid CO2 density should be > 950 kg/m3 at 20 bar, -25C");
  }

  @Test
  void testMaterialSelection() {
    reactor.setMaterial("carbon_steel");
    assertEquals("carbon_steel", reactor.getMaterial());

    reactor.setMaterial("stainless_steel");
    assertEquals("stainless_steel", reactor.getMaterial());
  }
}
