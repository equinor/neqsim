package neqsim.process.processmodel.dexpi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.EquipmentEnum;

/**
 * Loads DEXPI-to-NeqSim equipment and piping component mappings from properties files on the
 * classpath.
 *
 * <p>
 * The mapping files are located at:
 * </p>
 * <ul>
 * <li>{@code neqsim/process/processmodel/dexpi_equipment_mapping.properties}</li>
 * <li>{@code neqsim/process/processmodel/dexpi_piping_component_mapping.properties}</li>
 * </ul>
 *
 * <p>
 * Each properties file maps a DEXPI ComponentClass string (e.g. "CentrifugalPump") to a
 * {@link EquipmentEnum} name (e.g. "Pump"). If a properties file cannot be loaded, the loader falls
 * back to a built-in default mapping.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiMappingLoader {
  private static final Logger logger = LogManager.getLogger(DexpiMappingLoader.class);

  private static final String EQUIPMENT_RESOURCE =
      "neqsim/process/processmodel/dexpi_equipment_mapping.properties";
  private static final String PIPING_RESOURCE =
      "neqsim/process/processmodel/dexpi_piping_component_mapping.properties";

  private static volatile Map<String, EquipmentEnum> cachedEquipmentMapping;
  private static volatile Map<String, EquipmentEnum> cachedPipingMapping;

  private DexpiMappingLoader() {}

  /**
   * Loads the DEXPI equipment class to NeqSim EquipmentEnum mapping from the classpath properties
   * file.
   *
   * @return an unmodifiable map from DEXPI ComponentClass to EquipmentEnum
   */
  public static Map<String, EquipmentEnum> loadEquipmentMapping() {
    Map<String, EquipmentEnum> cached = cachedEquipmentMapping;
    if (cached != null) {
      return cached;
    }
    synchronized (DexpiMappingLoader.class) {
      if (cachedEquipmentMapping != null) {
        return cachedEquipmentMapping;
      }
      cachedEquipmentMapping = loadMapping(EQUIPMENT_RESOURCE, "equipment");
      return cachedEquipmentMapping;
    }
  }

  /**
   * Loads the DEXPI piping component class to NeqSim EquipmentEnum mapping from the classpath
   * properties file.
   *
   * @return an unmodifiable map from DEXPI ComponentClass to EquipmentEnum
   */
  public static Map<String, EquipmentEnum> loadPipingComponentMapping() {
    Map<String, EquipmentEnum> cached = cachedPipingMapping;
    if (cached != null) {
      return cached;
    }
    synchronized (DexpiMappingLoader.class) {
      if (cachedPipingMapping != null) {
        return cachedPipingMapping;
      }
      cachedPipingMapping = loadMapping(PIPING_RESOURCE, "piping component");
      return cachedPipingMapping;
    }
  }

  /**
   * Clears the cached mappings so they will be reloaded on next access. Useful for testing.
   */
  public static synchronized void clearCache() {
    cachedEquipmentMapping = null;
    cachedPipingMapping = null;
  }

  /**
   * Loads a mapping from a properties file on the classpath.
   *
   * @param resourcePath the classpath resource path
   * @param label a descriptive label for logging
   * @return an unmodifiable map from DEXPI class name to EquipmentEnum
   */
  private static Map<String, EquipmentEnum> loadMapping(String resourcePath, String label) {
    Properties props = new Properties();
    InputStream is = DexpiMappingLoader.class.getClassLoader().getResourceAsStream(resourcePath);
    if (is == null) {
      logger.warn("DEXPI {} mapping file not found on classpath: {}", label, resourcePath);
      return Collections.emptyMap();
    }
    try {
      props.load(is);
    } catch (IOException e) {
      logger.error("Failed to load DEXPI {} mapping from {}", label, resourcePath, e);
      return Collections.emptyMap();
    } finally {
      try {
        is.close();
      } catch (IOException ignored) {
        // ignore
      }
    }

    Map<String, EquipmentEnum> map = new HashMap<>();
    for (String key : props.stringPropertyNames()) {
      String value = props.getProperty(key);
      if (value == null || value.trim().isEmpty()) {
        continue;
      }
      try {
        EquipmentEnum enumVal = EquipmentEnum.valueOf(value.trim());
        map.put(key.trim(), enumVal);
      } catch (IllegalArgumentException e) {
        logger.warn("Unknown EquipmentEnum '{}' for DEXPI class '{}' in {}", value, key,
            resourcePath);
      }
    }

    logger.info("Loaded {} DEXPI {} mappings from {}", map.size(), label, resourcePath);
    return Collections.unmodifiableMap(map);
  }
}
