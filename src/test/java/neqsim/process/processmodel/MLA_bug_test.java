package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.absorber.WaterStripperColumn;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.Reboiler;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.util.empiric.BukacekWaterInGas;

public class MLA_bug_test extends neqsim.NeqSimTest {
  Logger logger = LogManager.getLogger(MLA_bug_test.class);

  @Test
  public void runProcessTEG() throws InterruptedException {
    ProcessSystem p = new ProcessSystem();

    SystemInterface feedGas = new SystemSrkCPA();
    feedGas.addComponent("nitrogen", 0.245);
    feedGas.addComponent("CO2", 3.4);
    feedGas.addComponent("methane", 85.7);
    feedGas.addComponent("ethane", 5.981);
    feedGas.addComponent("propane", 2.743);
    feedGas.addComponent("i-butane", 0.37);
    feedGas.addComponent("n-butane", 0.77);
    feedGas.addComponent("i-pentane", 0.142);
    feedGas.addComponent("n-pentane", 0.166);
    feedGas.addComponent("n-hexane", 0.06);
    feedGas.addComponent("benzene", 0.01);
    feedGas.addComponent("water", 0.0);
    feedGas.addComponent("TEG", 0.0);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(false);
    feedGas.init(0);

    SystemInterface coolingMedium = new SystemSrkCPA();
    coolingMedium.setTemperature(273.15 + 20.0);
    coolingMedium.setPressure(10.0, "bara");
    coolingMedium.addComponent("water", 0.7, "kg/sec");
    coolingMedium.addComponent("MEG", 0.3, "kg/sec");
    coolingMedium.setMixingRule(10);
    coolingMedium.setMultiPhaseCheck(false);

    StreamInterface coolingWater1 = new Stream("cooling water 1", coolingMedium);
    coolingWater1.setFlowRate(30000.0, "kg/hr");
    coolingWater1.setTemperature(18.0, "C");
    coolingWater1.setPressure(7.5, "bara");
    p.add(coolingWater1);

    StreamInterface coolingWater2 = new Stream("cooling water 2", coolingMedium.clone());
    coolingWater2.setFlowRate(3500.0, "kg/hr");
    coolingWater2.setTemperature(18.0, "C");
    coolingWater2.setPressure(7.5, "bara");
    p.add(coolingWater2);

    StreamInterface dryFeedGas = new Stream("dry feed gas", feedGas);
    dryFeedGas.setFlowRate(4.65, "MSm3/day");
    dryFeedGas.setTemperature(25.0, "C");
    dryFeedGas.setPressure(70.0, "bara");
    p.add(dryFeedGas);

    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil("water saturator", dryFeedGas);
    p.add(saturatedFeedGas);

    StreamInterface waterSaturatedFeedGas =
        new Stream("water saturated feed gas", saturatedFeedGas.getOutletStream());
    p.add(waterSaturatedFeedGas);

    SystemInterface feedTEG = feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97});

    Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", waterSaturatedFeedGas);
    feedTPsetterToAbsorber.setOutPressure(85.0, "bara");
    feedTPsetterToAbsorber.setOutTemperature(35.0, "C");
    p.add(feedTPsetterToAbsorber);

    StreamInterface feedToAbsorber =
        new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());
    p.add(feedToAbsorber);

    WaterDewPointAnalyser waterDewPointAnalyserToAbsorber =
        new WaterDewPointAnalyser("water dew point gas to absorber", feedToAbsorber);
    waterDewPointAnalyserToAbsorber.setMethod("multiphase");
    waterDewPointAnalyserToAbsorber.setReferencePressure(85.0);
    p.add(waterDewPointAnalyserToAbsorber);

    StreamInterface TEGFeed = new Stream("TEG feed", feedTEG);
    TEGFeed.setFlowRate(5500.0, "kg/hr");
    TEGFeed.setTemperature(48.5, "C");
    TEGFeed.setPressure(85.0, "bara");
    p.add(TEGFeed);

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.7);
    absorber.setInternalDiameter(2.240);
    p.add(absorber);

    SimpleTEGAbsorber absorberSetWater = new SimpleTEGAbsorber("TEG absorber set water");
    absorberSetWater.addGasInStream(feedToAbsorber);
    absorberSetWater.addSolventInStream(TEGFeed);
    absorberSetWater.setInternalDiameter(2.240);
    absorberSetWater.setWaterInDryGas(30e-6);
    p.add(absorberSetWater);

    StreamInterface dehydratedGasSetWater =
        new Stream("dry gas from absorber set water", absorberSetWater.getGasOutStream());
    p.add(dehydratedGasSetWater);

    Heater coolerDehydGas = new Heater("coolerDehydGas", dehydratedGasSetWater);
    coolerDehydGas.setOutTemperature(273.15 + 10.0);
    p.add(coolerDehydGas);

    Separator sepDehydratedGasSetWater =
        new Separator("dehyd gas separator", coolerDehydGas.getOutletStream());
    p.add(sepDehydratedGasSetWater);

    Heater pipelineSetTP = new Heater("pipelineSetTP", sepDehydratedGasSetWater.getGasOutStream());
    pipelineSetTP.setOutPressure(168.0, "bara");
    pipelineSetTP.setOutTemperature(4.0, "C");
    p.add(pipelineSetTP);

    StreamInterface pipelineSetTPStream =
        new Stream("pipelineSetTP stream", pipelineSetTP.getOutletStream());
    p.add(pipelineSetTPStream);

    WaterDewPointAnalyser waterDewPointAnalyser3 =
        new WaterDewPointAnalyser("water dew point analyser3", dehydratedGasSetWater);
    waterDewPointAnalyser3.setReferencePressure(70.0);
    p.add(waterDewPointAnalyser3);

    StreamInterface dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());
    p.add(dehydratedGas);

    StreamInterface richTEG = new Stream("rich TEG from absorber", absorber.getLiquidOutStream());
    p.add(richTEG);

    WaterDewPointAnalyser waterDewPointAnalyser2 =
        new WaterDewPointAnalyser("water dew point analyser", dehydratedGas);
    waterDewPointAnalyser2.setReferencePressure(70.0);
    p.add(waterDewPointAnalyser2);

    ThrottlingValve glycol_flash_valve = new ThrottlingValve("Rich TEG HP flash valve", richTEG);
    glycol_flash_valve.setOutletPressure(4.8);
    p.add(glycol_flash_valve);

    Heater richGLycolHeaterCondenser =
        new Heater("rich TEG preheater", glycol_flash_valve.getOutletStream());
    p.add(richGLycolHeaterCondenser);

    HeatExchanger heatEx2 =
        new HeatExchanger("rich TEG heat exchanger 1", richGLycolHeaterCondenser.getOutletStream());
    heatEx2.setGuessOutTemperature(273.15 + 62.0);
    heatEx2.setUAvalue(2224.0);
    p.add(heatEx2);

    Separator flashSep = new Separator("degassing separator", heatEx2.getOutStream(0));
    flashSep.setInternalDiameter(1.2);
    p.add(flashSep);

    StreamInterface flashGas =
        new Stream("gas from degassing separator", flashSep.getGasOutStream());
    p.add(flashGas);

    StreamInterface flashLiquid =
        new Stream("liquid from degassing separator", flashSep.getLiquidOutStream());
    p.add(flashLiquid);

    Filter fineFilter = new Filter("TEG fine filter", flashLiquid);
    fineFilter.setDeltaP(0.0, "bara");
    p.add(fineFilter);

    HeatExchanger heatEx =
        new HeatExchanger("lean/rich TEG heat-exchanger", fineFilter.getOutletStream());
    heatEx.setGuessOutTemperature(273.15 + 130.0);
    heatEx.setUAvalue(8316.0);
    p.add(heatEx);

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("Rich TEG LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setOutletPressure(1.2);
    p.add(glycol_flash_valve2);

    SystemInterface stripGas = feedGas.clone();

    StreamInterface strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(180.0, "Sm3/hr");
    strippingGas.setTemperature(78.3, "C");
    strippingGas.setPressure(1.2, "bara");
    p.add(strippingGas);

    StreamInterface gasToReboiler = strippingGas.clone("gas to reboiler");
    p.add(gasToReboiler);

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.setTemperatureTolerance(5.0e-2);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 197.5);
    column.getCondenser().setOutTemperature(273.15 + 85.0);
    column.getTray(1).addStream(gasToReboiler);
    column.setTopPressure(1.2);
    column.setBottomPressure(1.2);
    column.setInternalDiameter(0.56);
    p.add(column);

    Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + 47.0);
    p.add(coolerRegenGas);

    HeatExchanger overheadCondHX =
        new HeatExchanger("overhead condenser heat-exchanger", column.getGasOutStream());
    overheadCondHX.setGuessOutTemperature(273.15 + 50.0);
    overheadCondHX.setUAvalue(3247.0);
    overheadCondHX.setFeedStream(1, coolingWater1);
    p.add(overheadCondHX);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());
    p.add(sepregenGas);

    StreamInterface gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());
    p.add(gasToFlare);

    StreamInterface liquidToTreatment =
        new Stream("water to treatment", sepregenGas.getLiquidOutStream());
    p.add(liquidToTreatment);

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(2);
    stripper.setStageEfficiency(1.0);
    p.add(stripper);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);
    p.add(recycleGasFromStripper);

    heatEx.setFeedStream(1, stripper.getLiquidOutStream());

    Heater bufferTank = new Heater("TEG buffer tank", heatEx.getOutStream(1));
    bufferTank.setOutTemperature(273.15 + 90.5);
    p.add(bufferTank);

    Pump hotLeanTEGPump = new Pump("lean TEG LP pump", bufferTank.getOutletStream());
    hotLeanTEGPump.setOutletPressure(3.0);
    hotLeanTEGPump.setIsentropicEfficiency(0.75);
    p.add(hotLeanTEGPump);

    heatEx2.setFeedStream(1, hotLeanTEGPump.getOutletStream());

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", heatEx2.getOutStream(1));
    coolerhOTteg3.setOutTemperature(273.15 + 48.5);
    p.add(coolerhOTteg3);

    HeatExchanger coolerhOTteg3HX =
        new HeatExchanger("lean TEG heat-exchanger 3", heatEx2.getOutStream(1));
    coolerhOTteg3HX.setGuessOutTemperature(273.15 + 40.0);
    coolerhOTteg3HX.setUAvalue(7819.0);
    coolerhOTteg3HX.setFeedStream(1, coolingWater2);
    p.add(coolerhOTteg3HX);

    Pump hotLeanTEGPump2 = new Pump("lean TEG HP pump", coolerhOTteg3.getOutletStream());
    hotLeanTEGPump2.setOutletPressure(85.0);
    hotLeanTEGPump2.setIsentropicEfficiency(0.75);
    p.add(hotLeanTEGPump2);

    StreamInterface leanTEGtoabs =
        new Stream("lean TEG to absorber", hotLeanTEGPump2.getOutletStream());
    p.add(leanTEGtoabs);

    SystemInterface pureTEG = feedGas.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    Stream makeupTEG = new Stream("makeup TEG", pureTEG);
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(48.5, "C");
    makeupTEG.setPressure(85.0, "bara");
    p.add(makeupTEG);

    Calculator makeupCalculator = new Calculator("TEG makeup calculator");
    makeupCalculator.addInputVariable(dehydratedGas);
    makeupCalculator.addInputVariable(flashGas);
    makeupCalculator.addInputVariable(gasToFlare);
    makeupCalculator.addInputVariable(liquidToTreatment);
    makeupCalculator.setOutputVariable(makeupTEG);
    p.add(makeupCalculator);

    Mixer makeupMixer = new Mixer("makeup mixer");
    makeupMixer.addStream(leanTEGtoabs);
    makeupMixer.addStream(makeupTEG);
    p.add(makeupMixer);

    Recycle recycleLeanTEG = new Recycle("lean TEG recycle");
    recycleLeanTEG.addStream(makeupMixer.getOutletStream());
    recycleLeanTEG.setOutletStream(TEGFeed);
    recycleLeanTEG.setPriority(200);
    recycleLeanTEG.setDownstreamProperty("flow rate");
    p.add(recycleLeanTEG);

    richGLycolHeaterCondenser.setEnergyStream(column.getCondenser().getEnergyStream());

    Thread runThr = p.runAsThread();
    try {
      runThr.join(300000);
    } catch (Exception ex) {
      logger.error("Something failed");
    }
    // System.out.println("water in gas " + dehydratedGas.getFluid().getComponent("water").getx());

    assertEquals(-19.1886678,
        p.getMeasurementDevice("water dew point analyser3").getMeasuredValue("C"), 1e-1);
    assertEquals(203.02433149,
        ((Reboiler) ((DistillationColumn) p.getUnit("TEG regeneration column")).getReboiler())
            .getDuty() / 1e3,
        5e-2);
  }

  @Test
  public void testBukacekWaterInGas() {
    assertEquals(-36.485388110,
        BukacekWaterInGas.waterDewPointTemperature(8.2504356945e-6, 70.0) - 273.15, 1e-2);
  }
}
