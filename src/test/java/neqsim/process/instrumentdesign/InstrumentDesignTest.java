package neqsim.process.instrumentdesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.instrumentdesign.compressor.CompressorInstrumentDesign;
import neqsim.process.instrumentdesign.heatexchanger.HeatExchangerInstrumentDesign;
import neqsim.process.instrumentdesign.pipeline.PipelineInstrumentDesign;
import neqsim.process.instrumentdesign.separator.SeparatorInstrumentDesign;
import neqsim.process.instrumentdesign.system.SystemInstrumentDesign;
import neqsim.process.instrumentdesign.valve.ValveInstrumentDesign;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the instrument design package.
 *
 * @author Even Solbraa
 */
public class InstrumentDesignTest {

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
  void testInstrumentSpecificationAnalog() {
    InstrumentSpecification spec =
        new InstrumentSpecification("PT", "Inlet Pressure", 0.0, 100.0, "barg", "AI");
    assertEquals("PT", spec.getIsaSymbol());
    assertEquals("Inlet Pressure", spec.getService());
    assertEquals(0.0, spec.getRangeMin(), 0.001);
    assertEquals(100.0, spec.getRangeMax(), 0.001);
    assertEquals("barg", spec.getRangeUnit());
    assertEquals("AI", spec.getIoType());
    assertTrue(spec.getEstimatedCostUSD() > 0, "Should have default cost estimate");
  }

  @Test
  void testInstrumentSpecificationDiscrete() {
    InstrumentSpecification spec =
        new InstrumentSpecification("PSH", "High Pressure Switch", "DI", 2);
    assertEquals("PSH", spec.getIsaSymbol());
    assertEquals("DI", spec.getIoType());
    assertTrue(spec.isSafetyRelated());
    assertEquals(2, spec.getSilRating());
  }

  @Test
  void testInstrumentList() {
    InstrumentList list = new InstrumentList("TestEquip");
    list.add(new InstrumentSpecification("PT", "Pressure", 0.0, 100.0, "barg", "AI"));
    list.add(new InstrumentSpecification("TT", "Temperature", 0.0, 200.0, "degC", "AI"));
    list.add(new InstrumentSpecification("PSH", "High Pressure", "DI", 2));
    list.add(new InstrumentSpecification("ZC", "Valve Positioner", "AO", 0));

    assertEquals(4, list.getAll().size());
    assertEquals(2, list.getAnalogInputCount());
    assertEquals(1, list.getAnalogOutputCount());
    assertEquals(1, list.getDigitalInputCount());
    assertEquals(0, list.getDigitalOutputCount());
    assertEquals(4, list.getTotalIOCount());
    assertEquals(1, list.getSafetyInstrumentCount());
    assertTrue(list.getTotalCostUSD() > 0);
  }

  @Test
  void testInstrumentDesignBase() {
    InstrumentDesign design = new InstrumentDesign(testStream);
    design.calcDesign();

    assertNotNull(design.getInstrumentList());
    assertNotNull(design.getHazardousAreaZone());
    assertNotNull(design.getProtectionConcept());

    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.contains("equipmentName"));
  }

  @Test
  void testSeparatorInstrumentDesign() {
    Separator separator = new Separator("TestSep", testStream);
    separator.run();

    SeparatorInstrumentDesign instrDesign =
        (SeparatorInstrumentDesign) separator.getInstrumentDesign();
    assertNotNull(instrDesign, "Separator should have instrument design");

    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 8,
        "Separator should have at least 8 instruments: " + list.getAll().size());
    assertTrue(list.getAnalogInputCount() >= 4,
        "Should have multiple analog inputs: " + list.getAnalogInputCount());
    assertTrue(list.getSafetyInstrumentCount() >= 2,
        "Should have safety instruments: " + list.getSafetyInstrumentCount());

    String json = instrDesign.toJson();
    assertNotNull(json);
    assertTrue(json.contains("instrumentIndex"));
  }

  @Test
  void testCompressorInstrumentDesign() {
    Compressor compressor = new Compressor("TestComp", testStream);
    compressor.setOutletPressure(50.0);
    compressor.run();

    CompressorInstrumentDesign instrDesign =
        (CompressorInstrumentDesign) compressor.getInstrumentDesign();
    assertNotNull(instrDesign, "Compressor should have instrument design");

    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 10,
        "Compressor should have many instruments: " + list.getAll().size());
    assertTrue(list.getSafetyInstrumentCount() >= 3,
        "Should have multiple safety instruments: " + list.getSafetyInstrumentCount());

    String json = instrDesign.toJson();
    assertNotNull(json);
  }

  @Test
  void testHeaterInstrumentDesign() {
    Heater heater = new Heater("TestHeater", testStream);
    heater.setOutTemperature(273.15 + 80.0);
    heater.run();

    HeatExchangerInstrumentDesign instrDesign =
        (HeatExchangerInstrumentDesign) heater.getInstrumentDesign();
    assertNotNull(instrDesign, "Heater should have instrument design");

    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 5,
        "Heater should have instruments: " + list.getAll().size());

    String json = instrDesign.toJson();
    assertNotNull(json);
  }

  @Test
  void testCoolerInstrumentDesign() {
    Cooler cooler = new Cooler("TestCooler", testStream);
    cooler.setOutTemperature(273.15 + 10.0);
    cooler.run();

    HeatExchangerInstrumentDesign instrDesign =
        (HeatExchangerInstrumentDesign) cooler.getInstrumentDesign();
    assertNotNull(instrDesign, "Cooler should have instrument design");

    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 5,
        "Cooler should have instruments: " + list.getAll().size());
  }

  @Test
  void testPipelineInstrumentDesign() {
    AdiabaticPipe pipe = new AdiabaticPipe("TestPipe", testStream);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.3);
    pipe.run();

    PipelineInstrumentDesign instrDesign = (PipelineInstrumentDesign) pipe.getInstrumentDesign();
    assertNotNull(instrDesign, "Pipeline should have instrument design");

    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 5,
        "Pipeline should have instruments: " + list.getAll().size());
    assertTrue(list.getDigitalInputCount() >= 2,
        "Should have pig detection DI: " + list.getDigitalInputCount());
  }

  @Test
  void testPipelineWithoutPigDetection() {
    SystemInterface pipeFluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    pipeFluid.addComponent("methane", 0.85);
    pipeFluid.addComponent("ethane", 0.10);
    pipeFluid.addComponent("propane", 0.05);
    pipeFluid.setMixingRule("classic");

    Stream pipeStream = new Stream("pipeStream", pipeFluid);
    pipeStream.setFlowRate(10000.0, "kg/hr");
    pipeStream.run();

    AdiabaticPipe pipe = new AdiabaticPipe("NoPigPipe", pipeStream);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.run();

    PipelineInstrumentDesign instrDesign = (PipelineInstrumentDesign) pipe.getInstrumentDesign();
    instrDesign.setIncludePigDetection(false);
    instrDesign.setIncludeLeakDetection(false);
    instrDesign.setIncludeSafetyInstruments(false);
    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertEquals(0, list.getDigitalInputCount(),
        "No pig/leak/safety detection should yield zero DI: " + list.getDigitalInputCount());
  }

  @Test
  void testValveInstrumentDesign() {
    ThrottlingValve valve = new ThrottlingValve("TestValve", testStream);
    valve.setOutletPressure(5.0);
    valve.run();

    ValveInstrumentDesign instrDesign = new ValveInstrumentDesign(valve);
    instrDesign.calcDesign();

    InstrumentList list = instrDesign.getInstrumentList();
    assertNotNull(list);
    assertTrue(list.getAll().size() >= 2,
        "Valve should have at least ZT and ZC: " + list.getAll().size());
    assertTrue(list.getAnalogInputCount() >= 1, "Should have ZT analog input");
    assertTrue(list.getAnalogOutputCount() >= 1, "Should have ZC analog output");
  }

  @Test
  void testInstrumentDesignResponse() {
    Separator separator = new Separator("RespSep", testStream);
    separator.run();

    InstrumentDesign design = separator.getInstrumentDesign();
    design.calcDesign();

    InstrumentDesignResponse response = new InstrumentDesignResponse(design);
    String json = response.toJson();
    assertNotNull(json);
    assertTrue(json.contains("equipmentName"));
    assertTrue(json.contains("ioSummary"));
    assertTrue(json.contains("instrumentIndex"));
  }

  @Test
  void testSystemInstrumentDesign() {
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

    SystemInstrumentDesign sysDesign = process.getSystemInstrumentDesign();
    assertNotNull(sysDesign, "System instrument design should not be null");
    assertTrue(sysDesign.getTotalAI() > 0,
        "Total AI should be positive: " + sysDesign.getTotalAI());
    assertTrue(sysDesign.getTotalIO() > 0,
        "Total I/O should be positive: " + sysDesign.getTotalIO());
    assertTrue(sysDesign.getDcsCabinets() > 0,
        "DCS cabinets should be positive: " + sysDesign.getDcsCabinets());
    assertTrue(sysDesign.getTotalInstrumentCostUSD() > 0,
        "Total cost should be positive: " + sysDesign.getTotalInstrumentCostUSD());

    String json = sysDesign.toJson();
    assertNotNull(json);
    assertTrue(json.contains("totalIO"));
    assertTrue(json.contains("dcsCabinets"));
  }

  @Test
  void testSystemWithAllEquipmentTypes() {
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

    SystemInstrumentDesign sysDesign = process.getSystemInstrumentDesign();
    assertNotNull(sysDesign);

    // Should aggregate instruments from separator, compressor, and cooler
    assertTrue(sysDesign.getTotalIO() > 10,
        "Multiple equipment should yield many I/O points: " + sysDesign.getTotalIO());
    assertTrue(sysDesign.getTotalSafetyIO() > 0,
        "Should have safety I/O: " + sysDesign.getTotalSafetyIO());
  }
}
