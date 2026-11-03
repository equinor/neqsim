package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Native DEXPI 2.0.1 Plant export with controlled provenance and official-model validation. Legacy
 * {@link Dexpi20XmlWriter} overloads retain DEXPI 2.0.0 output.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201XmlWriter {
  /** Exact official Core model URI. */
  public static final String CORE_MODEL = "https://data.dexpi.org/models/2.0.1/Core.xml";
  /** Exact official Plant model URI. */
  public static final String PLANT_MODEL = "https://data.dexpi.org/models/2.0.1/Plant.xml";

  /** Prevents instantiation. */
  private Dexpi201XmlWriter() {
  }

  /**
   * Writes a DEXPI 2.0.1 Plant file with explicit boundary connectors.
   *
   * @param process source process
   * @param file destination file
   * @param metadata caller-controlled originating-system and plant provenance
   * @return assessment bound to the actual generated file
   * @throws IOException if export or validation fails
   */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem process, File file,
      Dexpi20PlantExportMetadata metadata) throws IOException {
    return writeAndAssess(process, file, Dexpi20PlantExportOptions.builder(metadata).specification201(true)
        .boundaryConnectionMode(Dexpi20PlantExportOptions.BoundaryConnectionMode.EXPLICIT_OFF_PAGE_CONNECTORS).build());
  }

  /**
   * Writes and assesses an explicitly configured native DEXPI 2.0.1 Plant file.
   *
   * @param process source process
   * @param file destination file
   * @param options export options with specification201 enabled
   * @return file-bound conformance assessment
   * @throws IOException if export or validation fails
   */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem process, File file,
      Dexpi20PlantExportOptions options) throws IOException {
    if (options == null || !options.isSpecification201()) {
      throw new IllegalArgumentException("DEXPI 2.0.1 export options are required");
    }
    Dexpi20XmlWriter.write(process, file, options);
    Files.write(file.toPath().resolveSibling(file.getName() + ".process-projection.json"), new GsonBuilder()
        .setPrettyPrinting().create().toJson(projectionEvidence(process)).getBytes(StandardCharsets.UTF_8));
    return assess(file.toPath());
  }

  /**
   * Records physical projections and every intentionally omitted numerical helper.
   *
   * @param process source simulation model
   * @return deterministic source-to-physical-model projection evidence
   * @throws IOException if a recycle alias is ambiguous
   */
  public static Map<String, Object> projectionEvidence(ProcessSystem process) throws IOException {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    Dexpi20XmlWriter.numericalAliases(process);
    List<Map<String, Object>> omitted = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> physical = new ArrayList<Map<String, Object>>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit == null) {
        continue;
      }
      if (Dexpi20XmlWriter.isNumericalHelper(unit)) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("sourceTag", unit.getName());
        item.put("sourceJavaType", unit.getClass().getName());
        item.put("projection", "OMITTED_NON_PHYSICAL_SIMULATION_HELPER");
        if (unit instanceof Recycle) {
          item.put("materialConnectivity", "DIRECTED_STREAM_IDENTITY_ALIAS");
          item.put("upstreamStream", unit.getInletStreams().get(0).getName());
          item.put("downstreamStream", unit.getOutletStreams().get(0).getName());
        } else {
          item.put("calculationLogic", "RETAINED_IN_EXECUTABLE_SIMULATION_MODEL_ONLY");
        }
        omitted.add(item);
      } else if (!(unit instanceof StreamInterface)) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("sourceTag", unit.getName());
        item.put("sourceJavaType", unit.getClass().getName());
        item.put("inletCount", Integer.valueOf(unit.getInletStreams().size()));
        item.put("outletCount", Integer.valueOf(unit.getOutletStreams().size()));
        physical.add(item);
      }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_dexpi_201_process_projection.v1");
    result.put("dexpiSpecificationVersion", "2.0.1");
    result.put("omittedNumericalHelpers", omitted);
    result.put("physicalSourceObjects", physical);
    result.put("approvalStatus", "REVIEW_REQUIRED");
    result.put("fitnessForConstruction", Boolean.FALSE);
    return result;
  }

  /**
   * Assesses an existing DEXPI 2.0.1 Plant file against pinned official definitions.
   *
   * @param file document to assess
   * @return assessment bound to the exact bytes
   * @throws IOException if the file or model definitions cannot be read
   */
  public static Dexpi20ConformanceAssessment.Report assess(java.nio.file.Path file) throws IOException {
    return Dexpi20ConformanceAssessment.assess(file, Dexpi20ConformanceAssessment.Profile.PLANT_P_ID, true);
  }

  /**
   * Identifies exact 2.0.1 Core imports without relying on a filename or class name.
   *
   * @param document parsed native XML
   * @return whether the exact official 2.0.1 Core URI is imported
   */
  public static boolean isSpecification201(Document document) {
    NodeList imports = document.getDocumentElement().getElementsByTagName("Import");
    for (int index = 0; index < imports.getLength(); index++) {
      Element item = (Element) imports.item(index);
      if ("Core".equals(item.getAttribute("prefix")) && CORE_MODEL.equals(item.getAttribute("source"))) {
        return true;
      }
    }
    return false;
  }
}
