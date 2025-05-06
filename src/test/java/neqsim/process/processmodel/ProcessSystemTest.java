package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.absorber.WaterStripperColumn;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;

/**
 * Class for testing ProcessSystem class.
 */
public class ProcessSystemTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessSystemTest.class);

  ProcessSystem p;
  String _name = "TestProcess";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }

  @Test
  void testGetName() {
    Assertions.assertEquals(_name, p.getName());
  }

  @Test
  public void testSetTimeStep() {
    double timeStep = p.getTimeStep() * 2;
    Assertions.assertEquals(timeStep / 2, p.getTimeStep());
    Assertions.assertNotEquals(timeStep, p.getTimeStep());

    p.setTimeStep(timeStep);
    Assertions.assertEquals(timeStep, p.getTimeStep());
    Assertions.assertNotEquals(timeStep / 2, p.getTimeStep());
  }

  @Test
  public void testHasUnitName() {
    String sepName = "TestSep";
    Assertions.assertFalse(p.hasUnitName(sepName));
    p.add(new Separator(sepName));
    Assertions.assertTrue(p.hasUnitName(sepName));
  }

  @Test
  void testAdd() {
    String sepName = "TestSep";
    Separator sep = new Separator("sep");
    sep.setName(sepName);
    p.add(sep);

    List<ProcessEquipmentInterface> list = p.getUnitOperations();

    Assertions.assertTrue(sep == p.getUnit(sepName));

    Assertions.assertEquals(1, list.size());
    Assertions.assertEquals(1, p.size());

    Assertions.assertTrue((Separator) list.get(0) == sep);

    p.removeUnit(sepName);
    Assertions.assertNull(p.getUnit(sepName));
    Assertions.assertEquals(0, p.size());

    list = p.getUnitOperations();

    Assertions.assertEquals(0, list.size());

    p.add(sep);
    Assertions.assertEquals(1, p.size());

    p.clear();
    Assertions.assertEquals(0, p.size());

    p.add(sep);
    Assertions.assertEquals(1, p.size());

    p.clearAll();
    Assertions.assertEquals(0, p.size());
  }

  @Test
  public void testAddUnitTwice() {
    Separator sep = new Separator("sep");
    p.add(sep);
    p.add(sep); // Won't add the copy
    Assertions.assertEquals(1, p.size());
  }

  @Test
  public void testRemoveUnit() {
    Separator sep = new Separator("Separator");
    p.add(sep);
    Assertions.assertEquals(1, p.size());
    p.removeUnit("Separator");
    Assertions.assertEquals(0, p.size());
  }

  @Test
  public void testAddUnitsWithDuplicateName() {
    String name = "TestSeparator";
    Separator sep = new Separator(name);
    p.add(sep);

    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      p.add(new Separator(name));
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: ProcessSystem:add - Input operation - Process equipment of type Separator named "
            + name + " already included in ProcessSystem",
        thrown.getMessage());
    p.removeUnit(name);
    Assertions.assertEquals(0, p.size());
    p.add(new Tank(name));
    Assertions.assertEquals(1, p.size());

    RuntimeException thrown2 = Assertions.assertThrows(RuntimeException.class, () -> {
      p.add(new Separator(name));
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: ProcessSystem:add - Input operation - Process equipment of type Tank named "
            + name + " already included in ProcessSystem",
        thrown2.getMessage());
  }

  @Test
  public void testGetUnitNumber() {
    Separator sep = new Separator("Separator");
    p.add(sep);
    Separator sep2 = new Separator("Separator2");
    p.add(sep2);
    Assertions.assertEquals(2, p.size());

    Assertions.assertEquals(0, p.getUnitNumber("Separator"));
    Assertions.assertEquals(1, p.getUnitNumber("Separator2"));

    p.removeUnit("Separator");
    Assertions.assertEquals(1, p.size());
    p.add(sep);

    Assertions.assertEquals(0, p.getUnitNumber("Separator2"));
    Assertions.assertEquals(1, p.getUnitNumber("Separator"));
  }

  @Test
  public void testSetSurroundingTemperature() {
    double temp = 200;
    p.setSurroundingTemperature(temp);
    Assertions.assertEquals(temp, p.getSurroundingTemperature());
  }

  @Test
  void testClear() {
    p.clear();
  }

  @Test
  void testClearAll() {
    p.clearAll();
  }

  @Test
  void testCopy() {
    ProcessSystem sys2 = p.copy();
    Assertions.assertTrue(p.equals(sys2));
    Assertions.assertEquals(p, sys2);
  }

  @Test
  void testGetAllUnitNames() {}

  @Test
  void testGetConditionMonitor() {}

  @Test
  void testGetCoolerDuty() {}

  @Test
  void testGetCostEstimator() {}

  @Test
  void testGetEntropyProduction() {}

  @Test
  void testGetExergyChange() {}

  @Test
  void testGetHeaterDuty() {}

  @Test
  void testGetMeasurementDevice() {}

  @Test
  void testGetMechanicalWeight() {}

  @Test
  void testGetPower() {}

  @Test
  void testGetSurroundingTemperature() {}

  @Test
  void testGetSystemMechanicalDesign() {}

  @Test
  void testGetUnit() {}

  @Test
  void testGetUnitOperations() {}

  @Test
  void testOpen() {}

  @Test
  void testPrintLogFile() {}

  @Test
  void testReplaceObject() {}

  @Test
  void testReportMeasuredValues() {}

  @Test
  void testReportResults() {}

  @Test
  void testRun() {}

  @Test
  void testRunAsThread() {}

  @Test
  void testSave() {}

  @Test
  void testSetFluid() {}

  @Test
  void testSetName() {}

  @Test
  void testSetSystemMechanicalDesign() {}

  @Test
  void testSize() {}

  @Test
  void testView() {}

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

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
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
    Stream waterFeed = new Stream("extra water", feedWater);
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
    makeupMixer.addStream(makeupTEG);

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", makeupMixer.getOutletStream());
    coolerhOTteg3.setOutTemperature(273.15 + 40.0);

    condHeat.setEnergyStream(column.getCondenser().getEnergyStream());

    Stream leanTEGtoabs = new Stream("resyc lean TEG to absorber", coolerhOTteg3.getOutletStream());

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
    operations.run();
    /*
     * System.out.println("flowo " + hotLeanTEGPump.getOutletStream().getFlowRate("kg/hr"));
     * System.out.println("makeup " + makeupTEG.getFlowRate("kg/hr")); System.out.println("mixo " +
     * coolerhOTteg3.getOutletStream().getFlowRate("kg/hr")); System.out.println("leantoresirc " +
     * leanTEGtoabs.getFlowRate("kg/hr")); // operations.run(); System.out.println("flowo " +
     * hotLeanTEGPump.getOutletStream().getFlowRate("kg/hr")); System.out.println("makeup " +
     * makeupTEG.getFlowRate("kg/hr")); System.out.println("mixo " +
     * coolerhOTteg3.getOutletStream().getFlowRate("kg/hr")); System.out.println("leantoresirc " +
     * leanTEGtoabs.getFlowRate("kg/hr"));
     */
    assertEquals(1.5449593316401103E-5, dehydratedGas.getFluid().getComponent("water").getx(),
        1e-6);
  }

  @Test
  public void testRun_step() {
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

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
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
    Stream waterFeed = new Stream("extra water", feedWater);
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
    recycleGasFromStripper.setTolerance(1.0e-2);

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
    makeupMixer.addStream(makeupTEG);

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", makeupMixer.getOutletStream());
    coolerhOTteg3.setOutTemperature(273.15 + 40.0);

    condHeat.setEnergyStream(column.getCondenser().getEnergyStream());

    Stream leanTEGtoabs = new Stream("resyc lean TEG to absorber", coolerhOTteg3.getOutletStream());

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
    operations.run();
    dryFeedGasMidgard.setFlowRate(11.1, "MSm3/day");
    operations.run_step();
    dryFeedGasMidgard.setFlowRate(12.3, "MSm3/day");
    operations.run_step();
    dryFeedGasMidgard.setFlowRate(13.5, "MSm3/day");
    ProcessSystem ops2 = operations.copy();
    operations.setRunInSteps(true);
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    ProcessSystem ops3 = operations.copy();
    operations.run();
    operations.run();
    dryFeedGasMidgard.setFlowRate(10.0, "MSm3/day");
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    operations.run();
    assertEquals(1.5322819175995646E-5, dehydratedGas.getFluid().getComponent("water").getx(),
        1e-6);

    operations.run();
    operations.run();
    operations.run();
    operations.run();
    assertEquals(1.5322819175995646E-5, dehydratedGas.getFluid().getComponent("water").getx(),
        1e-6);

    // run as time step as thread
    Thread thread = operations.runAsThread();
    Thread thread2 = ops2.runAsThread();
    Thread thread3 = ops3.runAsThread();
    try {
      thread.join();
      thread2.join();
      thread3.join();
    } catch (Exception e) {
      logger.error(e.getMessage());;
    }
  }

  @Test
  public void testSimplifiedModel() {

    neqsim.thermo.system.SystemInterface fluid1 = new neqsim.thermo.system.SystemSrkEos(190, 10);
    fluid1.addComponent("methane", 0.5);
    fluid1.addComponent("ethane", 0.2);
    fluid1.addComponent("propane", 0.1);
    fluid1.addComponent("nC10", 0.1);
    fluid1.setMixingRule("classic");


    ProcessSystem process1 = new ProcessSystem();

    Stream stream1 = process1.addUnit("stream_1", "Stream");
    stream1.setFluid(fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setPressure(30.0, "bara");

    ThrottlingValve valve1 = process1.addUnit("Valve 1", "ThrottlingValve");
    valve1.setOutletPressure(10.0, "bara");

    Separator separator1 = process1.addUnit("Separator 1", "Separator");

    process1.addUnit("GasOut", separator1.getGasOutStream());
    Compressor compressor1 = process1.addUnit("Compressor 1", "Compressor");
    compressor1.setOutletPressure(50.0, "bara");

    process1.addUnit(separator1.getLiquidOutStream());
    ThrottlingValve valve2 = process1.addUnit("Valve 2", "ThrottlingValve");
    valve2.setOutletPressure(1.0, "bara");
    process1.addUnit("stableoil", "Stream");

    process1.run();

    // System.out.println("name " + liquidOut.getName());

    // compressor1.getOutletStream().getFluid().prettyPrint();

    assertEquals(4.78589648, valve1.getOutletStream().getTemperature("C"), 1e-6);

    // process1.validateConnections();
    // process1.checkMassBalance();
  }

}
