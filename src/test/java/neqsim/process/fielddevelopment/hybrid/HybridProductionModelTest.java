package neqsim.process.fielddevelopment.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.reservoir.FlowlineVlpProvider;
import neqsim.process.fielddevelopment.reservoir.RecombinationFluidFactory;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of the coupled well and network solve, the time stepping, the fidelity modes and the facility-capacity
 * allocation of the hybrid production model, on analytic lift models with known answers.
 *
 * @author ESOL
 * @version 1.0
 */
class HybridProductionModelTest {
  /** Analytic tubing lift model: static head that grows with water cut plus quadratic friction. */
  private static final VlpProvider TUBING = (rate, thp, wct, gor, alq) -> thp + 90.0 + 40.0 * wct
      + 3.0e-5 * rate * rate;

  /**
   * Analytic branch lift model with quadratic friction in the liquid rate.
   *
   * @param coefficient friction coefficient in bar/(Sm3 liquid/day)^2
   * @return branch lift model
   */
  private static VlpProvider branch(final double coefficient) {
    return (rate, outlet, wct, gor, alq) -> {
      double liquid = rate / (1.0 - wct);
      return outlet + coefficient * liquid * liquid;
    };
  }

  /**
   * Builds a state proxy that declines with cumulative oil.
   *
   * @param name proxy name
   * @param gasOilRatio constant gas-oil ratio in Sm3/Sm3
   * @param waterCutEnd water cut at the end of the table
   * @return the proxy
   */
  private static ReservoirStateProxy state(String name, double gasOilRatio, double waterCutEnd) {
    return new ReservoirStateProxy(name, new double[] {0.0, 1.0e6, 2.0e6, 3.0e6},
        new double[] {250.0, 225.0, 205.0, 190.0})
        .setWaterCut(new double[] {0.0, waterCutEnd / 3.0, 2.0 * waterCutEnd / 3.0, waterCutEnd})
        .setGasOilRatio(new double[] {gasOilRatio, gasOilRatio, gasOilRatio, gasOilRatio})
        .setProductivityIndex(new double[] {20.0, 18.0, 16.0, 14.0});
  }

  /**
   * Builds a network with two manifolds and a common riser: W1 and W2 on M1, W3 on M2.
   *
   * @return the network with three wells
   */
  private static GatheringNetwork network() {
    GatheringNetwork network = new GatheringNetwork("SEP", 30.0);
    network.addBranch("RISER", "RB", "SEP", branch(1.5e-6));
    network.addBranch("FL1", "M1", "RB", branch(3.0e-6));
    network.addBranch("FL2", "M2", "RB", branch(4.0e-6));
    network.connectWell(new HybridWell("W1", state("W1", 150.0, 0.3), TUBING), "M1");
    network.connectWell(new HybridWell("W2", state("W2", 400.0, 0.1), TUBING), "M1");
    network.connectWell(new HybridWell("W3", state("W3", 200.0, 0.6), TUBING), "M2");
    return network;
  }

  /**
   * Finds a well by name.
   *
   * @param network the network
   * @param name well name
   * @return the well
   */
  private static HybridWell well(GatheringNetwork network, String name) {
    for (HybridWell well : network.getWells()) {
      if (well.getName().equals(name)) {
        return well;
      }
    }
    throw new IllegalArgumentException(name);
  }

  /** A single well on the boundary node reproduces the closed-form intersection of inflow and lift curve. */
  @Test
  void singleWellMatchesClosedFormOperatingPoint() {
    HybridWell single = new HybridWell("W", state("W", 150.0, 0.3), TUBING);
    double rate = single.solveOperatingPoint(30.0);
    // 250 - q/20 = 30 + 90 + 3e-5 q^2
    double expected = (-0.05 + Math.sqrt(0.05 * 0.05 + 4.0 * 3.0e-5 * 130.0)) / (2.0 * 3.0e-5);
    assertEquals(expected, rate, 1.0e-4);
    assertEquals(HybridWell.ControlMode.NETWORK_PRESSURE, single.getControlMode());
    assertEquals(250.0 - expected / 20.0, single.getBottomHolePressure(), 1.0e-6);
    assertEquals(expected, single.potentialOilRate(30.0), 1.0e-4);
  }

  /** A rate target below the potential is honoured and the surplus pressure appears upstream of the choke. */
  @Test
  void rateTargetIsHonouredAndChokeTakesSurplusPressure() {
    HybridWell single = new HybridWell("W", state("W", 150.0, 0.3), TUBING).setOilRateTarget(500.0);
    assertEquals(500.0, single.solveOperatingPoint(30.0), 1.0e-9);
    assertEquals(HybridWell.ControlMode.RATE_TARGET, single.getControlMode());
    // tubing outlet pressure that balances BHP = 250 - 25: 225 - 90 - 3e-5 * 500^2
    assertEquals(127.5, single.getWellheadPressure(), 1.0e-3);
    single.setOilRateTarget(1.0e9);
    single.solveOperatingPoint(30.0);
    assertEquals(HybridWell.ControlMode.NETWORK_PRESSURE, single.getControlMode());
  }

  /** A well cannot flow against a node pressure above its lift capability, and a closed well reports zero. */
  @Test
  void wellDiesAtHighBackPressureAndShutWellIsZero() {
    HybridWell single = new HybridWell("W", state("W", 150.0, 0.3), TUBING);
    assertEquals(0.0, single.solveOperatingPoint(170.0), 0.0);
    assertEquals(HybridWell.ControlMode.NO_FLOW, single.getControlMode());
    single.setOpen(false);
    assertEquals(0.0, single.solveOperatingPoint(30.0), 0.0);
    assertEquals(HybridWell.ControlMode.SHUT, single.getControlMode());
  }

  /** The minimum bottom-hole pressure limits a strong well and a fixed choke reduces the rate. */
  @Test
  void bottomHolePressureLimitAndFixedChoke() {
    HybridWell limited = new HybridWell("W", state("W", 150.0, 0.3), TUBING).setMinimumBottomHolePressure(240.0);
    assertEquals(200.0, limited.solveOperatingPoint(30.0), 1.0e-6);
    assertEquals(HybridWell.ControlMode.BHP_LIMIT, limited.getControlMode());
    HybridWell open = new HybridWell("W", state("W", 150.0, 0.3), TUBING);
    HybridWell choked = new HybridWell("W", state("W", 150.0, 0.3), TUBING).setChokeCoefficient(2.0e-5);
    double openRate = open.solveOperatingPoint(30.0);
    double chokedRate = choked.solveOperatingPoint(30.0);
    assertTrue(chokedRate < openRate);
    assertEquals(30.0 + 2.0e-5 * chokedRate * chokedRate, choked.getWellheadPressure(), 1.0e-6);
  }

  /** The coupled solution satisfies the inflow, lift and branch equations at every well and node. */
  @Test
  void coupledSolutionSatisfiesWellAndNetworkEquations() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    HybridStepResult result = model.solve();
    assertTrue(result.isConverged());
    assertTrue(result.getPressureResidualBar() < 1.0e-3);
    double liquidM1 = 0.0;
    double liquidAll = 0.0;
    for (HybridStepResult.WellResult w : result.getWells()) {
      assertEquals("NETWORK_PRESSURE", w.getControlMode());
      double offered = w.getReservoirPressure() - w.getOilRate() / w.getProductivityIndex();
      double needed = TUBING.bottomHolePressure(w.getOilRate(), w.getNodePressure(), w.getWaterCut(),
          w.getGasOilRatio(), 0.0);
      assertEquals(offered, needed, 1.0e-3);
      liquidAll += w.getOilRate() + w.getWaterRate();
      if ("M1".equals(w.getNode())) {
        liquidM1 += w.getOilRate() + w.getWaterRate();
      }
    }
    double riserBase = 30.0 + 1.5e-6 * liquidAll * liquidAll;
    assertEquals(riserBase, result.getNodePressures().get("RB"), 1.0e-6);
    assertEquals(riserBase + 3.0e-6 * liquidM1 * liquidM1, result.getNodePressures().get("M1"), 1.0e-6);
    assertEquals(result.getNodePressures().get("M1"), result.getWell("W1").getNodePressure(), 2.0e-3);
  }

  /** Choking one well lowers the network pressure and raises the rates of the other wells. */
  @Test
  void chokingOneWellRaisesTheOthersThroughTheNetwork() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    HybridStepResult base = model.solve();
    well(network, "W1").setOilRateTarget(0.5 * base.getWell("W1").getOilRate());
    HybridStepResult choked = model.solve();
    assertTrue(choked.isConverged());
    assertEquals(0.5 * base.getWell("W1").getOilRate(), choked.getWell("W1").getOilRate(), 1.0e-6);
    assertTrue(choked.getWell("W2").getOilRate() > base.getWell("W2").getOilRate());
    assertTrue(choked.getWell("W3").getOilRate() > base.getWell("W3").getOilRate());
    assertTrue(choked.getNodePressures().get("M1") < base.getNodePressures().get("M1"));
    // the gain on the same manifold is larger than on the other manifold
    double gainW2 = choked.getWell("W2").getOilRate() / base.getWell("W2").getOilRate();
    double gainW3 = choked.getWell("W3").getOilRate() / base.getWell("W3").getOilRate();
    assertTrue(gainW2 > gainW3);
    assertTrue(choked.getFieldOilRate() < base.getFieldOilRate());
  }

  /** A higher separator pressure lowers every rate, and re-routing a well changes the manifold pressures. */
  @Test
  void separatorPressureAndRoutingChangeTheRates() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    HybridStepResult base = model.solve();
    network.setBoundaryPressure(45.0);
    HybridStepResult highPressure = model.solve();
    for (HybridStepResult.WellResult w : highPressure.getWells()) {
      assertTrue(w.getOilRate() < base.getWell(w.getName()).getOilRate());
    }
    network.setBoundaryPressure(30.0);
    network.connectWell(well(network, "W2"), "M2");
    HybridStepResult rerouted = model.solve();
    assertTrue(rerouted.isConverged());
    assertEquals("M2", rerouted.getWell("W2").getNode());
    assertTrue(rerouted.getWell("W1").getOilRate() > base.getWell("W1").getOilRate());
    assertTrue(rerouted.getWell("W3").getOilRate() < base.getWell("W3").getOilRate());
  }

  /** The profile declines with depletion and the cumulative volumes equal the time integral of the rates. */
  @Test
  void profileDeclinesAndCumulativeVolumesAreConsistent() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    List<HybridStepResult> profile = model.run(720.0, 30.0);
    assertEquals(25, profile.size());
    assertEquals(720.0, profile.get(24).getTimeDays(), 1.0e-9);
    double integral = 0.0;
    for (int i = 1; i < profile.size(); i++) {
      assertTrue(profile.get(i).isConverged());
      assertTrue(profile.get(i).getFieldOilRate() < profile.get(i - 1).getFieldOilRate());
      integral += 0.5 * (profile.get(i).getFieldOilRate() + profile.get(i - 1).getFieldOilRate()) * 30.0;
    }
    double cumulative = 0.0;
    for (HybridStepResult.WellResult w : profile.get(24).getWells()) {
      cumulative += w.getCumulativeOil();
    }
    assertEquals(integral, cumulative, 2.0e-3 * integral);
    assertTrue(profile.get(24).getWell("W1").getWaterCut() > 0.0);
  }

  /** The predictor-corrector with a long step agrees with explicit Euler on a short step. */
  @Test
  void predictorCorrectorIsMoreAccurateThanEulerOnTheSameStep() {
    HybridProductionModel fine = new HybridProductionModel("fine", network()).setCorrector(false);
    List<HybridStepResult> reference = fine.run(720.0, 2.0);
    double exact = reference.get(reference.size() - 1).getFieldOilRate();
    HybridProductionModel euler = new HybridProductionModel("euler", network()).setCorrector(false);
    double eulerRate = euler.run(720.0, 60.0).get(12).getFieldOilRate();
    HybridProductionModel heun = new HybridProductionModel("heun", network()).setCorrector(true);
    double heunRate = heun.run(720.0, 60.0).get(12).getFieldOilRate();
    assertTrue(Math.abs(heunRate - exact) < 0.2 * Math.abs(eulerRate - exact));
    assertEquals(exact, heunRate, 2.0e-3 * exact);
  }

  /** The implicit integration reproduces a run whose rates and cumulative volumes satisfy the implicit relation. */
  @Test
  void implicitEulerSatisfiesTheSimulatorRelationBetweenRateAndCumulative() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network)
        .setTimeIntegration(HybridProductionModel.TimeIntegration.IMPLICIT_EULER);
    HybridStepResult previous = model.solve();
    for (int i = 0; i < 12; i++) {
      HybridStepResult next = model.step(30.0);
      assertTrue(next.isConverged());
      assertTrue(model.getLastTimeIterations() > 1 && model.getLastTimeIterations() < 15);
      for (HybridStepResult.WellResult w : next.getWells()) {
        double increment = w.getCumulativeOil() - previous.getWell(w.getName()).getCumulativeOil();
        assertEquals(w.getOilRate() * 30.0, increment, 1.0e-4 * increment);
      }
      previous = next;
    }
    assertEquals(HybridProductionModel.TimeIntegration.IMPLICIT_EULER, model.getTimeIntegration());
  }

  /** Wells that share a drainage unit see the pressure of the unit's total cumulative production. */
  @Test
  void drainageUnitFollowsTheCumulativeOfAllItsWells() {
    GatheringNetwork network = network();
    ReservoirStateProxy unit = new ReservoirStateProxy("unit", new double[] {0.0, 2.0e6, 4.0e6},
        new double[] {250.0, 210.0, 180.0});
    well(network, "W1").setDrainageUnit(unit);
    well(network, "W2").setDrainageUnit(unit);
    HybridProductionModel model = new HybridProductionModel("field", network);
    List<HybridStepResult> profile = model.run(360.0, 30.0);
    HybridStepResult last = profile.get(profile.size() - 1);
    double unitOil = last.getWell("W1").getCumulativeOil() + last.getWell("W2").getCumulativeOil();
    assertEquals(unitOil, unit.getCumulative(CumulativeQuantity.OIL_PRODUCED), 1.0e-6 * unitOil);
    assertEquals(unit.pressureAt(unitOil), last.getWell("W1").getReservoirPressure(), 1.0e-9);
    assertEquals(last.getWell("W1").getReservoirPressure(), last.getWell("W2").getReservoirPressure(), 1.0e-12);
  }

  /** Injection moves a scenario blend towards the high-injection scenario and keeps the rates up. */
  @Test
  void injectionRateMovesScenarioBlend() {
    double[] x = {0.0, 2.0e6, 4.0e6};
    ReservoirStateProxy low = new ReservoirStateProxy("low", x, new double[] {250.0, 200.0, 160.0})
        .setQuantityColumn(CumulativeQuantity.WATER_INJECTED, new double[] {0.0, 0.0, 0.0});
    ReservoirStateProxy high = new ReservoirStateProxy("high", x, new double[] {250.0, 245.0, 240.0})
        .setQuantityColumn(CumulativeQuantity.WATER_INJECTED, new double[] {0.0, 3.0e6, 6.0e6});
    ScenarioReservoirState unit = new ScenarioReservoirState("unit", CumulativeQuantity.WATER_INJECTED).addScenario(low)
        .addScenario(high);
    GatheringNetwork network = network();
    for (HybridWell w : network.getWells()) {
      w.setDrainageUnit(unit);
    }
    HybridProductionModel depletion = new HybridProductionModel("depletion", network);
    double depletionRate = depletion.run(720.0, 30.0).get(24).getFieldOilRate();
    assertEquals(0.0, unit.getBlendWeight(), 1.0e-12);

    ScenarioReservoirState supported = new ScenarioReservoirState("unit", CumulativeQuantity.WATER_INJECTED)
        .addScenario(low).addScenario(high);
    GatheringNetwork second = network();
    for (HybridWell w : second.getWells()) {
      w.setDrainageUnit(supported);
    }
    HybridProductionModel injection = new HybridProductionModel("injection", second).setInjectionRate(supported, 2500.0,
        0.0);
    HybridStepResult last = injection.run(720.0, 30.0).get(24);
    assertEquals(2500.0 * 720.0, supported.getCumulative(CumulativeQuantity.WATER_INJECTED), 1.0e-3);
    assertTrue(supported.getBlendWeight() > 0.3 && supported.getBlendWeight() < 1.0);
    assertTrue(last.getFieldOilRate() > depletionRate);
    assertFalse(last.isOutsideProxyValidity());
  }

  /** The hybrid mode reproduces the full network at correction steps with far fewer reference evaluations. */
  @Test
  void hybridModeTracksFullNetworkWithFewReferenceEvaluations() {
    GatheringNetwork fullNetwork = network();
    GatheringNetwork hybridNetwork = network();
    GatheringNetwork fastNetwork = network();
    for (GatheringNetwork n : Arrays.asList(fullNetwork, hybridNetwork)) {
      // the reference riser has 30 % more friction than the fast curve and the reference flowline 2 bar more head
      n.setReferenceModel("RISER", branch(1.95e-6));
      n.setReferenceModel("FL1",
          (rate, outlet, wct, gor, alq) -> branch(3.0e-6).bottomHolePressure(rate, outlet, wct, gor, alq) + 2.0);
    }
    HybridProductionModel full = new HybridProductionModel("full", fullNetwork)
        .setMode(HybridProductionModel.Mode.FULL_NETWORK);
    HybridProductionModel hybrid = new HybridProductionModel("hybrid", hybridNetwork)
        .setMode(HybridProductionModel.Mode.HYBRID).setHybridUpdateInterval(6);
    HybridProductionModel fast = new HybridProductionModel("fast", fastNetwork);
    List<HybridStepResult> fullProfile = full.run(720.0, 30.0);
    List<HybridStepResult> hybridProfile = hybrid.run(720.0, 30.0);
    List<HybridStepResult> fastProfile = fast.run(720.0, 30.0);
    assertTrue(hybridProfile.get(0).isCalibrated());
    assertTrue(hybridProfile.get(0).getLargestOffsetChangeBar() > 1.0);
    assertTrue(hybridProfile.get(6).isCalibrated());
    assertFalse(hybridProfile.get(5).isCalibrated());
    for (int i = 0; i < fullProfile.size(); i++) {
      double fullRate = fullProfile.get(i).getFieldOilRate();
      assertEquals(fullRate, hybridProfile.get(i).getFieldOilRate(), 2.0e-3 * fullRate);
      assertTrue(Math.abs(fastProfile.get(i).getFieldOilRate() - fullRate) > 5.0e-3 * fullRate);
    }
    assertEquals(fullProfile.get(12).getNodePressures().get("M1"), hybridProfile.get(12).getNodePressures().get("M1"),
        0.05);
    long hybridReference = hybridProfile.get(24).getReferenceEvaluations();
    long fullReference = fullProfile.get(24).getReferenceEvaluations();
    assertTrue(hybridReference > 0);
    assertTrue(hybridReference < fullReference / 10);
  }

  /** A gas capacity holds back the well with the highest gas-oil ratio first and is met within tolerance. */
  @Test
  void gasCapacityRestrictsHighestGasOilRatioWellFirst() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    HybridStepResult free = model.solve();
    double capacity = 0.8 * free.getFieldGasRate();
    model.setFacilityConstraints(new FacilityConstraints().setCapacity(FacilityConstraints.Quantity.GAS, capacity));
    HybridStepResult limited = model.solve();
    assertTrue(limited.isConverged());
    assertEquals("GAS", limited.getBindingConstraint());
    assertEquals(capacity, limited.getFieldGasRate(), 1.5e-3 * capacity);
    assertEquals("FACILITY_LIMIT", limited.getWell("W2").getControlMode());
    assertTrue(limited.getWell("W2").getOilRate() < free.getWell("W2").getOilRate());
    assertEquals("NETWORK_PRESSURE", limited.getWell("W1").getControlMode());
    assertTrue(limited.getWell("W1").getOilRate() > free.getWell("W1").getOilRate());
    // a tight capacity closes the first well and continues with the next
    model.setFacilityConstraints(
        new FacilityConstraints().setCapacity(FacilityConstraints.Quantity.GAS, 0.25 * free.getFieldGasRate()));
    HybridStepResult tight = model.solve();
    assertEquals(0.0, tight.getWell("W2").getOilRate(), 0.0);
    assertTrue(tight.getFieldGasRate() <= 0.25 * free.getFieldGasRate() * 1.0015);
    assertTrue(tight.getAllocationPasses() >= 2);
  }

  /** Wells with the same gas-oil ratio share a gas-capacity restriction in proportion to their rates. */
  @Test
  void gasCapacityIsSharedByWellsWithEqualGasOilRatio() {
    GatheringNetwork network = new GatheringNetwork("SEP", 30.0);
    network.addBranch("RISER", "RB", "SEP", branch(1.5e-6));
    network.addBranch("FL1", "M1", "RB", branch(3.0e-6));
    network.addBranch("FL2", "M2", "RB", branch(4.0e-6));
    network.connectWell(new HybridWell("W1", state("W1", 200.0, 0.3), TUBING), "M1");
    network.connectWell(new HybridWell("W2", state("W2", 201.0, 0.1), TUBING), "M1");
    network.connectWell(new HybridWell("W3", state("W3", 150.0, 0.2), TUBING), "M2");
    HybridProductionModel model = new HybridProductionModel("field", network);
    HybridStepResult free = model.solve();
    double capacity = 0.8 * free.getFieldGasRate();
    model.setFacilityConstraints(new FacilityConstraints().setCapacity(FacilityConstraints.Quantity.GAS, capacity));
    HybridStepResult shared = model.solve();
    assertEquals(capacity, shared.getFieldGasRate(), 1.5e-3 * capacity);
    assertEquals("FACILITY_LIMIT", shared.getWell("W1").getControlMode());
    assertEquals("FACILITY_LIMIT", shared.getWell("W2").getControlMode());
    assertEquals("NETWORK_PRESSURE", shared.getWell("W3").getControlMode());
    double fraction1 = shared.getWell("W1").getOilRate() / free.getWell("W1").getOilRate();
    double fraction2 = shared.getWell("W2").getOilRate() / free.getWell("W2").getOilRate();
    assertEquals(fraction1, fraction2, 1.0e-9);
    assertTrue(fraction1 > 0.0 && fraction1 < 1.0);
    // without the tie rule one of the two wells takes the whole restriction
    model.setAllocationTieTolerance(0.0);
    HybridStepResult single = model.solve();
    assertEquals(capacity, single.getFieldGasRate(), 1.5e-3 * capacity);
    double single1 = single.getWell("W1").getOilRate() / free.getWell("W1").getOilRate();
    double single2 = single.getWell("W2").getOilRate() / free.getWell("W2").getOilRate();
    assertTrue(Math.abs(single1 - single2) > 0.05);
  }

  /** A new simulator run replaces a well proxy without losing the cumulative volumes. */
  @Test
  void refreshedProxyKeepsCumulativeVolumes() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    model.run(360.0, 30.0);
    HybridWell w1 = well(network, "W1");
    double produced = w1.getState().getCumulative(CumulativeQuantity.OIL_PRODUCED);
    double before = model.solve().getWell("W1").getOilRate();
    ReservoirStateProxy rerun = new ReservoirStateProxy("W1 rerun", new double[] {0.0, 1.0e6, 2.0e6, 3.0e6},
        new double[] {250.0, 235.0, 220.0, 205.0}).setWaterCut(new double[] {0.0, 0.05, 0.1, 0.15})
        .setGasOilRatio(new double[] {150.0, 150.0, 150.0, 150.0})
        .setProductivityIndex(new double[] {20.0, 19.0, 18.0, 17.0});
    w1.refreshState(rerun);
    assertEquals(produced, w1.getState().getCumulative(CumulativeQuantity.OIL_PRODUCED), 1.0e-9);
    assertTrue(model.solve().getWell("W1").getOilRate() > before);
  }

  /**
   * Builds an SRK fluid for the feed and flowline tests.
   *
   * @param names component names
   * @param fractions mole fractions
   * @return fluid with mixing rule, initialised
   */
  private static SystemInterface fluid(String[] names, double[] fractions) {
    SystemInterface system = new SystemSrkEos(288.15, 1.01325);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], fractions[i]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    system.init(0);
    return system;
  }

  /**
   * Builds a recombination factory on the oil basis.
   *
   * @return factory for oil rate, water cut and gas-oil ratio
   */
  private static RecombinationFluidFactory factory() {
    String[] all = {"methane", "ethane", "propane", "n-heptane", "nC10", "water"};
    SystemInterface template = fluid(all, new double[] {0.4, 0.05, 0.05, 0.2, 0.3, 1.0e-6});
    SystemInterface gas = fluid(new String[] {"methane", "ethane", "propane"}, new double[] {0.85, 0.09, 0.06});
    SystemInterface oil = fluid(new String[] {"propane", "n-heptane", "nC10"}, new double[] {0.02, 0.40, 0.58});
    return new RecombinationFluidFactory(template, gas, oil).setRateBasis("OIL").setWaterRatioType("WCT")
        .setGasRatioType("GOR");
  }

  /** The molar feed carries the solved gas, oil and water of each well and adds up over the wells. */
  @Test
  void molarFeedCarriesWellRatesAsComponentMolarRates() {
    GatheringNetwork network = network();
    HybridProductionModel model = new HybridProductionModel("field", network);
    model.run(360.0, 30.0);
    MolarFeedAdapter adapter = new MolarFeedAdapter(factory());
    HybridWell w3 = well(network, "W3");
    SystemInterface feed = adapter.wellFeed(w3);
    assertNotNull(feed);
    double[] rates = MolarFeedAdapter.componentMolarRates(feed);
    String[] names = MolarFeedAdapter.componentNames(feed);
    int water = Arrays.asList(names).indexOf("water");
    int methane = Arrays.asList(names).indexOf("methane");
    assertEquals(w3.getWaterRate() * 1000.0 / 0.018015 / 86400.0, rates[water], 1.0e-6 * rates[water]);
    double gasMoles = w3.getGasRate() * RecombinationFluidFactory.getStandardGasMolarDensity() / 86400.0;
    assertEquals(0.85 * gasMoles, rates[methane], 1.0e-6 * rates[methane]);
    SystemInterface field = adapter.commingledFeed(network.getWells());
    double[] fieldRates = MolarFeedAdapter.componentMolarRates(field);
    double expected = 0.0;
    for (HybridWell w : network.getWells()) {
      expected += MolarFeedAdapter.componentMolarRates(adapter.wellFeed(w))[methane];
    }
    assertEquals(expected, fieldRates[methane], 1.0e-9 * expected);
    w3.setOpen(false);
    w3.solveOperatingPoint(30.0);
    assertNull(adapter.wellFeed(w3));
  }

  /** A state proxy with a composition feeds the hydrocarbon composition directly, plus the produced water. */
  @Test
  void molarFeedUsesCompositionOfTheStateProxy() {
    ReservoirStateProxy state = state("W", 150.0, 0.3).setComposition(new String[] {"methane", "nC10"},
        new double[][] {{0.6, 0.4}, {0.6, 0.4}, {0.7, 0.3}, {0.8, 0.2}},
        new double[] {9000.0, 9000.0, 9500.0, 10000.0});
    HybridWell single = new HybridWell("W", state, TUBING);
    state.produce(1.5e6, 0.0, 0.0);
    single.solveOperatingPoint(30.0);
    SystemInterface template = fluid(new String[] {"methane", "nC10", "water"}, new double[] {0.5, 0.5, 1.0e-6});
    SystemInterface feed = new MolarFeedAdapter(null, template).wellFeed(single);
    double[] rates = MolarFeedAdapter.componentMolarRates(feed);
    double hydrocarbon = single.getOilRate() * 9250.0 / 86400.0;
    assertEquals(0.65 * hydrocarbon, rates[0], 1.0e-6 * rates[0]);
    assertEquals(0.35 * hydrocarbon, rates[1], 1.0e-6 * rates[1]);
    assertEquals(single.getWaterRate() * 1000.0 / 0.018015 / 86400.0, rates[2], 1.0e-6 * rates[2]);
  }

  /** The flowline lift model returns an inlet pressure that the forward pipe march brings back to the outlet. */
  @Test
  void flowlineLiftModelInvertsThePipeMarch() {
    FlowlineVlpProvider flowline = new FlowlineVlpProvider(factory()).addSegment(3000.0, 0.0, 0.2)
        .addSegment(200.0, 200.0, 0.2).setInletTemperature(50.0).setAmbient(5.0, 5.0).setIncrementsPerSegment(5);
    double low = flowline.bottomHolePressure(1000.0, 30.0, 0.2, 150.0, 0.0);
    double high = flowline.bottomHolePressure(3000.0, 30.0, 0.2, 150.0, 0.0);
    assertTrue(low > 30.0);
    assertTrue(high > low);
    assertEquals(30.0, flowline.forward(3000.0, high, 0.2, 150.0, 0.0), 0.05);
    assertTrue(flowline.getPipeRuns() > 2);
    assertTrue(flowline.getLastOutletTemperatureC() < 50.0);
  }
}
