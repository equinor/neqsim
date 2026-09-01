package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit utility, flare, blowdown, electrical, or other shared-system coordination basis. */
public final class EngineeringSharedSystemPolicy implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Supported shared-system coordination families. */
  public enum Type {
    UTILITY_HEADER, FLARE_HEADER, BLOWDOWN_GROUP, ELECTRICAL_SYSTEM, SHARED_EQUIPMENT, OTHER
  }

  /** One area demand linked to a converged engineering design variable. */
  public static final class Demand implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String areaName;
    private final String designVariable;
    private final double simultaneityFactor;

    public Demand(String areaName, String designVariable, double simultaneityFactor) {
      this.areaName = text(areaName, "areaName");
      this.designVariable = text(designVariable, "designVariable");
      if (!Double.isFinite(simultaneityFactor) || simultaneityFactor < 0.0 || simultaneityFactor > 1.0) {
        throw new IllegalArgumentException("simultaneityFactor must be between zero and one");
      }
      this.simultaneityFactor = simultaneityFactor;
    }

    public String getAreaName() {
      return areaName;
    }

    public String getDesignVariable() {
      return designVariable;
    }

    public double getSimultaneityFactor() {
      return simultaneityFactor;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("areaName", areaName);
      result.put("designVariable", designVariable);
      result.put("simultaneityFactor", Double.valueOf(simultaneityFactor));
      return result;
    }
  }

  /** Controlled definition for one shared system. */
  public static final class Definition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final Type type;
    private final String concurrencyBasisReference;
    private final String evidenceReference;
    private final List<Demand> demands = new ArrayList<Demand>();

    public Definition(String id, Type type, String concurrencyBasisReference, String evidenceReference) {
      this.id = text(id, "id");
      if (type == null) {
        throw new IllegalArgumentException("type must not be null");
      }
      this.type = type;
      this.concurrencyBasisReference = text(concurrencyBasisReference, "concurrencyBasisReference");
      this.evidenceReference = text(evidenceReference, "evidenceReference");
    }

    public Definition addDemand(String areaName, String designVariable, double simultaneityFactor) {
      demands.add(new Demand(areaName, designVariable, simultaneityFactor));
      return this;
    }

    public String getId() {
      return id;
    }

    public Type getType() {
      return type;
    }

    public List<Demand> getDemands() {
      return Collections.unmodifiableList(demands);
    }

    public Set<String> getAreaNames() {
      Set<String> result = new LinkedHashSet<String>();
      for (Demand demand : demands) {
        result.add(demand.areaName);
      }
      return Collections.unmodifiableSet(result);
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("type", type.name());
      result.put("concurrencyBasisReference", concurrencyBasisReference);
      result.put("evidenceReference", evidenceReference);
      List<Map<String, Object>> demandRows = new ArrayList<Map<String, Object>>();
      for (Demand demand : demands) {
        demandRows.add(demand.toMap());
      }
      result.put("demands", demandRows);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }
  }

  private final String id;
  private final String revision;
  private final List<Definition> definitions = new ArrayList<Definition>();

  public EngineeringSharedSystemPolicy(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringSharedSystemPolicy add(Definition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("definition must not be null");
    }
    for (Definition existing : definitions) {
      if (existing.id.equals(definition.id)) {
        throw new IllegalArgumentException("Duplicate shared-system definition " + definition.id);
      }
    }
    definitions.add(definition);
    return this;
  }

  public String getId() {
    return id;
  }

  public String getRevision() {
    return revision;
  }

  public List<Definition> getDefinitions() {
    return Collections.unmodifiableList(definitions);
  }

  String fingerprintMaterial() {
    StringBuilder result = new StringBuilder(id).append('|').append(revision);
    for (Definition definition : definitions) {
      result.append('|').append(definition.toMap());
    }
    return result.toString();
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
