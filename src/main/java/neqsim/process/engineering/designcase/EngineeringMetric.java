package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
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
  private Double lowerAcceptanceLimit;
  private Double upperAcceptanceLimit;

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

  /** Creates a maximum actual inlet-volume-flow metric for sizing equipment and piping. */
  public static EngineeringMetric equipmentInletVolumeFlow(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".inletVolumeFlow", equipmentTag, "Actual inlet volume flow", "m3/s",
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
                total += inlet.getFlowRate("m3/sec");
              }
            }
            return total;
          }
        });
  }

  /** Creates a minimum inlet-density metric used by velocity and momentum sizing checks. */
  public static EngineeringMetric equipmentInletDensity(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".inletDensity", equipmentTag, "Inlet density", "kg/m3",
        GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            List<StreamInterface> inlets = requireUnit(process, equipmentTag).getInletStreams();
            if (inlets == null || inlets.isEmpty() || inlets.get(0) == null) {
              throw new IllegalStateException("No inlet stream for " + equipmentTag);
            }
            inlets.get(0).getFluid().initPhysicalProperties("density");
            return inlets.get(0).getFluid().getDensity("kg/m3");
          }
        });
  }

  /** Creates a maximum pressure-drop metric for any two-port process unit. */
  public static EngineeringMetric equipmentPressureDrop(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".pressureDrop", equipmentTag, "Pressure drop", "bar",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof TwoPortInterface)) {
              throw new IllegalArgumentException(equipmentTag + " is not a two-port unit");
            }
            TwoPortInterface twoPort = (TwoPortInterface) equipment;
            return twoPort.getInletStream().getPressure("bara") - twoPort.getOutletStream().getPressure("bara");
          }
        });
  }

  /** Creates a maximum compressor shaft-power metric. */
  public static EngineeringMetric compressorPower(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".power", equipmentTag, "Compressor shaft power", "kW",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof Compressor)) {
              throw new IllegalArgumentException(equipmentTag + " is not a Compressor");
            }
            return ((Compressor) equipment).getPower("kW");
          }
        });
  }

  /** Creates a maximum compressor polytropic-head metric. */
  public static EngineeringMetric compressorPolytropicHead(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".polytropicHead", equipmentTag, "Compressor polytropic head",
        "kJ/kg", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getPolytropicFluidHead();
          }
        });
  }

  /** Creates a maximum compressor-speed metric. */
  public static EngineeringMetric compressorSpeed(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".speed", equipmentTag, "Compressor shaft speed", "rpm",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getSpeed();
          }
        });
  }

  /** Creates a minimum compressor polytropic-efficiency metric. */
  public static EngineeringMetric compressorPolytropicEfficiency(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".polytropicEfficiency", equipmentTag,
        "Compressor polytropic efficiency", "fraction", GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getPolytropicEfficiency();
          }
        });
  }

  /** Creates a minimum fractional distance to the compressor surge line. */
  public static EngineeringMetric compressorSurgeMargin(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".surgeMargin", equipmentTag, "Distance to surge line", "fraction",
        GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getDistanceToSurge();
          }
        });
  }

  /** Creates a minimum fractional distance to the compressor stonewall/choke limit. */
  public static EngineeringMetric compressorStonewallMargin(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".stonewallMargin", equipmentTag, "Distance to stonewall",
        "fraction", GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getDistanceToStoneWall();
          }
        });
  }

  /** Creates a minimum fractional distance to the configured anti-surge control line. */
  public static EngineeringMetric compressorControlLineMargin(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".controlLineMargin", equipmentTag,
        "Distance to anti-surge control line", "fraction", GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getDistanceToControlLine();
          }
        });
  }

  /** Creates a maximum compressor recycle fraction required to remain on the anti-surge control line. */
  public static EngineeringMetric compressorRequiredRecycleFraction(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".requiredRecycleFraction", equipmentTag,
        "Required anti-surge recycle fraction", "fraction", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getRequiredRecycleFractionToControlLine();
          }
        });
  }

  /** Creates a maximum recycle-cooler duty associated with anti-surge recycle. */
  public static EngineeringMetric compressorRecycleCoolerDuty(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".recycleCoolerDuty", equipmentTag,
        "Anti-surge recycle cooler duty", "kW", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            Compressor compressor = compressor(process, equipmentTag);
            return compressor.getAntiSurgeRecycleHeatDuty(compressor.getRequiredRecycleFractionToControlLine(), "kW");
          }
        });
  }

  /** Creates a maximum compressor-discharge-temperature metric. */
  public static EngineeringMetric compressorDischargeTemperature(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".dischargeTemperature", equipmentTag,
        "Compressor discharge temperature", "C", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).getOutletStream().getTemperature("C");
          }
        });
  }

  /** Creates a numeric flag that is one when a compressor case extrapolates outside its map. */
  public static EngineeringMetric compressorChartExtrapolationFlag(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".chartExtrapolated", equipmentTag,
        "Compressor chart extrapolation flag", "flag", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            return compressor(process, equipmentTag).isChartExtrapolated() ? 1.0 : 0.0;
          }
        });
  }

  /** Creates a maximum outlet-volume-flow metric for one equipment outlet. */
  public static EngineeringMetric equipmentOutletVolumeFlow(final String equipmentTag, final int outletIndex,
      String outletName) {
    final String normalizedName = requireText(outletName, "outletName");
    return new EngineeringMetric(equipmentTag + "." + normalizedName + "VolumeFlow", equipmentTag,
        normalizedName + " actual volume flow", "m3/s", GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            List<StreamInterface> outlets = requireUnit(process, equipmentTag).getOutletStreams();
            if (outlets == null || outletIndex < 0 || outletIndex >= outlets.size()
                || outlets.get(outletIndex) == null) {
              throw new IllegalStateException("No outlet " + outletIndex + " for " + equipmentTag);
            }
            return outlets.get(outletIndex).getFlowRate("m3/sec");
          }
        });
  }

  /** Creates a maximum required-Cv metric using NeqSim's configured valve sizing method. */
  public static EngineeringMetric controlValveRequiredCv(final String equipmentTag, final double designOpeningPct) {
    return new EngineeringMetric(equipmentTag + ".requiredCv", equipmentTag, "Required control-valve Cv", "Cv",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof ThrottlingValve)) {
              throw new IllegalArgumentException(equipmentTag + " is not a ThrottlingValve");
            }
            Object value = ((ThrottlingValve) equipment).getMechanicalDesign().getValveSizingMethod()
                .calcValveSize(designOpeningPct).get("Cv");
            if (!(value instanceof Number)) {
              throw new IllegalStateException("Valve sizing did not return a numeric Cv for " + equipmentTag);
            }
            return ((Number) value).doubleValue();
          }
        });
  }

  /** Creates a maximum absolute thermal-duty metric for heaters, coolers, and heat exchangers. */
  public static EngineeringMetric equipmentDuty(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".duty", equipmentTag, "Absolute thermal duty", "kW",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof Heater)) {
              throw new IllegalArgumentException(equipmentTag + " is not a Heater/Cooler/HeatExchanger");
            }
            return Math.abs(((Heater) equipment).getDuty("kW"));
          }
        });
  }

  /** Creates a maximum pump shaft-power metric. */
  public static EngineeringMetric pumpPower(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".power", equipmentTag, "Pump shaft power", "kW",
        GoverningDirection.MAXIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof Pump)) {
              throw new IllegalArgumentException(equipmentTag + " is not a Pump");
            }
            return ((Pump) equipment).getPower("kW");
          }
        });
  }

  /** Creates a minimum NPSH margin metric for a pump. */
  public static EngineeringMetric pumpNpshMargin(final String equipmentTag) {
    return new EngineeringMetric(equipmentTag + ".npshMargin", equipmentTag, "NPSH margin", "m",
        GoverningDirection.MINIMUM, new Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
            if (!(equipment instanceof Pump)) {
              throw new IllegalArgumentException(equipmentTag + " is not a Pump");
            }
            return ((Pump) equipment).getNPSHAvailable() - ((Pump) equipment).getNPSHRequired();
          }
        });
  }

  /** Adds optional lower and upper acceptance limits used by the design-case matrix. */
  public EngineeringMetric setAcceptanceRange(Double lowerLimit, Double upperLimit) {
    if (lowerLimit != null && !Double.isFinite(lowerLimit.doubleValue())) {
      throw new IllegalArgumentException("lowerLimit must be finite");
    }
    if (upperLimit != null && !Double.isFinite(upperLimit.doubleValue())) {
      throw new IllegalArgumentException("upperLimit must be finite");
    }
    if (lowerLimit != null && upperLimit != null && lowerLimit.doubleValue() > upperLimit.doubleValue()) {
      throw new IllegalArgumentException("lowerLimit must not exceed upperLimit");
    }
    lowerAcceptanceLimit = lowerLimit;
    upperAcceptanceLimit = upperLimit;
    return this;
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

  public Double getLowerAcceptanceLimit() {
    return lowerAcceptanceLimit;
  }

  public Double getUpperAcceptanceLimit() {
    return upperAcceptanceLimit;
  }

  public String assess(double value) {
    if (lowerAcceptanceLimit != null && value < lowerAcceptanceLimit.doubleValue()) {
      return "BELOW_LOWER_LIMIT";
    }
    if (upperAcceptanceLimit != null && value > upperAcceptanceLimit.doubleValue()) {
      return "ABOVE_UPPER_LIMIT";
    }
    return lowerAcceptanceLimit == null && upperAcceptanceLimit == null ? "NOT_CONFIGURED" : "WITHIN_LIMITS";
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("subjectTag", subjectTag);
    result.put("name", name);
    result.put("unit", unit);
    result.put("governingDirection", governingDirection.name());
    if (lowerAcceptanceLimit != null) {
      result.put("lowerAcceptanceLimit", lowerAcceptanceLimit);
    }
    if (upperAcceptanceLimit != null) {
      result.put("upperAcceptanceLimit", upperAcceptanceLimit);
    }
    return result;
  }

  private static ProcessEquipmentInterface requireUnit(ProcessSystem process, String equipmentTag) {
    ProcessEquipmentInterface unit = process.getUnit(equipmentTag);
    if (unit == null) {
      throw new IllegalArgumentException("Unknown equipment " + equipmentTag);
    }
    return unit;
  }

  private static Compressor compressor(ProcessSystem process, String equipmentTag) {
    ProcessEquipmentInterface equipment = requireUnit(process, equipmentTag);
    if (!(equipment instanceof Compressor)) {
      throw new IllegalArgumentException(equipmentTag + " is not a Compressor");
    }
    return (Compressor) equipment;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
