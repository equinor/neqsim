package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/** A scalar engineering quantity evaluated for every executable design case. */
public final class EngineeringMetric implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum GoverningDirection {
    MAXIMUM, MINIMUM, MAXIMUM_ABSOLUTE
  }

  public interface Extractor extends Serializable {
    double extract(ProcessSystem process);
  }

  private final String id;
  private final String subjectTag;
  private final String name;
  private final String unit;
  private final GoverningDirection governingDirection;
  private final Extractor extractor;

  public EngineeringMetric(String id, String subjectTag, String name, String unit,
      GoverningDirection governingDirection, Extractor extractor) {
    this.id = requireText(id, "id");
    this.subjectTag = requireText(subjectTag, "subjectTag");
    this.name = requireText(name, "name");
    this.unit = requireText(unit, "unit");
    if (governingDirection == null) {
      throw new IllegalArgumentException("governingDirection must not be null");
    }
    if (extractor == null) {
      throw new IllegalArgumentException("extractor must not be null");
    }
    this.governingDirection = governingDirection;
    this.extractor = extractor;
  }

  public static EngineeringMetric equipmentPressure(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".pressure", equipmentTag, "Pressure", "bara",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return requireUnit(process, equipmentTag).getPressure("bara");
          }
        });
  }

  public static EngineeringMetric equipmentTemperature(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".temperature", equipmentTag, "Temperature", "C",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return requireUnit(process, equipmentTag).getTemperature("C");
          }
        });
  }

  public static EngineeringMetric equipmentInletMassFlow(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".inletMassFlow", equipmentTag, "Total inlet mass flow", "kg/s",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            List<StreamInterface> inlets = requireUnit(process, equipmentTag).getInletStreams();
            if (inlets == null || inlets.isEmpty()) {
              throw new IllegalStateException("No inlet streams for " + equipmentTag);
            }
            double total = 0.0;
            for (StreamInterface inlet : inlets) {
              if (inlet != null) {
                total += inlet.getFlowRate("kg/sec");
              }
            }
            return total;
          }
        });
  }

  double extract(ProcessSystem process) {
    double result = extractor.extract(process);
    if (!Double.isFinite(result)) {
      throw new IllegalStateException("Metric " + id + " produced a non-finite value");
    }
    return result;
  }

  public String getId() {
    return id;
  }

  public String getSubjectTag() {
    return subjectTag;
  }

  public String getName() {
    return name;
  }

  public String getUnit() {
    return unit;
  }

  public GoverningDirection getGoverningDirection() {
    return governingDirection;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("subjectTag", subjectTag);
    result.put("name", name);
    result.put("unit", unit);
    result.put("governingDirection", governingDirection.name());
    return result;
  }

  private static ProcessEquipmentInterface requireUnit(ProcessSystem process, String equipmentTag) {
    ProcessEquipmentInterface unit = process.getUnit(equipmentTag);
    if (unit == null) {
      throw new IllegalArgumentException("Unknown equipment " + equipmentTag);
    }
    return unit;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
