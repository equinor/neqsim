package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;

public class GlycolRigTest extends neqsim.NeqSimTest {
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

    Stream gasToReboiler = strippingGas.clone();
    gasToReboiler.setName("gas to reboiler");

    Stream TEGtoRegenerator = new Stream("feedTEG", feedTEG);
    TEGtoRegenerator.setName("TEG to regenerator");
    TEGtoRegenerator.setFlowRate(400.0, "kg/hr");
    TEGtoRegenerator.setTemperature(145.0, "C");
    TEGtoRegenerator.setPressure(0.2, "barg");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(TEGtoRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 209.0);
    column.getCondenser().setOutTemperature(273.15 + 104.0);
    // column.getReboiler().addStream(gasToReboiler);
    column.getTray(1).addStream(gasToReboiler); // this does not work at the moment
    column.setTopPressure(0.1 + 1.01325);
    column.setBottomPressure(0.2 + 1.01325);

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(2);
    stripper.setStageEfficiency(0.7);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    Heater coolerPipe = new Heater(column.getGasOutStream());
    coolerPipe.setName("heat loss cooling");
    coolerPipe.setOutTemperature(273.15 + 81.0);

    Heater coolerRegenGas = new Heater(coolerPipe.getOutStream());
    coolerRegenGas.setName("regen gas cooler");
    coolerRegenGas.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
    sepregenGas.setName("regen gas separator");

    Compressor blower = new Compressor(sepregenGas.getGasOutStream());
    blower.setOutletPressure(0.2, "barg");

    Heater gasHeater = new Heater(blower.getOutStream());
    gasHeater.setOutTemperature(273.15 + 53.0);

    Recycle recycleGasfEED = new Recycle("FEED gas recirc");
    recycleGasfEED.addStream(gasHeater.getOutStream());
    recycleGasfEED.setOutletStream(strippingGas);
    recycleGasfEED.setPriority(200);

    Heater coolerStripper = new Heater(stripper.getSolventOutStream());
    coolerStripper.setName("TEG cooler");
    coolerStripper.setOutTemperature(273.15 + 98.0);

    Stream liquidToTreatment = new Stream(sepregenGas.getLiquidOutStream());
    liquidToTreatment.setName("water to treatment");

    Mixer TEGWaterMixer = new Mixer("TEG water mixer");
    TEGWaterMixer.addStream(coolerStripper.getOutStream());
    TEGWaterMixer.addStream(liquidToTreatment);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
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
      runThr.join(100000);
    } catch (Exception ex) {

    }
    double wtpWaterRichTEG =
        TEGtoRegenerator.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    double wtpWaterFromReboil =
        column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    double wtpWaterFromStripper =
        stripper.getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("water") * 100.0;
    System.out.println("wtpRichTEG " + wtpWaterRichTEG);
    System.out.println("wtpWaterFromReboil " + wtpWaterFromReboil);
    System.out.println("wtpWaterFromStripper " + wtpWaterFromStripper);
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
    System.out.println("water to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    System.out.println("TEG to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("TEG").getTotalFlowRate("kg/hr"));
    // System.out.println("oxygen to regenerator "
    // + TEGtoRegenerator.getFluid().getComponent("oxygen").getTotalFlowRate("kg/hr"));
    System.out.println("nitrogen to regenerator "
        + TEGtoRegenerator.getFluid().getComponent("nitrogen").getTotalFlowRate("kg/hr"));

    System.out.println("water liquid from regenerator "
        + column.getLiquidOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    System.out.println("water gas from regenerator "
        + column.getGasOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr"));
    System.out.println("water from stripping gas "
        + gasToReboiler.getFluid().getComponent("water").getTotalFlowRate("kg/hr"));

    double waterBalanceColumn =
        column.getLiquidOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            + column.getGasOutStream().getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            - TEGtoRegenerator.getFluid().getComponent("water").getTotalFlowRate("kg/hr")
            - gasToReboiler.getFluid().getComponent("water").getTotalFlowRate("kg/hr");
    System.out.println("water balance " + waterBalanceColumn);

    System.out.println("wt water to reboil "
        + TEGtoRegenerator.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0);

    System.out.println("wt water from mixer "
        + TEGWaterMixer.getFluid().getPhase("aqueous").getWtFrac("water") * 100.0);
    // strippingGas.displayResult();
    System.out.println("stripping gas rate " + strippingGas.getFlowRate("kg/hr"));
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

    Stream feedToRegenerator = new Stream("feed", feed);
    feedToRegenerator.setName("feed to regenerator");
    feedToRegenerator.setFlowRate(400.0, "kg/hr");
    feedToRegenerator.setTemperature(20.0, "C");
    feedToRegenerator.setPressure(2.01325, "barg");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("distillation column");
    column.addFeedStream(feedToRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 70.0);
    column.getCondenser().setOutTemperature(273.15 - 10.0);
    column.setTopPressure(1.0 + 1.01325);
    column.setBottomPressure(1.0 + 1.01325);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedToRegenerator);
    operations.add(column);

    operations.run();

    System.out.println("wt n-hexane from column "
        + column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("n-hexane") * 100.0);

    System.out.println("wt methane from column "
        + column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("methane") * 100.0);

    System.out.println("wt propane from column "
        + column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("propane") * 100.0);


    System.out.println("wt n-hexane from gas column "
        + column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("n-hexane") * 100.0);

    System.out.println("wt methane from gas column "
        + column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("methane") * 100.0);

    System.out.println("wt propane from gas column "
        + column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("propane") * 100.0);

  }

  @Test
  public void runDistillationProcessTest2() {
    neqsim.thermo.system.SystemInterface feed =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 5.01325);
    feed.addComponent("propane", 0.3);
    feed.addComponent("n-hexane", 0.6);
    feed.setMixingRule("classic");

    Stream feedToRegenerator = new Stream("feed", feed);
    feedToRegenerator.setName("feed to regenerator");
    feedToRegenerator.setFlowRate(400.0, "kg/hr");
    feedToRegenerator.setTemperature(40.0, "C");
    feedToRegenerator.setPressure(5.0, "barg");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("distillation column");
    column.addFeedStream(feedToRegenerator, 1);
    column.getReboiler().setOutTemperature(273.15 + 100.0);
    column.getCondenser().setOutTemperature(273.15 + 50.0);
    column.setTopPressure(1.0 + 5.01325);
    column.setBottomPressure(1.0 + 5.01325);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedToRegenerator);
    operations.add(column);

    operations.run();

    System.out.println("wt n-hexane from column "
        + column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("n-hexane") * 100.0);

    System.out.println("wt propane from column "
        + column.getLiquidOutStream().getFluid().getPhase("oil").getWtFrac("propane") * 100.0);


    System.out.println("wt n-hexane from gas column "
        + column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("n-hexane") * 100.0);

    System.out.println("wt propane from gas column "
        + column.getGasOutStream().getFluid().getPhase("gas").getWtFrac("propane") * 100.0);

  }
}
