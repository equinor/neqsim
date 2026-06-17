package neqsim.process.safety.inventory;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.barrier.DocumentEvidence;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Calculates trapped inventory for a documented isolation envelope.
 *
 * <p>The calculator is intended for technical safety studies where P&amp;ID, line-list,
 * stress isometric, datasheet, or STID evidence defines the isolated equipment and piping volume.
 * It combines those volumes with a NeqSim fluid state to estimate gas and optional liquid inventory
 * before blowdown, relief, flare-load, or MDMT calculations.</p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TrappedInventoryCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<InventorySegment> segments = new ArrayList<InventorySegment>();
  private SystemInterface fluid;
  private double pressureBara = Double.NaN;
  private double temperatureK = Double.NaN;
  private double fallbackLiquidDensityKgPerM3 = 800.0;

  /**
   * Sets the representative fluid for all inventory segments.
   *
   * @param fluid representative fluid with components and mixing rule configured
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if the fluid is null
   */
  public TrappedInventoryCalculator setFluid(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    this.fluid = fluid;
    return this;
  }

  /**
   * Sets operating pressure and temperature for the trapped inventory.
   *
   * @param pressure pressure value in the supplied pressure unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}, for example bara or barg
   * @param temperature temperature value in the supplied temperature unit
   * @param temperatureUnit temperature unit supported by {@link TemperatureUnit}, for example K or C
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if pressure or temperature is non-physical
   */
  public TrappedInventoryCalculator setOperatingConditions(double pressure, String pressureUnit,
      double temperature, String temperatureUnit) {
    this.pressureBara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    this.temperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    if (pressureBara <= 0.0 || temperatureK <= 0.0) {
      throw new IllegalArgumentException("pressure and temperature must be positive absolute values");
    }
    return this;
  }

  /**
   * Sets operating pressure in bara and temperature in K.
   *
   * @param pressureBara absolute pressure in bara; must be positive
   * @param temperatureK absolute temperature in K; must be positive
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if pressure or temperature is non-physical
   */
  public TrappedInventoryCalculator setOperatingConditions(double pressureBara,
      double temperatureK) {
    return setOperatingConditions(pressureBara, "bara", temperatureK, "K");
  }

  /**
   * Sets fallback liquid density used when no liquid phase is present in the representative fluid.
   *
   * @param densityKgPerM3 fallback liquid density in kg/m3; must be positive
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if density is non-positive
   */
  public TrappedInventoryCalculator setFallbackLiquidDensity(double densityKgPerM3) {
    if (densityKgPerM3 <= 0.0) {
      throw new IllegalArgumentException("densityKgPerM3 must be positive");
    }
    this.fallbackLiquidDensityKgPerM3 = densityKgPerM3;
    return this;
  }

  /**
   * Adds a documented equipment or vessel volume.
   *
   * @param equipmentTag equipment tag or segment identifier
   * @param volumeM3 internal volume in m3; must be positive
   * @param liquidFillFraction liquid fill fraction from 0 to 1
   * @param evidence optional traceable document evidence; may be null
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if segment values are invalid
   */
  public TrappedInventoryCalculator addEquipmentVolume(String equipmentTag, double volumeM3,
      double liquidFillFraction, DocumentEvidence evidence) {
    InventorySegment segment = new InventorySegment(equipmentTag, "equipment", volumeM3,
        liquidFillFraction);
    if (evidence != null) {
      segment.addEvidence(evidence);
    }
    segments.add(segment);
    return this;
  }

  /**
   * Adds a documented volume segment using an engineering volume unit.
   *
   * @param segmentId segment identifier
   * @param volume volume value in the supplied unit; must be positive
   * @param volumeUnit volume unit, for example m3, L, litre, ft3, or bbl
   * @param liquidFillFraction liquid fill fraction from 0 to 1
   * @param evidence optional traceable document evidence; may be null
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if segment values are invalid
   */
  public TrappedInventoryCalculator addVolumeSegment(String segmentId, double volume,
      String volumeUnit, double liquidFillFraction, DocumentEvidence evidence) {
    return addEquipmentVolume(segmentId, toCubicMeters(volume, volumeUnit), liquidFillFraction,
        evidence);
  }

  /**
   * Adds a documented pipe segment from diameter and length.
   *
   * @param segmentId pipe segment identifier
   * @param internalDiameterM pipe internal diameter in m; must be positive
   * @param lengthM pipe length in m; must be positive
   * @param liquidFillFraction liquid fill fraction from 0 to 1
   * @param evidence optional traceable document evidence; may be null
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if segment values are invalid
   */
  public TrappedInventoryCalculator addPipeSegment(String segmentId, double internalDiameterM,
      double lengthM, double liquidFillFraction, DocumentEvidence evidence) {
    validatePositive(internalDiameterM, "internalDiameterM");
    validatePositive(lengthM, "lengthM");
    double volumeM3 = Math.PI * internalDiameterM * internalDiameterM * lengthM / 4.0;
    InventorySegment segment = new InventorySegment(segmentId, "pipe", volumeM3,
        liquidFillFraction);
    segment.setInternalDiameterM(internalDiameterM);
    segment.setLengthM(lengthM);
    if (evidence != null) {
      segment.addEvidence(evidence);
    }
    segments.add(segment);
    return this;
  }

  /**
   * Adds a documented pipe segment from diameter and length in engineering units.
   *
   * @param segmentId pipe segment identifier
   * @param internalDiameter pipe internal diameter in the supplied diameter unit
   * @param diameterUnit diameter unit, for example m, mm, in, or ft
   * @param length pipe length in the supplied length unit
   * @param lengthUnit length unit, for example m, mm, in, or ft
   * @param liquidFillFraction liquid fill fraction from 0 to 1
   * @param evidence optional traceable document evidence; may be null
   * @return this calculator for chained setup
   * @throws IllegalArgumentException if segment values are invalid
   */
  public TrappedInventoryCalculator addPipeSegment(String segmentId, double internalDiameter,
      String diameterUnit, double length, String lengthUnit, double liquidFillFraction,
      DocumentEvidence evidence) {
    return addPipeSegment(segmentId, toMeters(internalDiameter, diameterUnit),
        toMeters(length, lengthUnit), liquidFillFraction, evidence);
  }

  /**
   * Calculates trapped gas and liquid inventory for all configured segments.
   *
   * @return calculated inventory result
   * @throws IllegalStateException if required setup is missing
   */
  public InventoryResult calculate() {
    validateSetup();
    SystemInterface stateFluid = prepareFluidState();
    double gasDensity = getGasDensity(stateFluid);
    double liquidDensity = getLiquidDensity(stateFluid);

    List<InventorySegmentResult> segmentResults = new ArrayList<InventorySegmentResult>();
    List<String> warnings = new ArrayList<String>();
    double totalVolume = 0.0;
    double totalGasVolume = 0.0;
    double totalLiquidVolume = 0.0;
    double totalGasMass = 0.0;
    double totalLiquidMass = 0.0;

    for (InventorySegment segment : segments) {
      double gasVolume = segment.getVolumeM3() * (1.0 - segment.getLiquidFillFraction());
      double liquidVolume = segment.getVolumeM3() * segment.getLiquidFillFraction();
      double gasMass = gasVolume * gasDensity;
      double liquidMass = liquidVolume * liquidDensity;
      segmentResults.add(new InventorySegmentResult(segment, gasVolume, liquidVolume, gasMass,
          liquidMass, gasDensity, liquidDensity));
      totalVolume += segment.getVolumeM3();
      totalGasVolume += gasVolume;
      totalLiquidVolume += liquidVolume;
      totalGasMass += gasMass;
      totalLiquidMass += liquidMass;
      if (segment.getEvidence().isEmpty()) {
        warnings.add("Segment " + segment.getId() + " has no traceable document evidence.");
      }
    }
    if (totalLiquidVolume > 0.0 && liquidDensity == fallbackLiquidDensityKgPerM3) {
      warnings.add("No liquid phase found in representative fluid; fallback liquid density used.");
    }

    return new InventoryResult(pressureBara, temperatureK, gasDensity, liquidDensity,
        totalVolume, totalGasVolume, totalLiquidVolume, totalGasMass, totalLiquidMass,
        segmentResults, warnings);
  }

  /**
   * Creates a lumped gas fluid suitable for {@code DepressurizationSimulator} input.
   *
   * <p>The returned fluid is set to the calculated gas inventory because liquid holdup in
   * blocked-in piping usually does not discharge as gas through the blowdown valve. Liquid
   * inventory remains available in {@link InventoryResult} for KO-drum and cold-temperature
   * screening.</p>
   *
   * @return cloned fluid with total moles set from calculated gas mass
   * @throws IllegalStateException if the calculated mass or molar mass is non-positive
   */
  public SystemInterface createDepressurizationFluid() {
    InventoryResult result = calculate();
    SystemInterface lumpedFluid = prepareFluidState();
    double gasMass = result.getTotalGasMassKg();
    if (gasMass <= 0.0) {
      gasMass = result.getTotalMassKg();
    }
    if (gasMass <= 0.0 || lumpedFluid.getMolarMass() <= 0.0) {
      throw new IllegalStateException("calculated mass and fluid molar mass must be positive");
    }
    lumpedFluid.setTotalNumberOfMoles(gasMass / lumpedFluid.getMolarMass());
    ThermodynamicOperations operations = new ThermodynamicOperations(lumpedFluid);
    operations.TPflash();
    lumpedFluid.initProperties();
    return lumpedFluid;
  }

  /**
   * Converts the current inventory calculation to pretty JSON.
   *
   * @return JSON representation of the calculated inventory
   */
  public String toJson() {
    return calculate().toJson();
  }

  /**
   * Checks that all required inputs are present and valid.
   *
   * @throws IllegalStateException if a required input is missing
   */
  private void validateSetup() {
    if (fluid == null) {
      throw new IllegalStateException("representative fluid must be set before calculation");
    }
    if (Double.isNaN(pressureBara) || Double.isNaN(temperatureK)) {
      throw new IllegalStateException("operating pressure and temperature must be set");
    }
    if (segments.isEmpty()) {
      throw new IllegalStateException("at least one inventory segment must be configured");
    }
  }

  /**
   * Creates a flashed clone of the representative fluid at operating conditions.
   *
   * @return cloned and flashed fluid state
   */
  private SystemInterface prepareFluidState() {
    SystemInterface stateFluid = fluid.clone();
    stateFluid.setPressure(pressureBara, "bara");
    stateFluid.setTemperature(temperatureK);
    ThermodynamicOperations operations = new ThermodynamicOperations(stateFluid);
    operations.TPflash();
    stateFluid.initProperties();
    return stateFluid;
  }

  /**
   * Reads gas density from a flashed fluid state.
   *
   * @param stateFluid flashed fluid state
   * @return gas density in kg/m3, or bulk density when no gas phase exists
   */
  private double getGasDensity(SystemInterface stateFluid) {
    if (stateFluid.hasPhaseType("gas")) {
      return stateFluid.getPhase("gas").getDensity("kg/m3");
    }
    return stateFluid.getDensity("kg/m3");
  }

  /**
   * Reads liquid density from a flashed fluid state.
   *
   * @param stateFluid flashed fluid state
   * @return liquid density in kg/m3, or fallback density when no liquid phase exists
   */
  private double getLiquidDensity(SystemInterface stateFluid) {
    if (stateFluid.hasPhaseType("oil")) {
      return stateFluid.getPhase("oil").getDensity("kg/m3");
    }
    if (stateFluid.hasPhaseType("aqueous")) {
      return stateFluid.getPhase("aqueous").getDensity("kg/m3");
    }
    return fallbackLiquidDensityKgPerM3;
  }

  /**
   * Validates that a positive value was provided.
   *
   * @param value value to validate
   * @param name parameter name used in exception messages
   * @throws IllegalArgumentException if value is not positive and finite
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Converts a length value to meters.
   *
   * @param value length value in the supplied unit
   * @param unit length unit
   * @return length in m
   * @throws IllegalArgumentException if the unit is unsupported
   */
  private static double toMeters(double value, String unit) {
    validatePositive(value, "length");
    String normalized = normalizeUnit(unit);
    if ("m".equals(normalized) || "meter".equals(normalized) || "metre".equals(normalized)) {
      return value;
    }
    if ("mm".equals(normalized)) {
      return value / 1000.0;
    }
    if ("cm".equals(normalized)) {
      return value / 100.0;
    }
    if ("in".equals(normalized) || "inch".equals(normalized)) {
      return value * 0.0254;
    }
    if ("ft".equals(normalized) || "foot".equals(normalized)
        || "feet".equals(normalized)) {
      return value * 0.3048;
    }
    throw new IllegalArgumentException("unsupported length unit: " + unit);
  }

  /**
   * Converts a volume value to cubic meters.
   *
   * @param value volume value in the supplied unit
   * @param unit volume unit
   * @return volume in m3
   * @throws IllegalArgumentException if the unit is unsupported
   */
  private static double toCubicMeters(double value, String unit) {
    validatePositive(value, "volume");
    String normalized = normalizeUnit(unit);
    if ("m3".equals(normalized) || "m^3".equals(normalized)) {
      return value;
    }
    if ("l".equals(normalized) || "liter".equals(normalized)
        || "litre".equals(normalized)) {
      return value / 1000.0;
    }
    if ("ft3".equals(normalized) || "ft^3".equals(normalized)) {
      return value * 0.028316846592;
    }
    if ("bbl".equals(normalized)) {
      return value * 0.158987294928;
    }
    throw new IllegalArgumentException("unsupported volume unit: " + unit);
  }

  /**
   * Normalizes a unit string for comparison.
   *
   * @param unit unit string to normalize
   * @return trimmed lower-case unit string
   * @throws IllegalArgumentException if the unit is empty
   */
  private static String normalizeUnit(String unit) {
    if (unit == null || unit.trim().isEmpty()) {
      throw new IllegalArgumentException("unit must not be empty");
    }
    return unit.trim().toLowerCase();
  }

  /**
   * A documented isolated-volume segment.
   */
  public static class InventorySegment implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String type;
    private final double volumeM3;
    private final double liquidFillFraction;
    private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();
    private double internalDiameterM = Double.NaN;
    private double lengthM = Double.NaN;

    /**
     * Creates an inventory segment.
     *
     * @param id segment identifier
     * @param type segment type, for example pipe or equipment
     * @param volumeM3 segment internal volume in m3
     * @param liquidFillFraction liquid fill fraction from 0 to 1
     * @throws IllegalArgumentException if values are invalid
     */
    private InventorySegment(String id, String type, double volumeM3, double liquidFillFraction) {
      if (id == null || id.trim().isEmpty()) {
        throw new IllegalArgumentException("segment id must not be empty");
      }
      validatePositive(volumeM3, "volumeM3");
      if (liquidFillFraction < 0.0 || liquidFillFraction > 1.0) {
        throw new IllegalArgumentException("liquidFillFraction must be in [0,1]");
      }
      this.id = id.trim();
      this.type = type;
      this.volumeM3 = volumeM3;
      this.liquidFillFraction = liquidFillFraction;
    }

    /**
     * Adds traceable document evidence.
     *
     * @param evidence evidence to add; must not be null
     * @return this segment for chained setup
     * @throws IllegalArgumentException if evidence is null
     */
    public InventorySegment addEvidence(DocumentEvidence evidence) {
      if (evidence == null) {
        throw new IllegalArgumentException("evidence must not be null");
      }
      this.evidence.add(evidence);
      return this;
    }

    /**
     * Gets the segment identifier.
     *
     * @return segment identifier
     */
    public String getId() {
      return id;
    }

    /**
     * Gets the segment type.
     *
     * @return segment type
     */
    public String getType() {
      return type;
    }

    /**
     * Gets segment volume.
     *
     * @return internal volume in m3
     */
    public double getVolumeM3() {
      return volumeM3;
    }

    /**
     * Gets liquid fill fraction.
     *
     * @return liquid fill fraction from 0 to 1
     */
    public double getLiquidFillFraction() {
      return liquidFillFraction;
    }

    /**
     * Gets pipe internal diameter when supplied.
     *
     * @return internal diameter in m, or NaN for non-pipe segments
     */
    public double getInternalDiameterM() {
      return internalDiameterM;
    }

    /**
     * Gets pipe length when supplied.
     *
     * @return length in m, or NaN for non-pipe segments
     */
    public double getLengthM() {
      return lengthM;
    }

    /**
     * Gets evidence records.
     *
     * @return immutable evidence list
     */
    public List<DocumentEvidence> getEvidence() {
      return Collections.unmodifiableList(evidence);
    }

    /**
     * Sets pipe geometry metadata.
     *
     * @param internalDiameterM pipe internal diameter in m
     */
    private void setInternalDiameterM(double internalDiameterM) {
      this.internalDiameterM = internalDiameterM;
    }

    /**
     * Sets pipe length metadata.
     *
     * @param lengthM pipe length in m
     */
    private void setLengthM(double lengthM) {
      this.lengthM = lengthM;
    }
  }

  /**
   * Per-segment inventory calculation result.
   */
  public static class InventorySegmentResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final InventorySegment segment;
    private final double gasVolumeM3;
    private final double liquidVolumeM3;
    private final double gasMassKg;
    private final double liquidMassKg;
    private final double gasDensityKgPerM3;
    private final double liquidDensityKgPerM3;

    /**
     * Creates a segment result.
     *
     * @param segment source segment
     * @param gasVolumeM3 gas-filled volume in m3
     * @param liquidVolumeM3 liquid-filled volume in m3
     * @param gasMassKg gas mass in kg
     * @param liquidMassKg liquid mass in kg
     * @param gasDensityKgPerM3 gas density in kg/m3
     * @param liquidDensityKgPerM3 liquid density in kg/m3
     */
    private InventorySegmentResult(InventorySegment segment, double gasVolumeM3,
        double liquidVolumeM3, double gasMassKg, double liquidMassKg,
        double gasDensityKgPerM3, double liquidDensityKgPerM3) {
      this.segment = segment;
      this.gasVolumeM3 = gasVolumeM3;
      this.liquidVolumeM3 = liquidVolumeM3;
      this.gasMassKg = gasMassKg;
      this.liquidMassKg = liquidMassKg;
      this.gasDensityKgPerM3 = gasDensityKgPerM3;
      this.liquidDensityKgPerM3 = liquidDensityKgPerM3;
    }

    /**
     * Gets the segment identifier.
     *
     * @return segment identifier
     */
    public String getSegmentId() {
      return segment.getId();
    }

    /**
     * Gets total segment volume.
     *
     * @return total segment volume in m3
     */
    public double getTotalVolumeM3() {
      return segment.getVolumeM3();
    }

    /**
     * Gets gas-filled volume.
     *
     * @return gas volume in m3
     */
    public double getGasVolumeM3() {
      return gasVolumeM3;
    }

    /**
     * Gets liquid-filled volume.
     *
     * @return liquid volume in m3
     */
    public double getLiquidVolumeM3() {
      return liquidVolumeM3;
    }

    /**
     * Gets gas mass.
     *
     * @return gas mass in kg
     */
    public double getGasMassKg() {
      return gasMassKg;
    }

    /**
     * Gets liquid mass.
     *
     * @return liquid mass in kg
     */
    public double getLiquidMassKg() {
      return liquidMassKg;
    }

    /**
     * Gets total mass.
     *
     * @return total gas plus liquid mass in kg
     */
    public double getTotalMassKg() {
      return gasMassKg + liquidMassKg;
    }

    /**
     * Gets gas density used for this segment.
     *
     * @return gas density in kg/m3
     */
    public double getGasDensityKgPerM3() {
      return gasDensityKgPerM3;
    }

    /**
     * Gets liquid density used for this segment.
     *
     * @return liquid density in kg/m3
     */
    public double getLiquidDensityKgPerM3() {
      return liquidDensityKgPerM3;
    }

    /**
     * Converts the segment result to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("segmentId", segment.getId());
      map.put("type", segment.getType());
      map.put("totalVolumeM3", segment.getVolumeM3());
      map.put("gasVolumeM3", gasVolumeM3);
      map.put("liquidVolumeM3", liquidVolumeM3);
      map.put("gasMassKg", gasMassKg);
      map.put("liquidMassKg", liquidMassKg);
      map.put("totalMassKg", getTotalMassKg());
      map.put("gasDensityKgPerM3", gasDensityKgPerM3);
      map.put("liquidDensityKgPerM3", liquidDensityKgPerM3);
      map.put("liquidFillFraction", segment.getLiquidFillFraction());
      if (!Double.isNaN(segment.getInternalDiameterM())) {
        map.put("internalDiameterM", segment.getInternalDiameterM());
      }
      if (!Double.isNaN(segment.getLengthM())) {
        map.put("lengthM", segment.getLengthM());
      }
      List<Map<String, Object>> evidenceMaps = new ArrayList<Map<String, Object>>();
      for (DocumentEvidence item : segment.getEvidence()) {
        evidenceMaps.add(item.toMap());
      }
      map.put("evidence", evidenceMaps);
      return map;
    }
  }

  /**
   * Aggregate trapped-inventory result.
   */
  public static class InventoryResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double pressureBara;
    private final double temperatureK;
    private final double gasDensityKgPerM3;
    private final double liquidDensityKgPerM3;
    private final double totalVolumeM3;
    private final double totalGasVolumeM3;
    private final double totalLiquidVolumeM3;
    private final double totalGasMassKg;
    private final double totalLiquidMassKg;
    private final List<InventorySegmentResult> segmentResults;
    private final List<String> warnings;

    /**
     * Creates an aggregate result.
     *
     * @param pressureBara absolute pressure in bara
     * @param temperatureK absolute temperature in K
     * @param gasDensityKgPerM3 gas density in kg/m3
     * @param liquidDensityKgPerM3 liquid density in kg/m3
     * @param totalVolumeM3 total internal volume in m3
     * @param totalGasVolumeM3 gas-filled volume in m3
     * @param totalLiquidVolumeM3 liquid-filled volume in m3
     * @param totalGasMassKg gas mass in kg
     * @param totalLiquidMassKg liquid mass in kg
     * @param segmentResults per-segment results
     * @param warnings calculation warnings
     */
    private InventoryResult(double pressureBara, double temperatureK, double gasDensityKgPerM3,
        double liquidDensityKgPerM3, double totalVolumeM3, double totalGasVolumeM3,
        double totalLiquidVolumeM3, double totalGasMassKg, double totalLiquidMassKg,
        List<InventorySegmentResult> segmentResults, List<String> warnings) {
      this.pressureBara = pressureBara;
      this.temperatureK = temperatureK;
      this.gasDensityKgPerM3 = gasDensityKgPerM3;
      this.liquidDensityKgPerM3 = liquidDensityKgPerM3;
      this.totalVolumeM3 = totalVolumeM3;
      this.totalGasVolumeM3 = totalGasVolumeM3;
      this.totalLiquidVolumeM3 = totalLiquidVolumeM3;
      this.totalGasMassKg = totalGasMassKg;
      this.totalLiquidMassKg = totalLiquidMassKg;
      this.segmentResults = new ArrayList<InventorySegmentResult>(segmentResults);
      this.warnings = new ArrayList<String>(warnings);
    }

    /**
     * Gets the absolute pressure.
     *
     * @return pressure in bara
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Gets the absolute temperature.
     *
     * @return temperature in K
     */
    public double getTemperatureK() {
      return temperatureK;
    }

    /**
     * Gets gas density.
     *
     * @return gas density in kg/m3
     */
    public double getGasDensityKgPerM3() {
      return gasDensityKgPerM3;
    }

    /**
     * Gets liquid density.
     *
     * @return liquid density in kg/m3
     */
    public double getLiquidDensityKgPerM3() {
      return liquidDensityKgPerM3;
    }

    /**
     * Gets total internal volume.
     *
     * @return total volume in m3
     */
    public double getTotalVolumeM3() {
      return totalVolumeM3;
    }

    /**
     * Gets total gas-filled volume.
     *
     * @return gas volume in m3
     */
    public double getTotalGasVolumeM3() {
      return totalGasVolumeM3;
    }

    /**
     * Gets total liquid-filled volume.
     *
     * @return liquid volume in m3
     */
    public double getTotalLiquidVolumeM3() {
      return totalLiquidVolumeM3;
    }

    /**
     * Gets total gas mass.
     *
     * @return gas mass in kg
     */
    public double getTotalGasMassKg() {
      return totalGasMassKg;
    }

    /**
     * Gets total liquid mass.
     *
     * @return liquid mass in kg
     */
    public double getTotalLiquidMassKg() {
      return totalLiquidMassKg;
    }

    /**
     * Gets total gas plus liquid mass.
     *
     * @return total mass in kg
     */
    public double getTotalMassKg() {
      return totalGasMassKg + totalLiquidMassKg;
    }

    /**
     * Gets per-segment results.
     *
     * @return immutable list of segment results
     */
    public List<InventorySegmentResult> getSegmentResults() {
      return Collections.unmodifiableList(segmentResults);
    }

    /**
     * Gets calculation warnings.
     *
     * @return immutable list of warnings
     */
    public List<String> getWarnings() {
      return Collections.unmodifiableList(warnings);
    }

    /**
     * Converts the result to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("pressureBara", pressureBara);
      map.put("temperatureK", temperatureK);
      map.put("gasDensityKgPerM3", gasDensityKgPerM3);
      map.put("liquidDensityKgPerM3", liquidDensityKgPerM3);
      map.put("totalVolumeM3", totalVolumeM3);
      map.put("totalGasVolumeM3", totalGasVolumeM3);
      map.put("totalLiquidVolumeM3", totalLiquidVolumeM3);
      map.put("totalGasMassKg", totalGasMassKg);
      map.put("totalLiquidMassKg", totalLiquidMassKg);
      map.put("totalMassKg", getTotalMassKg());
      List<Map<String, Object>> segmentMaps = new ArrayList<Map<String, Object>>();
      for (InventorySegmentResult result : segmentResults) {
        segmentMaps.add(result.toMap());
      }
      map.put("segments", segmentMaps);
      map.put("warnings", new ArrayList<String>(warnings));
      return map;
    }

    /**
     * Converts the result to pretty JSON.
     *
     * @return JSON representation
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }
}
