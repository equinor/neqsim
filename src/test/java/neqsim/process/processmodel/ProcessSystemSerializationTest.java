package neqsim.process.processmodel;

import org.junit.jupiter.api.Test;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.absorber.WaterStripperColumn;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;

public class ProcessSystemSerializationTest extends neqsim.NeqSimTest {
  @Test
  public void runTEGProcessTest2() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
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

    Stream dryFeedGasSmorbukk = new Stream("dry feed gas Smorbukk", feedGas);
    dryFeedGasSmorbukk.setFlowRate(10.0, "MSm3/day");
    dryFeedGasSmorbukk.setTemperature(25.0, "C");
    dryFeedGasSmorbukk.setPressure(40.0, "bara");

    StreamSaturatorUtil saturatedFeedGasSmorbukk =
        new StreamSaturatorUtil("water saturator Smorbukk", dryFeedGasSmorbukk);

    Stream waterSaturatedFeedGasSmorbukk =
        new Stream("water saturated feed gas Smorbukk", saturatedFeedGasSmorbukk.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserSmorbukk =
        new HydrateEquilibriumTemperatureAnalyser("hydrate temperature analyser Smorbukk",
            waterSaturatedFeedGasSmorbukk);

    Splitter SmorbukkSplit = new Splitter("Smorbukk Splitter", waterSaturatedFeedGasSmorbukk);
    double[] splitSmorbukk = {1.0 - 1e-10, 1e-10};
    SmorbukkSplit.setSplitFactors(splitSmorbukk);

    Stream dryFeedGasMidgard = new Stream("dry feed gas Midgard201", feedGas.clone());
    dryFeedGasMidgard.setFlowRate(10, "MSm3/day");
    dryFeedGasMidgard.setTemperature(5, "C");
    dryFeedGasMidgard.setPressure(40.0, "bara");

    StreamSaturatorUtil saturatedFeedGasMidgard =
        new StreamSaturatorUtil("water saturator Midgard", dryFeedGasMidgard);

    Stream waterSaturatedFeedGasMidgard =
        new Stream("water saturated feed gas Midgard", saturatedFeedGasMidgard.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyserMidgard =
        new HydrateEquilibriumTemperatureAnalyser("hydrate temperature analyser Midgard",
            waterSaturatedFeedGasMidgard);

    Splitter MidgardSplit = new Splitter("Midgard Splitter", waterSaturatedFeedGasMidgard);
    double[] splitMidgard = {1e-10, 1 - 1e-10};
    MidgardSplit.setSplitFactors(splitMidgard);

    StaticMixer TrainB = new StaticMixer("mixer TrainB");
    TrainB.addStream(SmorbukkSplit.getSplitStream(1));
    TrainB.addStream(MidgardSplit.getSplitStream(1));

    Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", TrainB.getOutletStream());
    feedTPsetterToAbsorber.setOutPressure(40.0, "bara");
    feedTPsetterToAbsorber.setOutTemperature(37.0, "C");

    Stream feedToAbsorber =
        new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser2 =
        new HydrateEquilibriumTemperatureAnalyser("hydrate temperature gas to absorber",
            feedToAbsorber);

    WaterDewPointAnalyser waterDewPointAnalyserToAbsorber =
        new WaterDewPointAnalyser("water dew point gas to absorber", feedToAbsorber);
    waterDewPointAnalyserToAbsorber.setMethod("multiphase");
    waterDewPointAnalyserToAbsorber.setReferencePressure(40.0);

    neqsim.thermo.system.SystemInterface feedTEG =
        (neqsim.thermo.system.SystemInterface) feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.01, 0.99});

    Stream TEGFeed = new Stream("TEG feed", feedTEG);
    TEGFeed.setFlowRate(8000.0, "kg/hr");
    TEGFeed.setTemperature(40.0, "C");
    TEGFeed.setPressure(40.0, "bara");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.8);
    absorber.setInternalDiameter(2.240);

    Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());

    Stream richTEG = new Stream("rich TEG from absorber", absorber.getLiquidOutStream());

    HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
        new HydrateEquilibriumTemperatureAnalyser("hydrate dew point analyser", dehydratedGas);
    waterDewPointAnalyser.setReferencePressure(70.0);

    WaterDewPointAnalyser waterDewPointAnalyser2 =
        new WaterDewPointAnalyser("water dew point analyser", dehydratedGas);
    waterDewPointAnalyser2.setReferencePressure(70.0);

    Heater condHeat = new Heater("Condenser heat exchanger", richTEG);

    ThrottlingValve glycol_flash_valve =
        new ThrottlingValve("Rich TEG HP flash valve", condHeat.getOutletStream());
    glycol_flash_valve.setOutletPressure(7.0);

    HeatExchanger heatEx2 =
        new HeatExchanger("rich TEG heat exchanger 1", glycol_flash_valve.getOutletStream());
    heatEx2.setGuessOutTemperature(273.15 + 90.0);
    heatEx2.setUAvalue(1450.0);

    neqsim.thermo.system.SystemInterface feedWater =
        (neqsim.thermo.system.SystemInterface) feedGas.clone();
    feedWater.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0});

    double addedWaterRate = 0.0;
    Stream waterFeed = new Stream("water to absorber", feedWater);
    waterFeed.setFlowRate(addedWaterRate, "kg/hr");
    waterFeed.setTemperature(90.0, "C");
    waterFeed.setPressure(7.0, "bara");

    Separator flashSep = new Separator("degassing separator", heatEx2.getOutStream(0));
    if (addedWaterRate > 0) {
      flashSep.addStream(waterFeed);
    }
    flashSep.setInternalDiameter(1.2);

    Stream flashGas = new Stream("gas from degassing separator", flashSep.getGasOutStream());

    Stream flashLiquid =
        new Stream("liquid from degassing separator", flashSep.getLiquidOutStream());

    Filter filter = new Filter("TEG fine filter", flashLiquid);
    filter.setDeltaP(0.0, "bara");

    HeatExchanger heatEx =
        new HeatExchanger("lean/rich TEG heat-exchanger", filter.getOutletStream());
    heatEx.setGuessOutTemperature(273.15 + 140.0);
    heatEx.setUAvalue(9140.0);

    double reboilerPressure = 1.4;
    double condenserPressure = 1.2;
    double feedPressureGLycol = (reboilerPressure + condenserPressure) / 2.0; // enters middle of
                                                                              // column
    double feedPressureStripGas = (reboilerPressure + condenserPressure) / 2.0; // enters middle of
                                                                                // column

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("Rich TEG LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setOutletPressure(feedPressureGLycol);

    neqsim.thermo.system.SystemInterface stripGas =
        (neqsim.thermo.system.SystemInterface) feedGas.clone();
    stripGas.setMolarComposition(
        new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(250.0 * 0.8, "kg/hr");
    strippingGas.setTemperature(180.0, "C");
    strippingGas.setPressure(feedPressureStripGas, "bara");

    Stream gasToReboiler = strippingGas.clone("gas to reboiler");

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.setTemperatureTolerance(5.0e-2);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 202.0);
    column.getCondenser().setOutTemperature(273.15 + 89.0);
    column.getTray(1).addStream(gasToReboiler);
    column.setTopPressure(condenserPressure);
    column.setBottomPressure(reboilerPressure);
    column.setInternalDiameter(0.56);

    Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + 15.0);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());
    Stream gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());
    Stream liquidToTrreatment = new Stream("water to treatment", sepregenGas.getLiquidOutStream());

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(3);
    stripper.setStageEfficiency(0.8);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    neqsim.thermo.system.SystemInterface pureTEG =
        (neqsim.thermo.system.SystemInterface) feedGas.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    heatEx.setFeedStream(1, stripper.getLiquidOutStream());
    heatEx2.setFeedStream(1, heatEx.getOutStream(1));

    Pump hotLeanTEGPump = new Pump("lean TEG LP pump", heatEx2.getOutStream(1));
    hotLeanTEGPump.setOutletPressure(40.0);
    hotLeanTEGPump.setIsentropicEfficiency(0.9);

    Stream makeupTEG = new Stream("makeup TEG", pureTEG);
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(100.0, "C");
    makeupTEG.setPressure(40.0, "bara");

    Calculator makeupCalculator = new Calculator("TEG makeup calculator");
    makeupCalculator.addInputVariable(dehydratedGas);
    makeupCalculator.addInputVariable(flashGas);
    makeupCalculator.addInputVariable(gasToFlare);
    makeupCalculator.addInputVariable(liquidToTrreatment);
    makeupCalculator.setOutputVariable(makeupTEG);

    StaticMixer makeupMixer = new StaticMixer("makeup mixer");
    makeupMixer.addStream(hotLeanTEGPump.getOutletStream());
    // makeupMixer.addStream(makeupTEG);

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", makeupMixer.getOutletStream());
    coolerhOTteg3.setOutTemperature(273.15 + 40.0);

    condHeat.setEnergyStream(column.getCondenser().getEnergyStream());

    Stream leanTEGtoabs = new Stream("lean TEG to absorber", coolerhOTteg3.getOutletStream());

    Recycle recycleLeanTEG = new Recycle("lean TEG recycle");
    recycleLeanTEG.addStream(leanTEGtoabs);
    recycleLeanTEG.setOutletStream(TEGFeed);
    recycleLeanTEG.setPriority(200);
    recycleLeanTEG.setDownstreamProperty("flow rate");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(dryFeedGasSmorbukk);
    operations.add(saturatedFeedGasSmorbukk);
    operations.add(waterSaturatedFeedGasSmorbukk);
    operations.add(hydrateTAnalyserSmorbukk);
    operations.add(SmorbukkSplit);

    operations.add(dryFeedGasMidgard);
    operations.add(saturatedFeedGasMidgard);
    operations.add(waterSaturatedFeedGasMidgard);
    operations.add(hydrateTAnalyserMidgard);

    operations.add(MidgardSplit);

    operations.add(TrainB);

    operations.add(feedTPsetterToAbsorber);
    operations.add(feedToAbsorber);
    operations.add(hydrateTAnalyser2);
    operations.add(waterDewPointAnalyserToAbsorber);
    operations.add(TEGFeed);
    operations.add(absorber);
    operations.add(dehydratedGas);
    operations.add(richTEG);
    operations.add(waterDewPointAnalyser);
    operations.add(waterDewPointAnalyser2);

    operations.add(condHeat);

    operations.add(glycol_flash_valve);
    operations.add(heatEx2);
    operations.add(waterFeed);
    operations.add(flashSep);
    operations.add(flashGas);
    operations.add(flashLiquid);

    operations.add(filter);
    operations.add(heatEx);
    operations.add(glycol_flash_valve2);
    operations.add(strippingGas);

    operations.add(gasToReboiler);
    operations.add(column);

    operations.add(coolerRegenGas);
    operations.add(sepregenGas);
    operations.add(gasToFlare);
    operations.add(liquidToTrreatment);

    operations.add(stripper);
    operations.add(recycleGasFromStripper);

    operations.add(hotLeanTEGPump);
    operations.add(makeupTEG);
    operations.add(makeupCalculator);
    operations.add(makeupMixer);
    operations.add(coolerhOTteg3);
    operations.add(leanTEGtoabs);
    operations.add(recycleLeanTEG);

    // Check that process can run
    operations.run();

    // Serialize to xml using XStream
    XStream xstream = new XStream();
    String xml = xstream.toXML(operations);

    xstream.addPermission(AnyTypePermission.ANY);

    // Deserialize from xml
    neqsim.process.processmodel.ProcessSystem operationsCopy =
        (neqsim.process.processmodel.ProcessSystem) xstream.fromXML(xml);
    operationsCopy.run();
  }
}
