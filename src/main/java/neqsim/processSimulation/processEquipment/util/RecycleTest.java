package neqsim.processSimulation.processEquipment.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.filter.Filter;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;

public class RecycleTest {
  static Logger logger = LogManager.getLogger(RecycleTest.class);
  ProcessSystem p;
  String _name = "TestProcess";

  public static void main(String[] args) throws InterruptedException {
      neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0,
      10.00);
      feedGas.addComponent("nitrogen", 0.245);
      feedGas.addComponent("CO2", 3.4);
      feedGas.addComponent("methane", 85.7);
      feedGas.addComponent("ethane", 5.981);
      feedGas.addComponent("propane", 0.2743);
      feedGas.addComponent("i-butane", 0.037);
      feedGas.addComponent("n-butane", 0.077);
      feedGas.addComponent("i-pentane", 0.0142);
      feedGas.addComponent("n-pentane", 0.0166);
      feedGas.addComponent("n-hexane", 0.006);
      feedGas.addComponent("benzene", 0.001);
      feedGas.addComponent("water", 0.0);
      feedGas.addComponent("TEG", 0);
      feedGas.setMixingRule(10);
      feedGas.setMultiPhaseCheck(false);
      feedGas.init(0);
      
  
      Stream dryFeedGasSmøbukk = new Stream("dry feed gas Smøbukk", feedGas);
      dryFeedGasSmøbukk.setFlowRate(20.0, "MSm3/day");
      dryFeedGasSmøbukk.setTemperature(35.0, "C");
      dryFeedGasSmøbukk.setPressure(85.0, "bara");
  
      StreamSaturatorUtil saturatedFeedGasSmøbukk = new StreamSaturatorUtil(dryFeedGasSmøbukk);
      saturatedFeedGasSmøbukk.setName("water saturator Smøbukk");
      saturatedFeedGasSmøbukk.setApprachToSaturation(0.93);
  
      Stream waterSaturatedFeedGasSmøbukk = new Stream(saturatedFeedGasSmøbukk.getOutStream());
      waterSaturatedFeedGasSmøbukk.setName("water saturated feed gas Smøbukk");
  
      HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserSmøbukk = new HydrateEquilibriumTemperatureAnalyser(
          waterSaturatedFeedGasSmøbukk);
      hydrateTAnalyserSmøbukk.setName("hydrate temperature analyser Smøbukk");
  
      Splitter SmøbukkSplit = new Splitter("Smøbukk Splitter", waterSaturatedFeedGasSmøbukk);
      double[] splitSmøbukk = {1.0, 1e-5};
      SmøbukkSplit.setSplitFactors(splitSmøbukk);
  
          
      Stream dryFeedGasMidgard= new Stream("dry feed gas Midgard201", feedGas.clone());
      dryFeedGasMidgard.setFlowRate(15.0, "MSm3/day");
      dryFeedGasMidgard.setTemperature(5.0, "C");
      dryFeedGasMidgard.setPressure(85.0, "bara");
  
      StreamSaturatorUtil saturatedFeedGasMidgard = new StreamSaturatorUtil(dryFeedGasMidgard);
      saturatedFeedGasMidgard.setName("water saturator Midgard");
  
      Stream waterSaturatedFeedGasMidgard = new Stream(saturatedFeedGasMidgard.getOutStream());
      waterSaturatedFeedGasMidgard.setName("water saturated feed gas Midgard");
  
      HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserMidgard = new HydrateEquilibriumTemperatureAnalyser(
          waterSaturatedFeedGasMidgard);
      hydrateTAnalyserMidgard.setName("hydrate temperature analyser Midgard");
  
      
      Splitter MidgardSplit = new Splitter("Midgard Splitter", waterSaturatedFeedGasMidgard);
      double[] splitMidgard= {0.5,  0.5};
      MidgardSplit.setSplitFactors(splitMidgard);
  
  
      StaticMixer TrainA = new StaticMixer("mixer TrainA");
      TrainA.addStream(MidgardSplit.getSplitStream(0));
      TrainA.addStream(SmøbukkSplit.getSplitStream(0));
  
      Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", TrainA.getOutletStream());
      feedTPsetterToAbsorber.setOutPressure(80.0, "bara");
      feedTPsetterToAbsorber.setOutTemperature(40.0, "C");
  
      Stream feedToAbsorber = new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());
  
      HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser2 = new HydrateEquilibriumTemperatureAnalyser(feedToAbsorber);
      hydrateTAnalyser2.setName("hydrate temperature gas to absorber");
  
      WaterDewPointAnalyser waterDewPointAnalyserToAbsorber = new WaterDewPointAnalyser(feedToAbsorber);
      waterDewPointAnalyserToAbsorber.setMethod("multiphase");
      waterDewPointAnalyserToAbsorber.setReferencePressure(85.0);
      waterDewPointAnalyserToAbsorber.setName("water dew point gas to absorber");
  
      neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
      feedTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.01, 0.99 });
  
      Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
      TEGFeed.setFlowRate(8000.0, "kg/hr");
      TEGFeed.setTemperature(50.0, "C");
      TEGFeed.setPressure(80.0, "bara");
  
      SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
      absorber.addGasInStream(feedToAbsorber);
      absorber.addSolventInStream(TEGFeed);
      absorber.setNumberOfStages(4);
      absorber.setStageEfficiency(0.8);
      absorber.setInternalDiameter(3.65);
  
      Stream dehydratedGas = new Stream(absorber.getGasOutStream());
      dehydratedGas.setName("dry gas from absorber");
  
      Stream richTEG = new Stream(absorber.getLiquidOutStream());
      richTEG.setName("rich TEG from absorber");
  
      HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser = new HydrateEquilibriumTemperatureAnalyser(
          dehydratedGas);
      waterDewPointAnalyser.setReferencePressure(70.0);
      waterDewPointAnalyser.setName("hydrate dew point analyser");
  
      WaterDewPointAnalyser waterDewPointAnalyser2 = new WaterDewPointAnalyser(dehydratedGas);
      waterDewPointAnalyser2.setReferencePressure(70.0);
      waterDewPointAnalyser2.setName("water dew point analyser");
      
      Heater condHeat = new Heater(richTEG);
      condHeat.setName("Condenser heat exchanger");
      condHeat.setOutTemperature(273.15+70);
  
      ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", condHeat.getOutStream());
      glycol_flash_valve.setName("Rich TEG HP flash valve");
      glycol_flash_valve.setOutletPressure(7.5);
      
      Heater heatEx2 = new Heater(glycol_flash_valve.getOutStream());
      heatEx2.setName("rich TEG heat exchanger 1");
      heatEx2.setOutTemperature(273.15 + 90.0);
  
      neqsim.thermo.system.SystemInterface feedWater = (neqsim.thermo.system.SystemInterface) feedGas.clone();
      feedWater.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 });
  
      Stream waterFeed = new Stream("lean TEG to absorber", feedWater);
      waterFeed.setFlowRate(0.0, "kg/hr");
      waterFeed.setTemperature(80.0, "C");
      waterFeed.setPressure(7.5, "bara");
  
      Separator flashSep = new Separator(heatEx2.getOutStream());
      flashSep.setName("degasing separator");
      flashSep.setInternalDiameter(1.2);
  
      Stream flashGas = new Stream(flashSep.getGasOutStream());
      flashGas.setName("gas from degasing separator");
  
      Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
      flashLiquid.setName("liquid from degasing separator");
  
      Filter filter = new Filter(flashLiquid);
      filter.setName("TEG fine filter");
      filter.setDeltaP(0.0, "bara");
  
      Heater heatEx = new Heater(filter.getOutStream());
      heatEx.setName("lean/rich TEG heat-exchanger");
      heatEx.setOutTemperature(273.15 + 100.0);
  
      ThrottlingValve glycol_flash_valve2 = new ThrottlingValve("LP flash valve", heatEx.getOutStream());
      glycol_flash_valve2.setName("Rich TEG LP flash valve");
      glycol_flash_valve2.setOutletPressure(1.2);
  
      neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface) feedGas.clone();
      stripGas.setMolarComposition(new double[] {
        0.0002128857213353137, 
        0.5110910188699972, 
        0.2190898698037966,
        0.16052409483865984,
        0.01606189605889591, 
        0.016465508225107565, 
        0.014826862541365097, 
        0.009608477368526951,
        0.02161220401002106, 
        0.00409126470350764, 
        0.008573609325532592, 
        0.01785715047203254, 
       0.0});
  
      Stream strippingGas = new Stream("stripGas", stripGas);
      strippingGas.setFlowRate(160.0, "kg/hr");
      strippingGas.setTemperature(180.0, "C");
      strippingGas.setPressure(1.2, "bara");

  
      Stream gasToReboiler = (Stream) (strippingGas).clone();
      gasToReboiler.setName("gas to reboiler");
  
      DistillationColumn column = new DistillationColumn(1, true, true);
      column.setName("TEG regeneration column");
      column.addFeedStream(glycol_flash_valve2.getOutStream(), 1);
      column.getReboiler().setOutTemperature(273.15 + 204.0);
      column.getCondenser().setOutTemperature(273.15 + 90.5);
      column.getTray(1).addStream(gasToReboiler);
      column.setTopPressure(1.01);
      column.setBottomPressure(1.2);
      column.setInternalDiameter(0.56);
  
      Heater coolerRegenGas = new Heater(column.getGasOutStream());
      coolerRegenGas.setName("regen gas cooler");
      coolerRegenGas.setOutTemperature(273.15 + 24.0);
  
      Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
      sepregenGas.setName("regen gas separator");
  
      Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
      gasToFlare.setName("gas to flare");
  
      Splitter splitterGasToFlare = new Splitter("splitter GasToFlare", gasToFlare);
      splitterGasToFlare.setSplitNumber(2);
      splitterGasToFlare.setFlowRates(new double[] {160.0, -1}, "kg/hr");
  
      Heater strippingFlareGasTPsetter = new Heater("TP of stripping gas + flare", splitterGasToFlare.getSplitStream(0));
      strippingFlareGasTPsetter.setOutPressure(1.2, "bara");
      strippingFlareGasTPsetter.setOutTemperature(180.0, "C");
  
  
      Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());
      liquidToTrreatment.setName("water to treatment");
  
  
      WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
      stripper.addSolventInStream(column.getLiquidOutStream());
      stripper.addGasInStream(strippingGas);
      stripper.setNumberOfStages(3);
      stripper.setStageEfficiency(0.8);
  
      Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
      recycleGasFromStripper.addStream(stripper.getGasOutStream());
      recycleGasFromStripper.setOutletStream(gasToReboiler);
      
  
      Recycle recycleFlareGas = new Recycle("stripping gas recirc Flare Gas");
      recycleFlareGas.addStream(strippingFlareGasTPsetter.getOutletStream());
      recycleFlareGas.setOutletStream(strippingGas);
  
  
      neqsim.thermo.system.SystemInterface pureTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
      pureTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 });
  
      Stream makeupTEG = new Stream("makeup TEG", pureTEG);
      makeupTEG.setFlowRate(1e-6, "kg/hr");
      makeupTEG.setTemperature(45, "C");
      makeupTEG.setPressure(1.2, "bara");
  
      Calculator makeupCalculator = new Calculator("TEG makeup calculator");
      makeupCalculator.addInputVariable(dehydratedGas);
      makeupCalculator.addInputVariable(flashGas);
      makeupCalculator.addInputVariable(gasToFlare);
      makeupCalculator.addInputVariable(liquidToTrreatment);
      makeupCalculator.setOutputVariable(makeupTEG);
  
      StaticMixer makeupMixer = new StaticMixer("makeup mixer");
      makeupMixer.addStream(stripper.getLiquidOutStream());
      makeupMixer.addStream(makeupTEG);
  
      
      Pump hotLeanTEGPump = new Pump(makeupMixer.getOutStream());
      hotLeanTEGPump.setName("lean TEG LP pump");
      hotLeanTEGPump.setOutletPressure(85.0);
      hotLeanTEGPump.setIsentropicEfficiency(0.9);
  
      Heater coolerhOTteg3 = new Heater(hotLeanTEGPump.getOutStream());
      coolerhOTteg3.setName("lean TEG cooler");
      coolerhOTteg3.setOutTemperature(273.15 + 100.0);
  
      Stream leanTEGtoabs = new Stream(coolerhOTteg3.getOutStream());
      leanTEGtoabs.setName("lean TEG to absorber");
  
      Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
      resycleLeanTEG.addStream(leanTEGtoabs);
      resycleLeanTEG.setOutletStream(TEGFeed);
      resycleLeanTEG.setDownstreamProperty("flow rate");
  
  
      neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
      operations.add(dryFeedGasSmøbukk);
      operations.add(saturatedFeedGasSmøbukk);
      operations.add(waterSaturatedFeedGasSmøbukk);
      operations.add(hydrateTAnalyserSmøbukk);
      operations.add(SmøbukkSplit);
  
      operations.add(dryFeedGasMidgard);
      operations.add(saturatedFeedGasMidgard);
      operations.add(waterSaturatedFeedGasMidgard);
      operations.add(hydrateTAnalyserMidgard);
  
      operations.add(MidgardSplit);
  
      operations.add(TrainA);
  
      operations.add(feedTPsetterToAbsorber);
      operations.add(hydrateTAnalyser2);
      operations.add(waterDewPointAnalyserToAbsorber);
      
      // Rec module TEG 
      operations.add(TEGFeed);
      operations.add(feedToAbsorber);
      operations.add(absorber);
      operations.add(richTEG);
      operations.add(condHeat);
      operations.add(glycol_flash_valve);
      operations.add(heatEx2);
      operations.add(waterFeed);
      operations.add(flashSep);
      operations.add(flashGas);
      operations.add(flashLiquid);
      operations.add(filter);
      operations.add(heatEx);

      // Rec Stripping gas 
      operations.add(strippingGas);

      // Rec module gas to reboiler
      operations.add(gasToReboiler);
      operations.add(glycol_flash_valve2);
      operations.add(column);
      operations.add(stripper);
      operations.add(recycleGasFromStripper);
      //Finish Rec module gas to reboiler

      operations.add(coolerRegenGas);
      operations.add(sepregenGas);
      operations.add(gasToFlare);
      operations.add(splitterGasToFlare);
      operations.add(strippingFlareGasTPsetter);
      // operations.add(recycleFlareGas);
      // Finish Rec Stripping gas module

      operations.add(liquidToTrreatment);
      operations.add(makeupTEG);
      operations.add(makeupCalculator);
      operations.add(makeupMixer);
      operations.add(hotLeanTEGPump);
      operations.add(coolerhOTteg3);
      operations.add(leanTEGtoabs);
      operations.add(resycleLeanTEG);
      //Finish Rec Lean TEG module
      
      operations.add(dehydratedGas);
      operations.add(waterDewPointAnalyser);
      operations.add(waterDewPointAnalyser2);
  

    Thread runThr = operations.runAsThread();
    try {
      runThr.join(10000000);
    } catch (Exception ex) {

     }
     logger.info("Water out " 
     + column.getGasOutStream().getFluid().getComponent("water").getx());
     logger.info("Condenser duty " 
     + column.getCondenser().getEnergyStream().getDuty());

    }
  }



