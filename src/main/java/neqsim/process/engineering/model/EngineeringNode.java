package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;

/** A typed, stable-identity node in the canonical engineering graph. */
public final class EngineeringNode implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();

  /** Engineering object categories independent of any exchange format. */
  public enum Kind {
    PROJECT, EQUIPMENT, LINE, INSTRUMENT, REQUIREMENT, BOUNDARY, DESIGN_CASE, CALCULATION, DOCUMENT, PORT, NOZZLE,
    PIPE_SEGMENT, SIGNAL_CONNECTION, ENERGY_CONNECTION, PROCESS_TAP
  }

  private final String id;
  private final Kind kind;
  private final String externalKey;
  private String label;
  private final Map<String, Object> properties = new LinkedHashMap<String, Object>();
  private final List<EngineeringProvenance> provenance = new ArrayList<EngineeringProvenance>();

  public EngineeringNode(String id, Kind kind, String externalKey, String label) {
    this.id = requireText(id, "id");
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    this.kind = kind;
    this.externalKey = requireText(externalKey, "externalKey");
    this.label = requireText(label, "label");
  }

  public EngineeringNode setLabel(String value) {
    label = requireText(value, "label");
    return this;
  }

  public EngineeringNode putProperty(String name, Object value) {
    String key = requireText(name, "property name");
    if (value == null) {
      properties.remove(key);
    } else if (value instanceof Double && !Double.isFinite(((Double) value).doubleValue())) {
      properties.remove(key);
    } else if (value instanceof Float && !Float.isFinite(((Float) value).floatValue())) {
      properties.remove(key);
    } else {
      properties.put(key, value);
    }
    return this;
  }

  public EngineeringNode addProvenance(EngineeringProvenance value) {
    if (value == null) {
      throw new IllegalArgumentException("provenance must not be null");
    }
    provenance.add(value);
    return this;
  }

  public String getId() {
    return id;
  }

  public Kind getKind() {
    return kind;
  }

  public String getExternalKey() {
    return externalKey;
  }

  public String getLabel() {
    return label;
  }

  public Map<String, Object> getProperties() {
    return Collections.unmodifiableMap(properties);
  }

  public List<EngineeringProvenance> getProvenance() {
    return Collections.unmodifiableList(provenance);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("kind", kind.name());
    result.put("externalKey", externalKey);
    result.put("label", label);
    result.put("properties", new TreeMap<String, Object>(properties));
    List<Map<String, Object>> provenanceMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringProvenance item : provenance) {
      provenanceMaps.add(item.toMap());
    }
    result.put("provenance", provenanceMaps);
    return result;
  }

  String canonicalForm() {
    return GSON.toJson(toMap());
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
