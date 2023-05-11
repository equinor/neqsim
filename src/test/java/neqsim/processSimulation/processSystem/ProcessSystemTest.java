package neqsim.processSimulation.processSystem;

import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.filter.Filter;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.SetPoint;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * Class for testing ProcessSystem class.
 */
public class ProcessSystemTest extends neqsim.NeqSimTest {
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

    ArrayList<ProcessEquipmentInterface> list = p.getUnitOperations();

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
  @SuppressWarnings("deprecation")
  public void testAddUnitsWithNoName() {
    Separator sep = new Separator();
    p.add(sep);
    sep = new Separator();
    p.add(sep);
    Assertions.assertEquals(2, p.size());
    p.removeUnit("Separator2");
    Assertions.assertEquals(1, p.size());
    p.removeUnit("Separator");
    Assertions.assertEquals(0, p.size());
  }

  @Test
  public void testGetUnitNumber() {
    Separator sep = new Separator("Separator");
    p.add(sep);
    Separator sep2 = new Separator("Separator2");
    p.add(sep2);

    Assertions.assertEquals(0, p.getUnitNumber("Separator"));
    Assertions.assertEquals(1, p.getUnitNumber("Separator2"));

    p.removeUnit("Separator");
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
  void testDisplayResult() {}

  @Test
  void testGetAllUnitNames() {

  }

  @Test
  void testGetConditionMonitor() {

  }

  @Test
  void testGetCoolerDuty() {

  }

  @Test
  void testGetCostEstimator() {

  }

  @Test
  void testGetEntropyProduction() {

  }

  @Test
  void testGetExergyChange() {

  }

  @Test
  void testGetHeaterDuty() {

  }

  @Test
  void testGetMeasurementDevice() {

  }

  @Test
  void testGetMechanicalWeight() {

  }

  @Test
  void testGetPower() {

  }

  @Test
  void testGetSurroundingTemperature() {

  }

  @Test
  void testGetSystemMechanicalDesign() {

  }

  @Test
  void testGetUnit() {

  }

  @Test
  void testGetUnitOperations() {

  }

  @Test
  void testOpen() {

  }

  @Test
  void testPrintLogFile() {

  }

  @Test
  void testReplaceObject() {

  }

  @Test
  void testReportMeasuredValues() {

  }

  @Test
  void testReportResults() {

  }

  @Test
  void testRun() {

  }

  @Test
  void testRunAsThread() {

  }

  @Test
  void testSave() {

  }

  @Test
  void testSetFluid() {

  }

  @Test
  void testSetName() {}

  @Test
  void testSetSystemMechanicalDesign() {

  }

  @Test
  void testSize() {

  }

  @Test
  void testView() {}

  @Test
  public void runTEGProcessTest() {
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
    feedGas.setMultiPhaseCheck(false);

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
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 201.0);
    column.getCondenser().setOutTemperature(273.15 + 92.0);
    column.getTray(1).addStream(gasToReboiler);
    column.setTopPressure(1.01325);
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
    operations.run();
    dehydratedGas.getFluid().display();
    dehydratedGas.getFluid().display();
  }

  @Test
  public void runTEGProcessTest3() {
    neqsim.thermo.system.SystemInterface feedGasTrainB =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    feedGasTrainB.addComponent("nitrogen", 0.00258);
    feedGasTrainB.addComponent("CO2", 0.035499);
    feedGasTrainB.addComponent("methane", 0.8947);
    feedGasTrainB.addComponent("ethane", 0.06244);
    feedGasTrainB.addComponent("propane", 0.0028639);
    feedGasTrainB.addComponent("i-butane", 0.00038631);
    feedGasTrainB.addComponent("n-butane", 0.0008039);
    feedGasTrainB.addComponent("i-pentane", 0.0001482);
    feedGasTrainB.addComponent("n-pentane", 0.001733);
    feedGasTrainB.addComponent("n-hexane", 6.2645e-5);
    feedGasTrainB.addComponent("benzene", 1.044e-5);
    feedGasTrainB.addComponent("water", 0.0002626);
    feedGasTrainB.addComponent("TEG", 0);
    feedGasTrainB.setMixingRule(10);
    feedGasTrainB.setMultiPhaseCheck(false);
    feedGasTrainB.init(0);

    Stream satGasTrainB = new Stream("Saturated gas train B", feedGasTrainB);
    satGasTrainB.setFlowRate(309292.3538, "kg/hr");
    satGasTrainB.setTemperature(20, "C");
    satGasTrainB.setPressure(40, "bara");

    Heater feedTPsetterToAbsorber = new Heater("TP of gas to absorber", satGasTrainB);
    feedTPsetterToAbsorber.setOutPressure(44.44, "bara");
    feedTPsetterToAbsorber.setOutTemperature(16.365, "C");

    Stream feedToAbsorber =
        new Stream("feed to TEG absorber", feedTPsetterToAbsorber.getOutletStream());

    HydrateEquilibriumTemperatureAnalyser hydrateTAnalyser2 =
        new HydrateEquilibriumTemperatureAnalyser(feedToAbsorber);
    hydrateTAnalyser2.setName("hydrate temperature gas to absorber");

    neqsim.thermo.system.SystemInterface feedTEG =
        (neqsim.thermo.system.SystemInterface) feedGasTrainB.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.01, 0.99});

    Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
    TEGFeed.setFlowRate(8922.476, "kg/hr");
    TEGFeed.setTemperature(35, "C");
    TEGFeed.setPressure(44.44, "bara");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(TEGFeed);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.75);
    absorber.setInternalDiameter(3.65);

    Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());

    Stream richTEG = new Stream("rich TEG from absorber", absorber.getLiquidOutStream());

    HydrateEquilibriumTemperatureAnalyser waterDewPointAnalyser =
        new HydrateEquilibriumTemperatureAnalyser(dehydratedGas);
    waterDewPointAnalyser.setReferencePressure(70.0);
    waterDewPointAnalyser.setName("hydrate dew point analyser");

    WaterDewPointAnalyser waterDewPointAnalyser2 = new WaterDewPointAnalyser(dehydratedGas);
    waterDewPointAnalyser2.setReferencePressure(70.0);
    waterDewPointAnalyser2.setName("water dew point analyser");

    Heater condHeat = new Heater("Condenser heat exchanger", richTEG);

    ThrottlingValve glycol_flash_valve =
        new ThrottlingValve("Flash valve", condHeat.getOutletStream());
    glycol_flash_valve.setName("Rich TEG HP flash valve");
    glycol_flash_valve.setOutletPressure(7.013);

    HeatExchanger heatEx2 =
        new HeatExchanger("rich TEG heat exchanger 1", glycol_flash_valve.getOutletStream());
    heatEx2.setGuessOutTemperature(273.15 + 90);
    heatEx2.setUAvalue(1450);

    neqsim.thermo.system.SystemInterface feedWater =
        (neqsim.thermo.system.SystemInterface) feedGasTrainB.clone();
    feedWater.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0});

    Stream waterFeed = new Stream("lean TEG to absorber", feedWater);
    waterFeed.setFlowRate(0, "kg/hr");
    waterFeed.setTemperature(90, "C");
    waterFeed.setPressure(7.013, "bara");

    Separator flashSep = new Separator("degassing separator", heatEx2.getOutStream(0));
    flashSep.setInternalDiameter(1.2);

    Stream flashGas = new Stream("gas from degassing separator", flashSep.getGasOutStream());

    Stream flashLiquid =
        new Stream("liquid from degassing separator", flashSep.getLiquidOutStream());

    Filter filter = new Filter("TEG fine filter", flashLiquid);
    filter.setDeltaP(0, "bara");

    HeatExchanger heatEx =
        new HeatExchanger("lean/rich TEG heat-exchanger", filter.getOutletStream());
    heatEx.setGuessOutTemperature(273.15 + 105);
    heatEx.setUAvalue(9140);

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(1.2096);

    neqsim.thermo.system.SystemInterface stripGas =
        (neqsim.thermo.system.SystemInterface) feedGasTrainB.clone();
    stripGas.setMolarComposition(new double[] {0, 1, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 1e-6, 0.0});

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(153, "kg/hr");
    strippingGas.setTemperature(18.8918, "C");
    strippingGas.setPressure(1, "bara");

    StreamSaturatorUtil saturatedStrippingGas =
        new StreamSaturatorUtil("water saturator Stripping gas", strippingGas);

    Heater strippingGasTPsetter =
        new Heater("TP of stripping gas", saturatedStrippingGas.getOutletStream());
    strippingGasTPsetter.setOutPressure(1.2096, "bara");
    strippingGasTPsetter.setOutTemperature(193.46, "C");

    Stream gasToReboiler = (Stream) (strippingGasTPsetter.getOutletStream()).clone();
    gasToReboiler.setName("gas to reboiler");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(glycol_flash_valve2.getOutletStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + 204.269);
    column.getCondenser().setOutTemperature(273.15 + 102.79);
    // column.getReboiler().addStream(gasToReboiler);
    column.getTray(0).addStream(gasToReboiler);
    column.setTopPressure(1.1963);
    column.setBottomPressure(1.2096);
    column.setInternalDiameter(0.56);

    Heater coolerRegenGas = new Heater("regen gas cooler", column.getGasOutStream());
    coolerRegenGas.setOutTemperature(273.15 + 18.8918);

    Separator sepregenGas = new Separator("regen gas separator", coolerRegenGas.getOutletStream());

    Stream gasToFlare = new Stream("gas to flare", sepregenGas.getGasOutStream());

    Stream liquidToTrreatment = new Stream("water to treatment", sepregenGas.getLiquidOutStream());
    liquidToTrreatment.setName("water to treatment");

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(strippingGasTPsetter.getOutletStream());
    stripper.setNumberOfStages(3);
    stripper.setStageEfficiency(0.8);

    Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
    recycleGasFromStripper.addStream(stripper.getGasOutStream());
    recycleGasFromStripper.setOutletStream(gasToReboiler);

    neqsim.thermo.system.SystemInterface pureTEG =
        (neqsim.thermo.system.SystemInterface) feedGasTrainB.clone();
    pureTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0});

    Stream makeupTEG = new Stream("makeup TEG", pureTEG);
    makeupTEG.setFlowRate(1e-5, "kg/hr");
    makeupTEG.setTemperature(20.46, "C");
    makeupTEG.setPressure(1.2096, "bara");

    Calculator makeupCalculator = new Calculator("TEG makeup calculator");
    makeupCalculator.addInputVariable(dehydratedGas);
    makeupCalculator.addInputVariable(flashGas);
    makeupCalculator.addInputVariable(gasToFlare);
    makeupCalculator.addInputVariable(liquidToTrreatment);
    makeupCalculator.setOutputVariable(makeupTEG);

    StaticMixer makeupMixer = new StaticMixer("makeup mixer");
    makeupMixer.addStream(stripper.getLiquidOutStream());
    makeupMixer.addStream(makeupTEG);

    heatEx.setFeedStream(1, makeupMixer.getOutletStream());
    heatEx2.setFeedStream(1, heatEx.getOutStream(1));

    Pump hotLeanTEGPump = new Pump("lean TEG LP pump", heatEx2.getOutStream(1));
    hotLeanTEGPump.setOutletPressure(44.44);
    hotLeanTEGPump.setIsentropicEfficiency(1);

    Heater coolerhOTteg3 = new Heater("lean TEG cooler", hotLeanTEGPump.getOutletStream());
    coolerhOTteg3.setOutTemperature(273.15 + 35);

    condHeat.setEnergyStream(column.getCondenser().getEnergyStream());

    Stream leanTEGtoabs = new Stream("lean TEG to absorber", coolerhOTteg3.getOutletStream());

    Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
    resycleLeanTEG.addStream(leanTEGtoabs);
    resycleLeanTEG.setOutletStream(TEGFeed);
    resycleLeanTEG.setPriority(200);
    resycleLeanTEG.setDownstreamProperty("flow rate");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(satGasTrainB);
    operations.add(feedTPsetterToAbsorber);
    operations.add(feedToAbsorber);
    operations.add(hydrateTAnalyser2);
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
    operations.add(saturatedStrippingGas);
    operations.add(strippingGasTPsetter);

    operations.add(gasToReboiler);
    operations.add(column);

    operations.add(coolerRegenGas);
    operations.add(sepregenGas);
    operations.add(gasToFlare);
    operations.add(liquidToTrreatment);

    operations.add(stripper);
    operations.add(recycleGasFromStripper);

    operations.add(makeupTEG);
    operations.add(makeupCalculator);
    operations.add(makeupMixer);
    operations.add(hotLeanTEGPump);
    operations.add(coolerhOTteg3);
    operations.add(leanTEGtoabs);
    operations.add(resycleLeanTEG);
    // operations.run();
  }

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
        new HydrateEquilibriumTemperatureAnalyser(waterSaturatedFeedGasSmorbukk);
    hydrateTAnalyserSmorbukk.setName("hydrate temperature analyser Smorbukk");

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
        new HydrateEquilibriumTemperatureAnalyser(
            waterSaturatedFeedGasMidgard);
    hydrateTAnalyserMidgard.setName("hydrate temperature analyser Midgard");

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
        new HydrateEquilibriumTemperatureAnalyser(feedToAbsorber);
    hydrateTAnalyser2.setName("hydrate temperature gas to absorber");

    WaterDewPointAnalyser waterDewPointAnalyserToAbsorber =
        new WaterDewPointAnalyser(feedToAbsorber);
    waterDewPointAnalyserToAbsorber.setMethod("multiphase");
    waterDewPointAnalyserToAbsorber.setReferencePressure(40.0);
    waterDewPointAnalyserToAbsorber.setName("water dew point gas to absorber");

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
        new HydrateEquilibriumTemperatureAnalyser(dehydratedGas);
    waterDewPointAnalyser.setReferencePressure(70.0);
    waterDewPointAnalyser.setName("hydrate dew point analyser");

    WaterDewPointAnalyser waterDewPointAnalyser2 = new WaterDewPointAnalyser(dehydratedGas);
    waterDewPointAnalyser2.setReferencePressure(70.0);
    waterDewPointAnalyser2.setName("water dew point analyser");

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
    Stream waterFeed = new Stream("lean TEG to absorber", feedWater);
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
        new ThrottlingValve("LP flash valve", heatEx.getOutStream(0));
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(feedPressureGLycol);

    neqsim.thermo.system.SystemInterface stripGas =
        (neqsim.thermo.system.SystemInterface) feedGas.clone();
    stripGas.setMolarComposition(
        new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});

    Stream strippingGas = new Stream("stripGas", stripGas);
    strippingGas.setFlowRate(250.0 * 0.8, "kg/hr");
    strippingGas.setTemperature(180.0, "C");
    strippingGas.setPressure(feedPressureStripGas, "bara");

    Stream gasToReboiler = (Stream) strippingGas.clone();
    gasToReboiler.setName("gas to reboiler");

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
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

    Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
    resycleLeanTEG.addStream(leanTEGtoabs);
    resycleLeanTEG.setOutletStream(TEGFeed);
    resycleLeanTEG.setPriority(200);
    resycleLeanTEG.setDownstreamProperty("flow rate");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
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
    operations.add(resycleLeanTEG);

    operations.run();
  }
}
