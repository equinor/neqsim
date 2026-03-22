package neqsim.process.mechanicaldesign;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.motor.MotorMechanicalDesign;

/**
 * Generates a combined equipment design report covering mechanical, electrical, and motor design.
 *
 * <p>
 * This class aggregates the results from:
 * </p>
 * <ul>
 * <li>{@link MechanicalDesign} — process vessel/equipment sizing, wall thickness, weights</li>
 * <li>{@link ElectricalDesign} — motor selection, VFD, cable, switchgear, transformer</li>
 * <li>{@link MotorMechanicalDesign} — motor foundation, vibration, cooling, bearings, noise</li>
 * </ul>
 *
 * <p>
 * The report is equipment-agnostic and works with any process equipment that has mechanical and/or
 * electrical design requirements (compressors, pumps, fans, agitators, etc.).
 * </p>
 *
 * <p>
 * <strong>Standards coverage</strong>
 * </p>
 * <table>
 * <caption>Standards referenced in equipment design report</caption>
 * <tr>
 * <th>Discipline</th>
 * <th>Standards</th>
 * </tr>
 * <tr>
 * <td>Mechanical</td>
 * <td>ASME VIII, API 617/610/521, NORSOK P-001/P-002</td>
 * </tr>
 * <tr>
 * <td>Electrical</td>
 * <td>IEC 60034, IEC 60502, IEC 61439, IEEE 841, NORSOK E-001</td>
 * </tr>
 * <tr>
 * <td>Motor mech.</td>
 * <td>IEC 60034-14, ISO 10816-3, ISO 281, NORSOK S-002</td>
 * </tr>
 * <tr>
 * <td>Safety</td>
 * <td>IEC 60079 (ATEX/IECEx), IEC 61936</td>
 * </tr>
 * </table>
 *
 * <p>
 * <strong>Usage example</strong>
 * </p>
 *
 * <pre>
 * // After running a compressor
 * Compressor comp = new Compressor("export", feed);
 * comp.run();
 *
 * EquipmentDesignReport report = new EquipmentDesignReport(comp);
 * report.setUseVFD(true);
 * report.setRatedVoltageV(6600);
 * report.setHazardousZone(1);
 * report.setAmbientTemperatureC(45.0);
 * report.setAltitudeM(500.0);
 * report.generateReport();
 *
 * String json = report.toJson();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EquipmentDesignReport implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process equipment. */
  private ProcessEquipmentInterface equipment;

  /** Mechanical design (may already exist on equipment). */
  private MechanicalDesign mechanicalDesign;

  /** Electrical design. */
  private ElectricalDesign electricalDesign;

  /** Motor mechanical design. */
  private MotorMechanicalDesign motorMechanicalDesign;

  // === Configuration ===
  /** Whether to use a VFD. */
  private boolean useVFD = false;

  /** Supply voltage in volts. */
  private double ratedVoltageV = 400.0;

  /** Supply frequency in Hz. */
  private double frequencyHz = 50.0;

  /** Hazardous zone (-1 = safe, 0, 1, 2). */
  private int hazardousZone = -1;

  /** Gas group for Ex-rating (IIA, IIB, IIC). */
  private String gasGroup = "IIA";

  /** Ambient temperature in Celsius. */
  private double ambientTemperatureC = 40.0;

  /** Installation altitude in meters. */
  private double altitudeM = 0.0;

  /** Motor standard: "IEC" or "NEMA". */
  private String motorStandard = "IEC";

  /** Motor number of poles. */
  private int motorPoles = 4;

  /** Motor sizing margin factor. */
  private double motorSizingMargin = 1.10;

  /** Cable length from MCC to motor in meters. */
  private double cableLengthM = 50.0;

  /** Whether report has been generated. */
  private boolean reportGenerated = false;

  /** Report verdict. */
  private String verdict = "";

  /** Report issues and warnings. */
  private List<String> issues = new ArrayList<String>();

  /**
   * Constructor with process equipment.
   *
   * @param equipment the process equipment to generate design report for
   */
  public EquipmentDesignReport(ProcessEquipmentInterface equipment) {
    this.equipment = equipment;
  }

  /**
   * Generate the complete design report.
   *
   * <p>
   * Runs mechanical design (if not already done), electrical design, and motor mechanical design.
   * Aggregates results and generates verdict.
   * </p>
   */
  public void generateReport() {
    issues.clear();
    reportGenerated = true;

    // 1. Mechanical design
    mechanicalDesign = equipment.getMechanicalDesign();
    if (mechanicalDesign != null) {
      try {
        mechanicalDesign.calcDesign();
      } catch (Exception e) {
        issues.add("Mechanical design calculation failed: " + e.getMessage());
      }
    }

    // 2. Electrical design
    electricalDesign = equipment.getElectricalDesign();
    if (electricalDesign != null) {
      electricalDesign.setRatedVoltageV(ratedVoltageV);
      electricalDesign.setFrequencyHz(frequencyHz);
      electricalDesign.setUseVFD(useVFD);
      electricalDesign.setMotorSizingMargin(motorSizingMargin);
      electricalDesign.setMotorStandard(motorStandard);
      if (electricalDesign.getPowerCable() != null) {
        electricalDesign.getPowerCable().setLengthM(cableLengthM);
      }
      if (electricalDesign.getHazArea() != null) {
        String zoneStr = hazardousZone < 0 ? "Safe area" : "Zone " + hazardousZone;
        electricalDesign.getHazArea().setZone(zoneStr);
        electricalDesign.getHazArea().setGasGroup(gasGroup);
      }

      // Set shaft power from mechanical design if available
      if (mechanicalDesign != null && mechanicalDesign.getPower() > 0) {
        electricalDesign.setShaftPowerKW(mechanicalDesign.getPower());
      }

      try {
        electricalDesign.calcDesign();
      } catch (Exception e) {
        issues.add("Electrical design calculation failed: " + e.getMessage());
      }
    }

    // 3. Motor mechanical design (only if there's a motor)
    if (electricalDesign != null && electricalDesign.getMotor() != null
        && electricalDesign.getMotor().getRatedPowerKW() > 0) {
      motorMechanicalDesign = new MotorMechanicalDesign(electricalDesign);
      motorMechanicalDesign.setAmbientTemperatureC(ambientTemperatureC);
      motorMechanicalDesign.setAltitudeM(altitudeM);
      motorMechanicalDesign.setHazardousZone(hazardousZone);
      motorMechanicalDesign.setGasGroup(gasGroup);
      motorMechanicalDesign.setMotorStandard(motorStandard);
      motorMechanicalDesign.setPoles(motorPoles);
      motorMechanicalDesign.setHasVFD(useVFD);

      try {
        motorMechanicalDesign.calcDesign();
      } catch (Exception e) {
        issues.add("Motor mechanical design calculation failed: " + e.getMessage());
      }
    }

    // 4. Generate verdict
    evaluateVerdict();
  }

  /**
   * Evaluate overall design verdict based on all checks.
   */
  private void evaluateVerdict() {
    boolean hasWarning = false;
    boolean hasBlocker = false;

    // Check motor noise vs NORSOK S-002
    if (motorMechanicalDesign != null) {
      if (!motorMechanicalDesign.isNoiseWithinNorsokLimit()) {
        issues.add("WARNING: Motor noise exceeds NORSOK S-002 limit of 83 dB(A) at 1m");
        hasWarning = true;
      }

      // Check bearing life
      if (motorMechanicalDesign.getBearingL10LifeHours() > 0
          && motorMechanicalDesign.getBearingL10LifeHours() < 26280) {
        issues.add("WARNING: Bearing L10 life below IEEE 841 minimum of 3 years (26280 hours)");
        hasWarning = true;
      }

      // Check derating
      if (motorMechanicalDesign.getCombinedDeratingFactor() < 0.8) {
        issues.add("WARNING: Significant motor derating ("
            + String.format("%.0f", motorMechanicalDesign.getCombinedDeratingFactor() * 100)
            + "%) due to altitude/temperature");
        hasWarning = true;
      }

      // Add motor-specific notes
      for (String note : motorMechanicalDesign.getDesignNotes()) {
        issues.add("NOTE: " + note);
      }
    }

    // Check electrical design
    if (electricalDesign != null) {
      if (electricalDesign.getElectricalInputKW() > 0 && electricalDesign.getMotor() != null
          && electricalDesign.getMotor().getRatedPowerKW() > 0) {
        double loadFactor =
            electricalDesign.getShaftPowerKW() / electricalDesign.getMotor().getRatedPowerKW();
        if (loadFactor > 1.0) {
          issues.add("BLOCKER: Motor undersized — shaft power exceeds motor rating");
          hasBlocker = true;
        } else if (loadFactor < 0.5) {
          issues.add("WARNING: Motor oversized — operating below 50% load reduces efficiency");
          hasWarning = true;
        }
      }
    }

    if (hasBlocker) {
      verdict = "NOT_FEASIBLE";
    } else if (hasWarning) {
      verdict = "FEASIBLE_WITH_WARNINGS";
    } else {
      verdict = "FEASIBLE";
    }
  }

  /**
   * Serialize the complete design report to JSON.
   *
   * @return comprehensive JSON string with mechanical, electrical, and motor design data
   */
  public String toJson() {
    if (!reportGenerated) {
      generateReport();
    }

    JsonObject root = new JsonObject();

    // Header
    root.addProperty("equipmentName", equipment.getName());
    root.addProperty("equipmentType", equipment.getClass().getSimpleName());
    root.addProperty("verdict", verdict);

    // Issues
    JsonArray issuesArray = new JsonArray();
    for (String issue : issues) {
      issuesArray.add(issue);
    }
    root.add("issues", issuesArray);

    // Mechanical design
    if (mechanicalDesign != null) {
      JsonObject mechObj = new JsonObject();
      mechObj.addProperty("equipmentWeightKg", mechanicalDesign.getWeightTotal());
      mechObj.addProperty("innerDiameterM", mechanicalDesign.innerDiameter);
      mechObj.addProperty("outerDiameterM", mechanicalDesign.outerDiameter);
      mechObj.addProperty("wallThicknessMm", mechanicalDesign.wallThickness);
      mechObj.addProperty("tantanLengthM", mechanicalDesign.tantanLength);
      mechObj.addProperty("maxDesignPowerKW", mechanicalDesign.getPower());
      mechObj.addProperty("moduleLengthM", mechanicalDesign.moduleLength);
      mechObj.addProperty("moduleWidthM", mechanicalDesign.moduleWidth);
      mechObj.addProperty("moduleHeightM", mechanicalDesign.moduleHeight);
      root.add("mechanicalDesign", mechObj);
    }

    // Electrical design
    if (electricalDesign != null) {
      JsonObject elecObj = new JsonObject();
      elecObj.addProperty("shaftPowerKW", electricalDesign.getShaftPowerKW());
      elecObj.addProperty("electricalInputKW", electricalDesign.getElectricalInputKW());
      elecObj.addProperty("apparentPowerKVA", electricalDesign.getApparentPowerKVA());
      elecObj.addProperty("reactivePowerKVAR", electricalDesign.getReactivePowerKVAR());
      elecObj.addProperty("powerFactor", electricalDesign.getPowerFactor());
      elecObj.addProperty("ratedVoltageV", electricalDesign.getRatedVoltageV());
      elecObj.addProperty("frequencyHz", electricalDesign.getFrequencyHz());
      elecObj.addProperty("fullLoadCurrentA", electricalDesign.getFullLoadCurrentA());
      elecObj.addProperty("startingCurrentA", electricalDesign.getStartingCurrentA());
      elecObj.addProperty("totalLossesKW", electricalDesign.getTotalElectricalLossesKW());
      elecObj.addProperty("useVFD", electricalDesign.isUseVFD());

      // Motor data
      if (electricalDesign.getMotor() != null) {
        String motorJson = electricalDesign.getMotor().toJson();
        elecObj.add("motor", JsonParser.parseString(motorJson));
      }

      // VFD data
      if (electricalDesign.getVfd() != null) {
        String vfdJson = electricalDesign.getVfd().toJson();
        elecObj.add("vfd", JsonParser.parseString(vfdJson));
      }

      // Cable data
      if (electricalDesign.getPowerCable() != null) {
        String cableJson = electricalDesign.getPowerCable().toJson();
        elecObj.add("powerCable", JsonParser.parseString(cableJson));
      }

      // Switchgear data
      if (electricalDesign.getSwitchgear() != null) {
        String sgJson = electricalDesign.getSwitchgear().toJson();
        elecObj.add("switchgear", JsonParser.parseString(sgJson));
      }

      // Hazardous area
      if (electricalDesign.getHazArea() != null) {
        String hazJson = electricalDesign.getHazArea().toJson();
        elecObj.add("hazardousArea", JsonParser.parseString(hazJson));
      }

      root.add("electricalDesign", elecObj);
    }

    // Motor mechanical design
    if (motorMechanicalDesign != null) {
      String motorMechJson = motorMechanicalDesign.toJson();
      root.add("motorMechanicalDesign", JsonParser.parseString(motorMechJson));
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Generate a summary map for load list integration.
   *
   * @return map with key electrical load parameters
   */
  public Map<String, Object> toLoadListEntry() {
    Map<String, Object> entry = new LinkedHashMap<String, Object>();
    entry.put("equipmentName", equipment.getName());
    entry.put("equipmentType", equipment.getClass().getSimpleName());

    if (electricalDesign != null) {
      entry.put("ratedMotorPowerKW",
          electricalDesign.getMotor() != null ? electricalDesign.getMotor().getRatedPowerKW() : 0);
      entry.put("absorbedPowerKW", electricalDesign.getShaftPowerKW());
      entry.put("electricalInputKW", electricalDesign.getElectricalInputKW());
      entry.put("apparentPowerKVA", electricalDesign.getApparentPowerKVA());
      entry.put("powerFactor", electricalDesign.getPowerFactor());
      entry.put("ratedVoltageV", electricalDesign.getRatedVoltageV());
      entry.put("hasVFD", electricalDesign.isUseVFD());
    }

    return entry;
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Sets whether to use a VFD.
   *
   * @param useVFD true to use VFD
   */
  public void setUseVFD(boolean useVFD) {
    this.useVFD = useVFD;
  }

  /**
   * Gets whether VFD is used.
   *
   * @return true if VFD is used
   */
  public boolean isUseVFD() {
    return useVFD;
  }

  /**
   * Sets the supply voltage.
   *
   * @param ratedVoltageV voltage in volts
   */
  public void setRatedVoltageV(double ratedVoltageV) {
    this.ratedVoltageV = ratedVoltageV;
  }

  /**
   * Gets the supply voltage.
   *
   * @return voltage in volts
   */
  public double getRatedVoltageV() {
    return ratedVoltageV;
  }

  /**
   * Sets the supply frequency.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Sets the hazardous area zone.
   *
   * @param hazardousZone zone (-1=safe, 0, 1, or 2)
   */
  public void setHazardousZone(int hazardousZone) {
    this.hazardousZone = hazardousZone;
  }

  /**
   * Gets the hazardous area zone.
   *
   * @return zone number
   */
  public int getHazardousZone() {
    return hazardousZone;
  }

  /**
   * Sets the gas group for Ex classification.
   *
   * @param gasGroup gas group (IIA, IIB, IIC)
   */
  public void setGasGroup(String gasGroup) {
    this.gasGroup = gasGroup;
  }

  /**
   * Sets the ambient temperature.
   *
   * @param ambientTemperatureC ambient temperature in Celsius
   */
  public void setAmbientTemperatureC(double ambientTemperatureC) {
    this.ambientTemperatureC = ambientTemperatureC;
  }

  /**
   * Sets the installation altitude.
   *
   * @param altitudeM altitude in meters
   */
  public void setAltitudeM(double altitudeM) {
    this.altitudeM = altitudeM;
  }

  /**
   * Sets the motor standard.
   *
   * @param motorStandard "IEC" or "NEMA"
   */
  public void setMotorStandard(String motorStandard) {
    this.motorStandard = motorStandard;
  }

  /**
   * Sets the motor number of poles.
   *
   * @param motorPoles number of poles (2, 4, 6, 8)
   */
  public void setMotorPoles(int motorPoles) {
    this.motorPoles = motorPoles;
  }

  /**
   * Sets the motor sizing margin.
   *
   * @param margin sizing margin (e.g. 1.10 for 10%)
   */
  public void setMotorSizingMargin(double margin) {
    this.motorSizingMargin = margin;
  }

  /**
   * Sets the cable length from MCC to motor.
   *
   * @param cableLengthM cable length in meters
   */
  public void setCableLengthM(double cableLengthM) {
    this.cableLengthM = cableLengthM;
  }

  /**
   * Gets the verdict.
   *
   * @return "FEASIBLE", "FEASIBLE_WITH_WARNINGS", or "NOT_FEASIBLE"
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Gets the list of issues and warnings.
   *
   * @return list of issues
   */
  public List<String> getIssues() {
    return issues;
  }

  /**
   * Gets the mechanical design.
   *
   * @return mechanical design, or null
   */
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /**
   * Gets the electrical design.
   *
   * @return electrical design, or null
   */
  public ElectricalDesign getElectricalDesign() {
    return electricalDesign;
  }

  /**
   * Gets the motor mechanical design.
   *
   * @return motor mechanical design, or null
   */
  public MotorMechanicalDesign getMotorMechanicalDesign() {
    return motorMechanicalDesign;
  }

  /**
   * Checks if the report has been generated.
   *
   * @return true if report generated
   */
  public boolean isReportGenerated() {
    return reportGenerated;
  }
}
