package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression for a salt-free vacuum MEG regenerator with a partial condenser and independent duty and reflux
 * specifications.
 *
 * @author NeqSim
 * @version 1.0
 */
class MegVacuumRegenerationTest {
  private static final double PRESSURE_BARA = 0.8;
  private static final double REFLUX_RATIO = 0.45;
  private static final double NOMINAL_DUTY_W = 1.30e6;

  /**
   * A cold solve and a small reboiler duty change both conserve energy at the adiabatic feed tray. Verify the retained
   * state against a separate cold solve at the nearby operating point.
   */
  @Test
  void closesVacuumColumnBalancesAtNominalAndNearbyDuty() {
    Stream feed = richMeg(0.0);
    DistillationColumn column = buildColumn(feed, 1, NOMINAL_DUTY_W);
    column.run();
    assertQualified(column, feed, NOMINAL_DUTY_W);
    double initialBottomsMeg = componentRate(column.getLiquidOutStream(), "MEG")
        / column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(1120.26, column.getGasOutStream().getFlowRate("kg/hr"), 0.15);
    assertEquals(0.001308, vaporMeg(column), 5.0e-6);

    column.run();
    assertTrue(column.wasSequentialWarmStateReused());

    double nearbyDuty = 1.32e6;
    column.getReboiler().setHeatInput(nearbyDuty);
    column.run();
    assertQualified(column, feed, nearbyDuty);
    double updatedBottomsMeg = componentRate(column.getLiquidOutStream(), "MEG")
        / column.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(updatedBottomsMeg > initialBottomsMeg, "More boil-up should concentrate the lean MEG");

    Stream coldFeed = richMeg(0.0);
    DistillationColumn coldColumn = buildColumn(coldFeed, 1, nearbyDuty);
    coldColumn.run();
    assertQualified(coldColumn, coldFeed, nearbyDuty);
    assertEquals(coldColumn.getGasOutStream().getFlowRate("kg/hr"), column.getGasOutStream().getFlowRate("kg/hr"),
        0.02);
    assertEquals(vaporMeg(coldColumn), vaporMeg(column), 1.0e-7);
    for (int index = 0; index < column.getNumberOfTrays(); index++) {
      SimpleTray coldTray = coldColumn.getTray(index);
      SimpleTray retainedTray = column.getTray(index);
      assertEquals(coldTray.getVaporFlowRate("mol/sec"), retainedTray.getVaporFlowRate("mol/sec"), 1.0e-3);
      assertEquals(coldTray.getLiquidFlowRate("mol/sec"), retainedTray.getLiquidFlowRate("mol/sec"), 1.0e-3);
    }

    EnergyStream heatAdjustment = new EnergyStream("Reboiler heat adjustment");
    column.getReboiler().setEnergyStream(heatAdjustment);
    heatAdjustment.setDuty(nearbyDuty - NOMINAL_DUTY_W);
    column.run();
    assertQualified(column, feed, NOMINAL_DUTY_W);
    assertEquals(1120.26, column.getGasOutStream().getFlowRate("kg/hr"), 0.15);
  }

  /**
   * Confirm that the multi-tray calculation still closes with a small noncondensable fraction after feed degassing.
   */
  @Test
  void closesThreeTrayColumnWithResidualMethane() {
    Stream feed = richMeg(0.46);
    double duty = 1.25e6;
    DistillationColumn column = buildColumn(feed, 3, duty);
    column.run();
    assertQualified(column, feed, duty);
    assertEquals(1095.04, column.getGasOutStream().getFlowRate("kg/hr"), 0.15);
    assertEquals(5.20e-5, vaporMeg(column), 7.0e-7);
    assertEquals(0.46, componentRate(column.getGasOutStream(), "methane"), 1.0e-3);
  }

  /**
   * Create and equilibrate the rich solvent at column pressure.
   *
   * @param methaneKgPerHour trace methane replacing an equal mass of water
   * @return equilibrated rich feed
   */
  private static Stream richMeg(double methaneKgPerHour) {
    SystemInterface fluid = new SystemSrkCPAstatoil(353.15, PRESSURE_BARA);
    if (methaneKgPerHour > 0.0) {
      fluid.addComponent("methane", methaneKgPerHour, "kg/hr");
    }
    fluid.addComponent("water", 1500.0 - methaneKgPerHour, "kg/hr");
    fluid.addComponent("MEG", 4500.0, "kg/hr");
    fluid.setMixingRule(10);
    Stream feed = new Stream("Rich MEG", fluid);
    feed.run();
    return feed;
  }

  /**
   * Set up an equilibrium column with a duty-controlled reboiler and a ratio-controlled partial condenser.
   *
   * @param feed column feed at 80 C
   * @param trayCount number of trays between the reboiler and condenser
   * @param dutyW reboiler duty in W
   * @return configured column
   */
  private static DistillationColumn buildColumn(Stream feed, int trayCount, double dutyW) {
    DistillationColumn column = new DistillationColumn("Vacuum MEG regenerator", trayCount, true, true);
    column.addFeedStream(feed, (trayCount + 1) / 2);
    column.setTopPressure(PRESSURE_BARA);
    column.setBottomPressure(PRESSURE_BARA);
    column.setSeedTemperature(0, 408.15);
    column.setSeedTemperature(trayCount + 1, 374.15);
    column.getReboiler().setHeatInput(dutyW);
    column.setCondenserRefluxRatio(REFLUX_RATIO);
    column.setSolverType(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);
    column.setMaxNumberOfIterations(24);
    column.setTemperatureTolerance(1.0e-5);
    column.setMassBalanceTolerance(1.0e-6);
    column.setEnforceEnergyBalanceTolerance(true);
    column.setEnthalpyBalanceTolerance(1.0e-5);
    column.setEnforceMeshResidualTolerance(true);
    column.setMeshResidualTolerance(1.0e-5);
    return column;
  }

  /**
   * Independently verify product balances, adiabatic tray energy closure, and the active operating specifications.
   *
   * @param column solved MEG column
   * @param feed rich feed
   * @param dutyW specified reboiler duty in W
   */
  private static void assertQualified(DistillationColumn column, Stream feed, double dutyW) {
    assertTrue(column.solved(), column::getConvergenceDiagnostics);
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus());
    assertFalse(column.isInternalInventoryAccumulating());
    double flowChange = column.getLastInternalFlowChange();
    if (Double.isFinite(flowChange)) {
      assertTrue(flowChange < column.getInternalFlowStationarityTolerance());
    }
    assertTrue(column.getLastMeshResidualNorm() < column.getMeshResidualTolerance());

    StreamInterface bottoms = column.getLiquidOutStream();
    StreamInterface overhead = column.getGasOutStream();
    assertEquals(feed.getFlowRate("kg/hr"), bottoms.getFlowRate("kg/hr") + overhead.getFlowRate("kg/hr"), 1.0e-3);
    for (int index = 0; index < feed.getFluid().getNumberOfComponents(); index++) {
      String component = feed.getFluid().getComponent(index).getName();
      assertEquals(componentRate(feed, component),
          componentRate(bottoms, component) + componentRate(overhead, component), 1.0e-3, component);
    }
    double reboilerDuty = column.getReboiler().getDuty();
    double condenserDuty = column.getCondenser().getDuty();
    assertEquals(dutyW, reboilerDuty, 0.5);
    assertTrue(condenserDuty < 0.0);
    assertEquals(REFLUX_RATIO,
        column.getCondenser().getLiquidOutStream().getFlowRate("mol/sec") / overhead.getFlowRate("mol/sec"), 1.0e-6);
    assertEquals(feed.getFluid().getEnthalpy() + reboilerDuty + condenserDuty,
        overhead.getFluid().getEnthalpy() + bottoms.getFluid().getEnthalpy(), 0.1, "Column energy balance (W)");
    for (int index = 1; index < column.getNumberOfTrays() - 1; index++) {
      SimpleTray tray = column.getTray(index);
      double inletEnthalpy = 0.0;
      for (StreamInterface inlet : tray.getInletStreams()) {
        inletEnthalpy += inlet.getFluid().getEnthalpy();
      }
      assertEquals(inletEnthalpy,
          tray.getGasOutStream().getFluid().getEnthalpy() + tray.getLiquidOutStream().getFluid().getEnthalpy(), 0.2,
          "Adiabatic tray energy balance (W)");
    }
  }

  /**
   * Read a component's mass flow across all active phases.
   *
   * @param stream material stream
   * @param component component name
   * @return component mass flow in kg/h
   */
  private static double componentRate(StreamInterface stream, String component) {
    SystemInterface system = stream.getFluid();
    double rate = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      rate += system.getPhase(phase).getComponent(component).getFlowRate("kg/hr");
    }
    return rate;
  }

  /**
   * Read the mole fraction of MEG in the staged column's overhead.
   *
   * @param column converged MEG column
   * @return vapor mole fraction of MEG
   */
  private static double vaporMeg(DistillationColumn column) {
    return column.getGasOutStream().getFluid().getPhase("gas").getComponent("MEG").getx();
  }
}
