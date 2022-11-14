package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.filter.Filter;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.SetPoint;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * Test class for GlycolRig.
 */
public class GlycolRigTestML extends neqsim.NeqSimTest {
  ProcessSystem p;
  String _name = "TestProcessML";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }

  @Test
  public void runTEGProcessTest() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 145.0, 1.2);
    feedGas.addComponent("nitrogen", 1.42);
    feedGas.addComponent("CO2", 0.5339);
    feedGas.addComponent("methane", 95.2412);
    feedGas.addComponent("ethane", 2.2029);
    feedGas.addComponent("propane", 0.3231);
    feedGas.addComponent("i-butane", 0.01341);
    feedGas.addComponent("n-butane", 0.0000827);
    feedGas.addComponent("i-pentane", 0.0000679);
    feedGas.addComponent("n-pentane", 0.00035);
    feedGas.addComponent("n-hexane", 0.0006);
    feedGas.addComponent("benzene", 0.00001);
    feedGas.addComponent("water", 0.0);
    feedGas.addComponent("TEG", 0.0);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(false);
    feedGas.init(0);

    neqsim.thermo.system.SystemInterface coolingMedium =
    new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 145.0, 1.2);
    coolingMedium.setTemperature(273.15 + 20.0);
    coolingMedium.setPressure(10.0, "bara");
    coolingMedium.addComponent("water", 0.7, "kg/sec");
    coolingMedium.addComponent("MEG", 0.3, "kg/sec");
    coolingMedium.setMixingRule(10);
    coolingMedium.setMultiPhaseCheck(false);

    Stream coolingWater1 = new Stream(coolingMedium);
    coolingWater1.setName("cooling water 1");
    coolingWater1.setFlowRate(30000.0, "kg/hr");
    coolingWater1.setTemperature(18.0, "C");
    coolingWater1.setPressure(7.5, "bara");

    Stream coolingWater2 = new Stream(coolingMedium.clone());
    coolingWater2.setName("cooling water 2");
    coolingWater2.setFlowRate(3500.0, "kg/hr");
    coolingWater2.setTemperature(18.0, "C");
    coolingWater2.setPressure(7.5, "bara");

    Stream dryFeedGas = new Stream(feedGas);
    dryFeedGas.setName("dry feed gas");
    dryFeedGas.setFlowRate(4.65, "MSm3/day");
    dryFeedGas.setTemperature(25.0, "C");
    dryFeedGas.setPressure(70.0, "bara");

    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
    saturatedFeedGas.setName("water saturator");

    Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
    waterSaturatedFeedGas.setName("water saturated feed gas");

    neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    feedTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97});

    Heater feedTPsetterToAbsorber = new Heater(waterSaturatedFeedGas);
    feedTPsetterToAbsorber.setName("TP of gas to absorber");
    feedTPsetterToAbsorber.setOutPressure(85.0, "bara");
    feedTPsetterToAbsorber.setOutTemperature(35.0, "C");

    Stream feedToAbsorber = new Stream(feedTPsetterToAbsorber.getOutStream());
    feedToAbsorber.setName("feed to TEG absorber");

    WaterDewPointAnalyser waterDewPointAnalyserToAbsorber = new WaterDewPointAnalyser (feedToAbsorber);
    waterDewPointAnalyserToAbsorber.setMethod("multiphase");
    waterDewPointAnalyserToAbsorber.setReferencePressure(85.0);
    waterDewPointAnalyserToAbsorber.setName("water dew point gas to absorber");

    Stream TEGFeed = new Stream(feedTEG);
    TEGFeed.setName("lean TEG to absorber");
    TEGFeed.setFlowRate(5500.0, "kg/hr");
    TEGFeed.setTemperature(48.5, "C");
    TEGFeed.setPressure(85.0, "bara");

    SimpleTEGAbsorber absorber = new  SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.7);
    absorber.setInternalDiameter(2.240);

    SimpleTEGAbsorber absorberSetWater = new SimpleTEGAbsorber("TEG absorber set water");
    absorberSetWater.addGasInStream(feedToAbsorber);
    absorberSetWater.addSolventInStream(TEGFeed);
    absorberSetWater.setInternalDiameter(2.240);
    absorberSetWater.setWaterInDryGas(30e-6);

    Stream dehydratedGasSetWater = new Stream(absorberSetWater.getGasOutStream());
    dehydratedGasSetWater.setName("dry gas from absorber set water");

    Heater coolerDehydGas = new Heater(dehydratedGasSetWater);
    coolerDehydGas.setName("coolerDehydGas");
    coolerDehydGas.setOutTemperature(273.15 + 10.0);

    Separator sepDehydratedGasSetWater = new Separator(coolerDehydGas.getOutStream());
    sepDehydratedGasSetWater.setName("dehyd gas separator");

    Heater pipelineSetTP = new Heater(sepDehydratedGasSetWater.getGasOutStream());
    pipelineSetTP.setName("pipelineSetTP");
    pipelineSetTP.setOutPressure(168.0, "bara");
    pipelineSetTP.setOutTemperature(4.0, "C");

    Stream pipelineSetTPStream = new Stream(pipelineSetTP.getOutStream());
    pipelineSetTPStream.setName("pipelineSetTP stream");

    WaterDewPointAnalyser waterDewPointAnalyser3 = new WaterDewPointAnalyser(dehydratedGasSetWater);
    waterDewPointAnalyser3.setReferencePressure(70.0);
    waterDewPointAnalyser3.setName("water dew point analyser3");

    Stream dehydratedGas = new Stream(absorber.getGasOutStream());
    dehydratedGas.setName("dry gas from absorber");

    Stream richTEG = new Stream(absorber.getLiquidOutStream());
    richTEG.setName("rich TEG from absorber");

    WaterDewPointAnalyser waterDewPointAnalyser2 = new WaterDewPointAnalyser(dehydratedGas);
    waterDewPointAnalyser2.setReferencePressure(70.0);
    waterDewPointAnalyser2.setName("water dew point analyser");

    ThrottlingValve glycol_flash_valve = new ThrottlingValve(richTEG);
    glycol_flash_valve.setName("Rich TEG HP flash valve");
    glycol_flash_valve.setOutletPressure(4.8);

    Heater richGLycolHeaterCondenser = new Heater(glycol_flash_valve.getOutStream());
    richGLycolHeaterCondenser.setName("rich TEG preheater");

    HeatExchanger heatEx2 = new HeatExchanger(richGLycolHeaterCondenser.getOutStream());
    heatEx2.setName("rich TEG heat exchanger 1");
    heatEx2.setGuessOutTemperature(273.15 + 62.0);
    heatEx2.setUAvalue(2224.0);

    Separator flashSep = new Separator(heatEx2.getOutStream(0));
    flashSep.setName("degasing separator");
    flashSep.setInternalDiameter(1.2);

    Stream flashGas = new Stream(flashSep.getGasOutStream());
    flashGas.setName("gas from degasing separator");

    Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
    flashLiquid.setName("liquid from degasing separator");

    Filter fineFilter = new Filter(flashLiquid);
    fineFilter.setName("TEG fine filter");
    fineFilter.setDeltaP(0.0, "bara");

    HeatExchanger heatEx = new HeatExchanger(fineFilter.getOutStream());
    heatEx.setName("lean/rich TEG heat-exchanger");
    heatEx.setGuessOutTemperature(273.15 + 130.0);
    heatEx.setUAvalue(8316.0);

    ThrottlingValve glycol_flash_valve2 = new ThrottlingValve(heatEx.getOutStream(0));
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(1.2);

    neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface) feedGas.clone();

    Stream strippingGas = new Stream(stripGas);
    strippingGas.setName("stripGas");
    strippingGas.setFlowRate(180.0, "Sm3/hr");
    strippingGas.setTemperature(78.3, "C");
    strippingGas.setPressure(1.2, "bara");

    Stream gasToReboiler = new Stream(strippingGas.clone());
    gasToReboiler.setName("gas to reboiler");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(glycol_flash_valve2.getOutStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 197.5);
    column.getCondenser().setOutTemperature(273.15 + 80.0);
    column.getTray(1).addStream(gasToReboiler);
    column.setTopPressure(1.2);
    column.setBottomPressure(1.2);
    column.setInternalDiameter(0.56);

    Heater coolerRegenGas = new Heater(column.getGasOutStream());
    coolerRegenGas.setName("regen gas cooler");
    coolerRegenGas.setOutTemperature(273.15 + 47.0);

    HeatExchanger overheadCondHX = new HeatExchanger(column.getGasOutStream());
    overheadCondHX.setName("overhead condenser heat-exchanger");
    overheadCondHX.setGuessOutTemperature(273.15 + 50.0);
    overheadCondHX.setUAvalue(3247.0);
    overheadCondHX.setFeedStream(1, coolingWater1);

    Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
    sepregenGas.setName("regen gas separator");

    Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
    gasToFlare.setName("gas to flare");

    Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());
    liquidToTrreatment.setName("water to treatment");

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(2);
    stripper.setStageEfficiency(1.0);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    heatEx.setFeedStream(1, stripper.getLiquidOutStream());

    Heater bufferTank = new Heater(heatEx.getOutStream(1));
    bufferTank.setName("TEG buffer tank");
    bufferTank.setOutTemperature(273.15 + 90.5);

    Pump hotLeanTEGPump = new Pump(bufferTank.getOutStream());
    hotLeanTEGPump.setName("lean TEG LP pump");
    hotLeanTEGPump.setOutletPressure(3.0);
    hotLeanTEGPump.setIsentropicEfficiency(0.75);

    heatEx2.setFeedStream(1, hotLeanTEGPump.getOutStream());

    Heater coolerhOTteg3 = new Heater(heatEx2.getOutStream(1));
    coolerhOTteg3.setName("lean TEG cooler");
    coolerhOTteg3.setOutTemperature(273.15 + 48.5);

    HeatExchanger coolerhOTteg3HX = new HeatExchanger(heatEx2.getOutStream(1));
    coolerhOTteg3HX.setName("lean TEG heat-exchanger 3");
    coolerhOTteg3HX.setGuessOutTemperature(273.15 + 40.0);
    coolerhOTteg3HX.setUAvalue(7819.0);
    coolerhOTteg3HX.setFeedStream(1, coolingWater2);

    Pump hotLeanTEGPump2 = new Pump(coolerhOTteg3.getOutStream());
    hotLeanTEGPump2.setName("lean TEG HP pump");
    hotLeanTEGPump2.setOutletPressure(85.0);
    hotLeanTEGPump2.setIsentropicEfficiency(0.75);

    Stream leanTEGtoabs = new Stream(hotLeanTEGPump2.getOutStream());
    leanTEGtoabs.setName("lean TEG to absorber");

    neqsim.thermo.system.SystemInterface pureTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    pureTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    Stream makeupTEG = new Stream(pureTEG);
    makeupTEG.setName("makeup TEG");
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(48.5, "C");
    makeupTEG.setPressure(85.0, "bara");

    Calculator makeupCalculator = new Calculator("TEG makeup calculator");
    makeupCalculator.addInputVariable(dehydratedGas);
    makeupCalculator.addInputVariable(flashGas);
    makeupCalculator.addInputVariable(gasToFlare);
    makeupCalculator.addInputVariable(liquidToTrreatment);
    makeupCalculator.setOutputVariable(makeupTEG);

    StaticMixer makeupMixer = new StaticMixer("makeup mixer");
    makeupMixer.addStream(leanTEGtoabs);
    makeupMixer.addStream(makeupTEG);

    Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
    resycleLeanTEG.addStream(makeupMixer.getOutStream());
    resycleLeanTEG.setOutletStream(TEGFeed);
    resycleLeanTEG.setPriority(200);
    resycleLeanTEG.setDownstreamProperty("flow rate");

    richGLycolHeaterCondenser.setEnergyStream(column.getCondenser().getEnergyStream());





    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(coolingWater1);
    operations.add(coolingWater2);
    operations.add(dryFeedGas);
    operations.add(saturatedFeedGas);
    operations.add(waterSaturatedFeedGas);
    operations.add(feedTPsetterToAbsorber);
    operations.add(feedToAbsorber);
    operations.add(TEGFeed);
    operations.add(absorber);
    operations.add(absorberSetWater);
    operations.add(dehydratedGasSetWater);
    operations.add(coolerDehydGas);
    operations.add(sepDehydratedGasSetWater);
    operations.add(pipelineSetTP);
    operations.add(pipelineSetTPStream);
    operations.add(dehydratedGas);
    operations.add(richTEG);
    operations.add(glycol_flash_valve);
    operations.add(richGLycolHeaterCondenser);
    operations.add(heatEx2);
    operations.add(flashSep);
    operations.add(flashGas);
    operations.add(flashLiquid);
    operations.add(fineFilter);
    operations.add(heatEx);
    operations.add(fineFilter);
    operations.add(glycol_flash_valve2);
    operations.add(strippingGas);
    operations.add(gasToReboiler);
    operations.add(column);
    operations.add(coolerRegenGas);
    operations.add(overheadCondHX);
    operations.add(sepregenGas);
    operations.add(gasToFlare);
    operations.add(liquidToTrreatment);
    operations.add(stripper);
    operations.add(recycleGasFromStripper);
    operations.add(bufferTank);
    operations.add(hotLeanTEGPump);
    operations.add(coolerhOTteg3);
    operations.add(hotLeanTEGPump2);
    operations.add(leanTEGtoabs);
    operations.add(makeupTEG);
    operations.add(makeupCalculator);
    operations.add(makeupMixer);
    operations.add(resycleLeanTEG);






    Thread runThr = operations.runAsThread();
    try {
      runThr.join(100000);
    } catch (Exception ex) {

    }
    System.out.println("water in outlet  "
        + (absorber.getOutStream()).getFluid().getPhase(0).getComponent("water").getz()* 1.0e6);
  }
}

