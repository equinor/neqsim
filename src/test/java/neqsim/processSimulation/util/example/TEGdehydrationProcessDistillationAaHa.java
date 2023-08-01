package neqsim.processSimulation.util.example;

import neqsim.processSimulation.conditionMonitor.ConditionMonitor;
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
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * TEGdehydrationProcessDistillationAaHa class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcessDistillationAaHa {
  /**
   * <p>
   * getProcess.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
   */
  public static neqsim.processSimulation.processSystem.ProcessSystem getProcess() {
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
    dryFeedGas.setFlowRate(25.32, "MSm3/day");
    dryFeedGas.setTemperature(25.0, "C");
    dryFeedGas.setPressure(87.12, "bara");

    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil("water saturator", dryFeedGas);

    Stream waterSaturatedFeedGas =
        new Stream("water saturated feed gas", saturatedFeedGas.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser =
        new HydrateEquilibriumTemperatureAnalyser(waterSaturatedFeedGas);
    hydrateTAnalyser.setName("hydrate temperature analyser");

    neqsim.thermo.system.SystemInterface feedTEG = feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97});

    Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", waterSaturatedFeedGas);
    feedTPsetterToAbsorber.setOutPressure(87.12, "bara");
    feedTPsetterToAbsorber.setOutTemperature(27.93, "C");

    Stream feedToAbsorber =
        new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
    TEGFeed.setFlowRate(14.68 * 1100.0, "kg/hr");
    TEGFeed.setTemperature(43.4, "C");
    TEGFeed.setPressure(87.12, "bara");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(5);
    absorber.setStageEfficiency(0.5);

    Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());

    Stream richTEG = new Stream("rich TEG from absorber", absorber.getSolventOutStream());
    /*
     * WaterDewPointAnalyser waterDewPointAnalyser = new WaterDewPointAnalyser(dehydratedGas);
     * waterDewPointAnalyser.setName("water dew point analyser");
     */
    HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
        new HydrateEquilibriumTemperatureAnalyser(dehydratedGas);
    waterDewPointAnalyser.setReferencePressure(70.0);
    waterDewPointAnalyser.setName("water dew point analyser");

    ThrottlingValve glycol_flash_valve = new ThrottlingValve("Rich TEG HP flash valve", richTEG);
    glycol_flash_valve.setOutletPressure(5.5);

    Heater richGLycolHeaterCondenser =
        new Heater("rich TEG preheater", glycol_flash_valve.getOutletStream());

    HeatExchanger heatEx2 =
        new HeatExchanger("rich TEG heat exchanger 1", richGLycolHeaterCondenser.getOutletStream());
    heatEx2.setGuessOutTemperature(273.15 + 62.0);
    heatEx2.setUAvalue(200.0);

    Separator flashSep = new Separator("degassing separator", heatEx2.getOutStream(0));

    Stream flashGas = new Stream("gas from degassing separator", flashSep.getGasOutStream());

    Stream flashLiquid =
        new Stream("liquid from degassing separator", flashSep.getLiquidOutStream());

    Filter fineFilter = new Filter("TEG fine filter", flashLiquid);
    fineFilter.setDeltaP(0.05, "bara");

    Filter carbonFilter = new Filter("activated carbon filter", fineFilter.getOutletStream());
    carbonFilter.setDeltaP(0.01, "bara");

    HeatExchanger heatEx =
        new HeatExchanger("rich TEG heat exchanger 2", carbonFilter.getOutletStream());
    heatEx.setGuessOutTemperature(273.15 + 130.0);
    heatEx.setUAvalue(390.0);

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(1.23);

    neqsim.thermo.system.SystemInterface stripGas = feedGas.clone();
    // stripGas.setMolarComposition(new double[] { 0.0, 0.0, 1.0, 0.0, 0.0, 0.0,
    // 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 });

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(255.0, "Sm3/hr");
    strippingGas.setTemperature(80.0, "C");
    strippingGas.setPressure(1.02, "bara");

    Stream gasToReboiler = strippingGas.clone();
    gasToReboiler.setName("gas to reboiler");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 0);
    column.getReboiler().setOutTemperature(273.15 + 201.0);
    column.getCondenser().setOutTemperature(273.15 + 92.0);
    column.getReboiler().addStream(gasToReboiler);
    column.setTopPressure(ThermodynamicConstantsInterface.referencePressure);
    column.setBottomPressure(1.02);

    Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + 7.5);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());

    Stream gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());

    Stream liquidToTrreatment = new Stream("water to treatment", sepregenGas.getLiquidOutStream());

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGas);
    stripper.setNumberOfStages(4);
    stripper.setStageEfficiency(0.5);
    /*
     * DistillationColumn stripper = new DistillationColumn(3, false, false);
     * stripper.setName("TEG stripper"); stripper.addFeedStream(column.getLiquidOutStream(), 2);
     * stripper.getTray(0).addStream(strippingGas);
     */
    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    Heater bufferTank = new Heater("TEG buffer tank", stripper.getLiquidOutStream());
    bufferTank.setOutTemperature(273.15 + 191.0);

    Pump hotLeanTEGPump = new Pump("hot lean TEG pump", bufferTank.getOutletStream()); // stripper.getSolventOutStream());
    hotLeanTEGPump.setOutletPressure(5.0);
    hotLeanTEGPump.setIsentropicEfficiency(0.6);

    heatEx.setFeedStream(1, hotLeanTEGPump.getOutletStream());

    heatEx2.setFeedStream(1, heatEx.getOutStream(1));

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", heatEx2.getOutStream(1));
    coolerhOTteg3.setOutTemperature(273.15 + 35.41);

    Pump hotLeanTEGPump2 = new Pump("lean TEG HP pump", coolerhOTteg3.getOutletStream());
    hotLeanTEGPump2.setOutletPressure(87.2);
    hotLeanTEGPump2.setIsentropicEfficiency(0.75);

    SetPoint pumpHPPresSet =
        new SetPoint("HP pump set", hotLeanTEGPump2, "pressure", feedToAbsorber);

    Stream leanTEGtoabs = new Stream("lean TEG to absorber", hotLeanTEGPump2.getOutletStream());

    neqsim.thermo.system.SystemInterface pureTEG = feedGas.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    Stream makeupTEG = new Stream("makeup TEG", pureTEG);
    makeupTEG.setFlowRate(1e-6, "kg/hr");
    makeupTEG.setTemperature(43.0, "C");
    makeupTEG.setPressure(52.21, "bara");

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
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    neqsim.processSimulation.processSystem.ProcessSystem operations =
        TEGdehydrationProcessDistillationAaHa.getProcess();

    operations.run();
    // operations.run();

    // operations.save("c:/temp/TEGprocessAaHa.neqsim");
    // operations = ProcessSystem.open("c:/temp/TEGprocess.neqsim");
    // ((DistillationColumn)operations.getUnit("TEG regeneration
    // column")).setTopPressure(1.2);
    // operations.run();
    // ((DistillationColumn)operations.getUnit("TEG regeneration
    // column")).setNumberOfTrays(2);
    /*
     * System.out.println("water in wet gas  " + ((Stream)
     * operations.getUnit("water saturated feed gas")).getFluid()
     * .getPhase(0).getComponent("water").getz() * 1.0e6 * 0.01802 * 101325.0 / (8.314 * 288.15));
     * System.out.println("water in dry gas  " + ((Stream)
     * operations.getUnit("dry gas from absorber")).getFluid()
     * .getPhase(0).getComponent("water").getz() * 1.0e6); System.out.println("reboiler duty (KW) "
     * + ((Reboiler) ((DistillationColumn)
     * operations.getUnit("TEG regeneration column")).getReboiler()) .getDuty() / 1.0e3);
     * System.out.println("wt lean TEG " + ((WaterStripperColumn)
     * operations.getUnit("TEG stripper"))
     * .getSolventOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG") * 100.0);
     * 
     * double waterInWetGasppm =
     * waterSaturatedFeedGas.getFluid().getPhase(0).getComponent("water").getz() * 1.0e6; double
     * waterInWetGaskgMSm3 = waterInWetGasppm * 0.01802 * 101325.0 / (8.314 * 288.15); double
     * TEGfeedwt = TEGFeed.getFluid().getPhase("aqueous").getWtFrac("TEG"); double TEGfeedflw =
     * TEGFeed.getFlowRate("kg/hr"); double waterInDehydratedGasppm =
     * dehydratedGas.getFluid().getPhase(0).getComponent("water").getz() * 1.0e6; double
     * waterInDryGaskgMSm3 = waterInDehydratedGasppm * 0.01802 * 101325.0 / (8.314 * 288.15); double
     * richTEG2 = richTEG.getFluid().getPhase("aqueous").getWtFrac("TEG"); double temp =
     * ((Stream)operations.getUnit("feed to TEG absorber")).getFluid().getPhase(0).
     * getComponent("water").getz()*1.0e6*0.01802*101325.0/(8.314*288.15);
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
     * System.out.println("glycol out temperature2 " +heatEx2.getOutStream(0).getTemperature("C"));
     * System.out.println("glycol out temperature2 " +heatEx2.getOutStream(1).getTemperature("C"));
     * 
     * 
     * System.out.println("out water rate LP valve" +
     * glycol_flash_valve2.getOutStream().getFluid().getPhase(0).getComponent(
     * "water").getNumberOfmoles()); System.out.println("glycol out water rate reboil " +
     * ((Reboiler) column.getReboiler()).getLiquidOutStream().getFluid().getComponent("water").
     * getNumberOfmoles()); System.out.println("glycol out water rate condens " + ((Condenser)
     * column.getCondenser()).getGasOutStream().getFluid().getComponent("water").
     * getNumberOfmoles()); System.out.println("recycle out water rate  "
     * +recycleGasFromStripper.getOutletStream().getFluid().getComponent("water").
     * getNumberOfmoles());
     * 
     * System.out.println("water dew point of dry gas  " +
     * waterDewPointAnalyser.getMeasuredValue("C"));
     * 
     * 
     * System.out.println("hydrocarbons in lean TEG  " + (1.0-
     * stripper.getLiquidOutStream().getFluid().getPhase(0).getWtFrac("TEG")-
     * stripper.getLiquidOutStream().getFluid().getPhase(0).getWtFrac("water"))*1e6 + " mg/kg");
     * System.out.println("hydrocarbons in rich TEG  " + (1.0-
     * flashLiquid.getFluid().getPhase(0).getWtFrac("TEG")-flashLiquid.getFluid().
     * getPhase(0).getWtFrac("water"))*1e6 + " mg/kg");
     * 
     * //double dewT = ((WaterDewPointAnalyser)operations.
     * getMeasurementDevice("water dew point analyser")).getMeasuredValue("C");
     * //waterDewPointAnalyser.setOnlineValue(measured, unit)
     * //waterDewPointAnalyser.setOnlineSignal(isOnlineSignal, plantName, transmitterame);
     * 
     * 
     * 
     * //Heat echanger test
     * 
     * //Sabe and Open copy of model
     * 
     * ProcessSystem locoperations = operations.copy();
     * //((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * getInStream(0).setTemperature(298.15, "C");
     * //((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * getInStream(1).setTemperature(363.15, "C");
     * //((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * getOutStream(0).setTemperature(298.15, "C");
     * //((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * getOutStream(1).setTemperature(333.15, "C");
     * //((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * runConditionAnalysis((HeatExchanger)operations. getUnit("rich TEG heat exchanger 2")); double
     * eff = ((HeatExchanger)locoperations.getUnit("rich TEG heat exchanger 2")).
     * getThermalEffectiveness(); System.out.println("eff " + eff); //store fouling factor in
     * dataframe
     * 
     * 
     * dehydratedGas.getFluid().display();
     */

    ConditionMonitor monitor = operations.getConditionMonitor();
    monitor.conditionAnalysis();

    // Condition monitor TEG absorber
    double xcalc = ((SimpleTEGAbsorber) monitor.getProcess().getUnit("TEG absorber"))
        .getGasOutStream().getFluid().getPhase("gas").getComponent("water").getx() * 1.1;
    ((SimpleTEGAbsorber) monitor.getProcess().getUnit("TEG absorber")).getGasOutStream().getFluid()
        .getPhase("gas").getComponent("water").setx(xcalc);
    monitor.conditionAnalysis("TEG absorber");
    System.out.println("number of theoretical stages "
        + ((SimpleTEGAbsorber) monitor.getProcess().getUnit("TEG absorber"))
            .getNumberOfTheoreticalStages());

    // Condition monitor rich TEG heat exchanger
    ((HeatExchanger) monitor.getProcess().getUnit("rich TEG heat exchanger 2")).getInStream(0)
        .setTemperature(0.2, "C");
    double eff1 = ((HeatExchanger) monitor.getProcess().getUnit("rich TEG heat exchanger 2"))
        .getThermalEffectiveness();
    double eff2 =
        ((HeatExchanger) operations.getUnit("rich TEG heat exchanger 2")).getThermalEffectiveness();
    double relativeEfficiency = eff1 / eff2;

    // Condition monitor water dew point analyser
    ((HydrateEquilibriumTemperatureAnalyser) monitor.getProcess()
        .getMeasurementDevice("water dew point analyser")).setOnlineMeasurementValue(-23.5, "C");
    ((HydrateEquilibriumTemperatureAnalyser) monitor.getProcess()
        .getMeasurementDevice("water dew point analyser")).runConditionAnalysis();

    // Condition monitor TEG fine filter
    ((Filter) monitor.getProcess().getUnit("TEG fine filter")).setDeltaP(0.2, "bara");
    monitor.conditionAnalysis("TEG fine filter");
    System.out.println("fine filter deltaP "
        + ((Filter) monitor.getProcess().getUnit("TEG fine filter")).getDeltaP());
    System.out.println(
        "fine filter deltaP2 " + ((Filter) operations.getUnit("TEG fine filter")).getDeltaP());
    double relativeCv = ((Filter) monitor.getProcess().getUnit("TEG fine filter")).getCvFactor()
        / ((Filter) operations.getUnit("TEG fine filter")).getCvFactor();

    double filterCv1 = ((Filter) operations.getUnit("TEG fine filter")).getCvFactor();
    double filterCv2 = ((Filter) operations.getUnit("activated carbon filter")).getCvFactor();

    System.out.println("filterCv1" + filterCv1 + " filterCv2 " + filterCv2);
  }
}
