package neqsim.process.processmodel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.absorber.WaterStripperColumn;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Test class for GlycolRig.
 */
public class GlycolRigTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GlycolRigTest.class);

  ProcessSystem p;
  String _name = "TestProcess";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }

  @Test
  public void runTEGProcessTest() {
    neqsim.thermo.system.SystemInterface feedTEG =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 145.0, 1.2);
    feedTEG.addComponent("nitrogen", 0.00005);
    feedTEG.addComponent("water", 0.19 - 1 * 0.00005);
    feedTEG.addComponent("TEG", 0.8);
    feedTEG.setMixingRule(10);
    feedTEG.setMolarComposition(new double[] {0.00003, 0.2 - 1 * 0.00003, 0.8});

    neqsim.thermo.system.SystemInterface strippingGasToStripperFluid = feedTEG.clone();
    strippingGasToStripperFluid.setMolarComposition(new double[] {1.0, 0.0, 0.0});

    Stream strippingGas = new Stream("stripgas", strippingGasToStripperFluid);
    strippingGas.setFlowRate(13.0, "kg/hr");
    strippingGas.setTemperature(55.0, "C");
    strippingGas.setPressure(0.2, "barg");

    Stream gasToReboiler = strippingGas.clone("gas to reboiler");

    Stream TEGtoRegenerator = new Stream("TEG to regenerator", feedTEG);
    TEGtoRegenerator.setFlowRate(400.0, "kg/hr");
    TEGtoRegenerator.setTemperature(145.0, "C");
    TEGtoRegenerator.setPressure(0.2, "barg");

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.addFeedStream(TEGtoRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 209.0);
    column.getCondenser().setOutTemperature(273.15 + 104.0);
    column.getTray(1).addStream(gasToReboiler);
    // column.getReboiler().addStream(gasToReboiler);
    column.setTopPressure(0.1 + ThermodynamicConstantsInterface.referencePressure);
    column.setBottomPressure(0.2 + ThermodynamicConstantsInterface.referencePressure);

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(2);
    stripper.setStageEfficiency(0.7);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    Heater coolerPipe = new Heater("heat loss cooling", column.getGasOutStream());
    coolerPipe.setOutTemperature(273.15 + 81.0);

    Heater coolerRegenGas = new Heater("regen gas cooler", coolerPipe.getOutletStream());
    coolerRegenGas.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());

    Compressor blower = new Compressor("blower", sepregenGas.getGasOutStream());
    blower.setOutletPressure(0.2, "barg");

    Heater gasHeater = new Heater("heater", blower.getOutletStream());
    gasHeater.setOutTemperature(273.15 + 53.0);

    Recycle recycleGasfEED = new Recycle("FEED gas recirc");
    recycleGasfEED.addStream(gasHeater.getOutletStream());
    recycleGasfEED.setOutletStream(strippingGas);
    recycleGasfEED.setPriority(200);

    Heater coolerStripper = new Heater("TEG cooler", stripper.getSolventOutStream());
    coolerStripper.setOutTemperature(273.15 + 98.0);

    Stream liquidToTreatment = new Stream("water to treatment", sepregenGas.getLiquidOutStream());

    Mixer TEGWaterMixer = new Mixer("TEG water mixer");
    TEGWaterMixer.addStream(coolerStripper.getOutletStream());
    TEGWaterMixer.addStream(liquidToTreatment);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(TEGtoRegenerator);
    operations.add(strippingGas);
    operations.add(gasToReboiler);
    operations.add(column);
    operations.add(coolerPipe);
    operations.add(coolerRegenGas);
    operations.add(sepregenGas);
    operations.add(blower);
    operations.add(gasHeater);
    operations.add(recycleGasfEED);
    operations.add(liquidToTreatment);
    operations.add(stripper);
    operations.add(recycleGasFromStripper);
    operations.add(coolerStripper);
    operations.add(TEGWaterMixer);

    Thread runThr = operations.runAsThread();
    try {
      runThr.join(10 * 60000);
    } catch (Exception ex) {
    }
    double wtpWaterRichTEG =
        TEGtoRegenerator.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    double wtpWaterFromReboil =
        column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    double wtpWaterFromStripper =
        stripper.getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    logger.info("wtpRichTEG " + wtpWaterRichTEG);
    logger.info("wtpWaterFromReboil " + wtpWaterFromReboil);
    logger.info("wtpWaterFromStripper " + wtpWaterFromStripper);
    // double wtpWaterFromReboiler =
    // column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("water")*100.0
    // double wtpTEGSeparatorOut =
    // liquidToTreatment.getFluid().getPhase("aqueous").getWtFrac("TEG")*100.0
    // double flowrateLiquidSeparatorOut = liquidToTreatment.getFlowRate("kg/hr")
    // double wtWaterFromStripper =
    // stripper.getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("water")*100.0
    // double wtWaterBufferTank =
    // liquidFromBufferTank.getFluid().getPhase("aqueous").getWtFrac("water")*100.0
    // TEGtoRegenerator.displayResult();
    logger.info("water to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    logger.info("TEG to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("TEG").getTotalFlowRate("kg/hr"));
    // logger.info("oxygen to regenerator "
    // + TEGtoRegenerator.getFluid().getComponent("oxygen").getTotalFlowRate("kg/hr"));
    logger.info("nitrogen to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("nitrogen").getTotalFlowRate("kg/hr"));

    logger.info("water liquid from regenerator "
        + column.getLiquidOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    logger.info("water gas from regenerator "
        + column.getGasOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    logger.info("water from stripping gas "
        + gasToReboiler.getFluid().getComponent("water").getTotalFlowRate("kg/hr"));

    double waterBalanceColumn =
        column.getLiquidOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            + column.getGasOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            - TEGtoRegenerator.getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            - gasToReboiler.getFluid().getComponent("water").getTotalFlowRate("kg/hr");
    logger.info("water balance " + waterBalanceColumn);

    logger.info("wt water to reboil "
        + TEGtoRegenerator.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0);

    logger.info("wt water from mixer "
        + TEGWaterMixer.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0);
    // strippingGas.displayResult();
    logger.info("stripping gas rate " + strippingGas.getFlowRate("kg/hr"));
    Assertions.assertEquals(0.0, waterBalanceColumn, 1e-3);
  }

  @Test
  public void runDistillationProcessTest() {
    neqsim.thermo.system.SystemInterface feed =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 10.0, 2.01325);
    feed.addComponent("methane", 0.1);
    feed.addComponent("propane", 0.3);
    feed.addComponent("n-hexane", 0.6);
    feed.setMixingRule("classic");

    Stream feedToRegenerator = new Stream("feed to regenerator", feed);
    feedToRegenerator.setFlowRate(400.0, "kg/hr");
    feedToRegenerator.setTemperature(20.0, "C");
    feedToRegenerator.setPressure(2.01325, "barg");

    DistillationColumn column = new DistillationColumn("distillation column", 1, true, true);
    column.addFeedStream(feedToRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 70.0);
    column.getCondenser().setOutTemperature(273.15 - 10.0);
    column.setTopPressure(1.0 + ThermodynamicConstantsInterface.referencePressure);
    column.setBottomPressure(1.0 + ThermodynamicConstantsInterface.referencePressure);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedToRegenerator);
    operations.add(column);

    operations.run();
    /*
     * logger.info("wt n-hexane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt methane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("methane") * 100.0);
     *
     * logger.info("wt propane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("propane") * 100.0);
     *
     * logger.info("wt n-hexane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt methane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("methane") * 100.0);
     *
     * logger.info("wt propane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("propane") * 100.0);
     */
  }

  @Test
  public void runDistillationProcessTest2() {
    neqsim.thermo.system.SystemInterface feed =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 5.01325);
    feed.addComponent("propane", 0.3);
    feed.addComponent("n-hexane", 0.6);
    feed.setMixingRule("classic");

    Stream feedToRegenerator = new Stream("feed to regenerator", feed);
    feedToRegenerator.setFlowRate(400.0, "kg/hr");
    feedToRegenerator.setTemperature(80.0, "C");
    feedToRegenerator.setPressure(5.0, "barg");

    DistillationColumn column = new DistillationColumn("distillation column", 1, true, true);
    column.addFeedStream(feedToRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 100.0);
    column.getCondenser().setOutTemperature(273.15 + 50.0);
    column.setTopPressure(1.0 + 5.01325);
    column.setBottomPressure(1.0 + 5.01325);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedToRegenerator);
    operations.add(column);

    operations.run();
    /*
     * logger.info("wt n-hexane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt propane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("propane") * 100.0);
     *
     * logger.info("wt n-hexane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt propane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("propane") * 100.0);
     */
  }

  @Test
  public void runDistillationProcessTest3() {
    neqsim.thermo.system.SystemInterface feed =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 2.01325);
    feed.addComponent("ethane", 0.01);
    feed.addComponent("n-hexane", 0.99);
    feed.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface feed2 =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 2.01325);
    feed2.addComponent("ethane", 0.99);
    feed2.addComponent("n-hexane", 0.01);
    feed2.setMixingRule("classic");

    Stream feedToRegenerator = new Stream("feed to regenerator", feed);
    feedToRegenerator.setFlowRate(400.0, "kg/hr");
    feedToRegenerator.setTemperature(40.0, "C");
    feedToRegenerator.setPressure(2.0, "barg");

    Stream feedToRegenerator2 = new Stream("feed2 to regenerator", feed2);
    feedToRegenerator2.setFlowRate(400.0, "kg/hr");
    feedToRegenerator2.setTemperature(80.0, "C");
    feedToRegenerator2.setPressure(2.0, "barg");

    DistillationColumn column = new DistillationColumn("distillation column", 2, false, false);

    column.addFeedStream(feedToRegenerator2, 0);
    column.addFeedStream(feedToRegenerator, 1);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedToRegenerator);
    operations.add(feedToRegenerator2);
    operations.add(column);

    operations.run();
    operations.run();
    operations.run();

    /*
     * logger.info("wt n-hexane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt ethane from column " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("ethane") * 100.0);
     *
     * logger.info("wt n-hexane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("n-hexane") * 100.0);
     *
     * logger.info("wt ethane from gas column " +
     * column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("ethane") * 100.0);
     *
     * logger.info("flow rate gas " +
     * column.getGasOutStream().getFluid().getPhase("gas").getFlowRate("kg/hr") + " kg/hr");
     *
     * logger.info("flow rate oil " +
     * column.getLiquidOutStream().getFluid().getPhase("oil").getFlowRate("kg/hr") + " kg/hr");
     *
     * System.out .println("flow rate oil " + feedToRegenerator.getFluid().getFlowRate("kg/hr") +
     * " kg/hr");
     *
     * System.out .println("flow rate gas " + feedToRegenerator2.getFluid().getFlowRate("kg/hr") +
     * " kg/hr"); column.massBalanceCheck();
     */
  }
}
