package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermo.system.SystemUMRPRUMCEosNew;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Physical output, initialization and convergence regressions for issue #3739. */
class UMRPRUPhaseEnvelopeRegressionTest {
  private SystemInterface gas(String model, String heavy, double fraction) {
    SystemInterface fluid = "SRK".equals(model) ? new SystemSrkEos(288.15, 50.0)
        : "MC".equals(model) ? new SystemUMRPRUMCEos(288.15, 50.0) : new SystemUMRPRUMCEosNew(288.15, 50.0);
    fluid.addComponent("methane", 0.90 * (1.0 - fraction));
    fluid.addComponent("ethane", 0.07 * (1.0 - fraction));
    fluid.addComponent("propane", 0.03 * (1.0 - fraction));
    if (fraction > 0.0) {
      fluid.addComponent(heavy, fraction);
    }
    if ("SRK".equals(model)) {
      fluid.setMixingRule("classic");
    } else {
      fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    }
    return fluid;
  }

  @ParameterizedTest
  @CsvSource({ "NEW,none,0.0,false", "NEW,none,0.0,true", "MC,none,0.0,false", "SRK,none,0.0,false",
      "SRK,none,0.0,true", "NEW,n-heptane,0.01,false" })
  void gasHasBothPhysicalBranches(String model, String heavy, double fraction, boolean bubbleFirst) throws Exception {
    SystemInterface fluid = gas(model, heavy, fraction);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope(bubbleFirst);
    PTPhaseEnvelopeMichelsen envelope = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(envelope.isEnvelopeClosed());
    assertArrayEquals(ops.get("dewT"), envelope.getDewPointTemperatures());
    assertArrayEquals(ops.get("bubT"), envelope.getBubblePointTemperatures());
    for (String branch : new String[] { "dew", "bub" }) {
      int count = 0;
      int lowIndex = -1;
      double[] temperatures = ops.get(branch + "T");
      double[] pressures = ops.get(branch + "P");
      assertEquals(temperatures.length, pressures.length);
      for (int i = 0; i < temperatures.length; i++) {
        if (Double.isNaN(temperatures[i]) && Double.isNaN(pressures[i])) {
          continue;
        }
        assertTrue(Double.isFinite(temperatures[i]) && temperatures[i] > 80.0 && temperatures[i] < 550.0,
            branch + " temperature " + temperatures[i]);
        assertTrue(Double.isFinite(pressures[i]) && pressures[i] > 0.0 && pressures[i] < 300.0,
            branch + " pressure " + pressures[i]);
        if (lowIndex < 0 || pressures[i] < pressures[lowIndex]) {
          lowIndex = i;
        }
        count++;
      }
      assertTrue(count > 10 && count < 500, branch + " physical point count " + count);
      // Independently solve a low-pressure saturation point to check the physical branch labels.
      SystemInterface saturation = gas(model, heavy, fraction);
      saturation.setPressure(pressures[lowIndex]);
      saturation.setTemperature(temperatures[lowIndex] + 0.1);
      ThermodynamicOperations saturationOps = new ThermodynamicOperations(saturation);
      if ("dew".equals(branch)) {
        saturationOps.dewPointTemperatureFlash();
      } else {
        saturationOps.bubblePointTemperatureFlash();
      }
      assertEquals(saturation.getTemperature(), temperatures[lowIndex], 1.0e-3, branch + " saturation temperature");
    }
    assertTrue(ops.get("cricondentherm")[0] > 200.0 && ops.get("cricondentherm")[0] < 550.0);
    assertTrue(ops.get("cricondenbar")[1] > 40.0 && ops.get("cricondenbar")[1] < 300.0);
  }

  @ParameterizedTest
  @CsvSource({ "cumene,0.005", "cumene,0.01", "cumene,0.05", "toluene,0.01" })
  void truncatedAromaticTraceCannotReportExtrema(String component, double fraction) {
    ThermodynamicOperations ops = new ThermodynamicOperations(gas("NEW", component, fraction));
    IllegalStateException failure = assertThrows(IllegalStateException.class, ops::calcPTphaseEnvelope);
    assertTrue(failure.getMessage().contains("limit"));
    PTPhaseEnvelopeMichelsen envelope = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(!envelope.isEnvelopeClosed());
    assertTrue(Arrays.stream(ops.get("cricondentherm")).allMatch(Double::isNaN));
    assertTrue(Arrays.stream(ops.get("cricondenbar")).allMatch(Double::isNaN));
    for (String branch : new String[] { "dew", "bub" }) {
      double[] temperatures = ops.get(branch + "T");
      double[] pressures = ops.get(branch + "P");
      assertTrue(Arrays.stream(temperatures).filter(Double::isFinite).count() > 10);
      for (int i = 0; i < temperatures.length; i++) {
        if (Double.isNaN(temperatures[i])) {
          assertTrue(Double.isNaN(pressures[i]));
        } else {
          assertTrue(temperatures[i] > 80.0 && temperatures[i] < 550.0);
          assertTrue(pressures[i] > 0.0 && pressures[i] <= 1000.0);
        }
      }
    }
  }

  @Test
  void phaseFractionChangeDoesNotRequireRepeatedInitialization() throws Exception {
    SystemInterface fluid = gas("NEW", "none", 0.0);
    fluid.setTemperature(175.0);
    fluid.setPressure(1.0);
    new ThermodynamicOperations(fluid).dewPointTemperatureFlash();
    for (double beta : new double[] { 1.0 - 1.0e-10, 0.3, 1.0e-10 }) {
      fluid.setBeta(beta);
      fluid.calc_x_y();
      fluid.init(3);
      double[][] first = new double[2][3];
      for (int phase = 0; phase < 2; phase++) {
        for (int i = 0; i < 3; i++) {
          first[phase][i] = fluid.getPhase(phase).getComponent(i).getLogFugacityCoefficient();
        }
      }
      fluid.init(3);
      for (int phase = 0; phase < 2; phase++) {
        for (int i = 0; i < 3; i++) {
          assertEquals(first[phase][i], fluid.getPhase(phase).getComponent(i).getLogFugacityCoefficient(), 1.0e-10,
              "first initialization after beta=" + beta);
        }
      }
    }
  }

  @Test
  void acceptedContinuationPointsSatisfyEquilibrium() throws Exception {
    SystemInterface fluid = gas("NEW", "none", 0.0);
    fluid.setTemperature(175.0);
    fluid.setPressure(1.0);
    new ThermodynamicOperations(fluid).dewPointTemperatureFlash();
    fluid.setBeta(1.0 - 1.0e-10);
    SysNewtonRhapsonPhaseEnvelope solver = new SysNewtonRhapsonPhaseEnvelope(fluid, 2, 3);
    for (int point = 1; point <= 20; point++) {
      solver.calcInc(point);
      solver.solve(point);
      solver.init();
      solver.setfvec();
      assertTrue(solver.fvec.norm2() < 1.0e-8, "equilibrium residual at point " + point);
      for (int component = 0; component < 3; component++) {
        double recovered = fluid.getBeta(0) * fluid.getPhase(0).getComponent(component).getx()
            + fluid.getBeta(1) * fluid.getPhase(1).getComponent(component).getx();
        assertEquals(fluid.getComponent(component).getz(), recovered, 1.0e-10);
      }
    }
  }

  @Test
  void criticalRefinementPreservesTheAcceptedEquilibriumState() throws Exception {
    SystemInterface fluid = gas("NEW", "none", 0.0);
    fluid.setTemperature(175.0);
    fluid.setPressure(1.0);
    new ThermodynamicOperations(fluid).dewPointTemperatureFlash();
    fluid.setBeta(1.0 - 1.0e-10);
    SysNewtonRhapsonPhaseEnvelope solver = new SysNewtonRhapsonPhaseEnvelope(fluid, 2, 3);
    for (int point = 1; point <= 20; point++) {
      solver.calcInc(point);
      solver.solve(point);
    }
    SystemInterface accepted = fluid.clone();
    double[] state = solver.u.getColumnPackedCopy();
    solver.calcCrit();
    assertArrayEquals(state, solver.u.getColumnPackedCopy());
    assertEquals(accepted.getTemperature(), fluid.getTemperature(), 0.0);
    assertEquals(accepted.getPressure(), fluid.getPressure(), 0.0);
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(accepted.getPhase(phase).getMolarVolume(), fluid.getPhase(phase).getMolarVolume(), 0.0);
      for (int component = 0; component < 3; component++) {
        assertEquals(accepted.getPhase(phase).getComponent(component).getx(),
            fluid.getPhase(phase).getComponent(component).getx(), 0.0);
        assertEquals(accepted.getPhase(phase).getComponent(component).getLogFugacityCoefficient(),
            fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 0.0);
      }
    }
    solver.setfvec();
    assertTrue(solver.fvec.norm2() < 1.0e-8, "critical refinement must not contaminate the accepted residual");
  }

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void configuredPressureLimitCannotFabricateExtrema(boolean bubbleFirst) {
    PTPhaseEnvelopeMichelsen envelope = new PTPhaseEnvelopeMichelsen(gas("NEW", "none", 0.0), null,
        bubbleFirst ? 1.0e-10 : 1.0 - 1.0e-10, 1.0, bubbleFirst);
    envelope.setMaxPressure(20.0);
    assertThrows(IllegalStateException.class, envelope::run);
    assertTrue(!envelope.isEnvelopeClosed());
    assertTrue(Arrays.stream(envelope.get("cricondenbar")).allMatch(Double::isNaN));
    assertTrue(Arrays.stream(envelope.get("cricondentherm")).allMatch(Double::isNaN));
    for (String branch : new String[] { "dew", "bub" }) {
      assertTrue(Arrays.stream(envelope.get(branch + "P")).filter(Double::isFinite).count() > 10);
      assertTrue(Arrays.stream(envelope.get(branch + "P")).filter(Double::isFinite)
          .allMatch(pressure -> pressure > 0.0 && pressure <= 20.0));
    }
  }

  @Test
  void tinyCorrectionCannotHideAnUnconvergedResidual() throws Exception {
    SystemInterface fluid = gas("SRK", "none", 0.0);
    fluid.setTemperature(175.0);
    fluid.setPressure(1.0);
    new ThermodynamicOperations(fluid).dewPointTemperatureFlash();
    fluid.setBeta(1.0 - 1.0e-10);
    SysNewtonRhapsonPhaseEnvelope solver = new SysNewtonRhapsonPhaseEnvelope(fluid, 2, 3) {
      private static final long serialVersionUID = 1L;

      @Override
      public void setfvec() {
        fvec = new Jama.Matrix(neq, 1);
        fvec.set(0, 0, 1.0);
      }

      @Override
      public void setJac() {
        Jac = Jama.Matrix.identity(neq, neq).times(1.0e12);
      }
    };
    double[] previous = solver.uold.getColumnPackedCopy();
    solver.calcInc(1);
    assertThrows(IllegalStateException.class, () -> solver.solve(1));
    assertArrayEquals(previous, solver.uold.getColumnPackedCopy(), "failed point must not enter converged history");
  }

  @Test
  void emptyTraceFailsWithoutFabricatedExtrema() {
    PTPhaseEnvelopeMichelsen envelope = new PTPhaseEnvelopeMichelsen(gas("NEW", "none", 0.0), null, 1.0 - 1.0e-10,
        Double.NaN, false);
    assertThrows(IllegalStateException.class, envelope::run);
    assertEquals(0, envelope.getDewPointTemperatures().length);
    assertEquals(0, envelope.getBubblePointTemperatures().length);
    assertTrue(Arrays.stream(envelope.get("cricondentherm")).allMatch(Double::isNaN));
    assertTrue(Arrays.stream(envelope.get("cricondenbar")).allMatch(Double::isNaN));
  }
}
