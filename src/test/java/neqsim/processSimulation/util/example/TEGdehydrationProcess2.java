package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>
 * TEGdehydrationProcess2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcess2 {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // Create the input fluid to the TEG process and saturate it with water at
    // scrubber conditions
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 1.03);
    feedGas.addComponent("CO2", 1.42);
    feedGas.addComponent("methane", 83.88);
    feedGas.addComponent("ethane", 8.07);
    feedGas.addComponent("propane", 3.54);
    feedGas.addComponent("i-butane", 0.54);
    feedGas.addComponent("n-butane", 0.84);
    feedGas.addComponent("i-pentane", 0.21);
    feedGas.addComponent("n-pentane", 0.19);
    feedGas.addComponent("n-hexane", 0.28);
    feedGas.addComponent("water", 0.0);
    feedGas.addComponent("TEG", 0);
    feedGas.createDatabase(true);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(true);

    Stream dryFeedGas = new Stream("dry feed gas", feedGas);
    dryFeedGas.setFlowRate(11.23, "MSm3/day");
    dryFeedGas.setTemperature(30.4, "C");
    dryFeedGas.setPressure(52.21, "bara");
    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil("water saturator", dryFeedGas);
    Stream waterSaturatedFeedGas =
        new Stream("waterSaturatedFeedGas", saturatedFeedGas.getOutletStream());
    neqsim.thermo.system.SystemInterface feedTEG = feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.02, 0.98});

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
    TEGFeed.setFlowRate(6.1 * 1100.0, "kg/hr");
    TEGFeed.setTemperature(35.4, "C");
    TEGFeed.setPressure(52.21, "bara");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("SimpleTEGAbsorber");
    absorber.addGasInStream(waterSaturatedFeedGas);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(10);
    absorber.setStageEfficiency(0.35);

    Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());
    Stream richTEG = new Stream("richTEG", absorber.getSolventOutStream());

    ThrottlingValve glycol_flash_valve = new ThrottlingValve("Rich TEG HP flash valve", richTEG);
    glycol_flash_valve.setOutletPressure(4.9);

    Heater richGLycolHeaterCondenser =
        new Heater("rich TEG preheater", glycol_flash_valve.getOutletStream());
    richGLycolHeaterCondenser.setOutTemperature(273.15 + 35.5);

    Heater richGLycolHeater =
        new Heater("rich TEG heater HP", richGLycolHeaterCondenser.getOutletStream());
    richGLycolHeater.setOutTemperature(273.15 + 62.0);

    Separator flashSep = new Separator("degassing separator", richGLycolHeater.getOutletStream());
    Stream flashGas = new Stream("gas from degassing separator", flashSep.getGasOutStream());
    Stream flashLiquid =
        new Stream("liquid from degassing separator", flashSep.getLiquidOutStream());

    Heater richGLycolHeater2 = new Heater("LP rich glycol heater", flashLiquid);
    richGLycolHeater2.setOutTemperature(273.15 + 139.0);
    richGLycolHeater2.setOutPressure(1.23);

    Mixer mixerTOreboiler = new Mixer("reboil mxer");
    mixerTOreboiler.addStream(richGLycolHeater2.getOutletStream());

    Heater heaterToReboiler = new Heater("heaterToReboiler", mixerTOreboiler.getOutletStream());
    heaterToReboiler.setOutTemperature(273.15 + 206.6);

    Separator regenerator2 = new Separator("regenerator2", heaterToReboiler.getOutletStream());

    Stream gasFromRegenerator = new Stream("gasFromRegenerator", regenerator2.getGasOutStream());

    Heater sepregenGasCooler = new Heater("sepregenGasCooler", gasFromRegenerator);
    sepregenGasCooler.setOutTemperature(273.15 + 109.0);
    sepregenGasCooler.setOutPressure(1.23);
    // sepregenGasCooler.setEnergyStream(richGLycolHeaterCondenser.getEnergyStream());

    Separator sepRegen = new Separator("sepRegen", sepregenGasCooler.getOutletStream());

    Stream liquidRegenReflux = new Stream("liquidRegenReflux", sepRegen.getLiquidOutStream());

    Recycle recycle2 = new Recycle("reflux recycle");
    recycle2.addStream(liquidRegenReflux);

    Heater coolerRegenGas = new Heater("coolerRegenGas", sepRegen.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + 35.5);

    Separator sepregenGas = new Separator("sepregenGas", coolerRegenGas.getOutletStream());

    Stream gasToFlare = new Stream("gasToFlare", sepregenGas.getGasOutStream());

    Stream liquidToTrreatment = new Stream("liquidToTrreatment", sepregenGas.getLiquidOutStream());

    Stream hotLeanTEG = new Stream("hotLeanTEG", regenerator2.getLiquidOutStream());

    neqsim.thermo.system.SystemInterface stripGas = feedGas.clone();
    stripGas.setMolarComposition(
        new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(70.0, "kg/hr");
    strippingGas.setTemperature(206.6, "C");
    strippingGas.setPressure(1.23, "bara");

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addGasInStream(strippingGas);
    stripper.addSolventInStream(hotLeanTEG);
    stripper.setNumberOfStages(10);
    stripper.setStageEfficiency(0.5);

    Recycle recycle3 = new Recycle("gas stripper recycle");
    recycle3.addStream(stripper.getGasOutStream());

    Pump hotLeanTEGPump = new Pump("hot lean TEG pump", stripper.getSolventOutStream());
    hotLeanTEGPump.setOutletPressure(20.0);

    Heater coolerhOTteg = new Heater("hot lean TEG cooler", hotLeanTEGPump.getOutletStream());
    coolerhOTteg.setOutTemperature(273.15 + 116.8);

    Heater coolerhOTteg2 = new Heater("medium hot lean TEG cooler", coolerhOTteg.getOutletStream());
    coolerhOTteg2.setOutTemperature(273.15 + 89.3);

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", coolerhOTteg2.getOutletStream());
    coolerhOTteg3.setOutTemperature(273.15 + 44.85);

    Pump hotLeanTEGPump2 = new Pump("lean TEG HP pump", coolerhOTteg3.getOutletStream());
    hotLeanTEGPump2.setOutletPressure(52.21);

    Stream leanTEGtoabs = new Stream("lean TEG to absorber", hotLeanTEGPump2.getOutletStream());

    neqsim.thermo.system.SystemInterface pureTEG = feedGas.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    Stream makeupTEG = new Stream("lean TEG to absorber", pureTEG);
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(35.4, "C");
    makeupTEG.setPressure(52.21, "bara");

    Calculator makeupCalculator = new Calculator("makeup calculator");
    makeupCalculator.addInputVariable(dehydratedGas);
    makeupCalculator.addInputVariable(flashGas);
    makeupCalculator.addInputVariable(gasToFlare);
    makeupCalculator.addInputVariable(liquidToTrreatment);
    makeupCalculator.setOutputVariable(makeupTEG);

    StaticMixer makeupMixer = new StaticMixer("makeup mixer");
    makeupMixer.addStream(leanTEGtoabs);
    makeupMixer.addStream(makeupTEG);

    Recycle recycleLeanTEG = new Recycle("lean TEG recycle");
    recycleLeanTEG.addStream(makeupMixer.getOutletStream());

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(dryFeedGas);
    operations.add(saturatedFeedGas);
    operations.add(waterSaturatedFeedGas);
    operations.add(TEGFeed);
    operations.add(absorber);
    operations.add(dehydratedGas);
    operations.add(richTEG);
    operations.add(glycol_flash_valve);
    operations.add(richGLycolHeaterCondenser);
    operations.add(richGLycolHeater);
    operations.add(flashSep);
    operations.add(flashGas);
    operations.add(flashLiquid);
    operations.add(richGLycolHeater2);
    operations.add(mixerTOreboiler);
    operations.add(heaterToReboiler);
    operations.add(regenerator2);
    operations.add(gasFromRegenerator);
    operations.add(sepregenGasCooler);
    operations.add(sepRegen);
    operations.add(liquidRegenReflux);
    operations.add(recycle2);

    operations.add(coolerRegenGas);
    operations.add(sepregenGas);
    operations.add(gasToFlare);
    operations.add(liquidToTrreatment);
    operations.add(hotLeanTEG);
    operations.add(strippingGas);
    operations.add(stripper);

    operations.add(recycle3);
    operations.add(hotLeanTEGPump);
    operations.add(coolerhOTteg);
    operations.add(coolerhOTteg2);
    operations.add(coolerhOTteg3);
    operations.add(hotLeanTEGPump2);
    operations.add(leanTEGtoabs);
    operations.add(makeupCalculator);
    operations.add(makeupTEG);
    operations.add(makeupMixer);
    operations.add(recycleLeanTEG);

    operations.run();
    richGLycolHeater2.getOutletStream().getFluid().display();
    System.out.println("Energy reboiler " + heaterToReboiler.getDuty());
    mixerTOreboiler.addStream(liquidRegenReflux);
    mixerTOreboiler.addStream(recycle3.getOutletStream());

    operations.run();
    absorber.replaceSolventInStream(recycleLeanTEG.getOutletStream());
    operations.run();
    // richGLycolHeater2.getOutStream().getFluid().display();

    System.out.println("Energy reboiler 2 " + heaterToReboiler.getDuty());

    System.out.println(
        "wt lean TEG after stripper " + ((WaterStripperColumn) operations.getUnit("TEG stripper"))
            .getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));

    operations.save("c:/temp/TEGprocessSimple.neqsim");
  }
}
