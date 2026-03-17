package neqsim.process.electricaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.electricaldesign.compressor.CompressorElectricalDesign;
import neqsim.process.electricaldesign.components.ElectricalCable;
import neqsim.process.electricaldesign.components.ElectricalMotor;
import neqsim.process.electricaldesign.components.HazardousAreaClassification;
import neqsim.process.electricaldesign.components.Switchgear;
import neqsim.process.electricaldesign.components.Transformer;
import neqsim.process.electricaldesign.components.VariableFrequencyDrive;
import neqsim.process.electricaldesign.heatexchanger.HeatExchangerElectricalDesign;
import neqsim.process.electricaldesign.loadanalysis.ElectricalLoadList;
import neqsim.process.electricaldesign.loadanalysis.LoadItem;
import neqsim.process.electricaldesign.pipeline.PipelineElectricalDesign;
import neqsim.process.electricaldesign.separator.SeparatorElectricalDesign;
import neqsim.process.electricaldesign.system.SystemElectricalDesign;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the electrical design package.
 *
 * @author Even Solbraa
 */
public class ElectricalDesignTest {

  private static SystemInterface testFluid;
  private static Stream testStream;

  @BeforeAll
  static void setUp() {
    testFluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");

    testStream = new Stream("feed", testFluid);
    testStream.setFlowRate(50000.0, "kg/hr");
    testStream.run();
  }

  @Test
  void testMotorSizing() {
    ElectricalMotor motor = new ElectricalMotor();
    motor.sizeMotor(100.0, 1.10, "IEC");

    // Motor should be sized to next standard rating above 100 * 1.10 = 110 kW
    assertTrue(motor.getRatedPowerKW() >= 110.0,
        "Motor should be sized above required power: " + motor.getRatedPowerKW());
    assertTrue(motor.getEfficiencyPercent() > 90.0, "Motor efficiency should be above 90%");
    assertTrue(motor.getPowerFactorFL() > 0.7, "Power factor should be reasonable");
    assertNotNull(motor.getFrameSize(), "Frame size should be assigned");

    // Check JSON output
    String json = motor.toJson();
    assertNotNull(json);
    assertTrue(json.contains("ratedPowerKW"));
    assertTrue(json.contains("efficiencyClass"));
  }

  @Test
  void testMotorPartLoadPerformance() {
    ElectricalMotor motor = new ElectricalMotor();
    motor.sizeMotor(200.0, 1.10, "IEC");

    double effFull = motor.getEfficiencyAtLoad(1.0);
    double eff75 = motor.getEfficiencyAtLoad(0.75);
    double eff50 = motor.getEfficiencyAtLoad(0.5);

    assertTrue(effFull > 0, "Full load efficiency should be positive");
    assertTrue(eff75 > 0, "75% load efficiency should be positive");
    assertTrue(eff50 > 0, "50% load efficiency should be positive");
  }

  @Test
  void testVFDSizing() {
    ElectricalMotor motor = new ElectricalMotor();
    motor.sizeMotor(500.0, 1.10, "IEC");
    motor.setRatedVoltageV(6600);

    VariableFrequencyDrive vfd = new VariableFrequencyDrive();
    vfd.sizeVFD(motor);

    assertTrue(vfd.getRatedPowerKW() >= motor.getRatedPowerKW(), "VFD should cover motor rating");
    assertTrue(vfd.getEfficiencyPercent() > 90.0, "VFD efficiency should be above 90%");
    assertNotNull(vfd.getTopologyType(), "Topology should be selected");

    String json = vfd.toJson();
    assertNotNull(json);
    assertTrue(json.contains("topologyType"));
  }

  @Test
  void testCableSizing() {
    ElectricalCable cable = new ElectricalCable();
    cable.sizeCable(200.0, 400.0, 100.0, "Tray", 40.0);

    assertTrue(cable.getCrossSectionMM2() > 0, "Cross section should be selected");
    assertTrue(cable.getAmpacityA() >= 200.0, "Ampacity should cover load current");

    String json = cable.toJson();
    assertNotNull(json);
    assertTrue(json.contains("crossSectionMM2"));
  }

  @Test
  void testCableVoltageDrop() {
    ElectricalCable cable = new ElectricalCable();
    cable.sizeCable(100.0, 400.0, 200.0, "Tray", 30.0);

    double vDrop = cable.getVoltageDropPercent();
    assertTrue(vDrop >= 0, "Voltage drop should be non-negative");
  }

  @Test
  void testTransformerSizing() {
    Transformer transformer = new Transformer();
    transformer.sizeTransformer(800.0, 11000, 400);

    assertTrue(transformer.getRatedPowerKVA() >= 800.0, "Transformer should cover load");
    assertEquals(11000.0, transformer.getPrimaryVoltageV());
    assertEquals(400.0, transformer.getSecondaryVoltageV());
    assertTrue(transformer.getEfficiencyPercent() > 95.0,
        "Transformer efficiency should be above 95%");

    String json = transformer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("vectorGroup"));
  }

  @Test
  void testSwitchgearSizing() {
    Switchgear switchgear = new Switchgear();
    switchgear.sizeSwitchgear(300.0, 200.0, 400.0, false);

    assertTrue(switchgear.getRatedCurrentA() >= 300.0,
        "Switchgear rating should cover load current");
    assertNotNull(switchgear.getStarterType());

    String json = switchgear.toJson();
    assertNotNull(json);
    assertTrue(json.contains("starterType"));
  }

  @Test
  void testHazAreaClassification() {
    HazardousAreaClassification hazArea = new HazardousAreaClassification();
    hazArea.classify("Compressor", true, 200.0);

    assertNotNull(hazArea.getZone());
    assertNotNull(hazArea.getTemperatureClass());
    assertNotNull(hazArea.getRequiredExProtection());

    String marking = hazArea.getExMarking();
    assertNotNull(marking);
    assertTrue(marking.length() > 0, "Ex marking should not be empty");
  }

  @Test
  void testCompressorElectricalDesign() {
    Compressor compressor = new Compressor("TestComp", testStream);
    compressor.setOutletPressure(50.0);
    compressor.run();

    CompressorElectricalDesign elecDesign = compressor.getElectricalDesign();
    assertNotNull(elecDesign, "Electrical design should exist");

    elecDesign.calcDesign();

    assertTrue(elecDesign.getShaftPowerKW() > 0, "Shaft power should be positive after run");
    assertTrue(elecDesign.getElectricalInputKW() >= elecDesign.getShaftPowerKW(),
        "Electrical input should be >= shaft power");
    assertTrue(elecDesign.getMotor().getRatedPowerKW() > 0, "Motor should be sized");

    String json = elecDesign.toJson();
    assertNotNull(json);
  }

  @Test
  void testCompressorWithVFD() {
    Compressor compressor = new Compressor("VFDComp", testStream);
    compressor.setOutletPressure(30.0);
    compressor.run();

    CompressorElectricalDesign elecDesign = compressor.getElectricalDesign();
    elecDesign.setUseVFD(true);
    elecDesign.calcDesign();

    assertNotNull(elecDesign.getVfd(), "VFD should be created when useVFD is true");
    assertTrue(elecDesign.getVfd().getRatedPowerKW() > 0, "VFD should be sized");
  }

  @Test
  void testPumpElectricalDesign() {
    Pump pump = new Pump("TestPump", testStream);
    pump.setOutletPressure(50.0);
    pump.run();

    assertNotNull(pump.getElectricalDesign(), "Pump should have electrical design");
  }

  @Test
  void testLoadItem() {
    LoadItem item = new LoadItem("P-100", "Feed Pump", 75.0);
    item.setDemandFactor(0.9);
    item.setDiversityFactor(0.8);
    item.setPowerFactor(0.85);

    double maxDemand = item.getMaxDemandKW();
    assertEquals(75.0 * 0.9 * 0.8, maxDemand, 0.001, "Max demand should apply factors");

    item.setSpare(true);
    assertEquals(0.0, item.getMaxDemandKW(), "Spare items should have zero demand");
  }

  @Test
  void testElectricalLoadList() {
    ElectricalLoadList loadList = new ElectricalLoadList("Test Project");

    LoadItem item1 = new LoadItem("C-100", "Compressor", 500.0);
    item1.setAbsorbedPowerKW(450.0);
    item1.setApparentPowerKVA(530.0);
    item1.setPowerFactor(0.85);

    LoadItem item2 = new LoadItem("P-100", "Pump", 75.0);
    item2.setAbsorbedPowerKW(60.0);
    item2.setApparentPowerKVA(70.6);
    item2.setPowerFactor(0.85);

    loadList.addLoadItem(item1);
    loadList.addLoadItem(item2);
    loadList.calculateSummary();

    assertEquals(2, loadList.getLoadCount());
    assertTrue(loadList.getTotalConnectedLoadKW() > 0);
    assertTrue(loadList.getMaximumDemandKW() > 0);
    assertTrue(loadList.getRequiredTransformerKVA() > 0);

    String json = loadList.toJson();
    assertNotNull(json);
    assertTrue(json.contains("maximumDemandKW"));
  }

  @Test
  void testProcessSystemElectricalIntegration() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    // Run electrical designs
    process.runAllElectricalDesigns();

    // Get load list
    ElectricalLoadList loadList = process.getElectricalLoadList();
    assertNotNull(loadList);
    assertTrue(loadList.getLoadCount() > 0, "Should have at least one load item");
    assertTrue(loadList.getMaximumDemandKW() > 0, "Max demand should be positive");
  }

  @Test
  void testElectricalDesignResponse() {
    Compressor compressor = new Compressor("RespComp", testStream);
    compressor.setOutletPressure(30.0);
    compressor.run();

    ElectricalDesign design = compressor.getElectricalDesign();
    design.calcDesign();

    ElectricalDesignResponse response = new ElectricalDesignResponse(design);
    String json = response.toJson();
    assertNotNull(json);
    assertTrue(json.contains("equipmentName"));
    assertTrue(json.contains("motorData"));
  }

  @Test
  void testSeparatorElectricalDesign() {
    Separator separator = new Separator("TestSep", testStream);
    separator.run();

    SeparatorElectricalDesign elecDesign =
        (SeparatorElectricalDesign) separator.getElectricalDesign();
    assertNotNull(elecDesign, "Separator should have electrical design");

    elecDesign.calcDesign();

    // Separator has no shaft power — only auxiliary loads
    assertEquals(0.0, elecDesign.getShaftPowerKW(), 0.001);
    assertTrue(elecDesign.getTotalAuxiliaryKW() > 0, "Separator should have auxiliary loads");
    assertTrue(elecDesign.getElectricalInputKW() > 0,
        "Electrical input should reflect auxiliary loads");
    assertEquals(400.0, elecDesign.getRatedVoltageV(), 0.01, "Separator should use 400V");
  }

  @Test
  void testSeparatorElectricalDesignWithHeatTracing() {
    Separator separator = new Separator("HeatTracedSep", testStream);
    separator.run();

    SeparatorElectricalDesign elecDesign =
        (SeparatorElectricalDesign) separator.getElectricalDesign();
    elecDesign.setHasHeatTracing(true);
    elecDesign.setHeatTracingKW(15.0);
    elecDesign.calcDesign();

    double withoutTracing =
        elecDesign.getNumberOfControlValves() * elecDesign.getControlValvePowerKW()
            + elecDesign.getInstrumentationKW() + elecDesign.getLightingKW();
    assertTrue(elecDesign.getTotalAuxiliaryKW() > withoutTracing,
        "Heat tracing should add to auxiliary load");
  }

  @Test
  void testHeaterElectricalDesign() {
    Heater heater = new Heater("TestHeater", testStream);
    heater.setOutTemperature(273.15 + 80.0);
    heater.run();

    HeatExchangerElectricalDesign elecDesign =
        (HeatExchangerElectricalDesign) heater.getElectricalDesign();
    assertNotNull(elecDesign, "Heater should have electrical design");
    assertEquals(HeatExchangerElectricalDesign.HeatExchangerType.ELECTRIC_HEATER,
        elecDesign.getHeatExchangerType(), "Heater should be detected as electric heater");

    elecDesign.calcDesign();

    assertTrue(elecDesign.getShaftPowerKW() > 0, "Electric heater should have power from duty");
    assertTrue(elecDesign.getElectricalInputKW() > 0, "Electrical input should be positive");
  }

  @Test
  void testCoolerElectricalDesign() {
    Cooler cooler = new Cooler("TestCooler", testStream);
    cooler.setOutTemperature(273.15 + 10.0);
    cooler.run();

    HeatExchangerElectricalDesign elecDesign =
        (HeatExchangerElectricalDesign) cooler.getElectricalDesign();
    assertNotNull(elecDesign, "Cooler should have electrical design");
    assertEquals(HeatExchangerElectricalDesign.HeatExchangerType.AIR_COOLER,
        elecDesign.getHeatExchangerType(), "Cooler should be detected as air cooler");

    elecDesign.calcDesign();

    // Air cooler fan power should be a fraction of thermal duty
    assertTrue(elecDesign.getShaftPowerKW() > 0, "Fan power should be positive");
    assertTrue(elecDesign.getElectricalInputKW() > 0, "Electrical input should be positive");
  }

  @Test
  void testCoolerShellAndTubeType() {
    Cooler cooler = new Cooler("S&TCooler", testStream);
    cooler.setOutTemperature(273.15 + 10.0);
    cooler.run();

    HeatExchangerElectricalDesign elecDesign =
        (HeatExchangerElectricalDesign) cooler.getElectricalDesign();
    elecDesign.setHeatExchangerType(HeatExchangerElectricalDesign.HeatExchangerType.SHELL_AND_TUBE);
    elecDesign.calcDesign();

    // Shell-and-tube has only auxiliary loads (instrumentation, CW pump)
    assertEquals(0.0, elecDesign.getShaftPowerKW(), 0.001);
  }

  @Test
  void testPipelineElectricalDesign() {
    AdiabaticPipe pipe = new AdiabaticPipe("TestPipe", testStream);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.3);
    pipe.run();

    PipelineElectricalDesign elecDesign = (PipelineElectricalDesign) pipe.getElectricalDesign();
    assertNotNull(elecDesign, "Pipeline should have electrical design");

    // Without heat tracing or CP, only instrumentation
    elecDesign.calcDesign();
    assertEquals(0.0, elecDesign.getShaftPowerKW(), 0.001);
    assertTrue(elecDesign.getTotalAuxiliaryKW() > 0, "Should have at least instrumentation load");
  }

  @Test
  void testPipelineWithHeatTracing() {
    AdiabaticPipe pipe = new AdiabaticPipe("TracedPipe", testStream);
    pipe.setLength(2000.0);
    pipe.setDiameter(0.3);
    pipe.run();

    PipelineElectricalDesign elecDesign = (PipelineElectricalDesign) pipe.getElectricalDesign();
    elecDesign.setHasHeatTracing(true);
    elecDesign.setHeatTracingWPerM(30.0);
    elecDesign.setHasCathodicProtection(true);
    elecDesign.setCathodicProtectionKW(3.0);
    elecDesign.calcDesign();

    // Heat tracing: 30 W/m * 2000 m = 60 kW
    double expectedTracing = 30.0 * 2000.0 / 1000.0;
    assertTrue(elecDesign.getTotalAuxiliaryKW() >= expectedTracing,
        "Heat tracing should contribute 60 kW");
    assertTrue(elecDesign.getElectricalInputKW() > 0,
        "Electrical input should include heat tracing");
  }

  @Test
  void testSystemElectricalDesign() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(50.0);

    Separator sep = new Separator("sep", comp.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.add(sep);
    process.run();

    SystemElectricalDesign sysDesign = process.getSystemElectricalDesign();
    assertNotNull(sysDesign, "System electrical design should not be null");
    assertTrue(sysDesign.getTotalProcessLoadKW() > 0, "Total process load should be positive");
    assertTrue(sysDesign.getTotalPlantLoadKW() > sysDesign.getTotalProcessLoadKW(),
        "Plant load should include utility and UPS");
    assertTrue(sysDesign.getMainTransformerKVA() > 0, "Main transformer should be sized");
    assertTrue(sysDesign.getEmergencyGeneratorKVA() > 0, "Emergency generator should be sized");
    assertNotNull(sysDesign.getLoadList(), "Load list should be populated");
  }

  @Test
  void testProcessSystemWithAllEquipmentTypes() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Separator sep = new Separator("inlet sep", feed);

    Compressor comp = new Compressor("comp", sep.getGasOutStream());
    comp.setOutletPressure(50.0);

    Cooler cooler = new Cooler("aftercooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(comp);
    process.add(cooler);
    process.run();

    // Run all electrical designs and get load list
    process.runAllElectricalDesigns();
    ElectricalLoadList loadList = process.getElectricalLoadList();
    assertNotNull(loadList);
    // Compressor should contribute a load; separator and cooler may also contribute
    assertTrue(loadList.getLoadCount() >= 1, "Should have at least one load from compressor");
    assertTrue(loadList.getMaximumDemandKW() > 0, "Max demand should be positive");
  }
}
