package neqsim.process.mechanicaldesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Estimates interconnecting piping weight and material for a process system.
 *
 * <p>
 * This class analyzes the stream connections in a {@link ProcessSystem} and estimates:
 * </p>
 * <ul>
 * <li>Pipe sizes based on stream flow rates and velocity limits</li>
 * <li>Pipe wall thicknesses based on design pressure per ASME B31.3</li>
 * <li>Pipe length estimates between equipment</li>
 * <li>Total piping weight by size/schedule</li>
 * <li>Valve and fitting counts using typical ratios</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>ASME B31.3 - Process Piping</li>
 * <li>API RP 14E - Recommended Practice for Design and Installation of Offshore Production
 * Piping</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessInterconnectionDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // Design Constants
  // ============================================================================

  /** Steel density [kg/m³]. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Maximum gas velocity [m/s] - erosional limit. */
  private static final double MAX_GAS_VELOCITY = 20.0;

  /** Maximum liquid velocity [m/s]. */
  private static final double MAX_LIQUID_VELOCITY = 3.0;

  /** Maximum two-phase velocity [m/s]. */
  private static final double MAX_TWOPHASE_VELOCITY = 10.0;

  /** Allowable stress for carbon steel [MPa]. */
  private static final double ALLOWABLE_STRESS = 137.9;

  /** Weld joint efficiency. */
  private static final double JOINT_EFFICIENCY = 0.85;

  /** Corrosion allowance [mm]. */
  private static final double CORROSION_ALLOWANCE = 3.0;

  /** Standard pipe sizes in inches (NPS). */
  private static final double[] STANDARD_PIPE_SIZES = {0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0,
      10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 30.0, 36.0, 42.0, 48.0};

  /** Pipe outside diameters in mm corresponding to NPS. */
  private static final double[] PIPE_OD_MM = {21.3, 26.7, 33.4, 48.3, 60.3, 88.9, 114.3, 168.3,
      219.1, 273.1, 323.9, 355.6, 406.4, 457.2, 508.0, 609.6, 762.0, 914.4, 1066.8, 1219.2};

  // ============================================================================
  // Process System Reference
  // ============================================================================

  private ProcessSystem processSystem;

  // ============================================================================
  // Piping Segment Data
  // ============================================================================

  /** List of pipe segments. */
  private List<PipeSegment> pipeSegments = new ArrayList<PipeSegment>();

  /** Total piping weight [kg]. */
  private double totalPipingWeight = 0.0;

  /** Total piping length [m]. */
  private double totalPipingLength = 0.0;

  /** Weight by pipe size. */
  private Map<String, Double> weightBySize = new LinkedHashMap<String, Double>();

  /** Length by pipe size. */
  private Map<String, Double> lengthBySize = new LinkedHashMap<String, Double>();

  /** Total number of valves. */
  private int totalValveCount = 0;

  /** Total number of flanges. */
  private int totalFlangeCount = 0;

  /** Total number of elbows. */
  private int totalElbowCount = 0;

  /** Total number of tees. */
  private int totalTeeCount = 0;

  /** Estimated valve weight [kg]. */
  private double valveWeight = 0.0;

  /** Estimated flange weight [kg]. */
  private double flangeWeight = 0.0;

  /** Estimated fitting weight [kg]. */
  private double fittingWeight = 0.0;

  // ============================================================================
  // Pipe Segment Inner Class
  // ============================================================================

  /**
   * Represents a pipe segment between two equipment items.
   */
  public static class PipeSegment implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;

    private String fromEquipment;
    private String toEquipment;
    private String streamName;
    private double nominalSizeInch;
    private double outsideDiameterMm;
    private double wallThicknessMm;
    private double lengthM;
    private double weightKg;
    private double designPressureBara;
    private double designTemperatureC;
    private String schedule;
    private String material;
    private boolean isGasService;

    /**
     * Get the from equipment name.
     *
     * @return from equipment name
     */
    public String getFromEquipment() {
      return fromEquipment;
    }

    /**
     * Set the from equipment name.
     *
     * @param fromEquipment equipment name
     */
    public void setFromEquipment(String fromEquipment) {
      this.fromEquipment = fromEquipment;
    }

    /**
     * Get the to equipment name.
     *
     * @return to equipment name
     */
    public String getToEquipment() {
      return toEquipment;
    }

    /**
     * Set the to equipment name.
     *
     * @param toEquipment equipment name
     */
    public void setToEquipment(String toEquipment) {
      this.toEquipment = toEquipment;
    }

    /**
     * Get the stream name.
     *
     * @return stream name
     */
    public String getStreamName() {
      return streamName;
    }

    /**
     * Set the stream name.
     *
     * @param streamName stream name
     */
    public void setStreamName(String streamName) {
      this.streamName = streamName;
    }

    /**
     * Get the nominal pipe size in inches.
     *
     * @return nominal size in inches
     */
    public double getNominalSizeInch() {
      return nominalSizeInch;
    }

    /**
     * Set the nominal pipe size.
     *
     * @param nominalSizeInch size in inches
     */
    public void setNominalSizeInch(double nominalSizeInch) {
      this.nominalSizeInch = nominalSizeInch;
    }

    /**
     * Get the outside diameter.
     *
     * @return outside diameter in mm
     */
    public double getOutsideDiameterMm() {
      return outsideDiameterMm;
    }

    /**
     * Set the outside diameter.
     *
     * @param outsideDiameterMm diameter in mm
     */
    public void setOutsideDiameterMm(double outsideDiameterMm) {
      this.outsideDiameterMm = outsideDiameterMm;
    }

    /**
     * Get the wall thickness.
     *
     * @return wall thickness in mm
     */
    public double getWallThicknessMm() {
      return wallThicknessMm;
    }

    /**
     * Set the wall thickness.
     *
     * @param wallThicknessMm thickness in mm
     */
    public void setWallThicknessMm(double wallThicknessMm) {
      this.wallThicknessMm = wallThicknessMm;
    }

    /**
     * Get the pipe length.
     *
     * @return length in meters
     */
    public double getLengthM() {
      return lengthM;
    }

    /**
     * Set the pipe length.
     *
     * @param lengthM length in meters
     */
    public void setLengthM(double lengthM) {
      this.lengthM = lengthM;
    }

    /**
     * Get the pipe weight.
     *
     * @return weight in kg
     */
    public double getWeightKg() {
      return weightKg;
    }

    /**
     * Set the pipe weight.
     *
     * @param weightKg weight in kg
     */
    public void setWeightKg(double weightKg) {
      this.weightKg = weightKg;
    }

    /**
     * Get the design pressure.
     *
     * @return design pressure in bara
     */
    public double getDesignPressureBara() {
      return designPressureBara;
    }

    /**
     * Set the design pressure.
     *
     * @param designPressureBara pressure in bara
     */
    public void setDesignPressureBara(double designPressureBara) {
      this.designPressureBara = designPressureBara;
    }

    /**
     * Get the design temperature.
     *
     * @return design temperature in °C
     */
    public double getDesignTemperatureC() {
      return designTemperatureC;
    }

    /**
     * Set the design temperature.
     *
     * @param designTemperatureC temperature in °C
     */
    public void setDesignTemperatureC(double designTemperatureC) {
      this.designTemperatureC = designTemperatureC;
    }

    /**
     * Get the pipe schedule.
     *
     * @return schedule designation
     */
    public String getSchedule() {
      return schedule;
    }

    /**
     * Set the pipe schedule.
     *
     * @param schedule schedule designation
     */
    public void setSchedule(String schedule) {
      this.schedule = schedule;
    }

    /**
     * Get the pipe material.
     *
     * @return material designation
     */
    public String getMaterial() {
      return material;
    }

    /**
     * Set the pipe material.
     *
     * @param material material designation
     */
    public void setMaterial(String material) {
      this.material = material;
    }

    /**
     * Check if gas service.
     *
     * @return true if gas service
     */
    public boolean isGasService() {
      return isGasService;
    }

    /**
     * Set gas service flag.
     *
     * @param isGasService true for gas service
     */
    public void setGasService(boolean isGasService) {
      this.isGasService = isGasService;
    }

    @Override
    public String toString() {
      return String.format("%s -> %s: %.0f\" x %.1fmm x %.1fm = %.0fkg", fromEquipment, toEquipment,
          nominalSizeInch, wallThicknessMm, lengthM, weightKg);
    }
  }

  // ============================================================================
  // Constructor
  // ============================================================================

  /**
   * Constructor for ProcessInterconnectionDesign.
   *
   * @param processSystem the process system to analyze
   */
  public ProcessInterconnectionDesign(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  // ============================================================================
  // Main Calculation Method
  // ============================================================================

  /**
   * Calculate piping estimates for all streams in the process.
   */
  public void calculatePipingEstimates() {
    // Reset accumulators
    pipeSegments.clear();
    totalPipingWeight = 0.0;
    totalPipingLength = 0.0;
    weightBySize.clear();
    lengthBySize.clear();
    totalValveCount = 0;
    totalFlangeCount = 0;
    totalElbowCount = 0;
    totalTeeCount = 0;
    valveWeight = 0.0;
    flangeWeight = 0.0;
    fittingWeight = 0.0;

    // Iterate through all unit operations to find streams
    ArrayList<String> names = processSystem.getAllUnitNames();
    for (String name : names) {
      ProcessEquipmentInterface equipment = processSystem.getUnit(name);
      if (equipment == null) {
        continue;
      }

      // Check if this equipment has output streams that connect to other equipment
      analyzeEquipmentConnections(equipment);
    }

    // Calculate fittings based on typical ratios
    calculateFittingEstimates();
  }

  /**
   * Analyze connections from an equipment item.
   *
   * @param equipment the equipment to analyze
   */
  private void analyzeEquipmentConnections(ProcessEquipmentInterface equipment) {
    // For simplicity, we'll estimate piping based on stream properties
    // In a full implementation, this would trace actual connections

    try {
      // Check if equipment has an outlet stream
      if (equipment instanceof neqsim.process.equipment.TwoPortEquipment) {
        neqsim.process.equipment.TwoPortEquipment twoPort =
            (neqsim.process.equipment.TwoPortEquipment) equipment;
        StreamInterface outStream = twoPort.getOutletStream();
        if (outStream != null && outStream.getThermoSystem() != null) {
          PipeSegment segment = createPipeSegment(equipment.getName(), "Downstream", outStream);
          if (segment != null) {
            pipeSegments.add(segment);
            accumulateSegment(segment);
          }
        }
      }
    } catch (Exception e) {
      // Ignore equipment that doesn't have proper connections
    }
  }

  /**
   * Create a pipe segment from stream properties.
   *
   * @param fromEquip from equipment name
   * @param toEquip to equipment name
   * @param stream the stream
   * @return pipe segment or null if unable to create
   */
  private PipeSegment createPipeSegment(String fromEquip, String toEquip, StreamInterface stream) {
    try {
      if (stream.getThermoSystem() == null) {
        return null;
      }

      double pressure = stream.getPressure("bara");
      double temperature = stream.getTemperature("C");
      double volumeFlow = stream.getFlowRate("m3/hr");

      if (volumeFlow <= 0) {
        return null;
      }

      // Determine if gas or liquid service
      boolean isGas = stream.getThermoSystem().hasPhaseType("gas");
      boolean isLiquid = stream.getThermoSystem().hasPhaseType("oil")
          || stream.getThermoSystem().hasPhaseType("aqueous");
      boolean isTwoPhase = isGas && isLiquid;

      // Select velocity limit
      double maxVelocity;
      if (isTwoPhase) {
        maxVelocity = MAX_TWOPHASE_VELOCITY;
      } else if (isGas) {
        maxVelocity = MAX_GAS_VELOCITY;
      } else {
        maxVelocity = MAX_LIQUID_VELOCITY;
      }

      // Calculate required pipe ID
      double volumeFlowM3s = volumeFlow / 3600.0;
      double requiredAreaM2 = volumeFlowM3s / maxVelocity;
      double requiredIdMm = 2.0 * Math.sqrt(requiredAreaM2 / Math.PI) * 1000.0;

      // Find appropriate pipe size
      double nominalSize = 0.0;
      double outsideDiameter = 0.0;
      for (int i = 0; i < STANDARD_PIPE_SIZES.length; i++) {
        // Approximate ID = OD - 2 * wall (assume schedule 40)
        double approxId = PIPE_OD_MM[i] * 0.85;
        if (approxId >= requiredIdMm) {
          nominalSize = STANDARD_PIPE_SIZES[i];
          outsideDiameter = PIPE_OD_MM[i];
          break;
        }
      }
      if (nominalSize == 0.0) {
        nominalSize = STANDARD_PIPE_SIZES[STANDARD_PIPE_SIZES.length - 1];
        outsideDiameter = PIPE_OD_MM[PIPE_OD_MM.length - 1];
      }

      // Calculate wall thickness per ASME B31.3
      double designPressure = pressure * 1.1;
      double designTemperature = temperature + 30.0;
      double wallThickness = calculateWallThickness(outsideDiameter, designPressure);

      // Estimate pipe length (rule of thumb: 10-30m between equipment)
      double estimatedLength = 15.0 + nominalSize * 0.5;

      // Calculate weight
      double idMm = outsideDiameter - 2.0 * wallThickness;
      double pipeAreaM2 =
          Math.PI / 4.0 * (Math.pow(outsideDiameter / 1000.0, 2) - Math.pow(idMm / 1000.0, 2));
      double weight = pipeAreaM2 * estimatedLength * STEEL_DENSITY;

      // Create segment
      PipeSegment segment = new PipeSegment();
      segment.setFromEquipment(fromEquip);
      segment.setToEquipment(toEquip);
      segment.setStreamName(stream.getName());
      segment.setNominalSizeInch(nominalSize);
      segment.setOutsideDiameterMm(outsideDiameter);
      segment.setWallThicknessMm(wallThickness);
      segment.setLengthM(estimatedLength);
      segment.setWeightKg(weight);
      segment.setDesignPressureBara(designPressure);
      segment.setDesignTemperatureC(designTemperature);
      segment.setSchedule(selectSchedule(designPressure));
      segment.setMaterial("A106 Gr.B");
      segment.setGasService(isGas && !isLiquid);

      return segment;

    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Calculate wall thickness per ASME B31.3.
   *
   * @param outsideDiameterMm outside diameter in mm
   * @param designPressureBara design pressure in bara
   * @return wall thickness in mm
   */
  private double calculateWallThickness(double outsideDiameterMm, double designPressureBara) {
    // t = P * D / (2 * S * E + 0.8 * P)
    double pressureMPa = designPressureBara * 0.1;
    double thickness = (pressureMPa * outsideDiameterMm)
        / (2.0 * ALLOWABLE_STRESS * JOINT_EFFICIENCY + 0.8 * pressureMPa);

    // Add corrosion allowance
    thickness += CORROSION_ALLOWANCE;

    // Minimum practical thickness
    return Math.max(3.0, thickness);
  }

  /**
   * Select pipe schedule based on pressure.
   *
   * @param designPressureBara design pressure in bara
   * @return schedule designation
   */
  private String selectSchedule(double designPressureBara) {
    if (designPressureBara > 150) {
      return "XXS";
    } else if (designPressureBara > 100) {
      return "160";
    } else if (designPressureBara > 60) {
      return "80";
    } else if (designPressureBara > 30) {
      return "40";
    } else {
      return "STD";
    }
  }

  /**
   * Accumulate segment data.
   *
   * @param segment the segment to accumulate
   */
  private void accumulateSegment(PipeSegment segment) {
    totalPipingWeight += segment.getWeightKg();
    totalPipingLength += segment.getLengthM();

    String sizeKey = String.format("%.0f\"", segment.getNominalSizeInch());

    Double currentWeight = weightBySize.get(sizeKey);
    if (currentWeight == null) {
      currentWeight = 0.0;
    }
    weightBySize.put(sizeKey, currentWeight + segment.getWeightKg());

    Double currentLength = lengthBySize.get(sizeKey);
    if (currentLength == null) {
      currentLength = 0.0;
    }
    lengthBySize.put(sizeKey, currentLength + segment.getLengthM());
  }

  /**
   * Calculate fitting estimates based on typical ratios.
   */
  private void calculateFittingEstimates() {
    // Typical ratios from industry experience
    // Valves: ~3-5 per pipe segment
    // Flanges: 2 per valve + 2 per equipment connection
    // Elbows: ~2-4 per pipe run
    // Tees: ~0.5-1 per pipe run

    int numSegments = pipeSegments.size();

    totalValveCount = numSegments * 4;
    totalFlangeCount = totalValveCount * 2 + numSegments * 2;
    totalElbowCount = numSegments * 3;
    totalTeeCount = (int) (numSegments * 0.5);

    // Estimate weights (rough approximations)
    double avgValveWeight = 50.0; // kg per valve
    double avgFlangeWeight = 15.0; // kg per flange pair
    double avgFittingWeight = 10.0; // kg per elbow/tee

    valveWeight = totalValveCount * avgValveWeight;
    flangeWeight = totalFlangeCount * avgFlangeWeight;
    fittingWeight = (totalElbowCount + totalTeeCount) * avgFittingWeight;

    // Add to total piping weight
    totalPipingWeight += valveWeight + flangeWeight + fittingWeight;
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Get list of pipe segments.
   *
   * @return list of pipe segments
   */
  public List<PipeSegment> getPipeSegments() {
    return new ArrayList<PipeSegment>(pipeSegments);
  }

  /**
   * Get total piping weight.
   *
   * @return total weight in kg
   */
  public double getTotalPipingWeight() {
    return totalPipingWeight;
  }

  /**
   * Get total piping length.
   *
   * @return total length in meters
   */
  public double getTotalPipingLength() {
    return totalPipingLength;
  }

  /**
   * Get weight breakdown by pipe size.
   *
   * @return map of size to weight in kg
   */
  public Map<String, Double> getWeightBySize() {
    return new LinkedHashMap<String, Double>(weightBySize);
  }

  /**
   * Get length breakdown by pipe size.
   *
   * @return map of size to length in meters
   */
  public Map<String, Double> getLengthBySize() {
    return new LinkedHashMap<String, Double>(lengthBySize);
  }

  /**
   * Get total valve count.
   *
   * @return number of valves
   */
  public int getTotalValveCount() {
    return totalValveCount;
  }

  /**
   * Get total flange count.
   *
   * @return number of flanges
   */
  public int getTotalFlangeCount() {
    return totalFlangeCount;
  }

  /**
   * Get total elbow count.
   *
   * @return number of elbows
   */
  public int getTotalElbowCount() {
    return totalElbowCount;
  }

  /**
   * Get total tee count.
   *
   * @return number of tees
   */
  public int getTotalTeeCount() {
    return totalTeeCount;
  }

  /**
   * Get valve weight.
   *
   * @return valve weight in kg
   */
  public double getValveWeight() {
    return valveWeight;
  }

  /**
   * Get flange weight.
   *
   * @return flange weight in kg
   */
  public double getFlangeWeight() {
    return flangeWeight;
  }

  /**
   * Get fitting weight.
   *
   * @return fitting weight in kg
   */
  public double getFittingWeight() {
    return fittingWeight;
  }

  /**
   * Generate a piping summary report.
   *
   * @return formatted report string
   */
  public String generatePipingReport() {
    StringBuilder sb = new StringBuilder();
    String separator = repeat("=", 60);
    String subSeparator = repeat("-", 60);

    sb.append(separator).append("\n");
    sb.append("INTERCONNECTING PIPING ESTIMATE\n");
    sb.append(separator).append("\n\n");

    sb.append("SUMMARY\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("Total Piping Weight:       %.0f kg (%.1f tonnes)\n", totalPipingWeight,
        totalPipingWeight / 1000.0));
    sb.append(String.format("Total Piping Length:       %.0f m\n", totalPipingLength));
    sb.append(String.format("Number of Pipe Segments:   %d\n", pipeSegments.size()));
    sb.append(String.format("Number of Valves:          %d\n", totalValveCount));
    sb.append(String.format("Number of Flanges:         %d\n", totalFlangeCount));
    sb.append(String.format("Number of Elbows:          %d\n", totalElbowCount));
    sb.append(String.format("Number of Tees:            %d\n", totalTeeCount));
    sb.append("\n");

    sb.append("WEIGHT BY PIPE SIZE\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("%-10s %15s %15s\n", "Size", "Length (m)", "Weight (kg)"));
    for (String size : weightBySize.keySet()) {
      sb.append(String.format("%-10s %15.0f %15.0f\n", size, lengthBySize.get(size),
          weightBySize.get(size)));
    }
    sb.append("\n");

    sb.append("COMPONENT WEIGHTS\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("Pipe:                      %.0f kg\n",
        totalPipingWeight - valveWeight - flangeWeight - fittingWeight));
    sb.append(String.format("Valves:                    %.0f kg\n", valveWeight));
    sb.append(String.format("Flanges:                   %.0f kg\n", flangeWeight));
    sb.append(String.format("Fittings (Elbows/Tees):    %.0f kg\n", fittingWeight));
    sb.append("\n");

    sb.append(separator).append("\n");

    return sb.toString();
  }

  /**
   * Repeat a string n times (Java 8 compatible).
   *
   * @param str string to repeat
   * @param count number of times
   * @return repeated string
   */
  private static String repeat(String str, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }
}
