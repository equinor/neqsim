package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.distillation.Condenser;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.distillation.Reboiler;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>OnshoreMEGprocess class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class OnshoreMEGprocess {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface feedGas =
                new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
        feedGas.addComponent("nitrogen", 0.4);
        feedGas.addComponent("CO2", 0.00042);
        feedGas.addComponent("methane", 99.0);
        feedGas.addComponent("ethane", 0.07);
        feedGas.addComponent("propane", 0.054);
        feedGas.addComponent("i-butane", 0.0054);
        feedGas.addComponent("n-butane", 0.0084);
        feedGas.addComponent("water", 0.0);
        feedGas.addComponent("MEG", 0);
        feedGas.createDatabase(true);
        feedGas.setMixingRule(10);
        feedGas.setMultiPhaseCheck(true);

        Stream dryFeedGas = new Stream("feed gas", feedGas);
        dryFeedGas.setFlowRate(10.5, "MSm3/day");
        dryFeedGas.setTemperature(6.0, "C");
        dryFeedGas.setPressure(53.0, "bara");

        StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
        saturatedFeedGas.setName("water saturator");

        Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
        waterSaturatedFeedGas.setName("water saturated feed gas");

        Compressor inletCompressor =
                new Compressor("Compressor 1 - first stage", waterSaturatedFeedGas);
        inletCompressor.setOutletPressure(70.0, "bara");

        Cooler interstageGasCooler = new Cooler(inletCompressor.getOutStream());
        interstageGasCooler.setOutTemperature(40.0, "C");
        interstageGasCooler.setName("Compressor 1 - interstage cooler");

        Compressor inletCompressor2ndstage =
                new Compressor("Compressor 1 - second stage", interstageGasCooler.getOutStream());
        inletCompressor2ndstage.setOutletPressure(105.0, "bara");

        neqsim.thermo.system.SystemInterface feedMEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        feedMEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.4, 0.6});

        Stream MEGFeed = new Stream("lean MEG to header", feedMEG);
        MEGFeed.setFlowRate(1000.0, "kg/hr");
        MEGFeed.setTemperature(20.0, "C");
        MEGFeed.setPressure(105.0, "bara");

        Splitter MEGsplitter1 = new Splitter(MEGFeed);
        MEGsplitter1.setName("lean MEG header");
        MEGsplitter1.setSplitFactors(new double[] {0.01, 0.94, 0.05});

        StaticMixer MEGmixer1 = new StaticMixer("MEG mixer 1");
        MEGmixer1.addStream(inletCompressor2ndstage.getOutStream());
        MEGmixer1.addStream(MEGsplitter1.getSplitStream(0));

        Cooler inletGasCooler = new Cooler(MEGmixer1.getOutStream());
        inletGasCooler.setOutTemperature(10.0, "C");
        inletGasCooler.setName("dehydration cooler");

        StaticMixer MEGmixer2 = new StaticMixer("MEG mixer 2");
        MEGmixer2.addStream(inletGasCooler.getOutStream());
        MEGmixer2.addStream(MEGsplitter1.getSplitStream(1));

        HeatExchanger heatEx = new HeatExchanger(MEGmixer2.getOutStream());
        heatEx.setName("gas-gas heat exchanger");
        heatEx.setGuessOutTemperature(273.15 - 10.0);
        heatEx.setUAvalue(30000.0);

        StaticMixer MEGmixer3 = new StaticMixer("MEG mixer 3");
        MEGmixer3.addStream(heatEx.getOutStream(0));
        MEGmixer3.addStream(MEGsplitter1.getSplitStream(2));

        ThrottlingValve presRedValveLT = new ThrottlingValve("JT valve", MEGmixer3.getOutStream());
        presRedValveLT.setOutletPressure(92.0);

        ThreePhaseSeparator mpseparator =
                new ThreePhaseSeparator("low temperature scrubber", presRedValveLT.getOutStream());

        Stream coldGasFromSep = new Stream(mpseparator.getGasOutStream());
        coldGasFromSep.setName("gas from cold scrubber");

        heatEx.setFeedStream(1, coldGasFromSep);

        Stream heatedGasFromLPSep = new Stream(heatEx.getOutStream(1));
        heatedGasFromLPSep.setName("heated gas from cold scrubber");

        Stream richMEGstream = new Stream(mpseparator.getWaterOutStream());
        richMEGstream.setName("rich cold MEG stream");

        Heater richMEGstreamHeater = new Heater(richMEGstream);
        richMEGstreamHeater.setOutTemperature(15.0, "C");
        richMEGstreamHeater.setName("rich MEG pre-heater");

        Heater richMEGstreamHeater2 = new Heater(richMEGstreamHeater.getOutStream());
        // richMEGstreamHeater2.setOutTemperature(22.0, "C");
        richMEGstreamHeater2.setName("column condenser HX");

        ThrottlingValve presRedValve3 =
                new ThrottlingValve("valve to flash drum", richMEGstreamHeater2.getOutStream());
        presRedValve3.setOutletPressure(3.9);

        Separator flashDrumSep = new Separator("rich MEG flash drum", presRedValve3.getOutStream());

        Stream flashGasStream = new Stream(flashDrumSep.getGasOutStream());
        flashGasStream.setName("gas from flash drum");

        Stream flashLiquidStream = new Stream(flashDrumSep.getLiquidOutStream());
        flashLiquidStream.setName("rich TEG from flash drum");

        HeatExchanger columnPreHeater = new HeatExchanger(flashLiquidStream);
        columnPreHeater.setName("MEG/MEG heat exchanger");
        columnPreHeater.setGuessOutTemperature(273.15 + 120.0);
        columnPreHeater.setUAvalue(500.0);

        ThrottlingValve presRedValve4 =
                new ThrottlingValve("valve to regenerator", columnPreHeater.getOutStream(0));
        presRedValve4.setOutletPressure(1.23);

        DistillationColumn column = new DistillationColumn(2, true, true);
        column.setName("MEG regeneration column");
        column.addFeedStream(presRedValve4.getOutStream(), 0);
        column.getReboiler().setOutTemperature(273.15 + 135.0);
        column.getCondenser().setOutTemperature(273.15 + 105.0);
        column.setTopPressure(1.0);
        column.setBottomPressure(1.23);

        Cooler coolerRegenGas = new Cooler(column.getGasOutStream());
        coolerRegenGas.setName("regeneration overhead  gas cooler");
        coolerRegenGas.setOutTemperature(273.15 + 20.0);

        Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
        sepregenGas.setName("overhead condenser scrubber");

        Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
        gasToFlare.setName("gas to flare from regenerator");

        Stream waterToSea = new Stream(sepregenGas.getLiquidOutStream());
        waterToSea.setName("condensed water from regenerator");

        Cooler bufferTank = new Cooler("MEG buffer tank", column.getLiquidOutStream());
        bufferTank.setOutTemperature(273.15 + 130.0);

        Pump hotLeanMEGPump = new Pump(bufferTank.getOutStream());
        hotLeanMEGPump.setName("hot lean MEG pump");
        hotLeanMEGPump.setOutletPressure(105.0);
        hotLeanMEGPump.setIsentropicEfficiency(0.75);

        Stream streamHotPump = new Stream(hotLeanMEGPump.getOutStream());
        streamHotPump.setName("stream from hot lean MEG pump");

        columnPreHeater.setFeedStream(1, streamHotPump);

        Cooler coolerHotMEG2 = new Cooler(columnPreHeater.getOutStream(1));
        coolerHotMEG2.setName("lean MEG cooler");
        coolerHotMEG2.setOutTemperature(273.15 + 20.0);

        Stream leanMEGtoMixer = new Stream(coolerHotMEG2.getOutStream());
        leanMEGtoMixer.setName("lean MEG to makeup mixer");

        neqsim.thermo.system.SystemInterface pureMEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        pureMEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

        Stream makeupMEG = new Stream("makeup MEG", pureMEG);
        makeupMEG.setFlowRate(1e-6, "kg/hr");
        makeupMEG.setTemperature(20.0, "C");
        makeupMEG.setPressure(105.0, "bara");

        Calculator makeupCalculator = new Calculator("MEG makeup calculator");
        makeupCalculator.addInputVariable(coldGasFromSep);
        makeupCalculator.addInputVariable(waterToSea);
        makeupCalculator.addInputVariable(gasToFlare);
        makeupCalculator.addInputVariable(flashGasStream);
        makeupCalculator.setOutputVariable(makeupMEG);

        StaticMixer makeupMixer = new StaticMixer("MEG makeup mixer");
        makeupMixer.addStream(leanMEGtoMixer);
        makeupMixer.addStream(makeupMEG);

        Stream streamToResycle = new Stream(makeupMixer.getOutStream());

        Recycle resycleLeanMEG = new Recycle("lean MEG resycle");
        resycleLeanMEG.addStream(streamToResycle);
        resycleLeanMEG.setOutletStream(MEGFeed);
        // resycleLeanMEG.setPriority(200);
        resycleLeanMEG.setDownstreamProperty("flow rate");

        richMEGstreamHeater2.setEnergyStream(column.getCondenser().getEnergyStream());

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(dryFeedGas);
        operations.add(saturatedFeedGas);
        operations.add(waterSaturatedFeedGas);
        operations.add(inletCompressor);
        operations.add(interstageGasCooler);
        operations.add(inletCompressor2ndstage);
        operations.add(MEGFeed);
        operations.add(MEGsplitter1);
        operations.add(MEGmixer1);
        operations.add(inletGasCooler);
        operations.add(MEGmixer2);
        operations.add(heatEx);
        operations.add(MEGmixer3);
        operations.add(presRedValveLT);
        operations.add(mpseparator);
        operations.add(coldGasFromSep);
        operations.add(heatedGasFromLPSep);
        operations.add(richMEGstream);
        operations.add(richMEGstreamHeater);
        operations.add(richMEGstreamHeater2);
        operations.add(presRedValve3);
        operations.add(flashDrumSep);
        operations.add(flashGasStream);
        operations.add(flashLiquidStream);
        operations.add(columnPreHeater);
        operations.add(presRedValve4);
        operations.add(column);
        operations.add(coolerRegenGas);
        operations.add(sepregenGas);
        operations.add(gasToFlare);
        operations.add(waterToSea);
        operations.add(bufferTank);
        operations.add(hotLeanMEGPump);
        operations.add(streamHotPump);
        operations.add(columnPreHeater);
        operations.add(coolerHotMEG2);
        operations.add(leanMEGtoMixer);
        operations.add(makeupCalculator);
        operations.add(makeupMEG);
        operations.add(makeupMixer);
        operations.add(streamToResycle);
        operations.add(resycleLeanMEG);

        // operations = ProcessSystem.open("c:/temp/onshoreMEGprocess.neqsim");
        operations.run();
        System.out.println("MEG flow rate " + resycleLeanMEG.getFluid().getFlowRate("kg/hr"));

        // presRedValve4.displayResult();
        // System.out.println(
        // "temperature after cross cooler " +heatEx.getOutStream(0).getTemperature("C"));

        System.out.println("Heat ex 2 duty " + richMEGstreamHeater2.getDuty() / 1.0e3 + " kW");
        System.out.println("Heat ex 2 duty2 " + richMEGstreamHeater2.getDuty() / 1.0e3 + " kW");

        System.out.println("MEG flow rate " + richMEGstream.getFluid().getFlowRate("kg/hr"));
        System.out.println("MEG feed to column rate "
                + presRedValve4.getOutStream().getFluid().getFlowRate("kg/hr"));

        System.out.println("MEG flow rate " + resycleLeanMEG.getFluid().getFlowRate("kg/hr"));
        System.out.println(
                "Reboiler duty [kW] " + ((Reboiler) column.getReboiler()).getDuty() / 1.0e3);
        System.out.println(
                "Condenser duty [kW] " + ((Condenser) column.getCondenser()).getDuty() / 1.0e3);
        System.out.println(
                "wt% lean MEG  " + MEGFeed.getFluid().getPhase("aqueous").getWtFrac("MEG") * 100.0);
        // System.out.println("heat ex out temperature " +
        // heatEx.getOutStream(0).getTemperature("C"));
        System.out.println("cold gas temperature " + coldGasFromSep.getTemperature("C"));
        System.out.println("column glycol pre heater temperature "
                + columnPreHeater.getOutStream(0).getTemperature("C"));
        System.out.println("column glycol pre heater temperature "
                + columnPreHeater.getOutStream(1).getTemperature("C"));

        // presRedValve4.getOutStream().displayResult();
        gasToFlare.displayResult();
        waterToSea.displayResult();
        System.out.println("lean MEG wt% "
                + column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("MEG")
                        * 100.0);
        System.out.println("hydrate temperature 1 "
                + (inletGasCooler.getOutStream().getHydrateEquilibriumTemperature() - 273.15)
                + " wt% MEG "
                + inletGasCooler.getOutStream().getFluid().getPhase("aqueous").getWtFrac("MEG")
                        * 100.0);

        operations.save("c:/temp/MEGdehydrationProcess.neqsim");
    }
}
