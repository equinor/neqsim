package neqsim.process.processmodel.lifecycle.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One directly changed engineering node or relationship named by a model-change event. */
public final class ModelChangeSubject implements Serializable, Comparable<ModelChangeSubject> {
  private static final long serialVersionUID = 1000L;

  public enum SubjectType {
    ENGINEERING_NODE, ENGINEERING_EDGE
  }

  public enum ChangeKind {
    ADDED, MODIFIED, REMOVED
  }

  private final String subjectId;
  private final SubjectType subjectType;
  private final String objectKind;
  private final ChangeKind changeKind;
  private final List<String> changedProperties;

  public ModelChangeSubject(String subjectId, SubjectType subjectType, String objectKind, ChangeKind changeKind,
      List<String> changedProperties) {
    this.subjectId = requireText(subjectId, "subjectId");
    if (subjectType == null) {
      throw new IllegalArgumentException("subjectType must not be null");
    }
    if (changeKind == null) {
      throw new IllegalArgumentException("changeKind must not be null");
    }
    this.subjectType = subjectType;
    this.objectKind = requireText(objectKind, "objectKind");
    this.changeKind = changeKind;
    this.changedProperties = sorted(changedProperties);
  }

  public String getSubjectId() {
    return subjectId;
  }

  public SubjectType getSubjectType() {
    return subjectType;
  }

  public String getObjectKind() {
    return objectKind;
  }

  public ChangeKind getChangeKind() {
    return changeKind;
  }

  public List<String> getChangedProperties() {
    return Collections.unmodifiableList(changedProperties);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("subjectId", subjectId);
    result.put("subjectType", subjectType.name());
    result.put("objectKind", objectKind);
    result.put("changeKind", changeKind.name());
    result.put("changedProperties", new ArrayList<String>(changedProperties));
    return result;
  }

  static ModelChangeSubject fromMap(Map<String, Object> value) {
    return new ModelChangeSubject(required(value, "subjectId"), SubjectType.valueOf(required(value, "subjectType")),
        required(value, "objectKind"), ChangeKind.valueOf(required(value, "changeKind")),
        ModelChangeEvent.stringList(value.get("changedProperties"), "changedProperties"));
  }

  @Override
  public int compareTo(ModelChangeSubject other) {
    int byId = subjectId.compareTo(other.subjectId);
    if (byId != 0) {
      return byId;
    }
    int byType = subjectType.compareTo(other.subjectType);
    return byType != 0 ? byType : changeKind.compareTo(other.changeKind);
  }

  private static List<String> sorted(List<String> values) {
    List<String> result = values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    for (int i = 0; i < result.size(); i++) {
      result.set(i, requireText(result.get(i), "changedProperty"));
    }
    Collections.sort(result);
    return result;
  }

  private static String required(Map<String, Object> value, String field) {
    if (value == null || value.get(field) == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    return requireText(String.valueOf(value.get(field)), field);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
