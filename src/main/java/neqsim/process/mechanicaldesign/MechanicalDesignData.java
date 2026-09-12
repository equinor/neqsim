package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.adsorber.AdsorberMechanicalDesign;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;
import neqsim.process.mechanicaldesign.distillation.DistillationColumnMechanicalDesign;
import neqsim.process.mechanicaldesign.filter.FilterMechanicalDesign;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerSizingResult;
import neqsim.process.mechanicaldesign.membrane.MembraneMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

/**
 * Versioned snapshot for preliminary geometry and downstream design calculations.
 *
 * <p>
 * Every quantity declares its unit, source and availability. Geometry is in metres, pressures are absolute Pa,
 * temperatures are K and powers are W. Availability does not assert that a design was run, is current, meets a
 * standard, or is fabrication-ready. The caller must run the process and design calculation after changing inputs.
 * Export is side-effect free.
 * </p>
 */
public final class MechanicalDesignData implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String schemaVersion = "1.0";
  private final String intendedUse = "preliminary_geometry_and_design_review";
  private final String calculationStatus = "not_tracked";
  private String equipmentName;
  private String equipmentClass;
  private String designClass;
  private String selectedEquipmentType;
  private String geometryKind = "unspecified";
  private String geometryConsistency = "incomplete";
  private String orientation;
  private String headType;
  private final Map<String, Quantity> geometry = new LinkedHashMap<String, Quantity>();
  private final Map<String, Quantity> operatingConditions = new LinkedHashMap<String, Quantity>();
  private final Map<String, Quantity> designBasis = new LinkedHashMap<String, Quantity>();
  private final List<String> limitations = new ArrayList<String>();

  /** A numeric value together with its dimensional and availability contract. */
  public static final class Quantity implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Double value;
    private final String unit;
    private final String source;
    private final String status;

    private Quantity(double value, String unit, String source, boolean positive, String availableStatus) {
      boolean available = unit != null && Double.isFinite(value) && (!positive || value > 0.0);
      this.value = available ? value : null;
      this.unit = unit;
      this.source = source;
      this.status = available ? availableStatus : "unavailable";
    }
  }

  /**
   * Capture current values without running or mutating the equipment.
   *
   * @param design mechanical design to export
   */
  public MechanicalDesignData(MechanicalDesign design) {
    if (design == null) {
      throw new IllegalArgumentException("Mechanical design is required");
    }
    designClass = design.getClass().getSimpleName();
    ProcessEquipmentInterface equipment = design.getProcessEquipment();
    if (equipment != null) {
      equipmentName = equipment.getName();
      equipmentClass = equipment.getClass().getSimpleName();
    }
    limitations.add("Run process and calcDesign after every input change; calculation freshness is not tracked.");
    limitations.add(
        "Getter snapshots may contain configured defaults or empirical estimates; availability is not design approval.");
    limitations
        .add("No nozzle positions, head profile, supports, weld details or fabrication tolerances are inferred.");
    double thickness = design.getWallThickness();
    String thicknessUnit = wallThicknessUnit(design);
    if ("mm".equals(thicknessUnit)) {
      thickness /= 1000.0;
    }
    put(geometry, "innerDiameter", design.getInnerDiameter(), "m", "getInnerDiameter", true);
    put(geometry, "outerDiameter", design.getOuterDiameter(), "m", "getOuterDiameter", true);
    put(geometry, "wallThickness", thickness, thicknessUnit == null ? null : "m", "getWallThickness", true);
    put(geometry, "tangentLength", design.getTantanLength(), "m", "getTantanLength", true);
    put(geometry, "moduleLength", design.getModuleLength(), "m", "getModuleLength", true);
    put(geometry, "moduleWidth", design.getModuleWidth(), "m", "getModuleWidth", true);
    put(geometry, "moduleHeight", design.getModuleHeight(), "m", "getModuleHeight", true);
    designBasis.put("maximumOperatingPressure", new Quantity(design.getMaxOperationPressure() * 1e5, "Pa",
        "getMaxOperationPressure (bara)", true, "configured_or_default"));
    designBasis.put("maximumDesignPressure", new Quantity(design.getMaxDesignPressure() * 1e5, "Pa",
        "getMaxDesignPressure (bara)", true, "configured_or_default"));
    designBasis.put("maximumDesignTemperature", new Quantity(design.getDesignMaxTemperatureLimit("K"), "K",
        "getDesignMaxTemperatureLimit(K)", true, "configured_or_default"));
    put(designBasis, "corrosionAllowance", design.getCorrosionAllowance() / 1000.0, "m", "getCorrosionAllowance (mm)",
        false);

    if (equipment instanceof TwoPortInterface) {
      TwoPortInterface ports = (TwoPortInterface) equipment;
      stream("inlet", ports.getInletStream());
      stream("outlet", ports.getOutletStream());
    } else if (equipment instanceof Separator) {
      Separator separator = (Separator) equipment;
      int index = 0;
      for (StreamInterface inlet : separator.getInletStreams()) {
        stream("inlet" + index++, inlet);
      }
      stream("gasOutlet", separator.getGasOutStream());
    } else if (equipment instanceof HeatExchanger) {
      HeatExchanger exchanger = (HeatExchanger) equipment;
      for (int i = 0; i < 2; i++) {
        stream("inlet" + i, exchanger.getInStream(i));
        stream("outlet" + i, exchanger.getOutStream(i));
      }
    }

    if (design instanceof SeparatorMechanicalDesign) {
      geometryKind = "cylindrical_shell";
      put(geometry, "headThickness", Double.NaN, "m", "not specified by the separator design model", true);
      if (equipment instanceof Separator) {
        orientation = ((Separator) equipment).getOrientation();
      }
      SeparatorMechanicalDesign separatorDesign = (SeparatorMechanicalDesign) design;
      put(geometry, "inletNozzleInnerDiameter", separatorDesign.getInletNozzleID(), "m", "getInletNozzleID", true);
      put(geometry, "gasOutletNozzleInnerDiameter", separatorDesign.getGasOutletNozzleID(), "m", "getGasOutletNozzleID",
          true);
      put(geometry, "liquidOutletNozzleInnerDiameter", separatorDesign.getOilOutletNozzleID(), "m",
          "getOilOutletNozzleID", true);
      put(geometry, "normalLiquidLevel", separatorDesign.getNLL(), "m", "getNLL", false);
    } else if (design instanceof CompressorMechanicalDesign) {
      geometryKind = "compressor_envelope";
      CompressorMechanicalDesign compressorDesign = (CompressorMechanicalDesign) design;
      put(geometry, "impellerDiameter", compressorDesign.getImpellerDiameter() / 1000.0, "m",
          "getImpellerDiameter (mm)", true);
      put(geometry, "shaftDiameter", compressorDesign.getShaftDiameter() / 1000.0, "m", "getShaftDiameter (mm)", true);
      put(geometry, "bearingSpan", compressorDesign.getBearingSpan() / 1000.0, "m", "getBearingSpan (mm)", true);
      pressureAndTemperature(compressorDesign.getDesignPressure(), compressorDesign.getDesignTemperature());
      CompressorCasingDesignCalculator casing = compressorDesign.getCasingDesignCalculator();
      if (casing != null) {
        put(geometry, "pressureCasingInnerDiameter", casing.getCasingInnerDiameterMm() / 1000.0, "m",
            "getCasingDesignCalculator.getCasingInnerDiameterMm", true);
        put(geometry, "pressureCasingWallThickness", casing.getSelectedWallThicknessMm() / 1000.0, "m",
            "getCasingDesignCalculator.getSelectedWallThicknessMm", true);
      }
      if (equipment instanceof Compressor) {
        Compressor compressor = (Compressor) equipment;
        put(operatingConditions, "polytropicEfficiency", compressor.getPolytropicEfficiency(), "1",
            "Compressor.getPolytropicEfficiency", true);
        put(operatingConditions, "power", compressor.getPower("W"), "W", "Compressor.getPower(W)", false);
      }
      limitations.add(
          "Compressor wallThickness is an envelope gap; use pressureCasingWallThickness for the casing calculation result.");
    } else if (design instanceof PumpMechanicalDesign) {
      geometryKind = "pump_envelope";
      PumpMechanicalDesign pump = (PumpMechanicalDesign) design;
      put(geometry, "impellerDiameter", pump.getImpellerDiameter() / 1000.0, "m", "getImpellerDiameter (mm)", true);
      put(geometry, "shaftDiameter", pump.getShaftDiameter() / 1000.0, "m", "getShaftDiameter (mm)", true);
      pressureAndTemperature(pump.getDesignPressure(), pump.getDesignTemperature());
    } else if (design instanceof PipelineMechanicalDesign) {
      geometryKind = "straight_pipe_section";
      put(designBasis, "designWallThickness", thickness, "m", "getWallThickness (mm), sizing result after calcDesign",
          true);
      if (equipment instanceof PipeLineInterface) {
        PipeLineInterface pipe = (PipeLineInterface) equipment;
        put(geometry, "innerDiameter", pipe.getDiameter(), "m", "PipeLineInterface.getDiameter", true);
        put(geometry, "wallThickness", pipe.getWallThickness(), "m", "PipeLineInterface.getWallThickness", true);
        put(geometry, "outerDiameter",
            pipe.getWallThickness() > 0.0 ? pipe.getDiameter() + 2.0 * pipe.getWallThickness() : Double.NaN, "m",
            "PipeLineInterface.getDiameter + 2 * getWallThickness", true);
        put(geometry, "length", pipe.getLength(), "m", "PipeLineInterface.getLength", true);
      }
      limitations.add("Pipeline routing, bends and elevation are not represented by a straight section.");
    } else if (design instanceof ValveMechanicalDesign) {
      geometryKind = "valve_envelope";
      ValveMechanicalDesign valve = (ValveMechanicalDesign) design;
      put(geometry, "faceToFace", valve.getFaceToFace() / 1000.0, "m", "getFaceToFace (mm)", true);
      put(geometry, "stemDiameter", valve.getStemDiameter() / 1000.0, "m", "getStemDiameter (mm)", true);
      pressureAndTemperature(valve.getDesignPressure(), valve.getDesignTemperature());
    } else if (design instanceof HeatExchangerMechanicalDesign) {
      geometryKind = "heat_exchanger_envelope";
      HeatExchangerSizingResult sizing = ((HeatExchangerMechanicalDesign) design).getSelectedSizingResult();
      if (sizing != null) {
        selectedEquipmentType = sizing.getType().name();
        put(designBasis, "requiredArea", sizing.getRequiredArea(), "m2", "getSelectedSizingResult.getRequiredArea",
            true);
        put(designBasis, "requiredUA", sizing.getRequiredUA(), "W/K", "getSelectedSizingResult.getRequiredUA", true);
      }
      limitations.add(
          "Exchanger dimensions may be equivalent envelope dimensions; consult the selected exchanger type before meshing.");
    } else if (design instanceof DistillationColumnMechanicalDesign) {
      geometryKind = "column_envelope";
      put(geometry, "height", ((DistillationColumnMechanicalDesign) design).getColumnHeight(), "m", "getColumnHeight",
          true);
    }
    if (equipment instanceof Heater) {
      put(operatingConditions, "duty", ((Heater) equipment).getDuty("W"), "W", "Heater.getDuty(W)", false);
    } else if (equipment instanceof HeatExchanger) {
      put(operatingConditions, "duty", ((HeatExchanger) equipment).getDuty(), "W", "HeatExchanger.getDuty", false);
    }
    Quantity inner = geometry.get("innerDiameter");
    Quantity outer = geometry.get("outerDiameter");
    Quantity wall = geometry.get("wallThickness");
    if (inner.value != null && outer.value != null && wall.value != null) {
      double error = Math.abs(outer.value - inner.value - 2.0 * wall.value);
      geometryConsistency = outer.value > inner.value && error <= 1e-8 * Math.max(1.0, outer.value) ? "consistent"
          : "inconsistent";
      if ("inconsistent".equals(geometryConsistency)) {
        limitations.add(
            "Inner diameter, outer diameter and wall thickness are inconsistent; do not construct a pressure shell from them.");
      }
    }
    if (thicknessUnit == null) {
      limitations.add(
          "Legacy wall-thickness unit is not qualified for this design class; normalized thickness is unavailable.");
    }
  }

  private void stream(String prefix, StreamInterface stream) {
    put(operatingConditions, prefix + "Pressure", stream == null ? Double.NaN : stream.getPressure("Pa"), "Pa",
        prefix + "Stream.getPressure(Pa), absolute", true);
    put(operatingConditions, prefix + "Temperature", stream == null ? Double.NaN : stream.getTemperature("K"), "K",
        prefix + "Stream.getTemperature(K)", true);
    put(operatingConditions, prefix + "MassFlow", stream == null ? Double.NaN : stream.getFlowRate("kg/sec"), "kg/s",
        prefix + "Stream.getFlowRate(kg/sec)", false);
  }

  private void pressureAndTemperature(double pressureBara, double temperatureC) {
    // Keep the normalized pressure field in SI while preserving its source unit in the provenance.
    put(designBasis, "maximumDesignPressure", pressureBara > 0.0 ? pressureBara * 1e5 : Double.NaN, "Pa",
        "getDesignPressure (bara)", true);
    put(designBasis, "maximumDesignTemperature", pressureBara > 0.0 ? temperatureC + 273.15 : Double.NaN, "K",
        "getDesignTemperature (C)", true);
  }

  private static void put(Map<String, Quantity> fields, String name, double value, String unit, String source,
      boolean positive) {
    fields.put(name, new Quantity(value, unit, source, positive, "available"));
  }

  /**
   * Identify the verified unit of the existing, unmodified wall-thickness getter.
   *
   * @param design mechanical design
   * @return m, mm, or null when the unit is not qualified
   */
  public static String wallThicknessUnit(MechanicalDesign design) {
    if (design instanceof PumpMechanicalDesign || design instanceof PipelineMechanicalDesign
        || design instanceof DistillationColumnMechanicalDesign || design instanceof MembraneMechanicalDesign) {
      return "mm";
    }
    if (design instanceof SeparatorMechanicalDesign || design instanceof CompressorMechanicalDesign
        || design instanceof HeatExchangerMechanicalDesign || design instanceof ValveMechanicalDesign
        || design instanceof FilterMechanicalDesign || design instanceof AdsorberMechanicalDesign) {
      return "m";
    }
    return null;
  }

  /**
   * Serialize the snapshot using strict JSON, retaining explicit nulls.
   *
   * @return versioned JSON snapshot
   */
  public String toJson() {
    return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(this);
  }
}
