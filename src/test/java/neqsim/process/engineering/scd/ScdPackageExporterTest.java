package neqsim.process.engineering.scd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.processmodel.diagram.ScdDiagramRenderer;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests shared SCD/P&amp;ID/DEXPI identity, native drawing output and revision/engineering-input boundaries.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class ScdPackageExporterTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Executes the documented export and checks real DEXPI object references without changing the process.
   *
   * @throws Exception if package generation fails
   */
  @Test
  void documentedExportSharesNativeIdsAndExecutableBindings() throws Exception {
    Fixture fixture = new Fixture();
    EngineeringProject project = fixture.project;
    PidDesignModel pid = fixture.pid;
    PidSimulationBindings bindings = fixture.bindings;
    EngineeringStandard scdBasis = fixture.standard;
    project.getDesignBasis().addStandard(scdBasis);
    ScdDesignModel scd = new ScdDesignModel(project, pid, bindings, "SCD-20-001", scdBasis);
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-08T00:00:00Z", "NeqSim SCD example", "NeqSim", "workspace")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "SCD-REFERENCE")
        .build();
    Path outputDirectory = temporaryDirectory.resolve("scd-package");
    String requested = System.getProperty("neqsim.scd.exampleDirectory");
    if (requested != null) {
      outputDirectory = Paths.get(requested);
    }
    double pressure = fixture.valve.getOutletStream().getPressure();
    Path output = ScdPackageExporter.export(project, pid, bindings, scd, metadata, outputDirectory);
    assertEquals(pressure, fixture.valve.getOutletStream().getPressure(), 0.0);
    assertTrue(Files.exists(output.resolve("drawings/scd-sheet-001.svg")));
    assertTrue(Files.exists(output.resolve("drawings/scd-sheet-001.layout.json")));
    assertTrue(Files.size(output.resolve("drawings/scd-drawing-set.pdf")) > 1000);
    assertTrue(Files.exists(output.resolve("drawings/pid-drawing-set.pdf")));
    assertTrue(Files.exists(output.resolve("simulation-model.neqsim")));
    assertTrue(Dexpi201XmlWriter.assess(output.resolve("plant.dexpi.xml")).isSchemaAndProfileConformant());
    JsonObject crossReference = JsonParser.parseString(text(output.resolve("scd-dexpi-cross-reference.json")))
        .getAsJsonObject();
    assertEquals(3, crossReference.getAsJsonArray("blocks").size());
    String xml = text(output.resolve("plant.dexpi.xml"));
    for (com.google.gson.JsonElement entry : crossReference.getAsJsonArray("blocks")) {
      JsonObject row = entry.getAsJsonObject();
      assertEquals("NATIVE_OBJECT_MAPPED", row.get("mappingStatus").getAsString());
      assertTrue(xml.contains("id=\"" + row.get("dexpiNativeId").getAsString() + "\""));
    }
    assertEquals(2, crossReference.getAsJsonArray("signals").size());
    assertEquals(2, crossReference.getAsJsonArray("dexpiSignalProjection").size());
    assertTrue(scd.toJson().contains("20-PIC-101"));
    assertTrue(scd.toJson().contains("DECLARED_PROJECT_CONNECTION"));
    assertFalse(crossReference.get("fitnessForConstruction").getAsBoolean());
    assertThrows(IllegalArgumentException.class,
        () -> ScdPackageExporter.export(project, pid, bindings, scd, metadata, output));
  }

  /** Verifies that an edition is explicitly declared and a future draft is never presented as a published basis. */
  @Test
  void requiresDeclaredSupportedEdition() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalArgumentException.class, () -> fixture.scd());
    EngineeringStandard draft = new EngineeringStandard("IEC 63131-1", "ED1", "Draft", "Draft review only");
    fixture.project.getDesignBasis().addStandard(draft);
    assertThrows(IllegalArgumentException.class,
        () -> new ScdDesignModel(fixture.project, fixture.pid, fixture.bindings, "SCD-1", draft));
    EngineeringStandard withdrawn = new EngineeringStandard("IEC PAS 63131", "2017", "System control diagram",
        "Project-selected legacy drawing basis");
    fixture.project.getDesignBasis().addStandard(withdrawn);
    ScdDesignModel legacy = new ScdDesignModel(fixture.project, fixture.pid, fixture.bindings, "SCD-1", withdrawn);
    assertEquals("WITHDRAWN_2024-09-27", legacy.toMap().get("publicationStatus"));
  }

  /** Verifies that new binding registers cannot conceal a changed P&amp;ID from an old SCD. */
  @Test
  void rejectsStalePidAndRevision() {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    ScdDesignModel scd = fixture.scd();
    fixture.pid.getElements().get(0).attribute("scdOutputTerminal", "CHANGED");
    PidSimulationBindings fresh = PidSimulationBindings.from(fixture.project, fixture.pid);
    assertThrows(IllegalStateException.class, () -> scd.assertCurrent(fixture.project, fixture.pid, fresh));
    fixture.project.setRevision("B");
    assertThrows(IllegalStateException.class, () -> scd.assertCurrent(fixture.project, fixture.pid, fresh));
  }

  /**
   * Preserves immutable ownership and Java serialization of the reusable SCD artifact.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void sourceSnapshotIsDetachedAndSerializable() throws Exception {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    ScdDesignModel scd = fixture.scd();
    String before = scd.toJson();
    scd.getBlocks().get(0).attribute("system", "CHANGED");
    scd.getSignals().get(0).put("targetTag", "CHANGED");
    assertEquals(before, scd.toJson());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(scd);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      ScdDesignModel restored = (ScdDesignModel) input.readObject();
      assertEquals(before, restored.toJson());
      restored.assertCurrent(fixture.project, fixture.pid, fixture.bindings);
    }
  }

  /** Tests missing-input HOLDs and rejects dangling functional links and invalid template assignments. */
  @Test
  void retainsMissingInputsAndRejectsInventedSemantics() {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    PidElement source = fixture.pid.getElements().get(0);
    source.attribute("scdOutputTerminal", "").attribute("scdTemplate", "");
    ScdDesignModel incomplete = new ScdDesignModel(fixture.project, fixture.pid,
        PidSimulationBindings.from(fixture.project, fixture.pid), "SCD-1", fixture.standard);
    assertEquals("TERMINALS_OR_TYPE_REQUIRED", incomplete.getSignals().get(0).get("status"));
    assertEquals("HOLD", ScdDesignModel.template(incomplete.getBlocks().get(0)));
    source.attribute("scdTemplate", "CA");
    assertThrows(IllegalArgumentException.class, () -> new ScdDesignModel(fixture.project, fixture.pid,
        PidSimulationBindings.from(fixture.project, fixture.pid), "SCD-1", fixture.standard));
    source.attribute("scdTemplate", "MA").connect("missing-pid-id");
    assertThrows(IllegalArgumentException.class, () -> new ScdDesignModel(fixture.project, fixture.pid,
        PidSimulationBindings.from(fixture.project, fixture.pid), "SCD-1", fixture.standard));
  }

  /** Tests deterministic pagination, feedback and high fan-in without dropping an endpoint. */
  @Test
  void paginatesFeedbackAndFanInAndRejectsOverflow() {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    for (int index = 0; index < 7; index++) {
      fixture.pid.add(new PidElement("extra:" + index, "20-PT-20" + index, PidElementType.MEASUREMENT)
          .equipment(fixture.valve.getName()).connect("pid:pic"));
    }
    fixture.pid.getElements().get(1).connect("pid:pt");
    PidSimulationBindings bindings = PidSimulationBindings.from(fixture.project, fixture.pid);
    ScdDesignModel scd = new ScdDesignModel(fixture.project, fixture.pid, bindings, "SCD-1", fixture.standard);
    ScdDiagramRenderer renderer = new ScdDiagramRenderer(fixture.project, fixture.pid, bindings, scd);
    assertEquals(3, renderer.render().getSvgBySheetId().size());
    assertEquals(renderer.render().getSvgBySheetId(), renderer.render().getSvgBySheetId());
    String sheets = renderer.render().getSvgBySheetId().values().toString();
    for (java.util.Map<String, String> signal : scd.getSignals()) {
      assertTrue(sheets.contains(signal.get("id") + " from "));
      assertTrue(sheets.contains(signal.get("id") + " to "));
    }
    fixture.pid.getElements().get(0).attribute("system", new String(new char[300]).replace('\0', 'X'));
    bindings = PidSimulationBindings.from(fixture.project, fixture.pid);
    scd = new ScdDesignModel(fixture.project, fixture.pid, bindings, "SCD-1", fixture.standard);
    assertFalse(new ScdDiagramRenderer(fixture.project, fixture.pid, bindings, scd).render().isComplete());
  }

  /**
   * Reads a generated text artifact.
   *
   * @param path generated file
   * @return UTF-8 text
   * @throws Exception if the file cannot be read
   */
  private static String text(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  /** Checks unresolved IDs cannot become valid edges merely by matching another element's tag. */
  @Test
  void rejectsTagInsteadOfElementId() {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    fixture.pid.getElements().get(0).connect("20-PIC-101");
    assertThrows(IllegalArgumentException.class, () -> new ScdDesignModel(fixture.project, fixture.pid,
        PidSimulationBindings.from(fixture.project, fixture.pid), "SCD-1", fixture.standard));
  }

  /** Checks mixed analogue/binary measurement outputs and visibly unresolved signal type. */
  @Test
  void retainsMixedSignalTypesAndShowsTypeHolds() {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    fixture.pid.add(new PidElement("pid:trip", "20-TSHH-101", PidElementType.TRIP).equipment(fixture.valve.getName()));
    java.util.Map<String, String> types = new java.util.LinkedHashMap<String, String>();
    types.put("20-TSHH-101", "BINARY");
    fixture.pid.getElements().get(0).connect("pid:trip").attribute("scdSignalTypes", types);
    PidSimulationBindings bindings = PidSimulationBindings.from(fixture.project, fixture.pid);
    ScdDesignModel scd = new ScdDesignModel(fixture.project, fixture.pid, bindings, "SCD-1", fixture.standard);
    assertEquals("ANALOGUE", scd.getSignals().get(0).get("signalType"));
    assertEquals("BINARY", scd.getSignals().get(1).get("signalType"));
    fixture.pid.getElements().get(1).attribute("scdSignalType", "");
    bindings = PidSimulationBindings.from(fixture.project, fixture.pid);
    scd = new ScdDesignModel(fixture.project, fixture.pid, bindings, "SCD-1", fixture.standard);
    ScdDiagramRenderer renderer = new ScdDiagramRenderer(fixture.project, fixture.pid, bindings, scd);
    assertTrue(renderer.render().getSvgBySheetId().values().toString().contains("Y/HOLD"));
    assertTrue(renderer.getLayoutSpecifications().values().toString().contains("Y/HOLD"));
  }

  /**
   * Verifies rendering owns a snapshot while publication still rejects a changed source.
   *
   * @throws Exception if rejected publication unexpectedly writes a package
   */
  @Test
  void rendererRetainsSnapshotAndPublicationRejectsNewSource() throws Exception {
    Fixture fixture = new Fixture();
    fixture.project.getDesignBasis().addStandard(fixture.standard);
    ScdDesignModel scd = fixture.scd();
    ScdDiagramRenderer renderer = new ScdDiagramRenderer(fixture.project, fixture.pid, fixture.bindings, scd);
    java.util.Map<String, String> before = renderer.render().getSvgBySheetId();
    fixture.pid.getElements().get(0).attribute("system", "CHANGED");
    assertEquals(before, renderer.render().getSvgBySheetId());
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-08T00:00:00Z", "NeqSim stale SCD test", "NeqSim", "workspace")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "SCD-REFERENCE")
        .build();
    Path target = temporaryDirectory.resolve("stale-source");
    assertThrows(IllegalStateException.class, () -> ScdPackageExporter.export(fixture.project, fixture.pid,
        PidSimulationBindings.from(fixture.project, fixture.pid), scd, metadata, target));
    assertFalse(Files.exists(target));
  }

  /**
   * Actual native pressure-control chain plus explicit project-controlled SCD annotations.
   *
   * @author NeqSim development team
   * @version 1.0
   */
  private static final class Fixture {
    private final ThrottlingValve valve;
    private final EngineeringProject project;
    private final PidDesignModel pid;
    private final PidSimulationBindings bindings;
    private final EngineeringStandard standard = new EngineeringStandard("NORSOK I-005", "2013",
        "System control diagram", "Project-selected legacy SCD drawing basis; controlled text review required");

    /** Creates public synthetic source equipment and actual sensor/controller identities. */
    private Fixture() {
      SystemSrkEos fluid = new SystemSrkEos(300.0, 60.0);
      fluid.addComponent("methane", 0.95);
      fluid.addComponent("ethane", 0.05);
      fluid.setMixingRule(2);
      Stream feed = new Stream("20-FEED-001", fluid);
      feed.setFlowRate(1000.0, "kg/hr");
      valve = new ThrottlingValve("20-PCV-101", feed);
      valve.setOutletPressure(40.0);
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(valve);
      Cooler cooler = new Cooler("20-E-101", valve.getOutletStream());
      cooler.setOutTemperature(290.0);
      process.add(cooler);
      PressureTransmitter pressure = new PressureTransmitter("20-PT-101", valve.getOutletStream());
      ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("20-PIC-101");
      controller.setTransmitter(pressure);
      controller.setUnit("bara");
      controller.setControllerSetPoint(40.0);
      valve.setController(controller);
      process.add(pressure);
      process.add(controller);
      process.run();
      project = NorsokOffshoreEngineeringBuilder.from("Public SCD pressure-control example", process, "2.0.1")
          .projectId("SCD-REFERENCE").registerProposedInstruments(false).build().setRevision("A");
      pid = new PidDesignModel(project.getProjectId(), "SCD-PROJECT");
      pid.add(element("pid:pt", pressure.getName(), PidElementType.MEASUREMENT, "MA")
          .line(valve.getOutletStream().getName()).attribute("measuredVariable", "PRESSURE")
          .attribute("scdOutputTerminal", "Y").connect("pid:pic"));
      pid.add(element("pid:pic", controller.getName(), PidElementType.CONTROLLER, "CA")
          .attribute("scdInputTerminal", "X").attribute("scdOutputTerminal", "Y").connect("pid:pcv"));
      pid.add(
          element("pid:pcv", valve.getName(), PidElementType.CONTROL_VALVE, "OA").attribute("scdInputTerminal", "X"));
      bindings = PidSimulationBindings.from(project, pid).bindMeasurement(pressure.getName(), pressure.getName())
          .bindController(controller.getName(), controller.getName()).bindEquipment(valve.getName(), valve.getName());
    }

    /**
     * Adds a caller-declared template convention, without claiming its full normative behaviour.
     *
     * @param id persistent P&amp;ID ID
     * @param tag controlled tag
     * @param type source type
     * @param template reviewed project template selection
     * @return source element
     */
    private PidElement element(String id, String tag, PidElementType type, String template) {
      return new PidElement(id, tag, type).equipment(valve.getName()).attribute("system", "PCS")
          .attribute("scdTemplate", template).attribute("scdTemplateReference", "PUBLIC-EXAMPLE-CONVENTIONS-A")
          .attribute("scdSignalType", "ANALOGUE");
    }

    /** @return captured source-bound SCD */
    private ScdDesignModel scd() {
      return new ScdDesignModel(project, pid, bindings, "SCD-20-001", standard);
    }
  }
}
