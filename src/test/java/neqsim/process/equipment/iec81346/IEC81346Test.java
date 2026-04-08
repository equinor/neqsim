package neqsim.process.equipment.iec81346;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive tests for IEC 81346 reference designation support.
 *
 * <p>
 * Tests cover:
 * </p>
 * <ul>
 * <li>Letter code enum and equipment mapping</li>
 * <li>Reference designation data class</li>
 * <li>Automatic generation for single ProcessSystem</li>
 * <li>Automatic generation for multi-area ProcessModel</li>
 * <li>DEXPI export with IEC 81346 attributes</li>
 * <li>ProcessAutomation address resolution by reference designation</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class IEC81346Test {

  private SystemInterface gasFluid;

  @BeforeEach
  void setUp() {
    gasFluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.10);
    gasFluid.addComponent("propane", 0.05);
    gasFluid.setMixingRule("classic");
  }

  // ============================================================
  // IEC81346LetterCode Tests
  // ============================================================

  @Test
  void testLetterCodeDescription() {
    assertEquals("Converting, separating, changing form", IEC81346LetterCode.B.getDescription());
    assertEquals("Processing, compressing, driving", IEC81346LetterCode.K.getDescription());
    assertEquals("Controlling flow, movement", IEC81346LetterCode.Q.getDescription());
    assertEquals("Sensing, detecting, measuring", IEC81346LetterCode.S.getDescription());
    assertEquals("Transporting, moving", IEC81346LetterCode.T.getDescription());
    assertEquals("Connecting, branching", IEC81346LetterCode.X.getDescription());
    assertEquals("Storing, presenting information", IEC81346LetterCode.C.getDescription());
    assertEquals("Generating, providing energy", IEC81346LetterCode.G.getDescription());
  }

  @Test
  void testLetterCodeFromEquipmentEnum() {
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Separator));
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.HeatExchanger));
    assertEquals(IEC81346LetterCode.B, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Cooler));
    assertEquals(IEC81346LetterCode.B, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Heater));
    assertEquals(IEC81346LetterCode.B, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Reactor));
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.DistillationColumn));

    assertEquals(IEC81346LetterCode.K,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Compressor));
    assertEquals(IEC81346LetterCode.K, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Pump));
    assertEquals(IEC81346LetterCode.K,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Expander));

    assertEquals(IEC81346LetterCode.Q,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.ThrottlingValve));

    assertEquals(IEC81346LetterCode.T, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Stream));
    assertEquals(IEC81346LetterCode.T,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.AdiabaticPipe));

    assertEquals(IEC81346LetterCode.X, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Mixer));
    assertEquals(IEC81346LetterCode.X,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Splitter));

    assertEquals(IEC81346LetterCode.C, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Tank));

    assertEquals(IEC81346LetterCode.G,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.FuelCell));
    assertEquals(IEC81346LetterCode.G,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.WindTurbine));

    assertEquals(IEC81346LetterCode.N,
        IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Adjuster));
    assertEquals(IEC81346LetterCode.N, IEC81346LetterCode.fromEquipmentEnum(EquipmentEnum.Recycle));
  }

  @Test
  void testLetterCodeFromEquipmentEnumNullReturnsA() {
    assertEquals(IEC81346LetterCode.A, IEC81346LetterCode.fromEquipmentEnum(null));
  }

  @Test
  void testLetterCodeFromEquipmentInstance() {
    Stream stream = new Stream("feed", gasFluid);
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipment(new Separator("sep", stream)));
    assertEquals(IEC81346LetterCode.K,
        IEC81346LetterCode.fromEquipment(new Compressor("comp", stream)));
    assertEquals(IEC81346LetterCode.Q,
        IEC81346LetterCode.fromEquipment(new ThrottlingValve("valve", stream)));
    assertEquals(IEC81346LetterCode.X, IEC81346LetterCode.fromEquipment(new Mixer("mixer")));
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipment(new Heater("heater", stream)));
    assertEquals(IEC81346LetterCode.B,
        IEC81346LetterCode.fromEquipment(new Cooler("cooler", stream)));
    assertEquals(IEC81346LetterCode.A, IEC81346LetterCode.fromEquipment(null));
  }

  @Test
  void testEquipmentMappingIsUnmodifiable() {
    Map<EquipmentEnum, IEC81346LetterCode> map = IEC81346LetterCode.getEquipmentMapping();
    assertNotNull(map);
    assertFalse(map.isEmpty());
  }

  // ============================================================
  // ReferenceDesignation Tests
  // ============================================================

  @Test
  void testReferenceDesignationConstruction() {
    ReferenceDesignation refDes =
        new ReferenceDesignation("A1.K1", "B1", "P1.M1", IEC81346LetterCode.B, 1);
    assertEquals("A1.K1", refDes.getFunctionDesignation());
    assertEquals("B1", refDes.getProductDesignation());
    assertEquals("P1.M1", refDes.getLocationDesignation());
    assertEquals(IEC81346LetterCode.B, refDes.getLetterCode());
    assertEquals(1, refDes.getSequenceNumber());
  }

  @Test
  void testReferenceDesignationFormatting() {
    ReferenceDesignation refDes =
        new ReferenceDesignation("A1.K1", "B1", "P1.M1", IEC81346LetterCode.B, 1);
    assertEquals("=A1.K1", refDes.getFormattedFunctionDesignation());
    assertEquals("-B1", refDes.getFormattedProductDesignation());
    assertEquals("+P1.M1", refDes.getFormattedLocationDesignation());
    assertEquals("=A1.K1-B1+P1.M1", refDes.toReferenceDesignationString());
  }

  @Test
  void testReferenceDesignationEmptyAspects() {
    ReferenceDesignation refDes = new ReferenceDesignation();
    assertEquals("", refDes.getFormattedFunctionDesignation());
    assertEquals("", refDes.getFormattedProductDesignation());
    assertEquals("", refDes.getFormattedLocationDesignation());
    assertEquals("", refDes.toReferenceDesignationString());
    assertFalse(refDes.isSet());
  }

  @Test
  void testReferenceDesignationPartialAspects() {
    ReferenceDesignation refDes = new ReferenceDesignation();
    refDes.setProductDesignation("K2");
    assertEquals("", refDes.getFormattedFunctionDesignation());
    assertEquals("-K2", refDes.getFormattedProductDesignation());
    assertEquals("", refDes.getFormattedLocationDesignation());
    assertEquals("-K2", refDes.toReferenceDesignationString());
    assertTrue(refDes.isSet());
  }

  @Test
  void testReferenceDesignationProductCode() {
    ReferenceDesignation refDes =
        new ReferenceDesignation("A1", "B3", "P1", IEC81346LetterCode.B, 3);
    assertEquals("B3", refDes.getProductCode());
  }

  @Test
  void testReferenceDesignationEquality() {
    ReferenceDesignation rd1 = new ReferenceDesignation("A1", "B1", "P1", IEC81346LetterCode.B, 1);
    ReferenceDesignation rd2 = new ReferenceDesignation("A1", "B1", "P1", IEC81346LetterCode.B, 1);
    assertEquals(rd1, rd2);
    assertEquals(rd1.hashCode(), rd2.hashCode());
  }

  @Test
  void testReferenceDesignationNullHandling() {
    ReferenceDesignation refDes = new ReferenceDesignation(null, null, null, null, 0);
    assertEquals("", refDes.getFunctionDesignation());
    assertEquals("", refDes.getProductDesignation());
    assertEquals("", refDes.getLocationDesignation());
    assertEquals(IEC81346LetterCode.A, refDes.getLetterCode());
  }

  // ============================================================
  // ReferenceDesignationGenerator — Single ProcessSystem
  // ============================================================

  @Test
  void testGeneratorSingleSystem() {
    ProcessSystem process = new ProcessSystem("Test Process");

    Stream feed = new Stream("Feed Gas", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);
    process.add(compressor);

    Cooler cooler = new Cooler("Aftercooler", compressor.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);

    ThrottlingValve valve = new ThrottlingValve("LP Valve", separator.getLiquidOutStream());
    valve.setOutletPressure(10.0);
    process.add(valve);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("P1.M1");
    gen.setIncludeStreams(false);
    gen.generate();

    assertTrue(gen.isGenerated());
    // 4 equipment (separator, compressor, cooler, valve) — streams excluded
    assertEquals(4, gen.getDesignationCount());

    // Verify separator got B classification
    ReferenceDesignation sepRefDes = separator.getReferenceDesignation();
    assertNotNull(sepRefDes);
    assertTrue(sepRefDes.isSet());
    assertEquals(IEC81346LetterCode.B, sepRefDes.getLetterCode());
    assertEquals("=A1-B1+P1.M1", sepRefDes.toReferenceDesignationString());

    // Verify compressor got K classification
    ReferenceDesignation compRefDes = compressor.getReferenceDesignation();
    assertEquals(IEC81346LetterCode.K, compRefDes.getLetterCode());
    assertEquals("=A1-K1+P1.M1", compRefDes.toReferenceDesignationString());

    // Verify cooler got B classification (heat exchanger)
    ReferenceDesignation coolerRefDes = cooler.getReferenceDesignation();
    assertEquals(IEC81346LetterCode.B, coolerRefDes.getLetterCode());
    assertEquals("=A1-B2+P1.M1", coolerRefDes.toReferenceDesignationString());

    // Verify valve got Q classification
    ReferenceDesignation valveRefDes = valve.getReferenceDesignation();
    assertEquals(IEC81346LetterCode.Q, valveRefDes.getLetterCode());
    assertEquals("=A1-Q1+P1.M1", valveRefDes.toReferenceDesignationString());
  }

  @Test
  void testGeneratorIncludesStreams() {
    ProcessSystem process = new ProcessSystem("Test Process");

    Stream feed = new Stream("Feed Gas", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Sep", feed);
    process.add(separator);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setIncludeStreams(true);
    gen.generate();

    // 2 items: 1 stream + 1 separator
    assertEquals(2, gen.getDesignationCount());
  }

  @Test
  void testGeneratorWithoutLocationPrefix() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("Sep1", feed);
    process.add(sep);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("");
    gen.generate();

    assertEquals("=A1-B1", sep.getReferenceDesignationString());
  }

  @Test
  void testGeneratorLookupMethods() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("HP Sep", feed);
    process.add(sep);

    Compressor comp = new Compressor("Comp-1", sep.getGasOutStream());
    comp.setOutletPressure(100.0);
    process.add(comp);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("P1");
    gen.generate();

    // Find by name
    ReferenceDesignationGenerator.DesignationEntry sepEntry = gen.findByName("HP Sep");
    assertNotNull(sepEntry);
    assertEquals("HP Sep", sepEntry.getEquipmentName());
    assertEquals("Separator", sepEntry.getEquipmentType());
    assertEquals(IEC81346LetterCode.B, sepEntry.getLetterCode());

    // Find by designation
    ReferenceDesignationGenerator.DesignationEntry compEntry = gen.findByDesignation("=A1-K1+P1");
    assertNotNull(compEntry);
    assertEquals("Comp-1", compEntry.getEquipmentName());

    // Find by letter code
    List<ReferenceDesignationGenerator.DesignationEntry> bEntries =
        gen.findByLetterCode(IEC81346LetterCode.B);
    assertEquals(1, bEntries.size());

    // Name-to-designation map
    Map<String, String> nameMap = gen.getNameToDesignationMap();
    assertEquals("=A1-B1+P1", nameMap.get("HP Sep"));
    assertEquals("=A1-K1+P1", nameMap.get("Comp-1"));

    // Designation-to-name map
    Map<String, String> desMap = gen.getDesignationToNameMap();
    assertEquals("HP Sep", desMap.get("=A1-B1+P1"));

    // Letter code summary
    Map<IEC81346LetterCode, Integer> summary = gen.getLetterCodeSummary();
    assertEquals(Integer.valueOf(1), summary.get(IEC81346LetterCode.B));
    assertEquals(Integer.valueOf(1), summary.get(IEC81346LetterCode.K));
  }

  // ============================================================
  // ReferenceDesignationGenerator — Multi-area ProcessModel
  // ============================================================

  @Test
  void testGeneratorMultiAreaModel() {
    // Area 1: Separation
    ProcessSystem separation = new ProcessSystem("Separation");
    Stream feed1 = new Stream("Gas Feed", gasFluid);
    feed1.setFlowRate(100.0, "kg/hr");
    separation.add(feed1);
    Separator sep = new Separator("HP Sep", feed1);
    separation.add(sep);
    ThrottlingValve valve = new ThrottlingValve("LP Valve", sep.getLiquidOutStream());
    valve.setOutletPressure(10.0);
    separation.add(valve);

    // Area 2: Compression
    ProcessSystem compression = new ProcessSystem("Compression");
    Compressor comp1 = new Compressor("1st Stage", sep.getGasOutStream());
    comp1.setOutletPressure(100.0);
    compression.add(comp1);
    Cooler cooler = new Cooler("Intercooler", comp1.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);
    compression.add(cooler);
    Compressor comp2 = new Compressor("2nd Stage", cooler.getOutletStream());
    comp2.setOutletPressure(200.0);
    compression.add(comp2);

    ProcessModel plant = new ProcessModel();
    plant.add("Separation", separation);
    plant.add("Compression", compression);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(plant);
    gen.setLocationPrefix("P1");
    gen.generate();

    assertTrue(gen.isGenerated());

    // Separation area: HP Sep (B1), LP Valve (Q1) — streams excluded
    assertEquals("=A1-B1+P1", sep.getReferenceDesignationString());
    assertEquals("=A1-Q1+P1", valve.getReferenceDesignationString());

    // Compression area: 1st Stage (K1), Intercooler (B1), 2nd Stage (K2)
    assertEquals("=A2-K1+P1", comp1.getReferenceDesignationString());
    assertEquals("=A2-B1+P1", cooler.getReferenceDesignationString());
    assertEquals("=A2-K2+P1", comp2.getReferenceDesignationString());

    // Total entries: 2 (separation) + 3 (compression) = 5
    assertEquals(5, gen.getDesignationCount());
  }

  // ============================================================
  // ProcessEquipmentInterface Reference Designation Methods
  // ============================================================

  @Test
  void testEquipmentReferenceDesignationDefaultEmpty() {
    Stream feed = new Stream("Test Stream", gasFluid);
    ReferenceDesignation refDes = feed.getReferenceDesignation();
    assertNotNull(refDes);
    assertFalse(refDes.isSet());
    assertEquals("", feed.getReferenceDesignationString());
  }

  @Test
  void testEquipmentSetReferenceDesignation() {
    Separator sep = new Separator("HP Sep");
    ReferenceDesignation refDes =
        new ReferenceDesignation("A1", "B1", "P1", IEC81346LetterCode.B, 1);
    sep.setReferenceDesignation(refDes);

    assertEquals(refDes, sep.getReferenceDesignation());
    assertEquals("=A1-B1+P1", sep.getReferenceDesignationString());
  }

  @Test
  void testEquipmentSetNullReferenceDesignation() {
    Separator sep = new Separator("HP Sep");
    sep.setReferenceDesignation(null);
    assertNotNull(sep.getReferenceDesignation());
    assertFalse(sep.getReferenceDesignation().isSet());
  }

  // ============================================================
  // ProcessAutomation — IEC 81346 Address Resolution
  // ============================================================

  @Test
  void testAutomationResolvesReferenceDesignation() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed Gas", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("HP Sep", feed);
    process.add(sep);

    Compressor comp = new Compressor("Comp-1", sep.getGasOutStream());
    comp.setOutletPressure(100.0);
    process.add(comp);

    // Generate designations
    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("P1");
    gen.generate();

    // Run the process first to have values available
    process.run();

    // Access equipment by reference designation through Automation API
    ProcessAutomation auto = process.getAutomation();

    // The separator should have designation =A1-B1+P1
    assertEquals("=A1-B1+P1", sep.getReferenceDesignationString());

    // Resolve by reference designation
    String equipType = auto.getEquipmentType("=A1-B1+P1");
    assertEquals("Separator", equipType);

    String compType = auto.getEquipmentType("=A1-K1+P1");
    assertEquals("Compressor", compType);
  }

  // ============================================================
  // JSON Export
  // ============================================================

  @Test
  void testJsonExport() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("HP Sep", feed);
    process.add(sep);

    Compressor comp = new Compressor("Comp", sep.getGasOutStream());
    comp.setOutletPressure(100.0);
    process.add(comp);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("P1");
    gen.generate();

    String json = gen.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());

    // Verify JSON contains expected content
    assertTrue(json.contains("IEC 81346"));
    assertTrue(json.contains("HP Sep"));
    assertTrue(json.contains("Comp"));
    assertTrue(json.contains("\"letterCode\": \"B\""));
    assertTrue(json.contains("\"letterCode\": \"K\""));
    assertTrue(json.contains("Converting, separating, changing form"));
    assertTrue(json.contains("Processing, compressing, driving"));
  }

  // ============================================================
  // DEXPI Export with IEC 81346
  // ============================================================

  @Test
  void testDexpiExportIncludesIEC81346Attributes() throws Exception {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed Gas", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("HP Sep", feed);
    process.add(sep);

    process.run();

    // Generate IEC 81346 designations
    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("P1");
    gen.generate();

    // Export to DEXPI XML
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    neqsim.process.processmodel.dexpi.DexpiXmlWriter.write(process, baos);
    String xml = baos.toString("UTF-8");

    assertNotNull(xml);
    assertFalse(xml.isEmpty());

    // Verify IEC 81346 attributes are present in the XML
    assertTrue(xml.contains("IEC81346ReferenceDesignation"),
        "DEXPI XML should contain IEC81346ReferenceDesignation attribute");
    assertTrue(xml.contains("=A1-B1+P1"),
        "DEXPI XML should contain the actual reference designation value");
    assertTrue(xml.contains("IEC81346LetterCode"),
        "DEXPI XML should contain IEC81346LetterCode attribute");
  }

  // ============================================================
  // Multiple Equipment of Same Type (Sequence Numbering)
  // ============================================================

  @Test
  void testSequenceNumberingForSameType() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    // Three separators in sequence
    Separator sep1 = new Separator("HP Sep", feed);
    process.add(sep1);

    Separator sep2 = new Separator("MP Sep", sep1.getGasOutStream());
    process.add(sep2);

    Separator sep3 = new Separator("LP Sep", sep2.getGasOutStream());
    process.add(sep3);

    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.setLocationPrefix("");
    gen.generate();

    // All three should be B-coded with increasing sequence numbers
    assertEquals("=A1-B1", sep1.getReferenceDesignationString());
    assertEquals("=A1-B2", sep2.getReferenceDesignationString());
    assertEquals("=A1-B3", sep3.getReferenceDesignationString());
  }

  @Test
  void testMixedEquipmentSequencing() {
    ProcessSystem process = new ProcessSystem("Test");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(100.0, "kg/hr");
    process.add(feed);

    Separator sep1 = new Separator("Sep 1", feed);
    process.add(sep1);

    Cooler cool1 = new Cooler("Cooler 1", sep1.getGasOutStream());
    cool1.setOutTemperature(273.15 + 20.0);
    process.add(cool1);

    Separator sep2 = new Separator("Sep 2", cool1.getOutletStream());
    process.add(sep2);

    // sep1 -> B1, cool1 -> B2 (both are B category), sep2 -> B3
    ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
    gen.setFunctionPrefix("A1");
    gen.generate();

    assertEquals("=A1-B1", sep1.getReferenceDesignationString());
    assertEquals("=A1-B2", cool1.getReferenceDesignationString());
    assertEquals("=A1-B3", sep2.getReferenceDesignationString());
  }

  // ============================================================
  // ReferenceDesignation toString
  // ============================================================

  @Test
  void testReferenceDesignationToString() {
    ReferenceDesignation refDes =
        new ReferenceDesignation("A1", "B1", "P1", IEC81346LetterCode.B, 1);
    assertEquals("=A1-B1+P1", refDes.toString());

    ReferenceDesignation empty = new ReferenceDesignation();
    assertEquals("(no designation)", empty.toString());
  }

  @Test
  void testDesignationEntryToString() {
    ReferenceDesignationGenerator.DesignationEntry entry =
        new ReferenceDesignationGenerator.DesignationEntry("HP Sep", "Separator", "=A1-B1+P1",
            IEC81346LetterCode.B, 1, "A1");
    String str = entry.toString();
    assertTrue(str.contains("HP Sep"));
    assertTrue(str.contains("=A1-B1+P1"));
    assertTrue(str.contains("B"));
  }
}
