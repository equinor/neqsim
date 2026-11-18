package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork.DeviceRating;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork.Pipe;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork.Result;

/**
 * Independent flange, conservation and applicability checks for a dry-gas star header.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SteadyFlareGasNetworkTest {
  private static final Pipe NO_LOSS = new Pipe(0.25, 0.0, 0.02);
  private static final double RT_OVER_M = 8.3144621 * 300.0 / 0.020;

  /** At known choked nozzle capacity, integrate dP/dx = f G^2/(2 D rho) backwards from the terminal. */
  @Test
  void independentPipeIntegrationRecoversFlangePressure() {
    double capacity = 0.95 * 0.0002 * 8e5 * Math.sqrt(1.3 * Math.pow(2.0 / 2.3, 2.3 / 0.3) / RT_OVER_M);
    SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("header", new Pipe(0.14, 95.0, 0.02), "tip", 1.2, 1.2,
        2.0, 300, 0.020, 1.0, 1.3);
    network.addDevice("PSV", 8.0, NO_LOSS, 0.0002, 0.95, new Pipe(0.10, 120, 0.02), 1.6);
    Result result = network.rate(Collections.singletonMap("PSV", 0.4));
    double headerPa = integratePipePressure(1.2e5, capacity, 0.14, 95.0);
    double flangePa = integratePipePressure(headerPa, capacity, 0.10, 120.0);
    DeviceRating device = result.devices.get("PSV");
    assertEquals(capacity, result.totalKgS, 1e-8);
    assertEquals(headerPa / 1e5, result.headerBara, 1e-6);
    assertEquals(flangePa / 1e5, device.outletBara, 1e-6);
    assertEquals(device.outletBara - 1.2, device.outletRiseAboveStaticBar, 1e-12);
    assertEquals(1.6 - device.outletBara, device.outletPressureMarginBar, 1e-12);
    assertEquals(capacity - 0.4, device.capacityMarginKgS, 1e-8);
  }

  /**
   * Integrate the incompressible differential loss with the local gas density updated at every spatial increment.
   *
   * @param pressurePa downstream pressure, Pa
   * @param flow gas mass flow, kg/s
   * @param bore inside diameter, m
   * @param length pipe length, m
   * @return upstream pressure, Pa
   */
  private double integratePipePressure(double pressurePa, double flow, double bore, double length) {
    double flux = 4.0 * flow / (Math.PI * bore * bore);
    double step = length / 100000.0;
    for (int i = 0; i < 100000; i++) {
      double density = pressurePa / RT_OVER_M;
      pressurePa += step * 0.02 * flux * flux / (2.0 * bore * density);
    }
    return pressurePa;
  }

  /** A shared header competes for pressure head and each source sees its own local flange pressure. */
  @Test
  void asymmetricLateralsFeedBackOnEachOtherAndReportLimits() {
    Pipe common = new Pipe(0.15, 200, 0.02);
    Pipe shortTail = new Pipe(0.1, 5, 0.02);
    SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("H", common, "T", 1.2, 1.6, 2, 300, 0.020, 1, 1.3);
    network.addDevice("A", 2.0, shortTail, 0.001, 0.95, shortTail, null);
    Result onlyA = network.rate(Collections.singletonMap("A", 0.1));
    double alone = onlyA.devices.get("A").availableKgS;
    network.addDevice("B", 2.0, shortTail, 0.001, 0.95, new Pipe(0.1, 200, 0.02), 1.21);
    Map<String, Double> demands = new LinkedHashMap<>();
    demands.put("A", 0.1);
    demands.put("B", 2.0);
    network.addDevice("Closed", 4.0, shortTail, 0.0, 0.95, shortTail, null);
    demands.put("Closed", 0.0);
    Result result = network.rate(demands);
    DeviceRating closed = result.devices.get("Closed");
    assertEquals(0.0, closed.availableKgS, 0.0);
    assertEquals(result.headerBara, closed.outletBara, 1e-12);
    assertEquals(0.0, closed.incrementalBackpressureBar, 1e-12);
    assertTrue(closed.steadyPreOpeningBara > result.zeroFlowStaticBara);
    assertEquals(200.0, result.devices.get("B").tailpipe.equivalentLengthM, 0.0);
    DeviceRating a = result.devices.get("A");
    DeviceRating b = result.devices.get("B");
    assertTrue(a.availableKgS < alone && b.availableKgS < a.availableKgS);
    assertEquals(onlyA.headerBara, b.steadyPreOpeningBara, 1e-7);
    assertTrue(b.incrementalBackpressureBar > 0);
    assertTrue(b.outletBara > a.outletBara && a.outletBara > result.headerBara);
    assertTrue(result.headerBara > result.tipBara && result.tipBara > result.zeroFlowStaticBara);
    assertTrue(b.inletLossBar > 0.0 && b.capacityMarginKgS < 0.0 && b.outletPressureMarginBar < 0.0);
    assertNull(a.outletPressureMarginBar);
    assertEquals(a.availableKgS + b.availableKgS, result.totalKgS, 1e-8);
    assertEquals(0.0, result.massResidualKgS, 1e-8);
    assertTrue(result.toJson().contains("\"outletPressureMarginBar\":null"));
    assertThrows(UnsupportedOperationException.class, () -> result.devices.clear());
    assertThrows(IllegalStateException.class, () -> network.rate(demands, 1e-12, 1));
  }

  /** The blowdown handoff preserves the demand even when no forward nozzle capacity exists. */
  @Test
  void equalOrAdversePressureAndNegligibleLossAtTheSampledPeak() {
    DepressurizationResult source = new DepressurizationResult();
    source.time.add(0.5);
    source.massFlowKgPerS.add(0.1);
    source.time.add(1.5);
    source.massFlowKgPerS.add(0.3);
    MultiVesselBlowdownStudy.MultiVesselBlowdownResult history = new MultiVesselBlowdownStudy().setGridStep(1.0)
        .addSourceResult("BDV", source).run();
    assertEquals(0.0, history.getTotalMassFlowKgPerS().get(0), 0.0);
    assertEquals(1.5, history.getPeakTimeS(), 0.0);
    for (double pressure : new double[] {1.1, 1.2, 2.0}) {
      SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("H", NO_LOSS, "T", 1.2, 1.2, 2, 300, 0.020, 1, 1.3);
      network.addDevice("BDV", pressure, NO_LOSS, 1e-4, 0.95, NO_LOSS, null);
      Result result = history.ratePeakGasCapacity(network);
      DeviceRating device = result.devices.get("BDV");
      assertEquals(1.2, device.outletBara, 1e-12);
      assertEquals(pressure, device.inletBara, 1e-12);
      assertEquals(0.0, device.outletRiseAboveStaticBar, 0.0);
      assertEquals(device.availableKgS - 0.3, device.capacityMarginKgS, 1e-10);
      if (pressure <= 1.2) {
        assertEquals(0, result.totalKgS, 0);
      } else {
        assertTrue(result.totalKgS > 0);
      }
    }
  }

  /** Missing demand, extrapolated tip and high pipe Mach cannot produce an accepted result. */
  @Test
  void rejectsIncompleteAndOutOfRangeRatings() {
    SteadyFlareGasNetwork smallTip = new SteadyFlareGasNetwork("H", NO_LOSS, "T", 1, 1, 0.01, 300, 0.020, 1, 1.3);
    smallTip.addDevice("PSV", 8, NO_LOSS, 2e-4, 0.95, NO_LOSS, null);
    assertThrows(IllegalArgumentException.class, () -> smallTip.rate(Collections.emptyMap()));
    assertThrows(IllegalStateException.class, () -> smallTip.rate(Collections.singletonMap("PSV", 0.01)));
    SteadyFlareGasNetwork fast = new SteadyFlareGasNetwork("H", new Pipe(0.02, 0, 0.02), "T", 1, 1, 2, 300, 0.020, 1,
        1.3);
    fast.addDevice("PSV", 8, NO_LOSS, 2e-4, 0.95, NO_LOSS, null);
    assertThrows(IllegalStateException.class, () -> fast.rate(Collections.singletonMap("PSV", 0.1)));
  }

  /** Both devices flowing gives Mach 0.258, but a lone open lateral in the pre-opening case reaches 0.305. */
  @Test
  void verifiesMachOnTheSeparatePreOpeningState() {
    Pipe tail = new Pipe(0.06, 0.0, 0.02);
    SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("H", new Pipe(0.15, 200, 0.02), "T", 1.2, 1.6, 2, 300,
        0.02, 1, 1.3);
    network.addDevice("A", 2, NO_LOSS, 0.001, 0.95, tail, null);
    network.addDevice("B", 2, NO_LOSS, 0.001, 0.95, tail, null);
    Map<String, Double> demand = new LinkedHashMap<>();
    demand.put("A", 0.1);
    demand.put("B", 0.1);
    assertThrows(IllegalStateException.class, () -> network.rate(demand));
  }

}
