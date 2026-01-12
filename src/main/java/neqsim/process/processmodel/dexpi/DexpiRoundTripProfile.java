package neqsim.process.processmodel.dexpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Describes validation profiles for round-tripping DEXPI data through NeqSim.
 *
 * <p>
 * The minimal runnable profile validates that a process contains runnable {@link DexpiStream}
 * segments (with line/fluid references and operating conditions), tagged equipment and at least one
 * piece of equipment alongside the piping network.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiRoundTripProfile {
  private final String name;

  private DexpiRoundTripProfile(String name) {
    this.name = name;
  }

  /**
   * Returns a minimal profile guaranteeing that imported data can be executed and exported.
   *
   * @return profile enforcing stream metadata and equipment tagging
   */
  public static DexpiRoundTripProfile minimalRunnableProfile() {
    return Holder.MINIMAL_RUNNABLE;
  }

  /**
   * Validates the supplied process system against the profile.
   *
   * @param processSystem process system produced from DEXPI data
   * @return validation result indicating success and listing violations
   */
  public ValidationResult validate(ProcessSystem processSystem) {
    Objects.requireNonNull(processSystem, "processSystem");
    List<String> violations = new ArrayList<>();

    long streamCount =
        processSystem.getUnitOperations().stream().filter(DexpiStream.class::isInstance).count();
    if (streamCount == 0) {
      violations.add("Process must contain at least one DexpiStream");
    }

    List<DexpiStream> streams =
        processSystem.getUnitOperations().stream().filter(DexpiStream.class::isInstance)
            .map(DexpiStream.class::cast).collect(Collectors.toList());
    for (DexpiStream stream : streams) {
      if (isBlank(stream.getName())) {
        violations.add("DexpiStream is missing a name");
      }
      if (isBlank(stream.getLineNumber()) && isBlank(stream.getFluidCode())) {
        violations.add("DexpiStream " + stream.getName()
            + " requires a line number or fluid code to preserve connectivity");
      }
      if (Double.isNaN(stream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT))) {
        violations
            .add("DexpiStream " + stream.getName() + " is missing operating pressure metadata");
      }
      if (Double.isNaN(stream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT))) {
        violations
            .add("DexpiStream " + stream.getName() + " is missing operating temperature metadata");
      }
      if (Double.isNaN(stream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT))) {
        violations.add("DexpiStream " + stream.getName() + " is missing operating flow metadata");
      }
      if (!stream.isActive()) {
        violations.add("DexpiStream " + stream.getName() + " must be active after simulation");
      }
    }

    List<DexpiProcessUnit> units =
        processSystem.getUnitOperations().stream().filter(DexpiProcessUnit.class::isInstance)
            .map(DexpiProcessUnit.class::cast).collect(Collectors.toList());
    for (DexpiProcessUnit unit : units) {
      if (isBlank(unit.getName())) {
        violations.add("DexpiProcessUnit is missing a tag");
      }
      if (unit.getMappedEquipment() == null) {
        violations.add("DexpiProcessUnit " + unit.getName() + " lacks a mapped equipment enum");
      }
      if (isBlank(unit.getDexpiClass())) {
        violations.add(
            "DexpiProcessUnit " + unit.getName() + " does not expose its original DEXPI class");
      }
    }

    boolean hasEquipment = processSystem.getUnitOperations().stream()
        .anyMatch(unit -> unit instanceof DexpiProcessUnit);
    if (!hasEquipment) {
      violations.add("Process must contain at least one DexpiProcessUnit");
    }

    return new ValidationResult(violations.isEmpty(), Collections.unmodifiableList(violations));
  }

  /**
   * Profile validation result.
   */
  public static final class ValidationResult {
    private final boolean successful;
    private final List<String> violations;

    private ValidationResult(boolean successful, List<String> violations) {
      this.successful = successful;
      this.violations = violations;
    }

    /**
     * Indicates whether validation succeeded.
     *
     * @return true if valid
     */
    public boolean isSuccessful() {
      return successful;
    }

    /**
     * Detailed violations preventing the process from satisfying the profile.
     *
     * @return list of violation messages
     */
    public List<String> getViolations() {
      return violations;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static final class Holder {
    private static final DexpiRoundTripProfile MINIMAL_RUNNABLE =
        new DexpiRoundTripProfile("minimalRunnable");

    private Holder() {}
  }
}
