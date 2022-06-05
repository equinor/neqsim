package neqsim.processSimulation.util.example;

import neqsim.processSimulation.conditionMonitor.ConditionMonitor;
import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.Condenser;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.distillation.Reboiler;
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
 * TEGdehydrationProcessDistillationGFA class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcessDistillationGFA {
    /**
     * The flow rate of gas from the scrubber controlling the dew point (MSm3/hr)
     */
    public double feedGasFlowRate = 11.23;

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
    public double absorberFeedGasTemperature = 30.4;

    /**
     * The pressure of the gas entering the TEG absorption column (bara)
     */
    public double absorberFeedGasPressure = 52.21;

    /**
     * The flow rate of lean TEG entering the absorption column (kg/hr)
     */
    public double leanTEGFlowRate = 6786.0;

    /**
     * The temperature of the lean TEG entering the absorption column (Celcius)
     */
    public double leanTEGTemperature = 43.6;

    /**
     * The pressure in the flash drum (bara)
     */
    public double flashDrumPressure = 4.9;

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
    public double UAvalueRichTEGHeatExchanger_1 = 2900.0;

    /**
     * UA value of rich TEG heat exchanger 2
     */
    public double UAvalueRichTEGHeatExchanger_2 = 8200.0;

    /**
     * Pressure in reboiler (bara)
     */
    public double reboilerPressure = 1.23;

    /**
     * Temperature in condenser(Celcius)
     */
    public double condenserTemperature = 93.6;

    /**
     * Pressure in condenser (bara)
     */
    public double condenserPressure = 1.2;

    /**
     * Temperature in reboiler (Celcius)
     */
    public double reboilerTemperature = 206.6;

    /**
     * Stripping gas flow rate (Sm3/hr)
     */
    public double strippingGasRate = 91.2;

    /**
     * Stripping gas feed temperature (Celcius)
     */
    public double strippingGasFeedTemperature = 80.0;

    /**
     * TEG buffer tank temperature (Celcius)
     */
    public double bufferTankTemperatureTEG = 190.4;

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
    public double hotTEGpumpPressure = 20.0;

    /**
     * isentropic efficiency of cold lean TEG pump (0.0-1.0)
     */
    public double coldTEGpumpIsentropicEfficiency = 0.75;

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
        feedGas.setMultiPhaseCheck(false);

        Stream dryFeedGas = new Stream("dry feed gas", feedGas);
        dryFeedGas.setFlowRate(feedGasFlowRate, "MSm3/day");
        dryFeedGas.setTemperature(feedGasTemperature, "C");
        dryFeedGas.setPressure(feedGasPressure, "bara");

        StreamSaturatorUtil saturatedFeedGas =
                new StreamSaturatorUtil("water saturator", dryFeedGas);

        Stream waterSaturatedFeedGas =
            new Stream("water saturated feed gas", saturatedFeedGas.getOutletStream());

        HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser =
                new HydrateEquilibriumTemperatureAnalyser(waterSaturatedFeedGas);
        hydrateTAnalyser.setName("hydrate temperature analyser");

        neqsim.thermo.system.SystemInterface feedTEG = feedGas.clone();
        feedTEG.setMolarComposition(
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97});

        Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", waterSaturatedFeedGas);
        feedTPsetterToAbsorber.setOutPressure(absorberFeedGasPressure, "bara");
        feedTPsetterToAbsorber.setOutTemperature(absorberFeedGasTemperature, "C");

        Stream feedToAbsorber =
            new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());
        
        Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
        TEGFeed.setFlowRate(leanTEGFlowRate, "kg/hr");
        TEGFeed.setTemperature(leanTEGTemperature, "C");
        TEGFeed.setPressure(absorberFeedGasPressure, "bara");

        SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
        absorber.addGasInStream(feedToAbsorber);
        absorber.addSolventInStream(TEGFeed);
        absorber.setNumberOfStages(numberOfEquilibriumStagesTEGabsorber);
        absorber.setStageEfficiency(stageEfficiencyTEGabsorber);

        Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());

        Stream richTEG = new Stream("rich TEG from absorber", absorber.getSolventOutStream());

        WaterDewPointAnalyser waterDewPointAnalyser2 = new WaterDewPointAnalyser(dehydratedGas);
        waterDewPointAnalyser2.setName("water dew point analyser2");

        HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
                new HydrateEquilibriumTemperatureAnalyser(dehydratedGas);
        waterDewPointAnalyser.setName("water dew point analyser");

        ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", richTEG);
        glycol_flash_valve.setName("Rich TEG HP flash valve");
        glycol_flash_valve.setOutletPressure(flashDrumPressure);

        Heater richGLycolHeaterCondenser =
            new Heater("rich TEG preheater", glycol_flash_valve.getOutletStream());

        HeatExchanger heatEx2 = new HeatExchanger("rich TEG heat exchanger 1",
            richGLycolHeaterCondenser.getOutletStream());
        heatEx2.setGuessOutTemperature(273.15 + 62.0);
        heatEx2.setUAvalue(UAvalueRichTEGHeatExchanger_1);

        Separator flashSep = new Separator("degasing separator", heatEx2.getOutStream(0));

        Stream flashGas = new Stream("gas from degasing separator", flashSep.getGasOutStream());

        Stream flashLiquid =
                new Stream("liquid from degasing separator", flashSep.getLiquidOutStream());

        Filter filter = new Filter("filters", flashLiquid);

        HeatExchanger heatEx =
            new HeatExchanger("rich TEG heat exchanger 2", filter.getOutletStream());
        heatEx.setGuessOutTemperature(273.15 + 130.0);
        heatEx.setUAvalue(UAvalueRichTEGHeatExchanger_2);

        ThrottlingValve glycol_flash_valve2 =
                new ThrottlingValve("LP flash valve", heatEx.getOutStream(0));
        glycol_flash_valve2.setName("Rich TEG LP flash valve");
        glycol_flash_valve2.setOutletPressure(reboilerPressure);

        neqsim.thermo.system.SystemInterface stripGas = feedGas.clone();

        Stream strippingGas = new Stream("stripGas", stripGas);
        strippingGas.setFlowRate(strippingGasRate, "Sm3/hr");
        strippingGas.setTemperature(strippingGasFeedTemperature, "C");
        strippingGas.setPressure(reboilerPressure, "bara");

        Stream gasToReboiler = strippingGas.clone();
        gasToReboiler.setName("gas to reboiler");

        DistillationColumn column = new DistillationColumn(3, true, true);
        column.setName("TEG regeneration column");
        column.addFeedStream(glycol_flash_valve2.getOutletStream(), 1);
        column.getReboiler().setOutTemperature(273.15 + reboilerTemperature);
        column.getCondenser().setOutTemperature(273.15 + condenserTemperature);
        column.getReboiler().addStream(gasToReboiler);
        column.setTopPressure(condenserPressure);
        column.setBottomPressure(reboilerPressure);

        Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
        coolerRegenGas.setOutTemperature(273.15 + regenerationGasCoolerTemperature);

        Separator sepregenGas =
            new Separator("regen gas separator", coolerRegenGas.getOutletStream());

        Stream gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());

        Stream liquidToTrreatment =
                new Stream("water to treatment", sepregenGas.getLiquidOutStream());

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

        Pump hotLeanTEGPump = new Pump("hot lean TEG pump", bufferTank.getOutletStream());// stripper.getSolventOutStream());
        hotLeanTEGPump.setOutletPressure(hotTEGpumpPressure);
        hotLeanTEGPump.setIsentropicEfficiency(hotTEGpumpIsentropicEfficiency);

        heatEx.setFeedStream(1, hotLeanTEGPump.getOutStream());

        heatEx2.setFeedStream(1, heatEx.getOutStream(1));

        Heater coolerhOTteg3 = new Heater("lean TEG cooler", heatEx2.getOutStream(1));
        coolerhOTteg3.setOutTemperature(273.15 + leanTEGTemperature);

        Pump hotLeanTEGPump2 = new Pump("lean TEG HP pump", coolerhOTteg3.getOutletStream());
        hotLeanTEGPump2.setOutletPressure(absorberFeedGasPressure);
        hotLeanTEGPump2.setIsentropicEfficiency(coldTEGpumpIsentropicEfficiency);

        SetPoint pumpHPPresSet =
                new SetPoint("HP pump set", hotLeanTEGPump2, "pressure", feedToAbsorber);

        Stream leanTEGtoabs = new Stream("lean TEG to absorber", hotLeanTEGPump2.getOutStream());

        neqsim.thermo.system.SystemInterface pureTEG = feedGas.clone();
        pureTEG.setMolarComposition(
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

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
        resycleLeanTEG.addStream(makeupMixer.getOutletStream());
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
        operations.add(waterDewPointAnalyser2);
        operations.add(richTEG);
        operations.add(glycol_flash_valve);
        operations.add(richGLycolHeaterCondenser);
        operations.add(heatEx2);
        operations.add(flashSep);
        operations.add(flashGas);
        operations.add(flashLiquid);
        operations.add(filter);
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
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        TEGdehydrationProcessDistillationGFA tempClass = new TEGdehydrationProcessDistillationGFA();
        neqsim.processSimulation.processSystem.ProcessSystem operations = tempClass.getProcess();
        operations.run();
        // operations.run();
        // operations =
        // neqsim.processSimulation.processSystem.ProcessSystem.open("c:/temp/TEGprocessGFA.neqsim");

        double richTEGtemperature = ((Heater) operations.getUnit("rich TEG preheater"))
            .getOutletStream().getTemperature("C");
        System.out.println("temp rich TEG " + richTEGtemperature);
        System.out.println("condenser duty (KW) "
                + ((Condenser) ((DistillationColumn) operations.getUnit("TEG regeneration column"))
                        .getCondenser()).getDuty() / 1.0e3);
        System.out.println("reboiler duty (KW) "
                + ((Reboiler) ((DistillationColumn) operations.getUnit("TEG regeneration column"))
                        .getReboiler()).getDuty() / 1.0e3);

        System.out.println("temp out rich TEG " + richTEGtemperature);

        double rich2TEGtemperature =
                ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 1")).getOutStream(0)
                        .getTemperature("C");
        System.out.println("temp rich2 TEG " + rich2TEGtemperature);

        double rich22TEGtemperature =
                ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2")).getOutStream(0)
                        .getTemperature("C");
        System.out.println("temp rich2 to reboil TEG " + rich22TEGtemperature);

        double lean22TEGtemperature =
                ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2")).getOutStream(1)
                        .getTemperature("C");
        System.out.println("temp lean TEG to HX " + lean22TEGtemperature);

        // ((Stream)operations.getUnit("dry feed gas")).setFlowRate(10.23, "MSm3/day");
        // ((Stream)operations.getUnit("dry feed gas")).setTemperature(40.0, "C");
        // System.out.println("restart ");
        // operations.run();
        operations.save("c:/temp/TEGprocessGFA.neqsim");
        double eff = ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2"))
                .getThermalEffectiveness();
        System.out.println("HX2 thermal efficiency " + eff);
        /// operations = ProcessSystem.open("c:/temp/TEGprocess.neqsim");
        // ((DistillationColumn)operations.getUnit("TEG regeneration
        /// column")).setTopPressure(1.2);
        // operations.run();
        // ((DistillationColumn)operations.getUnit("TEG regeneration
        /// column")).setNumberOfTrays(2);
        /*
         * System.out.println("water in wet gas  " + ((Stream)
         * operations.getUnit("water saturated feed gas")).getFluid()
         * .getPhase(0).getComponent("water").getz() * 1.0e6 * 0.01802 * 101325.0 / (8.314 *
         * 288.15)); System.out.println("water in dry gas  " + ((Stream)
         * operations.getUnit("dry gas from absorber")).getFluid()
         * .getPhase(0).getComponent("water").getz() * 1.0e6);
         * System.out.println("reboiler duty (KW) " + ((Reboiler) ((DistillationColumn)
         * operations.getUnit("TEG regeneration column")).getReboiler()) .getDuty() / 1.0e3);
         * System.out.println("wt lean TEG " + ((WaterStripperColumn)
         * operations.getUnit("TEG stripper"))
         * .getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG") * 100.0);
         */
        double eff2 = ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2"))
                .getThermalEffectiveness();
        ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2")).run();
        ConditionMonitor monitor = operations.getConditionMonitor();
        // double prevTem= ((HeatExchanger)monitor.getProcess().getUnit("rich TEG heat exchanger
        // 2")).getInStream(0).getTemperature("C");
        ((HeatExchanger) monitor.getProcess().getUnit("rich TEG heat exchanger 2")).getInStream(0)
                .setTemperature(84.93, "C");
        monitor.conditionAnalysis("rich TEG heat exchanger 2");
        double eff3 = ((HeatExchanger) monitor.getProcess().getUnit("rich TEG heat exchanger 2"))
                .getThermalEffectiveness();
        System.out.println("ef3 " + eff3);

        // System.out.println("temp inn "+ (prevTem));
        /*
         * double waterInWetGasppm =
         * waterSaturatedFeedGas.getFluid().getPhase(0).getComponent("water").getz() * 1.0e6; double
         * waterInWetGaskgMSm3 = waterInWetGasppm * 0.01802 * 101325.0 / (8.314 * 288.15); double
         * TEGfeedwt = TEGFeed.getFluid().getPhase("aqueous").getWtFrac("TEG"); double TEGfeedflw =
         * TEGFeed.getFlowRate("kg/hr"); double waterInDehydratedGasppm =
         * dehydratedGas.getFluid().getPhase(0).getComponent("water").getz() * 1.0e6; double
         * waterInDryGaskgMSm3 = waterInDehydratedGasppm * 0.01802 * 101325.0 / (8.314 * 288.15);
         * double richTEG2 = richTEG.getFluid().getPhase("aqueous").getWtFrac("TEG"); double temp =
         * ((Stream)operations.getUnit("feed to TEG absorber")).getFluid().getPhase(0).getComponent(
         * "water").getz()*1.0e6*0.01802*101325.0/(8.314*288.15);
         * System.out.println("reboiler duty (KW) " + ((Reboiler) column.getReboiler()).getDuty() /
         * 1.0e3); System.out.println("flow rate from reboiler " + ((Reboiler)
         * column.getReboiler()).getLiquidOutStream().getFlowRate("kg/hr"));
         * System.out.println("flow rate from stripping column " +
         * stripper.getLiquidOutStream().getFlowRate("kg/hr"));
         * System.out.println("flow rate from pump2  " +
         * hotLeanTEGPump2.getOutStream().getFluid().getFlowRate("kg/hr"));
         * System.out.println("makeup TEG  " + makeupTEG.getFluid().getFlowRate("kg/hr"));
         * 
         * TEGFeed.getFluid().display(); absorber.run();
         * 
         * System.out.println("pump power " + hotLeanTEGPump.getDuty());
         * System.out.println("pump2 power " + hotLeanTEGPump2.getDuty());
         * System.out.println("wt lean TEG after reboiler " +
         * column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));
         * System.out.println("temperature from pump " +
         * (hotLeanTEGPump2.getOutStream().getTemperature() - 273.15));
         * 
         * System.out.println("flow rate from reboiler " + ((Reboiler)
         * column.getReboiler()).getLiquidOutStream().getFlowRate("kg/hr"));
         * System.out.println("flow rate from pump2  " +
         * hotLeanTEGPump2.getOutStream().getFluid().getFlowRate("kg/hr"));
         * System.out.println("flow rate to flare  " + gasToFlare.getFluid().getFlowRate("kg/hr"));
         * 
         * System.out.println("condenser duty  " + ((Condenser) ((DistillationColumn)
         * operations.getUnit("TEG regeneration column")).getCondenser()) .getDuty() / 1.0e3);
         * System.out.println( "richGLycolHeaterCondenser duty  " +
         * richGLycolHeaterCondenser.getEnergyStream().getDuty() / 1.0e3);
         * System.out.println("richGLycolHeaterCondenser temperature out  " +
         * richGLycolHeaterCondenser.getOutStream().getTemperature("C"));
         * richGLycolHeaterCondenser.run();
         * 
         * hotLeanTEGPump.getOutStream().displayResult(); flashLiquid.displayResult();
         * 
         * System.out.println("Temperature rich TEG out of reflux condenser " +
         * richGLycolHeaterCondenser.getOutStream().getTemperature("C")); heatEx.displayResult();
         * System.out.println("glycol out temperature " +
         * glycol_flash_valve2.getOutStream().getFluid().getTemperature("C"));
         * System.out.println("glycol out temperature2 "
         * +heatEx2.getOutStream(0).getTemperature("C"));
         * System.out.println("glycol out temperature2 "
         * +heatEx2.getOutStream(1).getTemperature("C"));
         * 
         * 
         * System.out.println("out water rate LP valve" +
         * glycol_flash_valve2.getOutStream().getFluid().getPhase(0).getComponent("water").
         * getNumberOfmoles()); System.out.println("glycol out water rate reboil " + ((Reboiler)
         * column.getReboiler()).getLiquidOutStream().getFluid().getComponent("water").
         * getNumberOfmoles()); System.out.println("glycol out water rate condens " + ((Condenser)
         * column.getCondenser()).getGasOutStream().getFluid().getComponent("water").
         * getNumberOfmoles()); System.out.println("recycle out water rate  "
         * +recycleGasFromStripper.getOutletStream().getFluid().getComponent("water").
         * getNumberOfmoles());
         * 
         * System.out.println("water dew point of dry gas  " +
         * waterDewPointAnalyser.getMeasuredValue("C"));
         * 
         * //double dewT =
         * ((WaterDewPointAnalyser)operations.getMeasurementDevice("water dew point analyser")).
         * getMeasuredValue("C"); //waterDewPointAnalyser.setOnlineValue(measured, unit)
         * //waterDewPointAnalyser.setOnlineSignal(isOnlineSignal, plantName, transmitterame);
         * 
         * 
         * 
         * //Heat exchanger test
         * 
         * //Sabe and Open copy of model
         */
        // ProcessSystem locoperations = operations.copy();
        // ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).getInStream(0).setTemperature(298.15, "C");
        // ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).getInStream(1).setTemperature(363.15, "C");
        // ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).getOutStream(0).setTemperature(298.15, "C");
        // ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).getOutStream(1).setTemperature(333.15, "C");
        // ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).runConditionAnalysis((HeatExchanger)operations.getUnit("rich TEG heat exchanger
        // 2"));
        // double eff = ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger
        // 2")).getThermalEffectiveness();
        // System.out.println("eff " + eff);
        // store fouling factor in dataframe
    }
}
