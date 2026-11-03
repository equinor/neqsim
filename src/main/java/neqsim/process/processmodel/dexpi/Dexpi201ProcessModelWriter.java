package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Explicit native DEXPI 2.0.1 Process/PFD export with official-model semantic checks. Legacy Process exporters and
 * multi-area compatibility packages remain unchanged.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201ProcessModelWriter {
  /** Prevents instantiation. */
  private Dexpi201ProcessModelWriter() {
  }

  /**
   * Writes a Process exchange with caller-controlled provenance and assesses exact output.
   *
   * @param process source process
   * @param file destination XML file
   * @param metadata controlled originating-system metadata
   * @return file-bound official-schema and supported-model assessment
   * @throws IOException if serialization or validation fails
   */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem process, File file,
      Dexpi20PlantExportMetadata metadata) throws IOException {
    if (process == null || file == null || metadata == null) {
      throw new IllegalArgumentException("process, file and metadata must not be null");
    }
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit != null && Dexpi20XmlWriter.isNumericalHelper(unit)) {
        throw new IllegalArgumentException(
            "DEXPI 2.0.1 Process profile has no reviewed physical alias projection for numerical helper "
                + unit.getClass().getName() + " (" + unit.getName() + ")");
      }
    }
    try (FileOutputStream stream = new FileOutputStream(file)) {
      Dexpi20ProcessModelWriter.write201(process, stream, metadata);
    }
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD,
        true);
  }
}
