package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression for issue #3698: the accepted column must publish one material and energy state. */
class NaphtaliSandholmPublishedStateTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(NaphtaliSandholmPublishedStateTest.class);
  private static final int FEED_TRAY = 6;
  private static final double ENERGY_TOLERANCE_W = 0.02;

  private static DistillationColumn createColumn(DistillationColumn.SolverType solver) {
    return createColumn(solver, 8, FEED_TRAY);
  }

  private static DistillationColumn createColumn(DistillationColumn.SolverType solver, int stages, int feedTray) {
    String[] names = { "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane" };
    double[] fractions = { 0.22, 0.34, 0.20, 0.08, 0.08, 0.03, 0.03, 0.02 };
    SystemSrkEos fluid = new SystemSrkEos(283.15, 25.0);
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], fractions[i]);
    }
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    DistillationColumn column = new DistillationColumn("deethanizer", stages, true, true);
    column.addFeedStream(feed, feedTray);
    column.setCondenserTemperature(0.0, "C");
    column.setReboilerTemperature(80.0, "C");
    column.setTopPressure(24.0);
    column.setBottomPressure(25.0);
    column.setSolverType(solver);
    column.setMaxNumberOfIterations(80, true);
    column.setTemperatureTolerance(1.0e-5);
    return column;
  }

  private static double enthalpy(StreamInterface stream) {
    stream.getFluid().init(3);
    return stream.getFluid().getEnthalpy();
  }

  private static double componentFlow(StreamInterface stream, int component) {
    return stream.getFlowRate("mol/hr") * stream.getFluid().getMolarComposition()[component];
  }

  private static void assertPublishedBalances(DistillationColumn column) {
    assertPublishedBalances(column, FEED_TRAY);
  }

  private static void assertPublishedBalances(DistillationColumn column, int feedTray) {
    assertPublishedBalances(column, feedTray, DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
  }

  private static void assertPublishedBalances(DistillationColumn column, int feedTray,
      DistillationColumn.SolverType expectedSolver) {
    StreamInterface feed = column.getFeedStreams(feedTray).get(0);
    int count = column.getNumberOfTrays();
    StreamInterface[] gas = new StreamInterface[count];
    StreamInterface[] liquid = new StreamInterface[count];
    for (int i = 0; i < count; i++) {
      gas[i] = column.getTray(i).getGasOutStream();
      liquid[i] = column.getTray(i).getLiquidOutStream();
    }
    double reboilerDuty = enthalpy(gas[0]) + enthalpy(liquid[0]) - enthalpy(liquid[1]);
    double condenserDuty = enthalpy(gas[count - 1]) + enthalpy(liquid[count - 1]) - enthalpy(gas[count - 2]);
    logger.info("Published duties W: reboiler={}, condenser={}; adjacent-outlet duties W: {}, {}",
        column.getReboiler().getDuty(), column.getCondenser().getDuty(), reboilerDuty, condenserDuty);
    assertEquals(reboilerDuty, column.getReboiler().getDuty(), ENERGY_TOLERANCE_W,
        "Reboiler duty from accepted outlets");
    assertEquals(condenserDuty, column.getCondenser().getDuty(), ENERGY_TOLERANCE_W,
        "Condenser duty from accepted outlets");
    assertEquals(reboilerDuty, column.getReboiler().getEnergyPort("heatDuty").getDuty(), ENERGY_TOLERANCE_W);
    assertEquals(condenserDuty, column.getCondenser().getEnergyPort("heatDuty").getDuty(), ENERGY_TOLERANCE_W);
    assertEquals(reboilerDuty / 1000.0, column.getReboiler().getDuty("kW"), ENERGY_TOLERANCE_W / 1000.0);

    for (int i = 0; i < count; i++) {
      double inlet = i == feedTray ? enthalpy(feed) : 0.0;
      if (i > 0) {
        inlet += enthalpy(gas[i - 1]);
      }
      if (i + 1 < count) {
        inlet += enthalpy(liquid[i + 1]);
      }
      double publicInlet = 0.0;
      for (int k = 0; k < column.getTray(i).getNumberOfInputStreams(); k++) {
        publicInlet += enthalpy(column.getTray(i).getStream(k));
      }
      assertEquals(inlet, publicInlet, ENERGY_TOLERANCE_W, "Tray " + i + " public inlets");
      double duty = i == 0 ? reboilerDuty : i == count - 1 ? condenserDuty : 0.0;
      assertEquals(inlet + duty, enthalpy(gas[i]) + enthalpy(liquid[i]), ENERGY_TOLERANCE_W, "Tray " + i + " energy");
      for (int c = 0; c < feed.getFluid().getNumberOfComponents(); c++) {
        double componentInlet = i == feedTray ? componentFlow(feed, c) : 0.0;
        if (i > 0) {
          componentInlet += componentFlow(gas[i - 1], c);
        }
        if (i + 1 < count) {
          componentInlet += componentFlow(liquid[i + 1], c);
        }
        assertEquals(componentInlet, componentFlow(gas[i], c) + componentFlow(liquid[i], c),
            Math.max(1.0e-5, Math.abs(componentInlet) * 1.0e-7), "Tray component closure");
        double publicComponentInlet = 0.0;
        for (int k = 0; k < column.getTray(i).getNumberOfInputStreams(); k++) {
          publicComponentInlet += componentFlow(column.getTray(i).getStream(k), c);
        }
        assertEquals(componentInlet, publicComponentInlet, Math.max(1.0e-5, Math.abs(componentInlet) * 1.0e-7),
            "Tray public inlet component closure");
      }
    }
    assertEquals(feed.getFlowRate("kg/hr"),
        column.getGasOutStream().getFlowRate("kg/hr") + column.getLiquidOutStream().getFlowRate("kg/hr"), 1.0e-4);
    assertEquals(enthalpy(feed) + reboilerDuty + condenserDuty,
        enthalpy(column.getGasOutStream()) + enthalpy(column.getLiquidOutStream()), ENERGY_TOLERANCE_W);
    for (int c = 0; c < feed.getFluid().getNumberOfComponents(); c++) {
      assertEquals(componentFlow(feed, c),
          componentFlow(column.getGasOutStream(), c) + componentFlow(column.getLiquidOutStream(), c),
          Math.max(1.0e-5, componentFlow(feed, c) * 1.0e-7));
    }
    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertEquals(expectedSolver, column.getLastSolverTypeUsed());
    if (expectedSolver == DistillationColumn.SolverType.NAPHTALI_SANDHOLM) {
      assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus());
    }
    assertTrue(column.getLastEnergyResidual() < 1.0e-7, column.getConvergenceDiagnostics());
    assertEquals(column.getEnergyBalanceError(), column.getLastEnergyResidual(), 1.0e-12);
    assertTrue(column.getLastMeshEnergyResidualNorm() < 1.0e-7, column.getConvergenceDiagnostics());
  }

  @Test
  void coldSolvePublishesBalancedDutiesAndInlets() {
    DistillationColumn column = createColumn(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    column.run();
    assertPublishedBalances(column);
    column.getCondenser().duty = Double.NaN;
    assertFalse(Double.isFinite(column.getEnergyBalanceError()), "The public energy diagnostic must fail closed");
    ColumnMeshResidual invalid = ColumnMeshResidualEvaluator.evaluate(column);
    assertFalse(invalid.isFinite(), "Non-finite duty must not be sanitized into a finite MESH residual");
    assertEquals(Double.POSITIVE_INFINITY, invalid.getInfinityNorm(ColumnMeshEquationType.ENERGY));
  }

  @Test
  void fixedLiquidRefluxIncludesTheSeparateCondenserProduct() {
    StreamInterface feed = createColumn(DistillationColumn.SolverType.NAPHTALI_SANDHOLM).getFeedStreams(FEED_TRAY)
        .get(0);
    Condenser condenser = new Condenser("condenser");
    condenser.addStream(feed);
    condenser.setOutTemperature(273.15);
    condenser.setSeparation_with_liquid_reflux(true, 10.0, "kg/hr");
    condenser.run();
    assertTrue(condenser.getLiquidProductStream().getFlowRate("kg/hr") > 1.0);
    double expected = enthalpy(condenser.getGasOutStream()) + enthalpy(condenser.getLiquidOutStream())
        + enthalpy(condenser.getLiquidProductStream()) - enthalpy(feed);
    condenser.updateDutyFromPublishedStreams();
    assertEquals(expected, condenser.getDuty(), ENERGY_TOLERANCE_W);
    assertEquals(expected, condenser.getEnergyPort("heatDuty").getDuty(), ENERGY_TOLERANCE_W);
  }

  @Test
  void directSolveRejectsAnUnqualifiedPublishedEnergyState() {
    DistillationColumn column = createColumn(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    column.setEnforceEnergyBalanceTolerance(true);
    column.setEnthalpyBalanceTolerance(1.0e-20);
    assertFalse(column.solveNaphtaliSandholm(UUID.randomUUID()));
    assertFalse(column.solved());
    assertEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus());
    assertTrue(column.getLastEnergyResidual() > column.getEnthalpyBalanceTolerance());
  }

  @Test
  void convergedSequentialReferenceAgreesWithPublishedState() {
    // Use a compact, independently initialized reference: the ten-stage reproducer can stall
    // under sequential substitution, so its acceptance is covered by the full balance audit.
    DistillationColumn simultaneous = createColumn(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, 1, 1);
    simultaneous.run();
    assertPublishedBalances(simultaneous, 1);
    DistillationColumn sequential = createColumn(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, 1, 1);
    sequential.setRelaxationFactor(1.0);
    sequential.setMinSequentialRelaxation(1.0);
    sequential.setMaxNumberOfIterations(600, true);
    sequential.setTemperatureTolerance(1.0e-10);
    sequential.setMassBalanceTolerance(1.0e-9);
    sequential.setEnthalpyBalanceTolerance(1.0e-8);
    sequential.setEnforceEnergyBalanceTolerance(true);
    sequential.run();
    logger.info("Sequential reference: {}", sequential.getConvergenceDiagnostics());
    assertPublishedBalances(sequential, 1, DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    // Independent Newton and TP/PH calculations agree to 1e-7 relative duty. The unscaled
    // 0.02 W material/energy audit above is applied identically to both states.
    assertEquals(simultaneous.getReboiler().getDuty(), sequential.getReboiler().getDuty(),
        Math.abs(simultaneous.getReboiler().getDuty()) * 1.0e-7);
    assertEquals(simultaneous.getCondenser().getDuty(), sequential.getCondenser().getDuty(),
        Math.abs(simultaneous.getCondenser().getDuty()) * 1.0e-7);
    assertEquals(simultaneous.getGasOutStream().getFlowRate("kg/hr"), sequential.getGasOutStream().getFlowRate("kg/hr"),
        1.0e-4);
    for (int i = 0; i < simultaneous.getNumberOfTrays(); i++) {
      assertEquals(simultaneous.getTray(i).getTemperature(), sequential.getTray(i).getTemperature(), 1.0e-5);
    }
  }

  @Test
  void repeatAndNearbyInputsKeepCallerHeldStreamsCurrent() {
    DistillationColumn column = createColumn(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    StreamInterface gasProduct = column.getGasOutStream();
    StreamInterface liquidProduct = column.getLiquidOutStream();
    column.run();
    StreamInterface[][] inlets = new StreamInterface[column.getNumberOfTrays()][];
    for (int i = 0; i < inlets.length; i++) {
      inlets[i] = new StreamInterface[column.getTray(i).getNumberOfInputStreams()];
      for (int k = 0; k < inlets[i].length; k++) {
        inlets[i][k] = column.getTray(i).getStream(k);
      }
    }
    StreamInterface condenserProduct = column.getCondenser().getProductOutStream();
    StreamInterface reboilerProduct = column.getReboiler().getLiquidOutStream();
    for (int scenario = 0; scenario < 3; scenario++) {
      if (scenario == 1) {
        column.setReboilerTemperature(81.0, "C");
      } else if (scenario == 2) {
        StreamInterface feed = column.getFeedStreams(FEED_TRAY).get(0);
        feed.setTemperature(284.15, "K");
        feed.run();
      }
      UUID id = UUID.randomUUID();
      column.run(id);
      assertPublishedBalances(column);
      assertSame(gasProduct, column.getGasOutStream());
      assertSame(liquidProduct, column.getLiquidOutStream());
      assertSame(condenserProduct, column.getCondenser().getProductOutStream());
      assertSame(reboilerProduct, column.getReboiler().getLiquidOutStream());
      assertEquals(id, gasProduct.getCalculationIdentifier());
      for (int i = 0; i < inlets.length; i++) {
        for (int k = 0; k < inlets[i].length; k++) {
          assertSame(inlets[i][k], column.getTray(i).getStream(k), "Retained inlet reference");
          assertEquals(id, inlets[i][k].getCalculationIdentifier());
        }
      }
    }
  }
}
