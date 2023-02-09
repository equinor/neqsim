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
    neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPA(273.15 + 42.0,
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
    //feedGas.setMolarComposition(new double[] { liftGas_N2, liftGas_CO2, liftGas_Methane, liftGas_Ethane, liftGas_Propane, liftGas_iButane, liftGas_nButane, liftGas_iPentane, liftGas_nPentane, 0.0, 0.0, 0.0, 0.0});

    Stream dryFeedGasSmøbukk = new Stream("dry feed gas Smøbukk", feedGas);
    dryFeedGasSmøbukk.setFlowRate(9.050238817357728, "MSm3/day");
    dryFeedGasSmøbukk.setTemperature(32.48975904520211, "C");
    dryFeedGasSmøbukk.setPressure(40.1205259689988, "bara");

    StreamSaturatorUtil saturatedFeedGasSmøbukk = new StreamSaturatorUtil(dryFeedGasSmøbukk);
    saturatedFeedGasSmøbukk.setName("water saturator Smøbukk");
    saturatedFeedGasSmøbukk.setApprachToSaturation(0.93);

    Stream waterSaturatedFeedGasSmøbukk = new Stream(saturatedFeedGasSmøbukk.getOutStream());
    waterSaturatedFeedGasSmøbukk.setName("water saturated feed gas Smøbukk");

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserSmøbukk = new HydrateEquilibriumTemperatureAnalyser(
        waterSaturatedFeedGasSmøbukk);
    hydrateTAnalyserSmøbukk.setName("hydrate temperature analyser Smøbukk");

    Splitter SmøbukkSplit = new Splitter("Smøbukk Splitter", waterSaturatedFeedGasSmøbukk);
    double[] splitSmøbukk = {0.9999999999, 1e-10};
    SmøbukkSplit.setSplitFactors(splitSmøbukk);

        
    Stream dryFeedGasMidgard= new Stream("dry feed gas Midgard201", feedGas.clone());
    dryFeedGasMidgard.setFlowRate(13.943929595435336, "MSm3/day");
    dryFeedGasMidgard.setTemperature(8.617179027757128, "C");
    dryFeedGasMidgard.setPressure(41.78261426145009, "bara");

    StreamSaturatorUtil saturatedFeedGasMidgard = new StreamSaturatorUtil(dryFeedGasMidgard);
    saturatedFeedGasMidgard.setName("water saturator Midgard");

    Stream waterSaturatedFeedGasMidgard = new Stream(saturatedFeedGasMidgard.getOutStream());
    waterSaturatedFeedGasMidgard.setName("water saturated feed gas Midgard");

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserMidgard = new HydrateEquilibriumTemperatureAnalyser(
        waterSaturatedFeedGasMidgard);
    hydrateTAnalyserMidgard.setName("hydrate temperature analyser Midgard");

    
    Splitter MidgardSplit = new Splitter("Midgard Splitter", waterSaturatedFeedGasMidgard);
    double[] splitMidgard= {0.11245704038738272, 0.8875429596126173};
    MidgardSplit.setSplitFactors(splitMidgard);


    StaticMixer TrainA = new StaticMixer("mixer TrainA");
    TrainA.addStream(MidgardSplit.getSplitStream(0));
    TrainA.addStream(SmøbukkSplit.getSplitStream(0));

    Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", TrainA.getOutletStream());
    feedTPsetterToAbsorber.setOutPressure(39.67967207899729, "bara");
    feedTPsetterToAbsorber.setOutTemperature(31.40346842493481, "C");

    Stream feedToAbsorber = new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser2 = new HydrateEquilibriumTemperatureAnalyser(feedToAbsorber);
    hydrateTAnalyser2.setName("hydrate temperature gas to absorber");

    WaterDewPointAnalyser waterDewPointAnalyserToAbsorber = new WaterDewPointAnalyser(feedToAbsorber);
    waterDewPointAnalyserToAbsorber.setMethod("multiphase");
    waterDewPointAnalyserToAbsorber.setReferencePressure(39.67967207899729);
    waterDewPointAnalyserToAbsorber.setName("water dew point gas to absorber");

    neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    feedTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.01, 0.99 });

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
    TEGFeed.setFlowRate(8923.576745846813, "kg/hr");
    TEGFeed.setTemperature(35.009563114341454, "C");
    TEGFeed.setPressure(39.67967207899729, "bara");

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

    ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", condHeat.getOutStream());
    glycol_flash_valve.setName("Rich TEG HP flash valve");
    glycol_flash_valve.setOutletPressure(7.513533287063168);
    
    Heater heatEx2 = new Heater(glycol_flash_valve.getOutStream());
    heatEx2.setName("rich TEG heat exchanger 1");
    heatEx2.setOutTemperature(273.15 + 90);

    neqsim.thermo.system.SystemInterface feedWater = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    feedWater.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 });

    Stream waterFeed = new Stream("lean TEG to absorber", feedWater);
    waterFeed.setFlowRate(0.0, "kg/hr");
    waterFeed.setTemperature(90, "C");
    waterFeed.setPressure(7.513533287063168, "bara");

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
    heatEx.setOutTemperature(273.15 + 105.0);

    ThrottlingValve glycol_flash_valve2 = new ThrottlingValve("LP flash valve", heatEx.getOutStream());
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(1.1714901511485545);

    neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    // stripGas.setMolarComposition(new double[] {
    //   0.0002128857213353137, 
    //   0.5110910188699972, 
    //   0.2190898698037966,
    //   0.16052409483865984,
    //   0.01606189605889591, 
    //   0.016465508225107565, 
    //   0.014826862541365097, 
    //   0.009608477368526951,
    //   0.02161220401002106, 
    //   0.00409126470350764, 
    //   0.008573609325532592, 
    //   0.01785715047203254, 
    //  0.0});

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(200, "kg/hr");
    strippingGas.setTemperature(185.4402968739743, "C");
    strippingGas.setPressure(1.1714901511485545, "bara");

    // Heater strippingGasTPsetter = new Heater("TP of stripping gas", strippingGas);
    // strippingGasTPsetter.setOutPressure(1.1714901511485545, "bara");
    // strippingGasTPsetter.setOutTemperature(185.4402968739743, "C");

    Stream gasToReboiler = (Stream) (strippingGas).clone();
    gasToReboiler.setName("gas to reboiler");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(glycol_flash_valve2.getOutStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 201.86991706268591);
    column.getCondenser().setOutTemperature(273.15 + 102.80145109927442);
    column.getTray(1).addStream(gasToReboiler);
    column.setTopPressure(1.1582401511485543);
    column.setBottomPressure(1.1714901511485545);
    column.setInternalDiameter(0.56);

    Heater coolerRegenGas = new Heater(column.getGasOutStream());
    coolerRegenGas.setName("regen gas cooler");
    coolerRegenGas.setOutTemperature(273.15 + 17.685590621935702);

    Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
    sepregenGas.setName("regen gas separator");

    Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
    gasToFlare.setName("gas to flare");

    Splitter splitterGasToFlare = new Splitter("splitter GasToFlare", gasToFlare);
    splitterGasToFlare.setSplitNumber(2);
    splitterGasToFlare.setFlowRates(new double[] {200, -1}, "kg/hr");

    Heater strippingFlareGasTPsetter = new Heater("TP of stripping gas + flare", splitterGasToFlare.getSplitStream(0));
    strippingFlareGasTPsetter.setOutPressure(1.1714901511485545, "bara");
    strippingFlareGasTPsetter.setOutTemperature(185.4402968739743, "C");


    Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());
    liquidToTrreatment.setName("water to treatment");


    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(3);
    stripper.setStageEfficiency(0.75);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);
    //recycleGasFromStripper.setPriority(500);
    //recycleGasFromStripper.setTolerance(1e-10);

    Recycle recycleFlareGas = new Recycle("stripping gas recirc Flare Gas");
    recycleFlareGas.addStream(strippingFlareGasTPsetter.getOutletStream());
    recycleFlareGas.setOutletStream(strippingGas);
    recycleFlareGas.setPriority(1000);
   //recycleFlareGas.setTolerance(0.1);


    neqsim.thermo.system.SystemInterface pureTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
    pureTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 });

    Stream makeupTEG = new Stream("makeup TEG", pureTEG);
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(35.009563114341454, "C");
    makeupTEG.setPressure(1.1714901511485545, "bara");

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
    hotLeanTEGPump.setOutletPressure(39.67967207899729);
    hotLeanTEGPump.setIsentropicEfficiency(0.9);

    Heater coolerhOTteg3 = new Heater(hotLeanTEGPump.getOutStream());
    coolerhOTteg3.setName("lean TEG cooler");
    coolerhOTteg3.setOutTemperature(273.15 + 35.009563114341454);

    condHeat.setEnergyStream(column.getCondenser().getEnergyStream());

    Stream leanTEGtoabs = new Stream(coolerhOTteg3.getOutStream());
    leanTEGtoabs.setName("lean TEG to absorber");

    Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
    resycleLeanTEG.addStream(leanTEGtoabs);
    resycleLeanTEG.setOutletStream(TEGFeed);
    resycleLeanTEG.setPriority(200);
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
      ///First le
      // Rec Strippins 
      operations.add(strippingGas);

      // Rec module gas to rebo,0
      operations.add(gasToReboiler);
      operations.add(glycol_flash_valve2);
      operations.add(column);
      operations.add(stripper);
      operations.add(recycleGasFromStripper);
      //Finish Rec module gas to reboil,1,0r

      operations.add(coolerRegenGas);
      operations.add(sepregenGas);
      operations.add(gasToFlare);
      operations.add(splitterGasToFlare);
      operations.add(strippingFlareGasTPsetter);
      operations.add(recycleFlareGas);
      // Finish Rec Stripping gas

      operations.add(liquidToTrreatment);
      operations.add(makeupTEG);
      operations.add(makeupCalculator);
      operations.add(makeupMixer);
      operations.add(hotLeanTEGPump);
      operations.add(coolerhOTteg3);
      operations.add(leanTEGtoabs);
      operations.add(resycleLeanTEG);
      //Finish Rec Lean TEG l  
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
     logger.info("calculatedWaterDewPointDryGas" + 
     (((WaterDewPointAnalyser)  operations.getMeasurementDevice("water dew point analyser")).getMeasuredValue() - 273.15));
     logger.info("Emissions");

     double stripping_gas_composition_n2 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("nitrogen").getz();
     double stripping_gas_composition_co2 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("CO2").getz(); 
     double stripping_gas_composition_ch4 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("methane").getz(); 
     double stripping_gas_composition_c2h6  = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("ethane").getz() ;
     double stripping_gas_composition_c3h8 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("propane").getz() ;
     double stripping_gas_composition_ic4h10 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("i-butane").getz() ;
     double stripping_gas_composition_nc4h10 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("n-butane").getz() ;
     double stripping_gas_composition_ic5h12 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("i-pentane").getz() ;
     double stripping_gas_composition_nc5h12 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("n-pentane").getz() ;
     double stripping_gas_composition_nc6h14 = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("n-hexane").getz() ;
     double stripping_gas_composition_benzene = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("benzene").getz() ;
     double stripping_gas_composition_water  = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("water").getz();
     double stripping_gas_composition_TEG  = ((Stream) operations.getUnit("stripGas")).getFluid().getComponent("TEG").getz();

     logger.info("stripping_gas_composition_n2 " + stripping_gas_composition_n2);
     logger.info("stripping_gas_composition_co2 " + stripping_gas_composition_co2);
     logger.info("stripping_gas_composition_ch4 " + stripping_gas_composition_ch4);
     logger.info("stripping_gas_composition_c2h6  " + stripping_gas_composition_c2h6);
     logger.info("stripping_gas_composition_c3h8 " + stripping_gas_composition_c3h8);
     logger.info("stripping_gas_composition_ic4h10 " + stripping_gas_composition_ic4h10);
     logger.info("stripping_gas_composition_nc4h10 " + stripping_gas_composition_nc4h10);
     logger.info("stripping_gas_composition_ic5h12" + stripping_gas_composition_ic5h12);
     logger.info("stripping_gas_composition_nc5h12 " + stripping_gas_composition_nc5h12);
     logger.info("stripping_gas_composition_nc6h14" + stripping_gas_composition_nc6h14);
     logger.info("stripping_gas_composition_benzene " + stripping_gas_composition_benzene);
     logger.info("stripping_gas_composition_water " + stripping_gas_composition_water);
     logger.info("stripping_gas_composition_TEG" + stripping_gas_composition_TEG);
     
    }
  }



