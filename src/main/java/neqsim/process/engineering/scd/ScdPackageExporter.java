package neqsim.process.engineering.scd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.pid.ProfessionalPidPackageExporter;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer;
import neqsim.process.processmodel.diagram.ScdDiagramRenderer;

/**
 * Publishes an atomic SCD/P&amp;ID/DEXPI companion package from a single engineering source.
 *
 * <p>
 * SCD-specific logic remains in an explicitly identified NeqSim sidecar. No proprietary SCD objects or template
 * semantics are inserted into the DEXPI schema. Native DEXPI IDs are read from the actual materializer report.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ScdPackageExporter {
  /** Prevents utility construction. */
  private ScdPackageExporter() {
  }

  /**
   * Exports shared native drawings, exchange, executable model, SCD source and actual DEXPI cross-references.
   *
   * @param project solved source project
   * @param pid shared P&amp;ID model
   * @param bindings shared actual runtime bindings
   * @param scd revision-bound SCD model
   * @param metadata explicit DEXPI export identity/date
   * @param directory new output directory; existing directories are never replaced
   * @return published package directory
   * @throws IOException when writing or publishing fails
   */
  public static Path export(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings,
      ScdDesignModel scd, Dexpi20PlantExportMetadata metadata, Path directory) throws IOException {
    if (scd == null || directory == null || metadata == null) {
      throw new IllegalArgumentException("SCD, metadata and output directory are required");
    }
    scd.assertCurrent(project, pid, bindings);
    ScdDiagramRenderer renderer = new ScdDiagramRenderer(project, pid, bindings, scd);
    NativeEngineeringDiagramRenderer.Result rendering = renderer.render();
    if (!rendering.isComplete()) {
      throw new IllegalArgumentException("Resolve SCD drawing diagnostics before exporting the package");
    }
    Path target = directory.toAbsolutePath().normalize();
    if (Files.exists(target)) {
      throw new IllegalArgumentException("Package directory already exists: " + target);
    }
    Path parent = target.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Package directory must have a parent: " + target);
    }
    Files.createDirectories(parent);
    Path staging = Files.createTempDirectory(parent, ".neqsim-scd-");
    try {
      Path output = ProfessionalPidPackageExporter.export(project, pid, bindings, metadata, staging.resolve("package"));
      write(output.resolve("scd-design-model.json"), scd.toJson());
      JsonObject nativeProjection = JsonParser.parseString(
          new String(Files.readAllBytes(output.resolve("plant.dexpi.xml.pid-projection.json")), StandardCharsets.UTF_8))
          .getAsJsonObject();
      JsonObject ids = nativeProjection.getAsJsonObject("nativeIdsByPidTag");
      List<Map<String, Object>> references = new ArrayList<Map<String, Object>>();
      for (PidElement block : scd.getBlocks()) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("pidId", block.getId());
        row.put("pidTag", block.getTag());
        row.put("scdDrawingNumber", scd.getDrawingNumber());
        row.put("dexpiFile", "plant.dexpi.xml");
        row.put("dexpiNativeId", ids.has(block.getTag()) ? ids.get(block.getTag()).getAsString() : "");
        row.put("mappingStatus", ids.has(block.getTag()) ? "NATIVE_OBJECT_MAPPED" : "DEXPI_PROJECTION_HOLD");
        references.add(row);
      }
      Map<String, Object> crossReference = new LinkedHashMap<String, Object>();
      crossReference.put("schemaVersion", "neqsim_scd_dexpi_cross_reference.v1");
      crossReference.put("standardReference", scd.getStandard().getReference());
      crossReference.put("blocks", references);
      crossReference.put("signals", scd.getSignals());
      crossReference.put("dexpiSignalProjection", nativeProjection.get("signalLinks"));
      crossReference.put("checksPerformed", rendering.getPerformedChecks());
      crossReference.put("standardsConformance", "NOT_ASSESSED");
      crossReference.put("fitnessForConstruction", Boolean.FALSE);
      write(output.resolve("scd-dexpi-cross-reference.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(crossReference));
      Files.write(output.resolve("drawings/scd-drawing-set.pdf"), rendering.getPdf());
      int sheet = 0;
      Map<String, String> layouts = renderer.getLayoutSpecifications();
      for (Map.Entry<String, String> svg : rendering.getSvgBySheetId().entrySet()) {
        String name = String.format(java.util.Locale.ROOT, "scd-sheet-%03d", Integer.valueOf(++sheet));
        write(output.resolve("drawings/" + name + ".svg"), svg.getValue());
        write(output.resolve("drawings/" + name + ".layout.json"), layouts.get(svg.getKey()));
      }
      DexpiEngineeringExporter.refreshPackageManifest(output);
      scd.assertCurrent(project, pid, bindings);
      try {
        Files.move(output, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(output, target);
      }
      return target;
    } finally {
      removeStaging(staging);
    }
  }

  /**
   * Writes UTF-8 drawing or JSON content.
   *
   * @param path output path
   * @param value serialized content
   * @throws IOException on a write failure
   */
  private static void write(Path path, String value) throws IOException {
    Files.write(path, value.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Removes only this invocation's private staging directory.
   *
   * @param directory private staging directory
   * @throws IOException when cleanup fails
   */
  private static void removeStaging(Path directory) throws IOException {
    try (Stream<Path> paths = Files.walk(directory)) {
      Path[] ordered = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
      for (Path path : ordered) {
        Files.deleteIfExists(path);
      }
    }
  }
}
