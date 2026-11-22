package neqsim.process.engineering.pid;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiStream;

/**
 * Drawing and exchange values for one engineering case. Only the mechanical-design copy is sized.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class PidSizing {
  /** Physical properties with the official DEXPI unit and the drawing legend. */
  public enum Dimension {
    /** Vessel shell internal diameter. */
    INSIDE_DIAMETER("InsideDiameter", "LengthUnit.Millimetre", "ID", "mm"),
    /** Straight shell length between tangents. */
    TANGENT_LENGTH("Length", "LengthUnit.Millimetre", "T/T", "mm"),
    /** Required heat-transfer area with design margin. */
    AREA("DesignHeatTransferArea", "AreaUnit.MetreSquared", "Area", "m2"),
    /** Pump rated volume flow. */
    FLOW("DesignVolumeFlowRate", "VolumeFlowRateUnit.MetreCubedPerHour", "Rated flow", "m3/h"),
    /** Pump rated head. */
    HEAD("DesignPressureHead", "LengthUnit.Metre", "Head", "m"),
    /** Preliminary inlet stage impeller. */
    IMPELLER("Diameter", "LengthUnit.Millimetre", "Inlet impeller", "mm");

    private final String property;
    private final String unit;
    private final String label;
    private final String symbol;

    Dimension(String property, String unit, String label, String symbol) {
      this.property = property;
      this.unit = unit;
      this.label = label;
      this.symbol = symbol;
    }

    /** @return native data property */
    public String getProperty() {
      return property;
    }

    /** @return native physical unit reference */
    public String getUnit() {
      return "Core/PhysicalQuantities." + unit;
    }
  }

  private final Map<String, Map<Dimension, Double>> equipment = new LinkedHashMap<>();
  private final Map<String, String> nominal = new LinkedHashMap<>();
  private final Map<String, String> pipingClass = new LinkedHashMap<>();
  private final Map<String, String> calculatedSchedule = new LinkedHashMap<>();
  private final Map<String, String> equipmentHolds = new LinkedHashMap<>();

  /**
   * Resolves supplied line designations and, when enabled, screens the current solved case.
   *
   * @param project source engineering project
   */
  public PidSizing(EngineeringProject project) {
    Map<String, LineDesignInput> inputs = new LinkedHashMap<>();
    for (LineDesignInput line : project.getLineDesignInputs()) {
      if (inputs.put(line.getLineTag(), line) != null) {
        throw new IllegalArgumentException("Ambiguous project line-design tag: " + line.getLineTag());
      }
      if (!line.getNominalPipeSize().isEmpty()) {
        String value = line.getNominalPipeSize();
        nominal.put(line.getLineTag(), value.matches("[0-9]+([./ ][0-9]+)*") ? "NPS " + value : value);
      }
      if (!line.getPipingClass().isEmpty()) {
        pipingClass.put(line.getLineTag(), line.getPipingClass());
      }
    }
    ProcessSystem process = project.getEngineeringProcessSystem();
    String schedule = project.getPidSizingSchedule();
    if (schedule != null && !process.solved()) {
      throw new IllegalStateException("Run and converge the process before requesting P&ID sizing");
    }
    Map<String, StreamInterface> streams = new LinkedHashMap<>();
    Set<StreamInterface> relief = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        continue;
      }
      for (StreamInterface stream : unit.getInletStreams()) {
        register(streams, stream);
      }
      for (StreamInterface stream : unit.getOutletStreams()) {
        register(streams, stream);
      }
      if (unit instanceof SafetyValve || unit instanceof SafetyReliefValve || unit instanceof RuptureDisk) {
        relief.addAll(unit.getInletStreams());
        relief.addAll(unit.getOutletStreams());
      }
    }
    NorsokP002LineSizingValidator rule = project.getPidLineSizingRule();
    for (Map.Entry<String, StreamInterface> entry : streams.entrySet()) {
      String tag = entry.getKey();
      StreamInterface stream = entry.getValue();
      if (stream == null) {
        if (schedule != null || inputs.containsKey(tag)) {
          throw new IllegalArgumentException("Distinct material streams share P&ID line tag: " + tag);
        }
        continue;
      }
      if (stream instanceof DexpiStream) {
        DexpiStream imported = (DexpiStream) stream;
        putImported(nominal, tag, imported.getNominalDiameterRepresentation());
        putImported(pipingClass, tag, imported.getPipingClassCode());
      }
      if (schedule != null && !nominal.containsKey(tag) && !relief.contains(stream)) {
        LineDesignInput input = inputs.get(tag);
        String selected = input == null || input.getSchedule().isEmpty() ? schedule : input.getSchedule();
        OptionalDouble nps = rule.recommendNominalPipeSize(stream.getFluid(), selected);
        if (nps.isPresent()) {
          nominal.put(tag, nominalDesignation(nps.getAsDouble()));
          calculatedSchedule.put(tag, selected);
        }
      }
    }
    if (schedule != null) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        sizeEquipment(unit);
      }
    }
  }

  /**
   * Formats the catalogued fractional NPS in conventional inches.
   * 
   * @param nps nominal size from the pipe schedule
   * @return readable NPS designation
   */
  private static String nominalDesignation(double nps) {
    int whole = (int) nps;
    int eighths = (int) Math.round(8 * (nps - whole));
    if (eighths == 0) {
      return "NPS " + whole;
    }
    int divisor = Integer.lowestOneBit(eighths);
    return "NPS " + (whole == 0 ? "" : whole + " ") + eighths / divisor + "/" + 8 / divisor;
  }

  /**
   * Retains an ambiguity marker for distinct streams with the same drawing tag.
   * 
   * @param streams stream index
   * @param stream actual port stream
   */
  private static void register(Map<String, StreamInterface> streams, StreamInterface stream) {
    if (stream != null) {
      String tag = stream.getName();
      if (!streams.containsKey(tag)) {
        streams.put(tag, stream);
      } else if (streams.get(tag) != stream) {
        streams.put(tag, null);
      }
    }
  }

  /**
   * Fills absent project information from imported nominal metadata.
   * 
   * @param values target values
   * @param tag stream tag
   * @param value source field
   */
  private static void putImported(Map<String, String> values, String tag, String value) {
    if (value != null && !value.trim().isEmpty()) {
      values.putIfAbsent(tag, value.trim());
    }
  }

  /**
   * Sizes supported equipment on the isolated case.
   * 
   * @param unit source equipment; design and its owner are copied before calculation
   */
  private void sizeEquipment(ProcessEquipmentInterface unit) {
    if (!(unit instanceof Separator || unit instanceof HeatExchanger || unit instanceof Pump
        || unit instanceof Compressor)) {
      return;
    }
    Map<Dimension, Double> values = new LinkedHashMap<>();
    String tag = unit.getName();
    try {
      if (unit.getInletStreams().isEmpty() || !(unit.getInletStreams().get(0).getFlowRate("kg/sec") > 0)) {
        equipmentHolds.put(tag, "Positive inlet flow required");
        return;
      }
      MechanicalDesign design = unit.getMechanicalDesign().copyForSizing();
      design.calcDesign();
      if (unit instanceof Separator) {
        values.put(Dimension.INSIDE_DIAMETER, design.getInnerDiameter() * 1000);
        values.put(Dimension.TANGENT_LENGTH, design.getTantanLength() * 1000);
      } else if (design instanceof PumpMechanicalDesign) {
        PumpMechanicalDesign pump = (PumpMechanicalDesign) design;
        values.put(Dimension.FLOW, pump.getRatedFlow());
        values.put(Dimension.HEAD, pump.getRatedHead());
      } else if (design instanceof HeatExchangerMechanicalDesign && Math.abs(((HeatExchanger) unit).getDuty()) > 0) {
        HeatExchangerMechanicalDesign exchanger = (HeatExchangerMechanicalDesign) design;
        if (exchanger.getSelectedSizingResult() != null) {
          values.put(Dimension.AREA,
              exchanger.getSelectedSizingResult().getRequiredArea() * exchanger.getAreaMarginFactor());
        }
      } else if (design instanceof CompressorMechanicalDesign) {
        CompressorMechanicalDesign compressor = (CompressorMechanicalDesign) design;
        if (!compressor.isImpellerSizingFeasible()) {
          equipmentHolds.put(tag, String.join(" ", compressor.getImpellerSizingIssues()));
          return;
        }
        values.put(Dimension.IMPELLER, compressor.getImpellerDiameter());
      }
      if (values.isEmpty() || values.values().stream().anyMatch(value -> !(value > 0) || !Double.isFinite(value))) {
        equipmentHolds.put(tag, "No finite positive design result");
      } else {
        equipment.put(tag, Collections.unmodifiableMap(values));
      }
    } catch (RuntimeException ex) {
      equipmentHolds.put(tag, ex.toString());
    }
  }

  /**
   * @param tag line tag
   * @return nominal designation or empty string
   */
  public String nominal(String tag) {
    return nominal.getOrDefault(tag, "");
  }

  /**
   * @param tag line tag
   * @return piping class or empty string
   */
  public String pipingClass(String tag) {
    return pipingClass.getOrDefault(tag, "");
  }

  /**
   * @param tag line tag
   * @return schedule used for a calculated proposal, empty for other lines
   */
  public String calculatedSchedule(String tag) {
    return calculatedSchedule.getOrDefault(tag, "");
  }

  /**
   * @param tag equipment tag
   * @return physical quantities in their declared units
   */
  public Map<Dimension, Double> dimensions(String tag) {
    return equipment.getOrDefault(tag, Collections.emptyMap());
  }

  /** @return unresolved mechanical sizing by equipment tag */
  public Map<String, String> getEquipmentHolds() {
    return Collections.unmodifiableMap(equipmentHolds);
  }

  /**
   * @param tag equipment tag
   * @return compact preliminary drawing callout
   */
  public String equipmentLabel(String tag) {
    StringBuilder label = new StringBuilder();
    for (Map.Entry<Dimension, Double> entry : dimensions(tag).entrySet()) {
      Dimension dimension = entry.getKey();
      label
          .append(label.length() == 0 ? "PREL.  " : "  /  ").append(dimension.label).append(" ").append(BigDecimal
              .valueOf(entry.getValue()).round(new java.math.MathContext(4)).stripTrailingZeros().toPlainString())
          .append(" ").append(dimension.symbol);
    }
    return equipmentHolds.containsKey(tag) ? "EQUIPMENT SIZE: HOLD" : label.toString();
  }
}
