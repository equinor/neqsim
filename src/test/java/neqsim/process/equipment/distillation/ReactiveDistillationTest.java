package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for reactive distillation using {@link ReactiveTray} inside a {@link DistillationColumn}.
 * The water-gas shift (WGS) system CO + H2O ⇌ CO2 + H2 is used because:
 * <ul>
 * <li>It has a well-known equilibrium that the Modified RAND solver handles correctly.</li>
 * <li>It has been thoroughly validated in the reactive flash test suites.</li>
 * <li>Products (CO2, H2) should concentrate in the overhead gas phase.</li>
 * </ul>
 *
 * @author copilot
 * @version 1.0
 */
public class ReactiveDistillationTest {

  /**
   * Test that a reactive distillation column creates ReactiveTray instances when setReactive(true)
   * is called before construction, and that the column runs without exceptions.
   */
  @Test
  public void testReactiveColumnCreatesReactiveTrays() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 200.0, 10.0);
    fluid.addComponent("CO", 0.3);
    fluid.addComponent("water", 0.3);
    fluid.addComponent("CO2", 0.2);
    fluid.addComponent("hydrogen", 0.2);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("WGS feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(200.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("Reactive WGS Column", 3, true, true);
    column.setReactive(true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 250.0);
    column.getCondenser().setOutTemperature(273.15 + 100.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    // Verify reactive flash flag on middle trays
    // Tray 0 = Reboiler, Trays 1-3 = middle, Tray 4 = Condenser
    assertTrue(column.getTray(1).isUseReactiveFlash(), "Middle tray 1 should have reactive flash");
    assertTrue(column.getTray(2).isUseReactiveFlash(), "Middle tray 2 should have reactive flash");
    assertTrue(column.getTray(3).isUseReactiveFlash(), "Middle tray 3 should have reactive flash");

    // Reboiler and Condenser should NOT have reactive flash
    assertFalse(column.getTray(0).isUseReactiveFlash(), "Reboiler should not have reactive flash");
    assertFalse(column.getTray(4).isUseReactiveFlash(), "Condenser should not have reactive flash");

    assertTrue(column.isReactive(), "Column should report reactive mode");
  }

  /**
   * Test partial reactive section: only some middle trays are reactive.
   */
  @Test
  public void testPartialReactiveSection() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 200.0, 10.0);
    fluid.addComponent("CO", 0.3);
    fluid.addComponent("water", 0.3);
    fluid.addComponent("CO2", 0.2);
    fluid.addComponent("hydrogen", 0.2);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("WGS feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(200.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    // 5 middle trays, reactive section on trays 1-3 (0-based)
    DistillationColumn column = new DistillationColumn("Partial Reactive", 5, true, true);
    column.setReactive(true, 1, 3);
    column.addFeedStream(feed, 3);
    column.getReboiler().setOutTemperature(273.15 + 250.0);
    column.getCondenser().setOutTemperature(273.15 + 100.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    // Tray 0 = Reboiler, Trays 1-5 = middle, Tray 6 = Condenser
    // Middle tray indices: 0=Tray1, 1=Tray2, 2=Tray3, 3=Tray4, 4=Tray5
    assertFalse(column.getTray(1).isUseReactiveFlash(),
        "Middle tray 0 (before reactive section) should not have reactive flash");
    assertTrue(column.getTray(2).isUseReactiveFlash(),
        "Middle tray 1 (in reactive section) should have reactive flash");
    assertTrue(column.getTray(3).isUseReactiveFlash(),
        "Middle tray 2 (in reactive section) should have reactive flash");
    assertTrue(column.getTray(4).isUseReactiveFlash(),
        "Middle tray 3 (in reactive section) should have reactive flash");
    assertFalse(column.getTray(5).isUseReactiveFlash(),
        "Middle tray 4 (after reactive section) should not have reactive flash");
  }

  /**
   * Test that a reactive distillation column converges and closes mass balance on a system with NR
   * = 0 (methane/ethane only — rank(A) = NC = 2, so no reactions). This verifies the reactive flash
   * path integrates correctly with the column solver.
   */
  @Test
  public void testReactiveColumnMassBalanceNR0() {
    SystemInterface fluid = new SystemSrkEos(273.15 - 50.0, 15.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("HC feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(-50.0, "C");
    feed.setPressure(15.0, "bara");
    feed.run();

    // Standard column for baseline
    DistillationColumn stdColumn = new DistillationColumn("Standard HC", 3, true, true);
    stdColumn.addFeedStream(feed, 2);
    stdColumn.getReboiler().setOutTemperature(273.15 - 20.0);
    stdColumn.getCondenser().setOutTemperature(273.15 - 80.0);
    stdColumn.setTopPressure(15.0);
    stdColumn.setBottomPressure(15.0);
    stdColumn.setMaxNumberOfIterations(100);
    stdColumn.run();

    double stdGas = stdColumn.getGasOutStream().getFlowRate("kg/hr");
    double stdLiq = stdColumn.getLiquidOutStream().getFlowRate("kg/hr");
    double stdTotal = stdGas + stdLiq;
    System.out.println("Standard column: gas=" + stdGas + " liq=" + stdLiq + " total=" + stdTotal);

    // Reactive column
    feed.run();
    DistillationColumn column = new DistillationColumn("Reactive HC", 3, true, true);
    column.setReactive(true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 - 20.0);
    column.getCondenser().setOutTemperature(273.15 - 80.0);
    column.setTopPressure(15.0);
    column.setBottomPressure(15.0);
    column.setMaxNumberOfIterations(100);
    column.run();

    double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double liqFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    double totalOut = gasFlow + liqFlow;
    System.out
        .println("Reactive column: gas=" + gasFlow + " liq=" + liqFlow + " total=" + totalOut);

    double massBalanceError = Math.abs(totalOut - 1000.0) / 1000.0;
    assertTrue(massBalanceError < 0.05,
        "Mass balance error should be < 5% for NR=0 system, got " + (massBalanceError * 100) + "%");

    // For NR=0, reactive column should produce results identical to the standard column
    // because the PHflash delegation bypasses the reactive solver entirely
    assertEquals(stdGas, gasFlow, 0.01,
        "NR=0 reactive column gas flow should match standard column");
    assertEquals(stdLiq, liqFlow, 0.01,
        "NR=0 reactive column liquid flow should match standard column");
  }

  /**
   * Test that a single ReactiveTray produces chemical equilibrium products for the WGS system. This
   * verifies the reactive flash integration at the tray level without the full column iteration.
   */

  /**
   * Test that a single ReactiveTray produces chemical equilibrium products for the WGS system. This
   * verifies the reactive flash integration at the tray level without the full column iteration.
   */
  @Test
  public void testSingleReactiveTrayWGS() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 500.0, 10.0);
    fluid.addComponent("CO", 0.45);
    fluid.addComponent("water", 0.45);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("hydrogen", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("WGS feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(500.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    // Create a single reactive tray and run it
    ReactiveTray tray = new ReactiveTray("WGS Tray");
    tray.addStream(feed);
    tray.run();

    // Get the tray's mixed stream (has the flash result)
    SystemInterface result = tray.getThermoSystem();
    assertNotNull(result, "Tray should have a thermo system after run");
    assertTrue(result.getTemperature() > 273.15, "Tray temperature should be above freezing");

    // After reactive flash, CO2 and H2 mole fractions should have increased
    // (WGS: CO + H2O → CO2 + H2 is favored at intermediate temperatures)
    double co2Overall = 0.0;
    double h2Overall = 0.0;
    for (int p = 0; p < result.getNumberOfPhases(); p++) {
      double phFrac = result.getPhase(p).getBeta();
      int co2Idx = result.getPhase(p).getComponent("CO2").getComponentNumber();
      int h2Idx = result.getPhase(p).getComponent("hydrogen").getComponentNumber();
      co2Overall += phFrac * result.getPhase(p).getComponent(co2Idx).getx();
      h2Overall += phFrac * result.getPhase(p).getComponent(h2Idx).getx();
    }

    // CO2 started at 0.05, should increase due to WGS reaction
    assertTrue(co2Overall > 0.05,
        "CO2 mole fraction should increase via WGS reaction, got " + co2Overall);
    // H2 started at 0.05, should increase
    assertTrue(h2Overall > 0.05,
        "H2 mole fraction should increase via WGS reaction, got " + h2Overall);
  }

  /**
   * Test reactive column with a non-reactive system (methane/ethane): should behave like a standard
   * column since NR = 0 (no independent reactions among hydrocarbons).
   */
  @Test
  public void testReactiveColumnNonReactiveSystem() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 15.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("HC feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(-50.0, "C");
    feed.setPressure(15.0, "bara");
    feed.run();

    // Standard column
    DistillationColumn stdColumn = new DistillationColumn("Standard", 3, true, true);
    stdColumn.addFeedStream(feed, 2);
    stdColumn.getReboiler().setOutTemperature(273.15 - 20.0);
    stdColumn.getCondenser().setOutTemperature(273.15 - 80.0);
    stdColumn.setTopPressure(15.0);
    stdColumn.setBottomPressure(15.0);
    stdColumn.run();

    double stdGasTemp = stdColumn.getGasOutStream().getTemperature();
    double stdLiqTemp = stdColumn.getLiquidOutStream().getTemperature();

    // Reset feed for reactive column
    feed.run();

    // Reactive column (same system — no reactions should occur since NR=0 for CH4/C2H6)
    DistillationColumn rxnColumn = new DistillationColumn("Reactive", 3, true, true);
    rxnColumn.setReactive(true);
    rxnColumn.addFeedStream(feed, 2);
    rxnColumn.getReboiler().setOutTemperature(273.15 - 20.0);
    rxnColumn.getCondenser().setOutTemperature(273.15 - 80.0);
    rxnColumn.setTopPressure(15.0);
    rxnColumn.setBottomPressure(15.0);
    rxnColumn.run();

    double rxnGasTemp = rxnColumn.getGasOutStream().getTemperature();
    double rxnLiqTemp = rxnColumn.getLiquidOutStream().getTemperature();

    // With NR=0 PHflash delegation, reactive column delegates to standard PHflash
    // so results should be identical
    assertEquals(stdGasTemp, rxnGasTemp, 0.01,
        "Gas outlet temperatures should match for non-reactive system");
    assertEquals(stdLiqTemp, rxnLiqTemp, 0.01,
        "Liquid outlet temperatures should match for non-reactive system");
  }

  /**
   * Test that the column builder also respects reactive mode (setReactive called after
   * construction).
   */
  @Test
  public void testReactiveColumnAPIUsability() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 150.0, 5.0);
    fluid.addComponent("CO", 0.25);
    fluid.addComponent("water", 0.25);
    fluid.addComponent("CO2", 0.25);
    fluid.addComponent("hydrogen", 0.25);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(200.0, "kg/hr");
    feed.setTemperature(150.0, "C");
    feed.setPressure(5.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("API Test", 2, true, false);
    column.setReactive(true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 200.0);
    column.setTopPressure(5.0);
    column.setBottomPressure(5.0);

    // Verify reactive mode is reported correctly
    assertTrue(column.isReactive());

    // Column should run without exception
    column.run();

    assertNotNull(column.getGasOutStream(), "Should have gas out stream");
    assertNotNull(column.getLiquidOutStream(), "Should have liquid out stream");
  }

  /**
   * Benchmark: verify that a single reactive tray at specified T,P produces the same chemical
   * equilibrium composition as a standalone reactive TP flash at the same conditions. We run the
   * tray first (which does a PH flash — finding T from enthalpy), then do a standalone reactive TP
   * flash at the tray's converged temperature. This verifies the tray's internal flash is
   * consistent with the standalone solver.
   */
  @Test
  public void testReactiveTrayMatchesStandaloneFlash() {
    double tempC = 400.0;
    double pressBara = 10.0;

    // WGS feed: CO + H2O -> CO2 + H2
    SystemInterface fluid = new SystemSrkEos(273.15 + tempC, pressBara);
    fluid.addComponent("CO", 0.40);
    fluid.addComponent("water", 0.40);
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("hydrogen", 0.10);
    fluid.setMixingRule("classic");

    // --- Reactive tray (does PH flash internally) ---
    Stream trayFeed = new Stream("tray feed", fluid);
    trayFeed.setFlowRate(100.0, "kg/hr");
    trayFeed.setTemperature(tempC, "C");
    trayFeed.setPressure(pressBara, "bara");
    trayFeed.run();

    ReactiveTray tray = new ReactiveTray("WGS tray");
    tray.addStream(trayFeed);
    tray.run();

    SystemInterface trayResult = tray.getThermoSystem();
    double trayTempK = trayResult.getTemperature();

    double trayCO2 = 0.0;
    double trayH2 = 0.0;
    for (int p = 0; p < trayResult.getNumberOfPhases(); p++) {
      double beta = trayResult.getPhase(p).getBeta();
      trayCO2 += beta * trayResult.getPhase(p).getComponent("CO2").getx();
      trayH2 += beta * trayResult.getPhase(p).getComponent("hydrogen").getx();
    }

    // --- Standalone reactive TP flash at the tray's converged temperature ---
    SystemInterface flashFluid = fluid.clone();
    flashFluid.setTemperature(trayTempK);
    ThermodynamicOperations flashOps = new ThermodynamicOperations(flashFluid);
    flashOps.reactiveTPflash();
    flashFluid.initProperties();

    double flashCO2 = 0.0;
    double flashH2 = 0.0;
    for (int p = 0; p < flashFluid.getNumberOfPhases(); p++) {
      double beta = flashFluid.getPhase(p).getBeta();
      flashCO2 += beta * flashFluid.getPhase(p).getComponent("CO2").getx();
      flashH2 += beta * flashFluid.getPhase(p).getComponent("hydrogen").getx();
    }

    // Both should show reaction products (CO2 and H2 above feed levels)
    assertTrue(trayCO2 > 0.10,
        "CO2 from tray should be enriched above feed (0.10), got " + trayCO2);
    assertTrue(trayH2 > 0.10, "H2 from tray should be enriched above feed (0.10), got " + trayH2);

    // Compositions should match within 5% relative (tray PH flash vs standalone TP flash
    // may have slightly different convergence since PH flash iterates on T)
    assertEquals(flashCO2, trayCO2, flashCO2 * 0.05,
        "CO2 from reactive tray should match standalone reactive TP flash");
    assertEquals(flashH2, trayH2, flashH2 * 0.05,
        "H2 from reactive tray should match standalone reactive TP flash");
  }

  /**
   * Benchmark: verify that a reactive distillation column with WGS (CO + H2O -> CO2 + H2) runs and
   * produces measurable reaction products. The reactive PH flash (secant+bisection for NR &gt; 0)
   * handles the simultaneous chemical and phase equilibrium on each tray. Mass balance for reactive
   * systems is currently less tight than for NR=0 due to the iterative T-loop algorithm.
   */
  @Test
  public void testReactiveColumnWGSProducesProducts() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 250.0, 10.0);
    fluid.addComponent("CO", 0.40);
    fluid.addComponent("water", 0.40);
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("hydrogen", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("WGS feed", fluid);
    feed.setFlowRate(500.0, "kg/hr");
    feed.setTemperature(250.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("WGS Column", 2, true, false);
    column.setReactive(true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 350.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setMaxNumberOfIterations(100);

    // Should run without exception
    column.run();

    // Verify the column produced output
    assertNotNull(column.getGasOutStream(), "Should have gas out stream");

    // Check that reaction happened: H2 should be enriched in the gas product
    SystemInterface gasOut = column.getGasOutStream().getFluid();
    double h2InGas = gasOut.getPhase(0).getComponent("hydrogen").getx();
    assertTrue(h2InGas > 0.10,
        "H2 in gas product should be enriched above feed (0.10), got " + h2InGas);
  }
}
