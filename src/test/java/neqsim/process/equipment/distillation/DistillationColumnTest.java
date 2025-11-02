package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class DistillationColumnTest {
  /**
   * @throws java.lang.Exception
   */
  @Test
  public void testRun() {
    neqsim.thermo.system.SystemInterface richTEG =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    richTEG.addComponent("nitrogen", 0.0003884521907420086);
    richTEG.addComponent("CO2", 0.3992611934362681);
    richTEG.addComponent("methane", 0.1707852619527612);
    richTEG.addComponent("ethane", 0.20533172990208282);
    richTEG.addComponent("propane", 0.28448628224749795);
    richTEG.addComponent("i-butane", 0.04538593257021818);
    richTEG.addComponent("n-butane", 0.1078982825);
    richTEG.addComponent("i-pentane", 0.08015009931573362);
    richTEG.addComponent("n-pentane", 0.07597175884128077);
    richTEG.addComponent("n-hexane", 0.735238469338);
    richTEG.addComponent("n-heptane", 0.0);
    richTEG.addComponent("nC8", 0.0);
    richTEG.addComponent("nC9", 0.0);
    richTEG.addComponent("benzene", 0.001);
    richTEG.addComponent("water", 9.281170624865437);
    richTEG.addComponent("TEG", 88.61393191277175);
    richTEG.setMixingRule(10);
    richTEG.setMultiPhaseCheck(false);
    richTEG.init(0);

    neqsim.thermo.system.SystemInterface gasToReboiler =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    gasToReboiler.addComponent("nitrogen", 0.007104922868929818);
    gasToReboiler.addComponent("CO2", 4.944830745821265);
    gasToReboiler.addComponent("methane", 3.013439464714221);
    gasToReboiler.addComponent("ethane", 3.1119159322353815);
    gasToReboiler.addComponent("propane", 4.001381171330917);
    gasToReboiler.addComponent("i-butane", 0.6934008192075206);
    gasToReboiler.addComponent("n-butane", 1.684816349773283);
    gasToReboiler.addComponent("i-pentane", 1.24185783393270);
    gasToReboiler.addComponent("n-pentane", 1.32322868124);
    gasToReboiler.addComponent("n-hexane", 12.2651);
    gasToReboiler.addComponent("n-heptane", 0.0);
    gasToReboiler.addComponent("nC8", 0.0);
    gasToReboiler.addComponent("nC9", 0.0);
    gasToReboiler.addComponent("benzene", 0.000);
    gasToReboiler.addComponent("water", 63.419578687948665);
    gasToReboiler.addComponent("TEG", 4.293253985703371);
    gasToReboiler.setMixingRule(10);
    gasToReboiler.setMultiPhaseCheck(false);
    gasToReboiler.init(0);

    Stream richTEGStream = new Stream("richTEGS", richTEG);
    richTEGStream.setFlowRate(9400.0, "kg/hr");
    richTEGStream.setTemperature(100, "C");
    richTEGStream.setPressure(1.12, "bara");
    richTEGStream.run();

    Stream gasToReboilerStream = new Stream("gasToReboilerS", gasToReboiler);
    gasToReboilerStream.setFlowRate(290, "kg/hr");
    gasToReboilerStream.setTemperature(200, "C");
    gasToReboilerStream.setPressure(1.12, "bara");
    gasToReboilerStream.run();

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.addFeedStream(richTEGStream, 1);
    column.getReboiler().setOutTemperature(273.15 + 202);
    column.getCondenser().setOutTemperature(273.15 + 88.165861);
    // column.getCondenser().setHeatInput(-50000);
    column.getTray(1).addStream(gasToReboilerStream);
    column.setTopPressure(1.12);
    column.setBottomPressure(1.12);
    column.setInternalDiameter(0.56);
    column.setMaxNumberOfIterations(40);
    column.run();

    double waterFlowRateInColumn =
        richTEGStream.getFluid().getPhase(0).getComponent("water").getFlowRate("kg/hr")
            + richTEGStream.getFluid().getPhase(1).getComponent("water").getFlowRate("kg/hr");
    double waterFlowRateInColumn2 = richTEGStream.getFluid().getComponent("water").getMolarMass()
        * richTEGStream.getFluid().getFlowRate("mole/hr")
        * richTEGStream.getFluid().getComponent("water").getz();
    assertEquals(waterFlowRateInColumn, waterFlowRateInColumn2, 0.00001);

    double waterFlowRateInColumnGasToReb = gasToReboilerStream.getFluid().getFlowRate("mole/hr")
        * gasToReboilerStream.getFluid().getComponent("water").getMolarMass()
        * gasToReboilerStream.getFluid().getComponent("water").getz();
    double waterFlowRateOutColumn = column.getGasOutStream().getFluid().getFlowRate("mole/hr")
        * column.getGasOutStream().getFluid().getComponent("water").getMolarMass()
        * column.getGasOutStream().getFluid().getComponent("water").getz();
    double waterFlowRateOutColumnLeanTEG =
        column.getLiquidOutStream().getFluid().getFlowRate("mole/hr")
            * column.getLiquidOutStream().getFluid().getComponent("water").getMolarMass()
            * column.getLiquidOutStream().getFluid().getComponent("water").getz();

    double totalWaterIn = waterFlowRateInColumn2 + waterFlowRateInColumnGasToReb;
    double totalWaterOut = waterFlowRateOutColumn + waterFlowRateOutColumnLeanTEG;
    /*
     * System.out.println("Column in is " + totalWaterIn + " kg/hr");
     * System.out.println("Column out is " + totalWaterOut + " kg/hr");
     * System.out.println("Column is solved  " + column.solved());
     *
     * System.out.println("Calc Water Flow rate via fluid component " + waterFlowRateInColumn);
     * System.out.println("Calc Water Flow rate via molar mass and flow rate total " +
     * waterFlowRateInColumn2 + " kg/hr");
     *
     * System.out .println("condenser temperature " +
     * column.getCondenser().getFluid().getTemperature("C")); System.out.println("condenser duty " +
     * ((Condenser) column.getCondenser()).getDuty());
     */
    assertEquals(totalWaterIn, totalWaterOut, 1.0);
  }

  /**
   * @throws java.lang.Exception
   */
  @Test
  public void deethanizerTest() {
    neqsim.thermo.system.SystemInterface gasToDeethanizer =
        new neqsim.thermo.system.SystemSrkEos(216, 30.00);
    gasToDeethanizer.addComponent("nitrogen", 1.67366E-3);
    gasToDeethanizer.addComponent("CO2", 1.06819E-4);
    gasToDeethanizer.addComponent("methane", 5.14168E-1);
    gasToDeethanizer.addComponent("ethane", 1.92528E-1);
    gasToDeethanizer.addComponent("propane", 1.70001E-1);
    gasToDeethanizer.addComponent("i-butane", 3.14561E-2);
    gasToDeethanizer.addComponent("n-butane", 5.58678E-2);
    gasToDeethanizer.addComponent("i-pentane", 1.29573E-2);
    gasToDeethanizer.addComponent("n-pentane", 1.23719E-2);
    gasToDeethanizer.addComponent("n-hexane", 5.12878E-3);
    gasToDeethanizer.addComponent("n-heptane", 1.0E-2);
    gasToDeethanizer.setMixingRule("classic");

    Stream gasToDeethanizerStream = new Stream("gasToDeethanizer", gasToDeethanizer);
    gasToDeethanizerStream.setFlowRate(100.0, "kg/hr");
    gasToDeethanizerStream.run();

    // gasToDeethanizerStream.getFluid().prettyPrint();

    DistillationColumn column = new DistillationColumn("Deethanizer", 5, true, false);
    column.addFeedStream(gasToDeethanizerStream, 5);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(50);
    column.run();
    column.run();

    double massbalance = (gasToDeethanizerStream.getFlowRate("kg/hr")
        - column.getLiquidOutStream().getFlowRate("kg/hr")
        - column.getGasOutStream().getFlowRate("kg/hr"))
        / gasToDeethanizerStream.getFlowRate("kg/hr") * 100;

    assertEquals(0.0, massbalance, 0.2);
    // column.getGasOutStream().getFluid().prettyPrint();
    // column.getLiquidOutStream().getFluid().prettyPrint();
  }

  /**
   * @throws java.lang.Exception
   */
  @Test
  public void debutanizerTest() {
    neqsim.thermo.system.SystemInterface gasToDbutanizer =
        new neqsim.thermo.system.SystemSrkEos(289.0, 11.00);
    gasToDbutanizer.addComponent("nitrogen", 3.09189E-7);
    gasToDbutanizer.addComponent("CO2", 2.20812E-4);
    gasToDbutanizer.addComponent("methane", 0.097192E-1);
    gasToDbutanizer.addComponent("ethane", 0.15433E-1);
    gasToDbutanizer.addComponent("propane", 2.01019E-1);
    gasToDbutanizer.addComponent("i-butane", 2.953E-2);
    gasToDbutanizer.addComponent("n-butane", 3.91507E-2);
    gasToDbutanizer.addComponent("i-pentane", 4.03877E-3);
    gasToDbutanizer.addComponent("n-pentane", 2.98172E-3);
    gasToDbutanizer.addComponent("n-hexane", 3.92672E-4);
    gasToDbutanizer.addComponent("n-heptane", 8.52258E-3);
    gasToDbutanizer.setMixingRule("classic");

    StreamInterface gasToDebutanizerStream = new Stream("gasToDbutanizer", gasToDbutanizer);
    gasToDebutanizerStream.setFlowRate(100.0, "kg/hr");
    gasToDebutanizerStream.run();

    // gasToDebutanizerStream.getFluid().prettyPrint();

    DistillationColumn column = new DistillationColumn("Deethanizer", 1, true, true);
    column.addFeedStream(gasToDebutanizerStream, 1);
    ((Condenser) column.getCondenser()).setRefluxRatio(0.1);
    ((Condenser) column.getCondenser()).setTotalCondenser(true);
    column.getCondenser().setOutTemperature(gasToDbutanizer.getTemperature() - 10.0);
    column.getReboiler().setOutTemperature(gasToDbutanizer.getTemperature() + 50.0);
    column.setTopPressure(9.0);
    column.setBottomPressure(13.0);
    column.run();
    // ((Condenser)
    // column.getCondenser()).getProductOutStream().getFluid().prettyPrint();

    // column.getReboiler().getLiquidOutStream().getFluid().prettyPrint();

    double massbalance = (gasToDebutanizerStream.getFlowRate("kg/hr")
        - column.getReboiler().getLiquidOutStream().getFlowRate("kg/hr")
        - ((Condenser) column.getCondenser()).getProductOutStream().getFlowRate("kg/hr"))
        / gasToDebutanizerStream.getFlowRate("kg/hr") * 100;

    assertEquals(0.0, massbalance, 0.2);
  }

  @Test
  public void adaptiveSolverRecordsSolveMetrics() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    Stream feed = new Stream("metricsFeed", simpleSystem);
    feed.run();

    DistillationColumn column = new DistillationColumn("metrics column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.run();

    DistillationColumn broydenColumn =
        new DistillationColumn("metrics column broyden", 1, true, true);
    Stream broydenFeed = new Stream("metricsFeedBroyden", simpleSystem.clone());
    broydenFeed.run();
    broydenColumn.addFeedStream(broydenFeed, 1);
    broydenColumn.runBroyden(UUID.randomUUID());

    assertTrue(column.getLastIterationCount() > 0);
    assertTrue(column.getLastTemperatureResidual() >= 0.0);
    assertTrue(Double.isFinite(column.getLastMassResidual()));
    assertTrue(Double.isFinite(column.getLastEnergyResidual()));
    assertTrue(column.getLastSolveTimeSeconds() >= 0.0);
    assertTrue(Double.isFinite(broydenColumn.getLastMassResidual()));
    assertTrue(Double.isFinite(broydenColumn.getLastEnergyResidual()));
  }

  @Test
  public void abortsWhenSolveTimeLimitExceeded() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 0.5);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    Stream feed = new Stream("fastAbortFeed", simpleSystem);
    feed.run();

    DistillationColumn column = new DistillationColumn("abort column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.setMaxSolveSeconds(1.0e-7);
    column.run();

    assertTrue(column.wasAborted());
    assertFalse(column.solved());
    String reason = column.getLastAbortReason();
    assertTrue(reason != null && reason.toLowerCase(Locale.ROOT).contains("time"));
  }

  @Test
  public void multipleFeedsOnDifferentTraysAreHandled() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    Stream feedOne = new Stream("feedOne", simpleSystem.clone());
    feedOne.run();
    Stream feedTwo = new Stream("feedTwo", simpleSystem.clone());
    feedTwo.run();
    Stream feedThree = new Stream("feedThree", simpleSystem.clone());
    feedThree.run();

    DistillationColumn column = new DistillationColumn("feed tracking", 3, true, true);
    column.addFeedStream(feedOne, 1);
    column.addFeedStream(feedTwo, 1);
    column.addFeedStream(feedThree, 3);

    assertEquals(2, column.getFeedStreams(1).size());
    assertEquals(1, column.getFeedStreams(3).size());
    assertEquals(0, column.getFeedStreams(2).size());
  }

  /**
   *
   */
  @Test
  public void testMEGregeneration() {
    SystemSrkCPAstatoil fluid1 = new SystemSrkCPAstatoil(273.15 + 40, 6.0);

    // Add components
    fluid1.addComponent("nitrogen", 0.1);
    fluid1.addComponent("CO2", 0.05);
    fluid1.addComponent("methane", 9.0);
    fluid1.addComponent("ethane", 0.5);
    fluid1.addComponent("propane", 0.3);
    fluid1.addComponent("water", 75.0);
    fluid1.addComponent("MEG", 25.0);

    // Set mixing rule (10 => classic CPA mixing rule)
    fluid1.setMixingRule(10);

    // Perform TP flash
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    try {
      ops.TPflash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    SystemInterface feedGas = fluid1.phaseToSystem(0);
    SystemInterface feedMEG = fluid1.phaseToSystem(1);

    // Calculate weight fraction of MEG in the first liquid phase
    double wtMEG = feedMEG.getPhase(0).getWtFrac("MEG") * 100.0;

    assertEquals(53.3718940, wtMEG, 0.2);

    // 1) Create a stream named 'stream1' using feedMEG
    Stream stream1 = new Stream("stream 1", feedMEG);
    stream1.setFlowRate(6000.0, "kg/hr");
    stream1.setTemperature(80.0, "C");
    stream1.setPressure(1.021, "bara");
    stream1.run();

    // 1) Create a stream named 'stream1' using feedMEG
    Stream stripgas = new Stream("stream 2", feedGas);
    stripgas.setFlowRate(10.0, "kg/hr");
    stripgas.setTemperature(80.0, "C");
    stripgas.setPressure(1.021, "bara");
    stripgas.run();

    DistillationColumn distillationColumn = new DistillationColumn("MEG column", 2, true, true);
    distillationColumn.addFeedStream(stream1, 1);
    distillationColumn.addFeedStream(stripgas, 1);
    distillationColumn.getReboiler().setOutTemperature(273.15 + 140.0);
    distillationColumn.getCondenser().setOutTemperature(273.15 + 35.0);
    distillationColumn.setTopPressure(1.021);
    distillationColumn.setBottomPressure(1.021);
    distillationColumn.getCondenser().setSeparation_with_liquid_reflux(true, 370.0, "kg/hr");

    distillationColumn.run();

    distillationColumn.toJson();
    /*
     * System.out.println("---------- PROCESS RESULTS ----------");
     * System.out.println("density feed " + stream1.getFluid().getDensity("kg/m3") + " kg/m3");
     * System.out.println("Distillation column top gas flow:    " +
     * distillationColumn.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
     * System.out.println("Distillation column top temperature:    " +
     * distillationColumn.getGasOutStream().getTemperature("C") + " C");
     * System.out.println("Distillation column bottom liquid flow: " +
     * distillationColumn.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
     * System.out.println("Split to water treatment: " +
     * distillationColumn.getCondenser().getLiquidProductStream().getFlowRate( "kg/hr") + " kg/hr");
     * System.out.println("Split to resycle to column: " +
     * distillationColumn.getCondenser().getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
     * System.out.println("wt% MEG to treatment " + (distillationColumn.getCondenser()
     * .getLiquidProductStream().getFluid().getPhase(0).getWtFrac("MEG") * 100) + " wt%");
     * System.out.println("wt% MEG to treatment " + (distillationColumn.getCondenser()
     * .getLiquidProductStream().getFluid().getPhase(0).getWtFrac("MEG") * 100) + " wt%");
     * System.out.println("flow MEG to treatment " + distillationColumn.getCondenser()
     * .getLiquidProductStream().getFluid().getComponent("MEG").getFlowRate("kg/hr") + " kg/hr");
     * System.out.println("density reflux " +
     * distillationColumn.getCondenser().getLiquidProductStream().getFluid(). getDensity("kg/m3") +
     * " kg/m3"); System.out.println("wt% MEG " +
     * (distillationColumn.getLiquidOutStream().getFluid().getPhase(0).getWtFrac( "MEG") * 100) +
     * " wt%"); System.out.println("KOF " +
     * (distillationColumn.getCondenser().getLiquidProductStream()
     * .getFluid().getPhase(0).getWtFrac("MEG") * 1e6 / 1.547) + " KOF"); System.out
     * .println("Reboiler duty " + (distillationColumn.getReboiler().getDuty() / 1e6) + " MW");
     * System.out .println("Condenser duty " + (distillationColumn.getCondenser().getDuty() / 1e6) +
     * " MW"); System.out.println( "Condenser temperature " +
     * (distillationColumn.getCondenser().getTemperature()) + " C"); System.out
     * .println("Tray 1 temperature " + (distillationColumn.getTray(1).getTemperature()) + " C");
     * System.out .println("Tray 2 temperature " + (distillationColumn.getTray(2).getTemperature())
     * + " C");
     */
  }

  @Test
  public void testBroydenSolver() {
    neqsim.thermo.system.SystemInterface simpleSystem =
        new neqsim.thermo.system.SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    Stream feed = new Stream("feed", simpleSystem);
    feed.run();

    DistillationColumn column = new DistillationColumn("test column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.runBroyden(java.util.UUID.randomUUID());

    assertEquals(true, column.solved());
  }

  /**
   * Basic check that the damped solver converges on a simple system.
   */
  @Test
  public void testDampedSolver() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    Stream feed = new Stream("feed", simpleSystem);
    feed.run();

    DistillationColumn column = new DistillationColumn("test column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.5);
    column.run();

    assertEquals(true, column.solved());
  }

  @Test
  public void testInsideOutSolverConvergesOnSimpleSystem() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 5.0);
    simpleSystem.addComponent("methane", 1.0);
    simpleSystem.addComponent("ethane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    // Ensure feed is two-phase
    ThermodynamicOperations ops = new ThermodynamicOperations(simpleSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    Stream feed = new Stream("insideOutFeed", simpleSystem);
    feed.run();

    DistillationColumn column = new DistillationColumn("inside-out column", 2, true, true);
    column.addFeedStream(feed, 1);
    column.useInsideOutSolver();
    column.setMaxNumberOfIterations(40);
    column.run();

    assertTrue(column.solved());
    assertTrue(column.getLastIterationCount() > 0);
    assertTrue(column.getLastMassResidual() >= 0.0);
    assertTrue(column.getLastEnergyResidual() >= 0.0);
  }

  @Test
  public void testInsideOutSolverOnThreeTraySystem() {
    SystemInterface simpleSystem = new SystemSrkEos(298.15, 10.0);
    simpleSystem.addComponent("methane", 2.0);
    simpleSystem.addComponent("ethane", 1.5);
    simpleSystem.addComponent("propane", 1.0);
    simpleSystem.createDatabase(true);
    simpleSystem.setMixingRule("classic");

    // Ensure feed is two-phase
    ThermodynamicOperations ops = new ThermodynamicOperations(simpleSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      e.printStackTrace();
    }

    Stream baseStream = new Stream("insideOutFeed", simpleSystem);
    baseStream.setFlowRate(100.0, "kg/hr");
    baseStream.run();

    DistillationColumn insideOutColumn =
        new DistillationColumn("three tray inside-out", 3, true, true);
    insideOutColumn.addFeedStream(baseStream, 2);
    insideOutColumn.getReboiler().setOutTemperature(273.15 + 50.0);
    insideOutColumn.getCondenser().setOutTemperature(273.15 + 10.0);
    insideOutColumn.setTopPressure(10.0);
    insideOutColumn.setBottomPressure(10.5);
    insideOutColumn.setMaxNumberOfIterations(60);
    insideOutColumn.useInsideOutSolver();
    insideOutColumn.run();

    double massbalance =
        (baseStream.getFlowRate("kg/hr") - insideOutColumn.getLiquidOutStream().getFlowRate("kg/hr")
            - insideOutColumn.getGasOutStream().getFlowRate("kg/hr"))
            / baseStream.getFlowRate("kg/hr") * 100;

    assertTrue(insideOutColumn.solved());
    assertEquals(0.0, massbalance, 1.0);
    assertTrue(insideOutColumn.getLastIterationCount() > 0);
    assertTrue(insideOutColumn.getLastMassResidual() >= 0.0);
  }
}
