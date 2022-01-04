package neqsim.processSimulation.util.example;

import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.filter.Filter;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.SetPoint;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>
 * TEGdehydrationProcessDistillationJS class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcessDistillationJS {
    /**
     * The flow rate of gas from the scrubber controlling the dew point (MSm3/hr)
     */
    public double feedGasFlowRate = 5.0;

    /**
     * The temperature of the gas from the scrubber controlling the dew point (Celisuis)
     */
    public double feedGasTemperature = 30.4;

    /**
     * The pressure of the gas from the scrubber controlling the dew point (bara)
     */
    public double feedGasPressure = 52.21;

    /**
     * The temperature of the gas entering the TEG absorption column (Celisuis)
     */
    public double absorberFeedGasTemperature = 35.0;

    /**
     * The pressure of the gas entering the TEG absorption column (bara)
     */
    public double absorberFeedGasPressure = 52.21;

    /**
     * The flow rate of lean TEG entering the absorption column (kg/hr)
     */
    public double leanTEGFlowRate = 6862.5;

    /**
     * The temperature of the lean TEG entering the absorption column (Celcius)
     */
    public double leanTEGTemperature = 43.0;

    /**
     * The pressure in the flash drum (bara)
     */
    public double flashDrumPressure = 5.5;

    /**
     * Number of equilibrium stages in TEG absorpber
     */
    public int numberOfEquilibriumStagesTEGabsorber = 5;

    /**
     * Stage efficiency in TEG absorpber
     */
    public double stageEfficiencyStripper = 0.5;

    /**
     * Number of equilibrium stages in TEG absorpber
     */
    public int numberOfEquilibriumStagesStripper = 4;

    /**
     * Stage efficiency in TEG absorpber
     */
    public double stageEfficiencyTEGabsorber = 0.55;

    /**
     * UA value of rich TEG heat exchanger 1
     */
    public double UAvalueRichTEGHeatExchanger_1 = 220.0;

    /**
     * UA value of rich TEG heat exchanger 2
     */
    public double UAvalueRichTEGHeatExchanger_2 = 600.0;

    /**
     * Pressure in reboiler (bara)
     */
    public double reboilerPressure = 1.2;

    /**
     * Temperature in condenser(Celcius)
     */
    public double condenserTemperature = 100.0;

    /**
     * Pressure in condenser (bara)
     */
    public double condenserPressure = 1.0;

    /**
     * Temperature in reboiler (Celcius)
     */
    public double reboilerTemperature = 205.0;

    /**
     * Stripping gas flow rate (Sm3/hr)
     */
    public double strippingGasRate = 55.0;

    /**
     * Stripping gas feed temperature (Celcius)
     */
    public double strippingGasFeedTemperature = 80.0;

    /**
     * TEG buffer tank temperature (Celcius)
     */
    public double bufferTankTemperatureTEG = 185.0;

    /**
     * temperature of after regeneration gas cooler (Celcius)
     */
    public double regenerationGasCoolerTemperature = 35.0;

    /**
     * isentropic efficiency of hot lean TEG pump (0.0-1.0)
     */
    public double hotTEGpumpIsentropicEfficiency = 0.75;

    /**
     * pressure after hot lean TEG pump (bara)
     */
    public double hotTEGpumpPressure = 5.0;

    /**
     * isentropic efficiency of cold lean TEG pump (0.0-1.0)
     */
    public double coldTEGpumpIsentropicEfficiency = 0.75;

    /**
     * <p>
     * Constructor for TEGdehydrationProcessDistillationJS.
     * </p>
     */
    public TEGdehydrationProcessDistillationJS() {}

    /**
     * <p>
     * getProcess.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
     */
    public neqsim.processSimulation.processSystem.ProcessSystem getProcess() {
        // Create the input fluid to the TEG process and saturate it with water at
        // scrubber conditions
        neqsim.thermo.system.SystemInterface feedGas =
                new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
        feedGas.addComponent("nitrogen", 1.42);
        feedGas.addComponent("CO2", 0.5339);
        feedGas.addComponent("methane", 95.2412);
        feedGas.addComponent("ethane", 2.2029);
        feedGas.addComponent("propane", 0.3231);
        feedGas.addComponent("i-butane", 0.1341);
        feedGas.addComponent("n-butane", 0.0827);
        feedGas.addComponent("i-pentane", 0.0679);
        feedGas.addComponent("n-pentane", 0.035);
        feedGas.addComponent("n-hexane", 0.0176);
        feedGas.addComponent("benzene", 0.0017);
        feedGas.addComponent("toluene", 0.0043);
        feedGas.addComponent("m-Xylene", 0.0031);
        feedGas.addComponent("water", 0.0);
        feedGas.addComponent("TEG", 0);
        feedGas.setMixingRule(10);
        feedGas.setMultiPhaseCheck(true);

        Stream dryFeedGas = new Stream("dry feed gas", feedGas);
        dryFeedGas.setFlowRate(feedGasFlowRate, "MSm3/day");
        dryFeedGas.setTemperature(feedGasTemperature, "C");
        dryFeedGas.setPressure(feedGasPressure, "bara");

        StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
        saturatedFeedGas.setName("water saturator");

        Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
        waterSaturatedFeedGas.setName("water saturated feed gas");

        HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser =
                new HydrateEquilibriumTemperatureAnalyser(waterSaturatedFeedGas);
        hydrateTAnalyser.setName("hydrate temperature analyser");

        neqsim.thermo.system.SystemInterface feedTEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        feedTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.03, 0.97});

        Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", waterSaturatedFeedGas);
        feedTPsetterToAbsorber.setOutPressure(absorberFeedGasPressure, "bara");
        feedTPsetterToAbsorber.setOutTemperature(absorberFeedGasTemperature, "C");

        Stream feedToAbsorber = new Stream(feedTPsetterToAbsorber.getOutStream());
        feedToAbsorber.setName("feed to TEG absorber");

        Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
        TEGFeed.setFlowRate(leanTEGFlowRate, "kg/hr");
        TEGFeed.setTemperature(leanTEGTemperature, "C");
        TEGFeed.setPressure(absorberFeedGasPressure, "bara");

        SimpleTEGAbsorber absorber = new SimpleTEGAbsorber();
        absorber.setName("TEG absorber");
        absorber.addGasInStream(feedToAbsorber);
        absorber.addSolventInStream(TEGFeed);
        absorber.setNumberOfStages(numberOfEquilibriumStagesTEGabsorber);
        absorber.setStageEfficiency(stageEfficiencyTEGabsorber);

        Stream dehydratedGas = new Stream(absorber.getGasOutStream());
        dehydratedGas.setName("dry gas from absorber");

        Stream richTEG = new Stream(absorber.getSolventOutStream());
        richTEG.setName("rich TEG from absorber");

        HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
                new HydrateEquilibriumTemperatureAnalyser(dehydratedGas);
        waterDewPointAnalyser.setName("water dew point analyser");
        ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", richTEG);
        glycol_flash_valve.setName("Rich TEG HP flash valve");
        glycol_flash_valve.setOutletPressure(flashDrumPressure);

        Heater richGLycolHeaterCondenser = new Heater(glycol_flash_valve.getOutStream());
        richGLycolHeaterCondenser.setName("rich TEG preheater");

        HeatExchanger heatEx2 = new HeatExchanger(richGLycolHeaterCondenser.getOutStream());
        heatEx2.setName("rich TEG heat exchanger 1");
        heatEx2.setGuessOutTemperature(273.15 + 62.0);
        heatEx2.setUAvalue(UAvalueRichTEGHeatExchanger_1);

        Separator flashSep = new Separator(heatEx2.getOutStream(0));
        flashSep.setName("degasing separator");

        Stream flashGas = new Stream(flashSep.getGasOutStream());
        flashGas.setName("gas from degasing separator");

        Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
        flashLiquid.setName("liquid from degasing separator");

        Filter fineFilter = new Filter(flashLiquid);
        fineFilter.setName("TEG fine filter");
        fineFilter.setDeltaP(0.05, "bara");

        Filter carbonFilter = new Filter(fineFilter.getOutStream());
        carbonFilter.setName("activated carbon filter");
        carbonFilter.setDeltaP(0.01, "bara");

        HeatExchanger heatEx = new HeatExchanger(carbonFilter.getOutStream());
        heatEx.setName("rich TEG heat exchanger 2");
        heatEx.setGuessOutTemperature(273.15 + 130.0);
        heatEx.setUAvalue(UAvalueRichTEGHeatExchanger_2);

        ThrottlingValve glycol_flash_valve2 =
                new ThrottlingValve("LP flash valve", heatEx.getOutStream(0));
        glycol_flash_valve2.setName("Rich TEG LP flash valve");
        glycol_flash_valve2.setOutletPressure(reboilerPressure);

        neqsim.thermo.system.SystemInterface stripGas =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();

        Stream strippingGas = new Stream("stripGas", stripGas);
        strippingGas.setFlowRate(strippingGasRate, "Sm3/hr");
        strippingGas.setTemperature(strippingGasFeedTemperature, "C");
        strippingGas.setPressure(reboilerPressure, "bara");

        Stream gasToReboiler = (Stream) strippingGas.clone();
        gasToReboiler.setName("gas to reboiler");

        DistillationColumn column = new DistillationColumn(1, true, true);
        column.setName("TEG regeneration column");
        column.addFeedStream(glycol_flash_valve2.getOutStream(), 0);
        column.getReboiler().setOutTemperature(273.15 + reboilerTemperature);
        column.getCondenser().setOutTemperature(273.15 + condenserTemperature);
        column.getReboiler().addStream(gasToReboiler);
        column.setTopPressure(condenserPressure);
        column.setBottomPressure(reboilerPressure);

        Heater coolerRegenGas = new Heater(column.getGasOutStream());
        coolerRegenGas.setName("regen gas cooler");
        coolerRegenGas.setOutTemperature(273.15 + regenerationGasCoolerTemperature);

        Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
        sepregenGas.setName("regen gas separator");

        Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
        gasToFlare.setName("gas to flare");

        Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());
        liquidToTrreatment.setName("water to treatment");

        WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
        stripper.addSolventInStream(column.getLiquidOutStream());
        stripper.addGasInStream(strippingGas);
        stripper.setNumberOfStages(numberOfEquilibriumStagesStripper);
        stripper.setStageEfficiency(stageEfficiencyStripper);

        Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
        recycleGasFromStripper.addStream(stripper.getGasOutStream());
        recycleGasFromStripper.setOutletStream(gasToReboiler);

        Heater bufferTank = new Heater("TEG buffer tank", stripper.getSolventOutStream());
        bufferTank.setOutTemperature(273.15 + bufferTankTemperatureTEG);

        Pump hotLeanTEGPump = new Pump(bufferTank.getOutStream());
        hotLeanTEGPump.setName("hot lean TEG pump");
        hotLeanTEGPump.setOutletPressure(hotTEGpumpPressure);
        hotLeanTEGPump.setIsentropicEfficiency(hotTEGpumpIsentropicEfficiency);

        heatEx.setFeedStream(1, hotLeanTEGPump.getOutStream());

        heatEx2.setFeedStream(1, heatEx.getOutStream(1));

        Heater coolerhOTteg3 = new Heater(heatEx2.getOutStream(1));
        coolerhOTteg3.setName("lean TEG cooler");
        coolerhOTteg3.setOutTemperature(273.15 + leanTEGTemperature);

        Pump hotLeanTEGPump2 = new Pump(coolerhOTteg3.getOutStream());
        hotLeanTEGPump2.setName("lean TEG HP pump");
        hotLeanTEGPump2.setOutletPressure(absorberFeedGasPressure);
        hotLeanTEGPump2.setIsentropicEfficiency(coldTEGpumpIsentropicEfficiency);

        SetPoint pumpHPPresSet =
                new SetPoint("HP pump set", hotLeanTEGPump2, "pressure", feedToAbsorber);

        Stream leanTEGtoabs = new Stream(hotLeanTEGPump2.getOutStream());
        leanTEGtoabs.setName("lean TEG to absorber");

        neqsim.thermo.system.SystemInterface pureTEG =
                (neqsim.thermo.system.SystemInterface) feedGas.clone();
        pureTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 1.0});

        Stream makeupTEG = new Stream("makeup TEG", pureTEG);
        makeupTEG.setFlowRate(1e-6, "kg/hr");
        makeupTEG.setTemperature(leanTEGTemperature, "C");
        makeupTEG.setPressure(absorberFeedGasPressure, "bara");

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
        // richGLycolHeater.isSetEnergyStream();

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(dryFeedGas);
        operations.add(saturatedFeedGas);
        operations.add(waterSaturatedFeedGas);
        operations.add(hydrateTAnalyser);
        operations.add(feedTPsetterToAbsorber);
        operations.add(feedToAbsorber);
        operations.add(TEGFeed);
        operations.add(absorber);
        operations.add(dehydratedGas);
        operations.add(waterDewPointAnalyser);
        operations.add(richTEG);
        operations.add(glycol_flash_valve);
        operations.add(richGLycolHeaterCondenser);
        operations.add(heatEx2);
        operations.add(flashSep);
        operations.add(flashGas);

        operations.add(flashLiquid);
        operations.add(fineFilter);
        operations.add(carbonFilter);
        operations.add(heatEx);
        operations.add(glycol_flash_valve2);
        operations.add(gasToReboiler);
        operations.add(column);
        operations.add(coolerRegenGas);
        operations.add(sepregenGas);
        operations.add(gasToFlare);
        operations.add(liquidToTrreatment);
        operations.add(strippingGas);
        operations.add(stripper);
        operations.add(recycleGasFromStripper);
        operations.add(bufferTank);
        operations.add(hotLeanTEGPump);
        operations.add(coolerhOTteg3);
        operations.add(pumpHPPresSet);
        operations.add(hotLeanTEGPump2);
        operations.add(leanTEGtoabs);
        operations.add(makeupCalculator);
        operations.add(makeupTEG);
        operations.add(makeupMixer);
        operations.add(resycleLeanTEG);

        return operations;
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        TEGdehydrationProcessDistillationJS tempClass = new TEGdehydrationProcessDistillationJS();
        neqsim.processSimulation.processSystem.ProcessSystem operations = tempClass.getProcess();
        operations.run();

        operations.save("c:/temp/TEGprocessJS.neqsim");
    }
}
