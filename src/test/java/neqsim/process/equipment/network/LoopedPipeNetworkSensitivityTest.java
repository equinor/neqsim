package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Reservoir-pressure sweep regression coverage for issue #3626. */
class LoopedPipeNetworkSensitivityTest {
  @Test
  void reservoirSweepMatchesIndependentlyConfiguredNetworksAndRestoresBaseline() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("sweep"), 120.0);
    network.run();
    double originalFlow = network.getTotalSinkFlow();
    double[] pressures = { 80.0, 100.0, 140.0, 180.0 };
    Map<String, double[]> result = network.sensitivityAnalysis("ipr", "reservoir_pressure", pressures);
    double previous = 0.0;
    for (int i = 0; i < pressures.length; i++) {
      LoopedPipeNetwork reference = network(new LoopedPipeNetwork("reference"), pressures[i]);
      reference.run();
      assertTrue(reference.isProductionOptimizationApplicable());
      double flow = result.get("totalFlow_kghr")[i];
      assertEquals(reference.getTotalSinkFlow() * 3600.0, flow, 1e-2,
          "Sweep must update the fixed source boundary at " + pressures[i] + " bara");
      assertEquals(flow, result.get("objective")[i], 1e-8);
      assertEquals(1.0, result.get("converged")[i]);
      assertEquals(1.0, result.get("applicable")[i]);
      assertEquals(1.0, result.get("valid")[i]);
      assertTrue(flow > previous, "Production must increase with reservoir pressure");
      previous = flow;
    }
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(120.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
    assertEquals(originalFlow, network.getTotalSinkFlow(), 1e-6);
    assertTrue(network.getSensitivityFailures().isEmpty());

    Map<String, double[]> reverse = network.sensitivityAnalysis("reservoir", "reservoir_pressure",
        new double[] { 180.0, 140.0, 100.0, 80.0 });
    for (int i = 0; i < pressures.length; i++) {
      assertEquals(result.get("totalFlow_kghr")[i], reverse.get("totalFlow_kghr")[3 - i], 1e-2);
    }
    assertEquals(originalFlow, network.getTotalSinkFlow(), 1e-6);
  }

  @Test
  void failedPointIsNotReportedAsZeroProduction() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("injected failure") {
      private int calls;

      @Override
      public void run(UUID id) {
        if (++calls == 2) {
          throw new IllegalStateException("injected sample failure");
        }
        super.run(id);
      }
    }, 120.0);
    Map<String, double[]> result = network.sensitivityAnalysis("ipr", "reservoir_pressure",
        new double[] { 100.0, 140.0, 180.0 });
    assertTrue(Double.isNaN(result.get("totalFlow_kghr")[1]));
    assertTrue(Double.isNaN(result.get("objective")[1]));
    assertEquals(0.0, result.get("valid")[1]);
    assertEquals(0.0, result.get("converged")[1], "A thrown solve must not reuse previous convergence");
    assertEquals(0.0, result.get("applicable")[1]);
    assertTrue(network.getSensitivityFailures().get(1).contains("injected sample failure"));
    assertTrue(Double.isFinite(result.get("totalFlow_kghr")[2]), "Sweep must continue after a failed sample");
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(120.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
  }

  @Test
  void sharedSourceUpdatesAllIprsAndRestoresTheirDistinctOriginalSettings() {
    final List<double[]> boundaries = new ArrayList<>();
    final boolean[] failSample = { false };
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("shared source") {
      @Override
      public void run(UUID id) {
        boundaries.add(new double[] { getNodePressure("reservoir"), getPipe("ipr").getReservoirPressure(),
            getPipe("second ipr").getReservoirPressure(), getNodePressure("other reservoir"),
            getPipe("other ipr").getReservoirPressure() });
        if (failSample[0] && getNodePressure("reservoir") == 180.0) {
          throw new IllegalStateException("injected shared-source failure");
        }
        super.run(id);
      }
    }, 120.0);
    network.addWellIPR("reservoir", "wellhead", "second ipr", 2e-5, false);
    network.getPipe("ipr").setReservoirPressure(115.0e5);
    network.getPipe("second ipr").setReservoirPressure(125.0e5);
    network.addSourceNode("other reservoir", 160.0, 0.0);
    network.addWellIPR("other reservoir", "arrival", "other ipr", 1e-6, false);
    network.getPipe("other ipr").setReservoirPressure(155.0e5);
    network.sensitivityAnalysis("ipr", "reservoir_pressure", new double[] { 80.0, 180.0 });
    assertEquals(3, boundaries.size());
    assertArrayEquals(new double[] { 80.0, 80.0e5, 80.0e5, 160.0, 155.0e5 }, boundaries.get(0), 1e-6);
    assertArrayEquals(new double[] { 180.0, 180.0e5, 180.0e5, 160.0, 155.0e5 }, boundaries.get(1), 1e-6);
    assertArrayEquals(new double[] { 120.0, 115.0e5, 125.0e5, 160.0, 155.0e5 }, boundaries.get(2), 1e-6);
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(115.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
    assertEquals(125.0e5, network.getPipe("second ipr").getReservoirPressure(), 1e-6);

    boundaries.clear();
    failSample[0] = true;
    Map<String, double[]> failed = network.sensitivityAnalysis("ipr", "reservoir_pressure",
        new double[] { 80.0, 180.0 });
    assertTrue(Double.isNaN(failed.get("totalFlow_kghr")[1]));
    assertTrue(network.getSensitivityFailures().get(1).contains("shared-source failure"));
    assertArrayEquals(new double[] { 120.0, 115.0e5, 125.0e5, 160.0, 155.0e5 }, boundaries.get(2), 1e-6);
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(115.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
    assertEquals(125.0e5, network.getPipe("second ipr").getReservoirPressure(), 1e-6);
  }

  @Test
  void baselineSolveFailureStillRestoresSourceAndIprInputs() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("restoration failure") {
      @Override
      public void run(UUID id) {
        if (getNodePressure("reservoir") == 120.0) {
          throw new IllegalStateException("injected baseline failure");
        }
        super.run(id);
      }
    }, 120.0);
    network.getPipe("ipr").setReservoirPressure(115.0e5);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> network.sensitivityAnalysis("ipr", "reservoir_pressure", new double[] { 100.0, 140.0 }));
    assertEquals("injected baseline failure", failure.getMessage());
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(115.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
  }

  @Test
  void invalidSamplesKeepFailureEvidenceAndDoNotPreventLaterSamples() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("invalid values"), 120.0);
    double[] pressures = { Double.NaN, Double.POSITIVE_INFINITY, 0.0, -10.0, 140.0 };
    Map<String, double[]> result = network.sensitivityAnalysis("ipr", "reservoir_pressure", pressures);
    Map<Integer, String> failures = network.getSensitivityFailures();
    assertEquals(4, failures.size());
    for (int i = 0; i < 4; i++) {
      assertTrue(Double.isNaN(result.get("totalFlow_kghr")[i]));
      assertTrue(Double.isNaN(result.get("objective")[i]));
      assertEquals(0.0, result.get("valid")[i]);
      assertTrue(failures.get(i).contains("IllegalArgumentException"));
    }
    assertEquals(1.0, result.get("valid")[4]);
    assertEquals(120.0, network.getNodePressure("reservoir"), 1e-10);
    assertEquals(120.0e5, network.getPipe("ipr").getReservoirPressure(), 1e-6);
    assertThrows(UnsupportedOperationException.class, () -> failures.clear());
    pressures[4] = 200.0;
    assertEquals(140.0, result.get("paramValues")[4]);
    assertTrue(network.sensitivityAnalysis("ipr", "reservoir_pressure", null).isEmpty());
    assertTrue(network.getSensitivityFailures().isEmpty());
    assertEquals(4, failures.size(), "Returned diagnostics must remain a snapshot after the next sweep");
  }

  @Test
  void nonconvergenceAndUnsupportedChokeEvidenceRemainDistinct() {
    LoopedPipeNetwork nonconverged = network(new LoopedPipeNetwork("nonconverged"), 120.0);
    nonconverged.setMaxIterations(0);
    Map<String, double[]> failed = nonconverged.sensitivityAnalysis("ipr", "reservoir_pressure",
        new double[] { 100.0 });
    assertEquals(0.0, failed.get("converged")[0]);
    assertEquals(0.0, failed.get("valid")[0]);
    assertTrue(Double.isNaN(failed.get("totalFlow_kghr")[0]));
    assertTrue(nonconverged.getSensitivityFailures().get(0).contains("did not converge"));

    LoopedPipeNetwork unsupported = network(new LoopedPipeNetwork("unsupported screening"), 120.0);
    unsupported.getPipe("choke").setChokeUseValveModel(false);
    unsupported.getPipe("choke").setChokeKv(1.0);
    Map<String, double[]> rejected = unsupported.sensitivityAnalysis("ipr", "reservoir_pressure",
        new double[] { 100.0 });
    assertEquals(1.0, rejected.get("converged")[0]);
    assertEquals(0.0, rejected.get("applicable")[0]);
    assertEquals(0.0, rejected.get("valid")[0]);
    assertTrue(Double.isNaN(rejected.get("objective")[0]));
    assertTrue(Double.isNaN(rejected.get("totalFlow_kghr")[0]));
    assertTrue(unsupported.getSensitivityFailures().get(0).contains("choke=UNSUPPORTED_CRITICAL_FLOW"));
  }

  @Test
  void chokeSweepPreservesGenuineZeroFlowAndRestoresClosedChoke() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("closed choke"), 120.0);
    network.getPipe("choke").setChokeOpening(0.0);
    Map<String, double[]> result = network.sensitivityAnalysis("choke", "choke_opening",
        new double[] { 0.0, 20.0, 60.0, -1.0, 101.0 });
    assertEquals(1.0, result.get("valid")[0]);
    assertEquals(0.0, result.get("totalFlow_kghr")[0], 1e-3);
    assertTrue(result.get("totalFlow_kghr")[2] > result.get("totalFlow_kghr")[1]);
    assertTrue(Double.isNaN(result.get("objective")[3]));
    assertTrue(Double.isNaN(result.get("objective")[4]));
    assertEquals(0.0, network.getPipe("choke").getChokeOpening());
    assertEquals(0.0, network.getTotalSinkFlow(), 1e-6);
    assertTrue(network.isProductionOptimizationApplicable());
  }

  @Test
  void unsupportedTargetsAndFeedControlledBoundariesAreRejectedWithoutMutation() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("invalid targets"), 120.0);
    for (String target : new String[] { "missing", "choke", "arrival", "wellhead" }) {
      assertThrows(IllegalArgumentException.class,
          () -> network.sensitivityAnalysis(target, "reservoir_pressure", new double[] { 100.0 }));
    }
    assertThrows(IllegalArgumentException.class,
        () -> network.sensitivityAnalysis("ipr", "typo", new double[] { 100.0 }));
    Stream feed = new Stream("reservoir feed", new SystemSrkEos(313.15, 120.0));
    network.setFeedStream("reservoir", feed);
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> network.sensitivityAnalysis("ipr", "reservoir_pressure", new double[] { 100.0 }));
    assertTrue(failure.getMessage().contains("feed stream"));
    assertEquals(120.0, network.getNodePressure("reservoir"));
    assertEquals(120.0e5, network.getPipe("ipr").getReservoirPressure());
    assertFalse(network.isConverged());
  }

  @Test
  void sinkPressurePiAndDiameterSweepsStillMatchDirectConfiguration() {
    for (String parameter : new String[] { "sink_pressure", "well_pi", "pipe_diameter" }) {
      LoopedPipeNetwork network = network(new LoopedPipeNetwork("other parameters"), 120.0);
      String target;
      double[] values;
      if ("sink_pressure".equals(parameter)) {
        target = "arrival";
        values = new double[] { 90.0, 60.0, 20.0 };
      } else if ("well_pi".equals(parameter)) {
        target = "ipr";
        values = new double[] { 1e-6, 5e-6, 2e-5 };
      } else {
        network.addPipe("wellhead", "arrival", "bypass", 1000.0, 0.05, 5e-5);
        target = "bypass";
        values = new double[] { 0.03, 0.05, 0.08 };
      }
      network.run();
      double baseline = network.getTotalSinkFlow();
      Map<String, double[]> results = network.sensitivityAnalysis(target, parameter, values);
      for (int i = 0; i < values.length; i++) {
        LoopedPipeNetwork reference = network(new LoopedPipeNetwork("direct reference"), 120.0);
        if ("sink_pressure".equals(parameter)) {
          reference.setNodePressure("arrival", values[i]);
        } else if ("well_pi".equals(parameter)) {
          reference.getPipe("ipr").setProductivityIndex(values[i]);
        } else {
          reference.addPipe("wellhead", "arrival", "bypass", 1000.0, values[i], 5e-5);
        }
        reference.run();
        assertTrue(reference.isProductionOptimizationApplicable());
        assertEquals(1.0, results.get("valid")[i]);
        assertEquals(reference.getTotalSinkFlow() * 3600.0, results.get("totalFlow_kghr")[i], 1e-2);
      }
      assertEquals(20.0, network.getNodePressure("arrival"), 1e-10);
      assertEquals(1e-5, network.getPipe("ipr").getProductivityIndex());
      if ("pipe_diameter".equals(parameter)) {
        assertEquals(0.05, network.getPipe("bypass").getDiameter());
      }
      assertEquals(baseline, network.getTotalSinkFlow(), 1e-6);
    }
  }

  @Test
  void documentedIprConstructionAndPressureUnitsMatchPublicApi() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("documented IPR setup");
    network.addSourceNode("reservoir", 350.0, 0.0);
    network.addJunctionNode("wellhead");
    network.addWellIPR("reservoir", "wellhead", "ipr1", 5e-7, false);
    network.addWellIPRVogel("reservoir", "wellhead", "vogel1", 50.0);
    network.addWellIPRFetkovich("reservoir", "wellhead", "fetk1", 1e-12, 0.8);
    for (String name : new String[] { "ipr1", "vogel1", "fetk1" }) {
      assertEquals(350.0e5, network.getPipe(name).getReservoirPressure());
    }
    network.setReservoirPressure("reservoir", 300.0);
    assertEquals(300.0, network.getNodePressure("reservoir"));
    for (String name : new String[] { "ipr1", "vogel1", "fetk1" }) {
      assertEquals(300.0e5, network.getPipe(name).getReservoirPressure());
    }
  }

  @Test
  void nonFiniteObjectiveIsRejectedDespiteConvergenceAndApplicability() {
    LoopedPipeNetwork network = network(new LoopedPipeNetwork("non-finite objective"), 120.0);
    network.setWellPrice("ipr", Double.NaN);
    Map<String, double[]> result = network.sensitivityAnalysis("ipr", "reservoir_pressure", new double[] { 100.0 });
    assertEquals(1.0, result.get("converged")[0]);
    assertEquals(1.0, result.get("applicable")[0]);
    assertEquals(0.0, result.get("valid")[0]);
    assertTrue(Double.isNaN(result.get("totalFlow_kghr")[0]));
    assertTrue(Double.isNaN(result.get("objective")[0]));
    assertEquals("Non-finite flow or objective", network.getSensitivityFailures().get(0));
  }

  private static LoopedPipeNetwork network(LoopedPipeNetwork network, double pressure) {
    SystemInterface fluid = new SystemSrkEos(313.15, pressure);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.01);
    network.setMaxIterations(150);
    network.addSourceNode("reservoir", pressure, 0.0);
    network.addJunctionNode("wellhead");
    network.getNode("wellhead").setTemperature(313.15);
    network.addFixedPressureSinkNode("arrival", 20.0);
    network.getNode("arrival").setTemperature(313.15);
    network.addWellIPR("reservoir", "wellhead", "ipr", 1e-5, false);
    network.addChoke("wellhead", "arrival", "choke", 10.0, 60.0).setChokeUseValveModel(true);
    return network;
  }
}
