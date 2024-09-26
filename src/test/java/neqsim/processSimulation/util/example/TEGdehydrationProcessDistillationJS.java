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
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGdehydrationProcessDistillationJS {
  /**
   * The flow rate of gas from the scrubber controlling the dew point (MSm3/hr).
   */
  public double feedGasFlowRate = 5.0;

  /**
   * The temperature of the gas from the scrubber controlling the dew point (Celsuis).
   */
  public double feedGasTemperature = 30.4;

  /**
   * The pressure of the gas from the scrubber controlling the dew point (bara).
   */
  public double feedGasPressure = 52.21;

  /**
   * The temperature of the gas entering the TEG absorption column (Celsius).
   */
  public double absorberFeedGasTemperature = 35.0;

  /**
   * The pressure of the gas entering the TEG absorption column (bara).
   */
  public double absorberFeedGasPressure = 52.21;

  /**
   * The flow rate of lean TEG entering the absorption column (kg/hr).
   */
  public double leanTEGFlowRate = 6862.5;

  /**
   * The temperature of the lean TEG entering the absorption column (Celsius).
   */
  public double leanTEGTemperature = 43.0;

  /**
   * The pressure in the flash drum (bara).
   */
  public double flashDrumPressure = 5.5;

  /**
   * Number of equilibrium stages in TEG absorber.
   */
  public int numberOfEquilibriumStagesTEGabsorber = 5;

  /**
   * Stage efficiency in TEG absorber.
   */
  public double stageEfficiencyStripper = 0.5;

  /**
   * Number of equilibrium stages in TEG absorber.
   */
  public int numberOfEquilibriumStagesStripper = 4;

  /**
   * Stage efficiency in TEG absorber.
   */
  public double stageEfficiencyTEGabsorber = 0.55;

  /**
   * UA value of rich TEG heat exchanger 1.
   */
  public double UAvalueRichTEGHeatExchanger_1 = 220.0;

  /**
   * UA value of rich TEG heat exchanger 2.
   */
  public double UAvalueRichTEGHeatExchanger_2 = 600.0;

  /**
   * Pressure in reboiler (bara).
   */
  public double reboilerPressure = 1.2;

  /**
   * Temperature in condenser (Celsius).
   */
  public double condenserTemperature = 100.0;

  /**
   * Pressure in condenser (bara).
   */
  public double condenserPressure = 1.0;

  /**
   * Temperature in reboiler (Celsius).
   */
  public double reboilerTemperature = 205.0;

  /**
   * Stripping gas flow rate (Sm3/hr).
   */
  public double strippingGasRate = 55.0;

  /**
   * Stripping gas feed temperature (Celsius).
   */
  public double strippingGasFeedTemperature = 80.0;

  /**
   * TEG buffer tank temperature (Celsius).
   */
  public double bufferTankTemperatureTEG = 185.0;

  /**
   * temperature of after regeneration gas cooler (Celsius).
   */
  public double regenerationGasCoolerTemperature = 35.0;

  /**
   * isentropic efficiency of hot lean TEG pump (0.0-1.0).
   */
  public double hotTEGpumpIsentropicEfficiency = 0.75;

  /**
   * pressure after hot lean TEG pump (bara).
   */
  public double hotTEGpumpPressure = 5.0;

  /**
   * isentropic efficiency of cold lean TEG pump (0.0-1.0).
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

    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil("water saturator", dryFeedGas);

    Stream waterSaturatedFeedGas =
        new Stream("water saturated feed gas", saturatedFeedGas.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser =
        new HydrateEquilibriumTemperatureAnalyser("hydrate temperature analyser",
            waterSaturatedFeedGas);

    neqsim.thermo.system.SystemInterface feedTEG = feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97});

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

    HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
        new HydrateEquilibriumTemperatureAnalyser("water dew point analyser", dehydratedGas);
    ThrottlingValve glycol_flash_valve = new ThrottlingValve("Rich TEG HP flash valve", richTEG);
    glycol_flash_valve.setOutletPressure(flashDrumPressure);

    Heater richGLycolHeaterCondenser =
        new Heater("rich TEG preheater", glycol_flash_valve.getOutletStream());

    HeatExchanger heatEx2 =
        new HeatExchanger("rich TEG heat exchanger 1", richGLycolHeaterCondenser.getOutletStream());
    heatEx2.setGuessOutTemperature(273.15 + 62.0);
    heatEx2.setUAvalue(UAvalueRichTEGHeatExchanger_1);

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
    heatEx.setUAvalue(UAvalueRichTEGHeatExchanger_2);

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("Rich TEG LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setOutletPressure(reboilerPressure);

    neqsim.thermo.system.SystemInterface stripGas = feedGas.clone();

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(strippingGasRate, "Sm3/hr");
    strippingGas.setTemperature(strippingGasFeedTemperature, "C");
    strippingGas.setPressure(reboilerPressure, "bara");

    Stream gasToReboiler = strippingGas.clone("gas to reboiler");

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 0);
    column.getReboiler().setOutTemperature(273.15 + reboilerTemperature);
    column.getCondenser().setOutTemperature(273.15 + condenserTemperature);
    column.getReboiler().addStream(gasToReboiler);
    column.setTopPressure(condenserPressure);
    column.setBottomPressure(reboilerPressure);

    Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + regenerationGasCoolerTemperature);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());

    Stream gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());

    Stream liquidToTrreatment = new Stream("water to treatment", sepregenGas.getLiquidOutStream());

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

    Pump hotLeanTEGPump = new Pump("hot lean TEG pump", bufferTank.getOutletStream());
    hotLeanTEGPump.setOutletPressure(hotTEGpumpPressure);
    hotLeanTEGPump.setIsentropicEfficiency(hotTEGpumpIsentropicEfficiency);

    heatEx.setFeedStream(1, hotLeanTEGPump.getOutletStream());

    heatEx2.setFeedStream(1, heatEx.getOutStream(1));

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", heatEx2.getOutStream(1));
    coolerhOTteg3.setOutTemperature(273.15 + leanTEGTemperature);

    Pump hotLeanTEGPump2 = new Pump("lean TEG HP pump", coolerhOTteg3.getOutletStream());
    hotLeanTEGPump2.setOutletPressure(absorberFeedGasPressure);
    hotLeanTEGPump2.setIsentropicEfficiency(coldTEGpumpIsentropicEfficiency);

    SetPoint pumpHPPresSet =
        new SetPoint("HP pump set", hotLeanTEGPump2, "pressure", feedToAbsorber);

    Stream leanTEGtoabs = new Stream("lean TEG to absorber", hotLeanTEGPump2.getOutletStream());

    neqsim.thermo.system.SystemInterface pureTEG = feedGas.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

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

    Recycle recycleLeanTEG = new Recycle("lean TEG recycle");
    recycleLeanTEG.addStream(makeupMixer.getOutletStream());
    recycleLeanTEG.setOutletStream(TEGFeed);
    recycleLeanTEG.setPriority(200);
    recycleLeanTEG.setDownstreamProperty("flow rate");

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
    operations.add(recycleLeanTEG);

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
