package neqsim.process.equipment.iec81346;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.ProcessElementInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates IEC 81346 reference designations for all elements in a {@link ProcessSystem} or
 * {@link ProcessModel}.
 *
 * <p>
 * The generator walks the process topology and automatically assigns reference designations based
 * on the three IEC 81346 aspects:
 * </p>
 * <ul>
 * <li><strong>Function aspect</strong> ({@code =}): Derived from the process area name or a
 * user-defined prefix. In a {@link ProcessModel}, each process area becomes a function
 * sub-level.</li>
 * <li><strong>Product aspect</strong> ({@code -}): Derived from the IEC 81346-2 letter code (mapped
 * from equipment type) and a sequence number within that category.</li>
 * <li><strong>Location aspect</strong> ({@code +}): User-defined location prefix, e.g. "P1" for
 * Platform 1.</li>
 * </ul>
 *
 * <p>
 * <strong>Usage example — single ProcessSystem:</strong>
 * </p>
 *
 * <pre>
 * ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
 * gen.setFunctionPrefix("A1");
 * gen.setLocationPrefix("P1.M1");
 * gen.generate();
 *
 * // Each equipment now has a reference designation:
 * String ref = process.getUnit("HP Sep").getReferenceDesignationString();
 * // e.g. "=A1-B1+P1.M1"
 *
 * // Export report as JSON:
 * String json = gen.toJson();
 * </pre>
 *
 * <p>
 * <strong>Usage example — multi-area ProcessModel:</strong>
 * </p>
 *
 * <pre>
 * ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(plant);
 * gen.setLocationPrefix("P1");
 * gen.generate();
 *
 * // Area names are used as function sub-levels:
 * // "Separation" area → =A1 (first area)
 * // "Compression" area → =A2 (second area)
 * // Equipment in Separation: =A1-B1, =A1-B2, =A1-K1
 * // Equipment in Compression: =A2-K1, =A2-K2
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ReferenceDesignationGenerator implements Serializable {

  private static final long serialVersionUID = 1002L;

  /** The single process system to generate designations for (null if multi-area). */
  private ProcessSystem processSystem;

  /** The multi-area process model to generate designations for (null if single-area). */
  private ProcessModel processModel;

  /** User-defined function prefix (without {@code =}), e.g. "A1". */
  private String functionPrefix = "A1";

  /** User-defined location prefix (without {@code +}), e.g. "P1.M1". */
  private String locationPrefix = "";

  /** Whether to assign designations to stream objects. */
  private boolean includeStreams = false;

  /** Whether to assign designations to measurement devices. */
  private boolean includeMeasurementDevices = true;

  /**
   * Whether to use hierarchical function designations in multi-area models. When true, each area
   * function prefix is formed by appending to the top-level function prefix with a dot separator
   * (e.g. "A1.A1", "A1.A2"). When false (default), areas use flat numbering ("A1", "A2").
   */
  private boolean useHierarchicalFunctions = false;

  /** Whether generation has been run. */
  private boolean generated = false;

  /** The generated designation entries, in order. */
  private final List<DesignationEntry> entries = new ArrayList<DesignationEntry>();

  /**
   * Creates a generator for a single process system.
   *
   * @param processSystem the process system to generate designations for
   */
  public ReferenceDesignationGenerator(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.processModel = null;
  }

  /**
   * Creates a generator for a multi-area process model.
   *
   * @param processModel the process model to generate designations for
   */
  public ReferenceDesignationGenerator(ProcessModel processModel) {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    this.processModel = processModel;
    this.processSystem = null;
  }

  /**
   * Creates a generator with no bound system. Use with {@link #generate(ProcessSystem)} or
   * {@link #generate(ProcessModel)} to bind a system at generation time.
   */
  public ReferenceDesignationGenerator() {
    this.processSystem = null;
    this.processModel = null;
  }

  /**
   * Sets the function prefix for the top-level function aspect.
   *
   * <p>
   * For a single {@link ProcessSystem}, this is used directly (e.g. "A1" produces "=A1"). For a
   * {@link ProcessModel}, each area gets a sub-level (e.g. first area becomes "A1", second "A2").
   * </p>
   *
   * @param functionPrefix the function prefix without the {@code =} character
   */
  public void setFunctionPrefix(String functionPrefix) {
    this.functionPrefix = functionPrefix != null ? functionPrefix : "A1";
  }

  /**
   * Returns the current function prefix.
   *
   * @return the function prefix without the {@code =} character
   */
  public String getFunctionPrefix() {
    return functionPrefix;
  }

  /**
   * Sets the location prefix for the location aspect.
   *
   * @param locationPrefix the location prefix without the {@code +} character, e.g. "P1.M1"
   */
  public void setLocationPrefix(String locationPrefix) {
    this.locationPrefix = locationPrefix != null ? locationPrefix : "";
  }

  /**
   * Returns the current location prefix.
   *
   * @return the location prefix without the {@code +} character
   */
  public String getLocationPrefix() {
    return locationPrefix;
  }

  /**
   * Sets whether to include stream objects in designation generation.
   *
   * @param includeStreams true to assign designations to streams
   */
  public void setIncludeStreams(boolean includeStreams) {
    this.includeStreams = includeStreams;
  }

  /**
   * Returns whether stream objects are included in designation generation.
   *
   * @return true if streams are included
   */
  public boolean isIncludeStreams() {
    return includeStreams;
  }

  /**
   * Sets whether to include measurement devices in designation generation.
   *
   * @param includeMeasurementDevices true to assign designations to measurement devices
   */
  public void setIncludeMeasurementDevices(boolean includeMeasurementDevices) {
    this.includeMeasurementDevices = includeMeasurementDevices;
  }

  /**
   * Returns whether measurement devices are included in designation generation.
   *
   * @return true if measurement devices are included
   */
  public boolean isIncludeMeasurementDevices() {
    return includeMeasurementDevices;
  }

  /**
   * Sets whether to use hierarchical function designations in multi-area models. When true, each
   * area function prefix is formed by appending to the top-level function prefix with a dot (e.g.
   * "A1.A1", "A1.A2"). When false (default), areas use flat numbering ("A1", "A2").
   *
   * @param useHierarchicalFunctions true for hierarchical, false for flat
   */
  public void setUseHierarchicalFunctions(boolean useHierarchicalFunctions) {
    this.useHierarchicalFunctions = useHierarchicalFunctions;
  }

  /**
   * Returns whether hierarchical function designations are used.
   *
   * @return true if hierarchical mode is enabled
   */
  public boolean isUseHierarchicalFunctions() {
    return useHierarchicalFunctions;
  }

  /**
   * Generates IEC 81346 reference designations for all elements.
   *
   * <p>
   * After calling this method, each equipment object in the process system or model will have its
   * {@link ReferenceDesignation} set. The generated designations can be retrieved with
   * {@link ProcessEquipmentInterface#getReferenceDesignation()}.
   * </p>
   *
   * <p>
   * Calling this method multiple times will regenerate all designations.
   * </p>
   */
  public void generate() {
    entries.clear();
    generated = false;

    if (processModel != null) {
      generateForModel();
    } else if (processSystem != null) {
      generateForSystem(processSystem, functionPrefix, locationPrefix);
    } else {
      throw new IllegalStateException(
          "No process system or model bound. Use generate(ProcessSystem) or generate(ProcessModel).");
    }

    generated = true;
  }

  /**
   * Binds the given process system and generates designations for it.
   *
   * @param system the process system to generate designations for
   */
  public void generate(ProcessSystem system) {
    if (system == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = system;
    this.processModel = null;
    generate();
  }

  /**
   * Binds the given process model and generates designations for it.
   *
   * @param model the process model to generate designations for
   */
  public void generate(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    this.processModel = model;
    this.processSystem = null;
    generate();
  }

  /**
   * Generates designations for a multi-area process model.
   */
  private void generateForModel() {
    List<String> areaNames = processModel.getProcessSystemNames();
    int areaIndex = 1;

    for (String areaName : areaNames) {
      ProcessSystem areaSystem = processModel.get(areaName);
      if (areaSystem == null) {
        continue;
      }

      // Each area gets a function sub-level
      String areaFunctionPrefix;
      if (useHierarchicalFunctions) {
        // Hierarchical: A1.A1, A1.A2, A1.A3 (nested under top-level functionPrefix)
        areaFunctionPrefix = functionPrefix + ".A" + areaIndex;
      } else {
        // Flat: A1, A2, A3 (default)
        areaFunctionPrefix = "A" + areaIndex;
      }
      generateForSystem(areaSystem, areaFunctionPrefix, locationPrefix);
      areaIndex++;
    }
  }

  /**
   * Generates designations for a single process system.
   *
   * @param system the process system
   * @param funcPrefix the function prefix for this system
   * @param locPrefix the location prefix for this system
   */
  private void generateForSystem(ProcessSystem system, String funcPrefix, String locPrefix) {
    // Counter per letter code for generating sequence numbers
    Map<IEC81346LetterCode, Integer> counters =
        new EnumMap<IEC81346LetterCode, Integer>(IEC81346LetterCode.class);

    for (ProcessEquipmentInterface unit : system.getUnitOperations()) {
      // Skip streams unless explicitly included
      if (!includeStreams && unit instanceof neqsim.process.equipment.stream.StreamInterface) {
        continue;
      }

      IEC81346LetterCode letterCode = IEC81346LetterCode.fromEquipment(unit);
      int seq = incrementCounter(counters, letterCode);

      String productDes = letterCode.name() + seq;
      ReferenceDesignation refDes =
          new ReferenceDesignation(funcPrefix, productDes, locPrefix, letterCode, seq);

      unit.setReferenceDesignation(refDes);

      entries.add(new DesignationEntry(unit.getName(), unit.getClass().getSimpleName(),
          refDes.toReferenceDesignationString(), letterCode, seq, funcPrefix));
    }

    // Process measurement devices if requested
    if (includeMeasurementDevices) {
      generateForMeasurementDevices(system, funcPrefix, locPrefix, counters);
    }

    // Enrich explicit connections with reference designations
    enrichConnections(system);
  }

  /**
   * Enriches any explicit {@link neqsim.process.processmodel.ProcessConnection} objects in the
   * given process system with IEC 81346 reference designation strings. For each connection, the
   * source and target equipment names are looked up and their reference designation strings are
   * copied to the connection metadata.
   *
   * @param system the process system whose connections should be enriched
   */
  private void enrichConnections(ProcessSystem system) {
    for (neqsim.process.processmodel.ProcessConnection conn : system.getConnections()) {
      // Resolve source equipment ref des
      ProcessEquipmentInterface sourceUnit = system.getUnit(conn.getSourceEquipment());
      if (sourceUnit != null) {
        String srcRefDes = sourceUnit.getReferenceDesignationString();
        if (srcRefDes != null && !srcRefDes.isEmpty()) {
          conn.setSourceReferenceDesignation(srcRefDes);
        }
      }
      // Resolve target equipment ref des
      ProcessEquipmentInterface targetUnit = system.getUnit(conn.getTargetEquipment());
      if (targetUnit != null) {
        String tgtRefDes = targetUnit.getReferenceDesignationString();
        if (tgtRefDes != null && !tgtRefDes.isEmpty()) {
          conn.setTargetReferenceDesignation(tgtRefDes);
        }
      }
    }
  }

  /**
   * Generates designations for measurement devices in a process system.
   *
   * @param system the process system
   * @param funcPrefix the function prefix
   * @param locPrefix the location prefix
   * @param counters the letter code counters (shared with equipment)
   */
  private void generateForMeasurementDevices(ProcessSystem system, String funcPrefix,
      String locPrefix, Map<IEC81346LetterCode, Integer> counters) {

    List<ProcessElementInterface> allElements = system.getAllElements();

    for (ProcessElementInterface element : allElements) {
      if (element instanceof MeasurementDeviceInterface) {
        MeasurementDeviceInterface device = (MeasurementDeviceInterface) element;

        // Measurement devices are always classified as S (Sensing)
        IEC81346LetterCode letterCode = IEC81346LetterCode.S;
        int seq = incrementCounter(counters, letterCode);

        String productDes = letterCode.name() + seq;
        ReferenceDesignation refDes =
            new ReferenceDesignation(funcPrefix, productDes, locPrefix, letterCode, seq);

        // Measurement devices don't implement ProcessEquipmentInterface,
        // so we store the designation as the tag number if not already set
        String existingTag = device.getTag();
        if (existingTag == null || existingTag.trim().isEmpty()) {
          device.setTag(refDes.toReferenceDesignationString());
        }

        entries.add(new DesignationEntry(device.getName(), device.getClass().getSimpleName(),
            refDes.toReferenceDesignationString(), letterCode, seq, funcPrefix));
      }
    }
  }

  /**
   * Increments and returns the counter for a given letter code.
   *
   * @param counters the counter map
   * @param letterCode the letter code to increment
   * @return the new sequence number (1-based)
   */
  private int incrementCounter(Map<IEC81346LetterCode, Integer> counters,
      IEC81346LetterCode letterCode) {
    Integer current = counters.get(letterCode);
    int next = (current != null) ? current + 1 : 1;
    counters.put(letterCode, next);
    return next;
  }

  /**
   * Returns the list of all generated designation entries.
   *
   * @return unmodifiable list of designation entries
   */
  public List<DesignationEntry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  /**
   * Returns the number of generated designations.
   *
   * @return the number of entries
   */
  public int getDesignationCount() {
    return entries.size();
  }

  /**
   * Checks if generation has been performed.
   *
   * @return true if {@link #generate()} has been called
   */
  public boolean isGenerated() {
    return generated;
  }

  /**
   * Finds a designation entry by equipment name.
   *
   * @param equipmentName the equipment name to search for
   * @return the designation entry, or null if not found
   */
  public DesignationEntry findByName(String equipmentName) {
    for (DesignationEntry entry : entries) {
      if (entry.getEquipmentName().equals(equipmentName)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Finds a designation entry by reference designation string.
   *
   * @param referenceDesignation the reference designation string to search for
   * @return the designation entry, or null if not found
   */
  public DesignationEntry findByDesignation(String referenceDesignation) {
    for (DesignationEntry entry : entries) {
      if (entry.getReferenceDesignation().equals(referenceDesignation)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Returns all designation entries for a given IEC 81346-2 letter code.
   *
   * @param letterCode the letter code to filter by
   * @return unmodifiable list of matching entries
   */
  public List<DesignationEntry> findByLetterCode(IEC81346LetterCode letterCode) {
    List<DesignationEntry> result = new ArrayList<DesignationEntry>();
    for (DesignationEntry entry : entries) {
      if (entry.getLetterCode() == letterCode) {
        result.add(entry);
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns a summary map of letter code counts.
   *
   * @return map of letter code to count of equipment with that code
   */
  public Map<IEC81346LetterCode, Integer> getLetterCodeSummary() {
    Map<IEC81346LetterCode, Integer> summary =
        new EnumMap<IEC81346LetterCode, Integer>(IEC81346LetterCode.class);
    for (DesignationEntry entry : entries) {
      Integer count = summary.get(entry.getLetterCode());
      summary.put(entry.getLetterCode(), (count != null) ? count + 1 : 1);
    }
    return summary;
  }

  /**
   * Returns a cross-reference mapping from equipment name to reference designation string.
   *
   * @return unmodifiable map of equipment name to reference designation
   */
  public Map<String, String> getNameToDesignationMap() {
    Map<String, String> map = new LinkedHashMap<String, String>();
    for (DesignationEntry entry : entries) {
      map.put(entry.getEquipmentName(), entry.getReferenceDesignation());
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Returns a cross-reference mapping from reference designation string to equipment name.
   *
   * @return unmodifiable map of reference designation to equipment name
   */
  public Map<String, String> getDesignationToNameMap() {
    Map<String, String> map = new LinkedHashMap<String, String>();
    for (DesignationEntry entry : entries) {
      map.put(entry.getReferenceDesignation(), entry.getEquipmentName());
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Exports the generated designations as a JSON string.
   *
   * <p>
   * The JSON structure contains:
   * </p>
   * <ul>
   * <li>{@code standard}: "IEC 81346"</li>
   * <li>{@code functionPrefix}: The function prefix used</li>
   * <li>{@code locationPrefix}: The location prefix used</li>
   * <li>{@code designationCount}: Number of generated designations</li>
   * <li>{@code letterCodeSummary}: Count per letter code</li>
   * <li>{@code designations}: Array of designation entries with name, type, designation,
   * letterCode, and description</li>
   * </ul>
   *
   * @return JSON string representation of all generated designations
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("standard", "IEC 81346");
    root.addProperty("functionPrefix", functionPrefix);
    root.addProperty("locationPrefix", locationPrefix);
    root.addProperty("designationCount", entries.size());

    // Letter code summary
    JsonObject summaryObj = new JsonObject();
    Map<IEC81346LetterCode, Integer> summary = getLetterCodeSummary();
    for (Map.Entry<IEC81346LetterCode, Integer> entry : summary.entrySet()) {
      summaryObj.addProperty(entry.getKey().name() + " (" + entry.getKey().getDescription() + ")",
          entry.getValue());
    }
    root.add("letterCodeSummary", summaryObj);

    // Designation entries
    JsonArray designationsArray = new JsonArray();
    for (DesignationEntry entry : entries) {
      JsonObject entryObj = new JsonObject();
      entryObj.addProperty("equipmentName", entry.getEquipmentName());
      entryObj.addProperty("equipmentType", entry.getEquipmentType());
      entryObj.addProperty("referenceDesignation", entry.getReferenceDesignation());
      entryObj.addProperty("letterCode", entry.getLetterCode().name());
      entryObj.addProperty("letterCodeDescription", entry.getLetterCode().getDescription());
      entryObj.addProperty("sequenceNumber", entry.getSequenceNumber());
      entryObj.addProperty("functionArea", entry.getFunctionArea());
      designationsArray.add(entryObj);
    }
    root.add("designations", designationsArray);

    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  /**
   * Represents a single generated IEC 81346 designation entry.
   *
   * <p>
   * Each entry captures the mapping from a NeqSim equipment name to its IEC 81346 reference
   * designation, along with classification metadata.
   * </p>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class DesignationEntry implements Serializable {

    private static final long serialVersionUID = 1003L;

    /** The original equipment name in NeqSim. */
    private final String equipmentName;

    /** The simple class name of the equipment (e.g. "Separator", "Compressor"). */
    private final String equipmentType;

    /** The full IEC 81346 reference designation string. */
    private final String referenceDesignation;

    /** The IEC 81346-2 letter code. */
    private final IEC81346LetterCode letterCode;

    /** The sequence number within the letter code category. */
    private final int sequenceNumber;

    /** The function area identifier (e.g. "A1", "A2"). */
    private final String functionArea;

    /**
     * Creates a new designation entry.
     *
     * @param equipmentName the original equipment name
     * @param equipmentType the simple class name of the equipment
     * @param referenceDesignation the full reference designation string
     * @param letterCode the IEC 81346-2 letter code
     * @param sequenceNumber the sequence number within the letter code category
     * @param functionArea the function area identifier
     */
    public DesignationEntry(String equipmentName, String equipmentType, String referenceDesignation,
        IEC81346LetterCode letterCode, int sequenceNumber, String functionArea) {
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.referenceDesignation = referenceDesignation;
      this.letterCode = letterCode;
      this.sequenceNumber = sequenceNumber;
      this.functionArea = functionArea;
    }

    /**
     * Returns the original equipment name in NeqSim.
     *
     * @return the equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Returns the simple class name of the equipment.
     *
     * @return the equipment type, e.g. "Separator", "Compressor"
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Returns the full IEC 81346 reference designation string.
     *
     * @return the reference designation, e.g. "=A1-B1+P1.M1"
     */
    public String getReferenceDesignation() {
      return referenceDesignation;
    }

    /**
     * Returns the IEC 81346-2 letter code.
     *
     * @return the letter code
     */
    public IEC81346LetterCode getLetterCode() {
      return letterCode;
    }

    /**
     * Returns the sequence number within the letter code category.
     *
     * @return the sequence number (1-based)
     */
    public int getSequenceNumber() {
      return sequenceNumber;
    }

    /**
     * Returns the function area identifier.
     *
     * @return the function area, e.g. "A1"
     */
    public String getFunctionArea() {
      return functionArea;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return equipmentName + " -> " + referenceDesignation + " [" + letterCode.name() + ": "
          + letterCode.getDescription() + "]";
    }
  }
}
