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
 * <p>TEGdehydrationProcess2 class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcess2 {
    /**
     * <p>main.</p>
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
        StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
        Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
        saturatedFeedGas.setName("water saturator");
        neqsim.thermo.system.SystemInterface feedTEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        feedTEG.setMolarComposition(
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.02, 0.98});

                Stream dehydratedGas = new Stream(absorber.getGasOutStream());
                dehydratedGas.setName("dry gas from absorber");
                Stream richTEG = new Stream(absorber.getSolventOutStream());

                ThrottlingValve glycol_flash_valve = new ThrottlingValve(richTEG);
                glycol_flash_valve.setName("Rich TEG HP flash valve");
                glycol_flash_valve.setOutletPressure(4.9);

                Heater richGLycolHeaterCondeser = new Heater(glycol_flash_valve.getOutStream());
                richGLycolHeaterCondeser.setName("rich TEG preheater");
                richGLycolHeaterCondeser.setOutTemperature(273.15 + 35.5);

                Heater richGLycolHeater = new Heater(richGLycolHeaterCondeser.getOutStream());
                richGLycolHeater.setName("rich TEG heater HP");
                richGLycolHeater.setOutTemperature(273.15 + 62.0);

                Separator flashSep = new Separator(richGLycolHeater.getOutStream());
                flashSep.setName("degasing separator");
                Stream flashGas = new Stream(flashSep.getGasOutStream());
                flashGas.setName("gas from degasing separator");
                Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
                flashLiquid.setName("liquid from degasing separator");

                Heater richGLycolHeater2 = new Heater(flashLiquid);
                richGLycolHeater2.setName("LP rich glycol heater");
                richGLycolHeater2.setOutTemperature(273.15 + 139.0);
                richGLycolHeater2.setOutPressure(1.23);

                Mixer mixerTOreboiler = new Mixer("reboil mxer");
                mixerTOreboiler.addStream(richGLycolHeater2.getOutStream());

                Heater heaterToReboiler = new Heater(mixerTOreboiler.getOutStream());
                heaterToReboiler.setOutTemperature(273.15 + 206.6);

                Separator regenerator2 = new Separator(heaterToReboiler.getOutStream());

                Stream gasFromRegenerator = new Stream(regenerator2.getGasOutStream());

                Heater sepregenGasCooler = new Heater(gasFromRegenerator);
                sepregenGasCooler.setOutTemperature(273.15 + 109.0);
                sepregenGasCooler.setOutPressure(1.23);
                // sepregenGasCooler.setEnergyStream(richGLycolHeaterCondeser.getEnergyStream());

                Separator sepRegen = new Separator(sepregenGasCooler.getOutStream());

        Heater sepregenGasCooler = new Heater(gasFromRegenerator);
        sepregenGasCooler.setOutTemperature(273.15 + 109.0);
        sepregenGasCooler.setOutPressure(1.23);
        // sepregenGasCooler.setEnergyStream(richGLycolHeaterCondeser.getEnergyStream());

                Recycle resycle2 = new Recycle("reflux resycle");
                resycle2.addStream(liquidRegenReflux);

                Heater coolerRegenGas = new Heater(sepRegen.getGasOutStream());
                coolerRegenGas.setOutTemperature(273.15 + 35.5);

                Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());

                Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());

                Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());

                Stream hotLeanTEG = new Stream(regenerator2.getLiquidOutStream());

                neqsim.thermo.system.SystemInterface stripGas =
                                (neqsim.thermo.system.SystemInterface) feedGas.clone();
                stripGas.setMolarComposition(new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                0.0, 0.0, 0.0, 0.0});

                Stream strippingGas = new Stream("stripGas", stripGas);
                strippingGas.setFlowRate(70.0, "kg/hr");
                strippingGas.setTemperature(206.6, "C");
                strippingGas.setPressure(1.23, "bara");

        neqsim.thermo.system.SystemInterface stripGas =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        stripGas.setMolarComposition(
                new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});

                Recycle resycle3 = new Recycle("gas stripper resycle");
                resycle3.addStream(stripper.getGasOutStream());

                Pump hotLeanTEGPump = new Pump(stripper.getSolventOutStream());
                hotLeanTEGPump.setName("hot lean TEG pump");
                hotLeanTEGPump.setOutletPressure(20.0);

                Heater coolerhOTteg = new Heater(hotLeanTEGPump.getOutStream());
                coolerhOTteg.setName("hot lean TEG cooler");
                coolerhOTteg.setOutTemperature(273.15 + 116.8);

                Heater coolerhOTteg2 = new Heater(coolerhOTteg.getOutStream());
                coolerhOTteg2.setName("medium hot lean TEG cooler");
                coolerhOTteg2.setOutTemperature(273.15 + 89.3);

                Heater coolerhOTteg3 = new Heater(coolerhOTteg2.getOutStream());
                coolerhOTteg3.setName("lean TEG cooler");
                coolerhOTteg3.setOutTemperature(273.15 + 44.85);

                Pump hotLeanTEGPump2 = new Pump(coolerhOTteg3.getOutStream());
                hotLeanTEGPump2.setName("lean TEG HP pump");
                hotLeanTEGPump2.setOutletPressure(52.21);

                Stream leanTEGtoabs = new Stream(hotLeanTEGPump2.getOutStream());
                leanTEGtoabs.setName("lean TEG to absorber");

                neqsim.thermo.system.SystemInterface pureTEG =
                                (neqsim.thermo.system.SystemInterface) feedGas.clone();
                pureTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                0.0, 0.0, 0.0, 1.0});

                Stream makeupTEG = new Stream("lean TEG to absorber", pureTEG);
                makeupTEG.setFlowRate(1e-6, "kg/hr");
                makeupTEG.setTemperature(35.4, "C");
                makeupTEG.setPressure(52.21, "bara");

        neqsim.thermo.system.SystemInterface pureTEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        pureTEG.setMolarComposition(
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

                StaticMixer makeupMixer = new StaticMixer("makeup mixer");
                makeupMixer.addStream(leanTEGtoabs);
                makeupMixer.addStream(makeupTEG);

                Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
                resycleLeanTEG.addStream(makeupMixer.getOutStream());

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
                operations.add(richGLycolHeaterCondeser);
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
                operations.add(resycle2);

                operations.add(coolerRegenGas);
                operations.add(sepregenGas);
                operations.add(gasToFlare);
                operations.add(liquidToTrreatment);
                operations.add(hotLeanTEG);
                operations.add(strippingGas);
                operations.add(stripper);

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
        operations.add(richGLycolHeaterCondeser);
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
        operations.add(resycle2);

                operations.run();
                richGLycolHeater2.getOutStream().getFluid().display();
                System.out.println("Energy reboiler " + heaterToReboiler.getDuty());
                mixerTOreboiler.addStream(liquidRegenReflux);
                mixerTOreboiler.addStream(resycle3.getOutStream());

                operations.run();
                absorber.replaceSolventInStream(resycleLeanTEG.getOutStream());
                operations.run();
                // richGLycolHeater2.getOutStream().getFluid().display();

                System.out.println("Energy reboiler 2 " + heaterToReboiler.getDuty());

        System.out.println("Energy reboiler 2 " + heaterToReboiler.getDuty());

        System.out.println("wt lean TEG after stripper "
                + ((WaterStripperColumn) operations.getUnit("TEG stripper")).getSolventOutStream()
                        .getFluid().getPhase("aqueous").getWtFrac("TEG"));

        operations.save("c:/temp/TEGprocessSimple.neqsim");
    }
}
