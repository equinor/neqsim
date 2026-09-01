package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Deterministic registry of equipment, stream, area, model, shared-resource, and coupled-group constraints.
 *
 * <p>
 * The registry extends, rather than replaces, equipment-local {@link CapacityConstraint} objects. Registrations are
 * immutable metadata and contain no process object, supplier, or callback reference. They therefore remain safe to
 * serialize, inspect through JPype, and compare across process executions. Runtime sampling and utilization snapshots
 * are deliberately outside this class.
 * </p>
 */
public final class PlantConstraintRegistry implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.0";

  private final Map<String, PlantConstraintDefinition> definitions = new TreeMap<String, PlantConstraintDefinition>();

  /** @return registry schema version */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  /**
   * Registers one immutable definition.
   *
   * @param definition complete or explicitly incomplete registration evidence
   * @return this registry for chaining
   */
  public PlantConstraintRegistry register(PlantConstraintDefinition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("Plant constraint definition is required");
    }
    String identity = definition.getQualifiedId();
    if (definitions.containsKey(identity)) {
      throw new IllegalArgumentException("Duplicate plant constraint identity " + identity);
    }
    definitions.put(identity, definition);
    return this;
  }

  /**
   * Registers metadata adapted from an existing equipment-local capacity constraint.
   *
   * <p>
   * The adapter copies the established #2941 identity, provenance, confidence, validity, severity, direction, unit, and
   * enablement evidence without retaining or sampling the mutable supplier.
   * </p>
   *
   * @param modelName stable process-model name
   * @param areaName stable process-area name
   * @param equipmentName stable equipment name
   * @param constraintName equipment-local constraint name
   * @param basis explicit measurement or rating basis
   * @param category engineering category
   * @param owner accountable owner or discipline
   * @param reference source document or equipment tag
   * @param constraint existing equipment-local definition
   * @return copied immutable definition
   */
  public PlantConstraintDefinition registerEquipmentConstraint(String modelName, String areaName, String equipmentName,
      String constraintName, String basis, PlantConstraintDefinition.Category category, String owner, String reference,
      CapacityConstraint constraint) {
    if (constraint == null) {
      throw new IllegalArgumentException("Equipment capacity constraint is required");
    }
    PlantConstraintDefinition.Builder builder = PlantConstraintDefinition
        .builder(constraintName, PlantConstraintScope.equipment(modelName, areaName, equipmentName))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.DIRECT)
        .limitDirection(constraint.isMinimumConstraint() ? PlantConstraintDefinition.LimitDirection.MINIMUM
            : PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(category).severity(constraint.getSeverity()).unit(constraint.getUnit()).basis(basis)
        .provenance(constraint.getDataSource()).owner(owner).reference(reference)
        .description(constraint.getDescription()).enabled(constraint.isEnabled());
    if (constraint.hasConfidence()) {
      builder.confidence(constraint.getConfidence());
    }
    if (constraint.hasValidityRange()) {
      builder.validityRange(constraint.getValidityMinimum(), constraint.getValidityMaximum());
    }
    PlantConstraintDefinition definition = builder.build();
    register(definition);
    return definition;
  }

  /** @return number of retained registrations, including disabled and incomplete rows */
  public int size() {
    return definitions.size();
  }

  /** @return true when an exact qualified identity is present */
  public boolean contains(String qualifiedId) {
    return definitions.containsKey(qualifiedId);
  }

  /** @return exact immutable definition, or null when absent */
  public PlantConstraintDefinition get(String qualifiedId) {
    return definitions.get(qualifiedId);
  }

  /** @return deterministic immutable list sorted by qualified identity */
  public List<PlantConstraintDefinition> getDefinitions() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintDefinition>(definitions.values()));
  }

  /** Returns deterministic definitions for one exact scope. */
  public List<PlantConstraintDefinition> getDefinitions(PlantConstraintScope scope) {
    if (scope == null) {
      return Collections.emptyList();
    }
    List<PlantConstraintDefinition> matches = new ArrayList<PlantConstraintDefinition>();
    for (PlantConstraintDefinition definition : definitions.values()) {
      if (scope.equals(definition.getScope())) {
        matches.add(definition);
      }
    }
    return Collections.unmodifiableList(matches);
  }

  /**
   * Returns a deterministic SHA-256 identity for the complete registration contract.
   *
   * @return lowercase hexadecimal digest independent of insertion order
   */
  public String getIdentityDigest() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(SCHEMA_VERSION.getBytes(StandardCharsets.UTF_8));
      for (PlantConstraintDefinition definition : definitions.values()) {
        digest.update((byte) '\n');
        digest.update(definition.canonicalForm().getBytes(StandardCharsets.UTF_8));
      }
      return toHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String toHex(byte[] bytes) {
    char[] digits = "0123456789abcdef".toCharArray();
    char[] output = new char[bytes.length * 2];
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xff;
      output[index * 2] = digits[value >>> 4];
      output[index * 2 + 1] = digits[value & 0x0f];
    }
    return new String(output);
  }
}
