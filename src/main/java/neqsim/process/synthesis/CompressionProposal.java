package neqsim.process.synthesis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Result of {@link FlowsheetSynthesisEngine#proposeAndBuildCompression(CompressionDuty)}.
 *
 * <p>
 * A compression proposal carries the assembled {@link ProcessSystem} (compressors + inter-stage
 * coolers, optionally an after-cooler), the chosen number of stages, the per-stage pressure ratio,
 * a human-readable rationale, and the total estimated shaft power once the process has been
 * {@link ProcessSystem#run() run}.
 * </p>
 *
 * <p>
 * The engine does <em>not</em> automatically run the proposed process; the caller controls when
 * convergence happens so that downstream changes (e.g. integration with an existing network) can
 * be applied first.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CompressionProposal implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final int stages;
  private final double perStagePressureRatio;
  private final String rationale;
  private final List<String> stageNames;

  /**
   * Creates a compression proposal.
   *
   * @param processSystem the assembled flowsheet (non-null)
   * @param stages number of compression stages
   * @param perStagePressureRatio per-stage ratio (geometric)
   * @param rationale human-readable description
   * @param stageNames ordered list of compressor unit names, oldest first
   */
  public CompressionProposal(ProcessSystem processSystem, int stages, double perStagePressureRatio,
      String rationale, List<String> stageNames) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    if (stages < 1) {
      throw new IllegalArgumentException("stages must be >= 1, got " + stages);
    }
    this.processSystem = processSystem;
    this.stages = stages;
    this.perStagePressureRatio = perStagePressureRatio;
    this.rationale = rationale;
    this.stageNames = stageNames == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(stageNames));
  }

  /**
   * Returns the assembled process.
   *
   * @return non-null process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Returns the number of compression stages.
   *
   * @return stage count
   */
  public int getStages() {
    return stages;
  }

  /**
   * Returns the per-stage geometric pressure ratio.
   *
   * @return per-stage pressure ratio
   */
  public double getPerStagePressureRatio() {
    return perStagePressureRatio;
  }

  /**
   * Returns the rationale string.
   *
   * @return non-null rationale
   */
  public String getRationale() {
    return rationale;
  }

  /**
   * Returns the ordered list of compressor unit names.
   *
   * @return non-null list (may be empty)
   */
  public List<String> getStageNames() {
    return stageNames;
  }

  /**
   * Returns a JSON summary of the proposal. The process system itself is not serialized; the
   * caller can persist it separately via {@link ProcessSystem}'s lifecycle helpers.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("stages", stages);
    root.addProperty("perStagePressureRatio", perStagePressureRatio);
    root.addProperty("rationale", rationale == null ? "" : rationale);
    JsonArray arr = new JsonArray();
    for (String s : stageNames) {
      arr.add(s);
    }
    root.add("stageNames", arr);
    return root.toString();
  }
}
