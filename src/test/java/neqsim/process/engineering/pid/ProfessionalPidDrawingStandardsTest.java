package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pins the drawing conventions of the professional P&amp;ID: ISO 5457 frame and ISO 7200 title block, ISA-5.1 valve
 * and function symbols, direct same-sheet signals, line numbers instead of simulation names, header merges drawn as
 * tees, hidden solver helpers, and standard relief/blowdown connections drawn instead of held.
 */
public class ProfessionalPidDrawingStandardsTest {
  @TempDir
  File tempDir;

  /**
   * Creates the test gas-condensate fluid.
   *
   * @return fluid
   */
  private static SystemSrkEos fluid() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, 60.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 6.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("n-heptane", 6.0);
    fluid.addComponent("n-decane", 4.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a separator with gas cooling, compression and a recompressed recycle through a header mixer.
   *
   * @return converged process
   */
  private static ProcessSystem process() {
    Stream feed = new Stream("20-FEED-001", fluid());
    feed.setFlowRate(30000.0, "kg/hr");
    Heater heater = new Heater("20-HA-001", feed);
    heater.setOutTemperature(273.15 + 70.0);
    Separator separator = new Separator("20-VA-001", heater.getOutletStream());
    Stream recycled = new Stream("23-RECYCLE", fluid());
    recycled.setFlowRate(100.0, "kg/hr");
    recycled.setTemperature(40.0, "C");
    recycled.setPressure(60.0, "bara");
    Mixer header = new Mixer("23-MA-001");
    header.addStream(separator.getGasOutStream());
    header.addStream(recycled);
    Cooler cooler = new Cooler("23-HA-001", header.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);
    Separator scrubber = new Separator("23-VG-001", cooler.getOutletStream());
    Compressor compressor = new Compressor("23-KA-001", scrubber.getGasOutStream());
    compressor.setOutletPressure(100.0);
    ThrottlingValve letdown = new ThrottlingValve("20-LV-001", separator.getLiquidOutStream());
    letdown.setOutletPressure(10.0);
    Separator lp = new Separator("20-VA-002", letdown.getOutletStream());
    Compressor recompressor = new Compressor("23-KA-002", lp.getGasOutStream());
    recompressor.setOutletPressure(60.0);
    Cooler recycleCooler = new Cooler("23-HA-003", recompressor.getOutletStream());
    recycleCooler.setOutTemperature(273.15 + 40.0);
    Recycle recycle = new Recycle("23-RECYCLE-CALC");
    recycle.addStream(recycleCooler.getOutletStream());
    recycle.setOutletStream(recycled);
    recycle.setTolerance(1.0e-3);
    ProcessSystem process = new ProcessSystem();
    process.setName("Standards train");
    for (ProcessEquipmentInterface unit : new ProcessEquipmentInterface[] {feed, heater, separator, recycled, header,
        cooler, scrubber, compressor, letdown, lp, recompressor, recycleCooler, recycle}) {
      process.add(unit);
    }
    process.run();
    if (!process.solved()) {
      process.run();
    }
    return process;
  }

  /**
   * Synthesizes the P&amp;ID proposal and exports the professional package.
   *
   * @param standardConnections true to complete standard relief, blowdown, vent and drain connections
   * @return package directory
   * @throws Exception on failure
   */
  private Path export(boolean standardConnections) throws Exception {
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Standards train", process(), "2.0.1")
        .projectId("STD-PID").build();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());
    boolean existingLevelValve = false;
    for (PidElement element : model.getElements()) {
      assertFalse("20-VA-001".equals(element.getEquipmentTag()) && element.getTag().contains("-LCV-"),
          "no new level control valve in series with the modelled letdown valve: " + element.getTag());
      existingLevelValve |= "20-LV-001".equals(element.getTag()) && element.getType() == PidElementType.CONTROL_VALVE;
    }
    assertTrue(existingLevelValve, "the modelled letdown valve is the level-control final element");
    if (standardConnections) {
      List<String> completed = PidStandardConnectionProposals.complete(model, project.getEngineeringProcessSystem());
      assertFalse(completed.isEmpty(), "standard relief, blowdown, vent and drain proposals are completed");
    }
    PidSimulationBindings bindings = PidSimulationBindings.from(project, model);
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-21T06:00:00Z", "Drawing standards test", "NeqSim", "test")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "STD-PID").build();
    return ProfessionalPidPackageExporter.export(project, model, bindings, metadata,
        tempDir.toPath().resolve(standardConnections ? "standard" : "plain"));
  }

  /**
   * Reads a JSON object file.
   *
   * @param file JSON file
   * @return parsed object
   * @throws Exception on failure
   */
  private static JsonObject json(Path file) throws Exception {
    return JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
  }

  /**
   * The generated set passes every drawing and standards check, and each sheet carries the ISO frame, title block,
   * correct ISA symbols, line numbers and directly drawn same-sheet signals.
   *
   * @throws Exception on failure
   */
  @Test
  void generatedSheetsFollowTheDrawingStandards() throws Exception {
    Path output = export(true);
    JsonObject readiness = json(output.resolve("professional-pid-readiness.json"));
    assertTrue(readiness.get("drawingHasNoErrors").getAsBoolean(), readiness.get("drawingDiagnostics").toString());

    File[] sheets = output.resolve("drawings").toFile().listFiles((dir, name) -> name.endsWith(".layout.json"));
    assertTrue(sheets != null && sheets.length > 3);
    int directSignals = 0;
    boolean shutdownValve = false;
    boolean controlFunction = false;
    boolean safetyFunction = false;
    boolean reliefValve = false;
    boolean headerTee = false;
    Pattern sheetNumber = Pattern.compile("SHEET: ([0-9]{3}) OF ([0-9]{3})");
    for (File sheet : sheets) {
      JsonObject layout = json(sheet.toPath());
      assertTrue(layout.has("sheet_furniture"), "ISO 5457 frame furniture on " + sheet.getName());
      String number = layout.get("sheetNumber").getAsString();
      boolean titleSheet = false;
      for (JsonElement element : layout.getAsJsonArray("labels")) {
        String text = element.getAsJsonObject().get("text").getAsString();
        assertFalse(text.contains("@@"), text);
        assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("stream /"), "internal stream name: " + text);
        assertFalse(text.startsWith("BL "), "battery limit uses the line number: " + text);
        Matcher matcher = sheetNumber.matcher(text);
        if (matcher.find()) {
          titleSheet = true;
          assertEquals(number, matcher.group(1));
          assertEquals(String.format("%03d", sheets.length), matcher.group(2));
        }
        assertFalse(text.contains("23-RECYCLE-CALC"), "solver helpers are not drawn");
        if (!layout.toString().contains("HOLD register")) {
          assertFalse(text.matches(".*-NZ-[0-9]+.*"), "nozzles are labelled N1, N2, ...: " + text);
        }
      }
      assertTrue(titleSheet, "ISO 7200 sheet number on " + sheet.getName());
      for (JsonElement element : layout.getAsJsonArray("nodes")) {
        JsonObject node = element.getAsJsonObject();
        String family = node.get("symbolFamily").getAsString();
        String tag = node.get("tag").getAsString();
        shutdownValve |= "SHUTDOWN_VALVE".equals(family) && tag.contains("ESDV");
        controlFunction |= "CONTROL_FUNCTION".equals(family);
        safetyFunction |= "SAFETY_FUNCTION".equals(family);
        reliefValve |= "RELIEF_VALVE".equals(family);
        headerTee |= "PIPE_TEE".equals(family) && "23-MA-001".equals(tag);
        if (tag.contains("-ESDV-")) {
          assertEquals("SHUTDOWN_VALVE", family, tag + " is drawn with its actuator");
        }
      }
      JsonArray edges = layout.getAsJsonArray("edges");
      for (JsonElement element : edges) {
        JsonObject edge = element.getAsJsonObject();
        if ("signal".equals(edge.get("fluid").getAsString()) && edge.get("id").getAsString().startsWith("SG-")) {
          directSignals++;
        }
      }
    }
    assertTrue(shutdownValve && controlFunction && safetyFunction, "ISA-5.1 valve and function symbols");
    assertTrue(reliefValve, "relief valve drawn after standard connection completion");
    assertTrue(headerTee, "header mixer drawn as a tee on the receiving sheet");
    assertTrue(directSignals > 0, "same-sheet signals are drawn directly");

    JsonObject content = json(output.resolve("pid-drawing-content-report.json"));
    assertTrue(content.getAsJsonObject("symbolConventions").has("SAFETY_FUNCTION"));
    assertTrue(content.getAsJsonObject("lineNumberRegister").size() > 0);
    assertTrue(content.getAsJsonObject("nozzleRegister").has("20-VA-001"), "nozzle register maps N-labels");
    int pooled = 0;
    for (JsonElement entry : content.getAsJsonArray("drawingIndex")) {
      pooled += "STD-PID".equals(entry.getAsJsonObject().get("subjectTag").getAsString()) ? 1 : 0;
    }
    assertTrue(pooled >= 1, "relief, blowdown, vent and drain routes share pooled auxiliary sheets");
    for (String lineNumber : content.getAsJsonObject("lineNumberRegister").keySet()) {
      assertTrue(lineNumber.matches("^[0-9A-Z]{1,4}-[A-Z]{1,5}-[0-9A-Z]{2,}.*$"), lineNumber);
    }
  }

  /**
   * Standard connection completion fills only missing inputs, records its evidence and leaves explicit project inputs
   * untouched.
   */
  @Test
  void standardConnectionCompletionNeverOverridesProjectInput() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("FEED", fluid());
    Separator vessel = new Separator("20-VA-001", feed);
    process.add(feed);
    process.add(vessel);
    PidDesignModel model = new PidDesignModel("P", "TEST");
    model.add(new PidElement("psv", "20-PSV-001", PidElementType.SAFETY_RELIEF_VALVE).equipment("20-VA-001"));
    model.add(new PidElement("dv", "20-DV-001", PidElementType.DRAIN).equipment("20-VA-001"));
    model.add(new PidElement("bdv", "20-BDV-001", PidElementType.BLOWDOWN_VALVE).equipment("20-VA-001")
        .attribute("destinationTag", "PROJECT-FLARE-KO-DRUM"));
    List<String> completed = PidStandardConnectionProposals.complete(model, process);
    assertEquals(java.util.Arrays.asList("20-PSV-001", "20-DV-001"), completed);
    PidElement psv = model.getElements().get(0);
    assertEquals("20-VA-001", psv.getAttributes().get("physicalSourceEquipmentTag"));
    assertEquals(PidStandardConnectionProposals.FLARE_HEADER, psv.getAttributes().get("destinationTag"));
    assertTrue(String.valueOf(psv.getAttributes().get("designEvidenceReference"))
        .startsWith(PidStandardConnectionProposals.EVIDENCE_PREFIX));
    assertEquals(PidStandardConnectionProposals.CLOSED_DRAIN_HEADER,
        model.getElements().get(1).getAttributes().get("destinationTag"));
    assertEquals("PROJECT-FLARE-KO-DRUM", model.getElements().get(2).getAttributes().get("destinationTag"));
    assertFalse(model.getElements().get(2).getAttributes().containsKey("physicalSourceNozzleTag"));
  }
}
