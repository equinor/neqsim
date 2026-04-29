package neqsim.thermo.util.componentmapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Translates gas-chromatography (GC) analyser labels to NeqSim component names.
 *
 * <p>
 * GC instruments report component peaks with analyser-specific labels that often differ from
 * NeqSim's database names. This class loads a dictionary of known aliases from
 * {@code neqsim/data/gc_component_aliases.csv} on the classpath and provides methods
 * to resolve labels, identify co-elution groups, and retrieve PNA class information.
 * </p>
 *
 * <p>
 * Matching is case-insensitive and tolerant of leading/trailing whitespace.
 * </p>
 *
 * @author NeqSim Agent
 * @version 1.0
 */
public final class GcComponentMap {

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(GcComponentMap.class);

  /** Resource path for the aliases CSV file. */
  private static final String RESOURCE_PATH = "/neqsim/data/gc_component_aliases.csv";

  /**
   * Map from normalised GC label (lower-case, trimmed) to alias entry. Preserves insertion order
   * for predictable iteration.
   */
  private final Map<String, AliasEntry> aliasMap;

  /** Singleton instance, lazily initialised. */
  private static volatile GcComponentMap instance;

  /**
   * A single row from the alias dictionary.
   *
   * @author NeqSim Agent
   * @version 1.0
   */
  public static final class AliasEntry {
    private final String gcLabel;
    private final String neqsimName;
    private final String pnaClass;
    private final String notes;

    /**
     * Constructs an alias entry.
     *
     * @param gcLabel the GC analyser label
     * @param neqsimName the corresponding NeqSim component name
     * @param pnaClass P (paraffin), N (naphthene), A (aromatic), or O (other)
     * @param notes optional notes about the mapping
     */
    AliasEntry(String gcLabel, String neqsimName, String pnaClass, String notes) {
      this.gcLabel = gcLabel;
      this.neqsimName = neqsimName;
      this.pnaClass = pnaClass;
      this.notes = notes;
    }

    /**
     * Returns the original GC label.
     *
     * @return the GC label
     */
    public String getGcLabel() {
      return gcLabel;
    }

    /**
     * Returns the NeqSim component name.
     *
     * @return the NeqSim name
     */
    public String getNeqsimName() {
      return neqsimName;
    }

    /**
     * Returns the PNA class code.
     *
     * @return P, N, A, or O
     */
    public String getPnaClass() {
      return pnaClass;
    }

    /**
     * Returns any notes about this alias.
     *
     * @return notes string (may be empty)
     */
    public String getNotes() {
      return notes;
    }
  }

  /**
   * Private constructor; loads the dictionary from the classpath resource.
   *
   * @param entries pre-parsed alias entries
   */
  private GcComponentMap(Map<String, AliasEntry> entries) {
    this.aliasMap = Collections.unmodifiableMap(entries);
  }

  /**
   * Returns the shared singleton instance, loading the dictionary on first access.
   *
   * @return the shared GcComponentMap
   */
  public static GcComponentMap getInstance() {
    if (instance == null) {
      synchronized (GcComponentMap.class) {
        if (instance == null) {
          instance = loadFromResource();
        }
      }
    }
    return instance;
  }

  /**
   * Resolves a GC label to the NeqSim component name.
   *
   * @param gcLabel the GC analyser label to look up
   * @return the NeqSim component name
   * @throws IllegalArgumentException if the label is not found in the dictionary
   */
  public String resolve(String gcLabel) {
    if (gcLabel == null) {
      throw new IllegalArgumentException("gcLabel must not be null");
    }
    String key = gcLabel.trim().toLowerCase();
    AliasEntry entry = aliasMap.get(key);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Unknown GC label: '" + gcLabel + "'. Not found in gc_component_aliases.csv.");
    }
    return entry.neqsimName;
  }

  /**
   * Resolves a GC label to the NeqSim component name, returning the original label unchanged if no
   * mapping is found. A warning is logged for unmapped labels.
   *
   * @param gcLabel the GC analyser label to look up
   * @return the NeqSim component name, or the original label if not found
   */
  public String resolveLenient(String gcLabel) {
    if (gcLabel == null) {
      return null;
    }
    String key = gcLabel.trim().toLowerCase();
    AliasEntry entry = aliasMap.get(key);
    if (entry == null) {
      logger.warn("No GC alias found for '{}'; passing through unchanged.", gcLabel);
      return gcLabel.trim();
    }
    return entry.neqsimName;
  }

  /**
   * Returns the PNA class for a GC label.
   *
   * @param gcLabel the GC analyser label
   * @return P, N, A, or O; or empty string if not found
   */
  public String getPnaClass(String gcLabel) {
    if (gcLabel == null) {
      return "";
    }
    AliasEntry entry = aliasMap.get(gcLabel.trim().toLowerCase());
    return entry != null ? entry.pnaClass : "";
  }

  /**
   * Checks whether a GC label is known to the dictionary.
   *
   * @param gcLabel the GC analyser label
   * @return true if the label has a mapping
   */
  public boolean contains(String gcLabel) {
    if (gcLabel == null) {
      return false;
    }
    return aliasMap.containsKey(gcLabel.trim().toLowerCase());
  }

  /**
   * Returns all GC labels that map to the given NeqSim name. This identifies co-elution groups
   * where multiple GC peaks map to the same NeqSim component.
   *
   * @param neqsimName the NeqSim component name
   * @return list of GC labels mapping to this name (may be empty)
   */
  public List<String> findCoElutionGroup(String neqsimName) {
    List<String> group = new ArrayList<String>();
    if (neqsimName == null) {
      return group;
    }
    String target = neqsimName.trim().toLowerCase();
    for (AliasEntry entry : aliasMap.values()) {
      if (entry.neqsimName.toLowerCase().equals(target)) {
        group.add(entry.gcLabel);
      }
    }
    return group;
  }

  /**
   * Returns the number of entries in the dictionary.
   *
   * @return dictionary size
   */
  public int size() {
    return aliasMap.size();
  }

  /**
   * Loads the alias dictionary from the classpath resource.
   *
   * @return a new GcComponentMap instance
   */
  private static GcComponentMap loadFromResource() {
    Map<String, AliasEntry> entries = new LinkedHashMap<String, AliasEntry>();
    InputStream is = GcComponentMap.class.getResourceAsStream(RESOURCE_PATH);
    if (is == null) {
      logger.error("GC alias resource not found: {}", RESOURCE_PATH);
      return new GcComponentMap(entries);
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String line = reader.readLine(); // skip header
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 2) {
          continue;
        }
        String gcLabel = parts[0].trim();
        String neqsimName = parts[1].trim();
        String pnaClass = parts.length > 2 ? parts[2].trim() : "";
        String notes = parts.length > 3 ? parts[3].trim() : "";

        if (!gcLabel.isEmpty() && !neqsimName.isEmpty()) {
          entries.put(gcLabel.toLowerCase(), new AliasEntry(gcLabel, neqsimName, pnaClass, notes));
        }
      }
      logger.info("Loaded {} GC component aliases from {}", entries.size(), RESOURCE_PATH);
    } catch (IOException ex) {
      logger.error("Failed to read GC alias resource", ex);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {
          logger.debug("Error closing reader", ex);
        }
      }
    }
    return new GcComponentMap(entries);
  }
}
