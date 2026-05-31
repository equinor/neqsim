package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Catalog of typical gas turbine packages used in upstream oil &amp; gas topside power generation.
 * Data is loaded from {@code resources/data/powergeneration/gas_turbine_catalog.csv} on first
 * access.
 *
 * <p>
 * All entries are based on publicly available OEM datasheets and trade press. Use
 * {@link #get(String)} to retrieve a named entry or {@link #findBestFit} to pick the smallest unit
 * that can deliver a given power demand with the requested redundancy.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public final class GasTurbineCatalog implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(GasTurbineCatalog.class);
  /** Resource path for the bundled catalog CSV. */
  private static final String RESOURCE_PATH = "/data/powergeneration/gas_turbine_catalog.csv";

  private static volatile Map<String, GasTurbineSpec> CACHE = null;

  private GasTurbineCatalog() {
    // utility class
  }

  /**
   * Load the catalog from the bundled CSV resource (lazy, thread safe).
   *
   * @return immutable map from model name to spec
   */
  public static Map<String, GasTurbineSpec> all() {
    Map<String, GasTurbineSpec> local = CACHE;
    if (local == null) {
      synchronized (GasTurbineCatalog.class) {
        local = CACHE;
        if (local == null) {
          local = loadFromResource();
          CACHE = local;
        }
      }
    }
    return local;
  }

  /**
   * Lookup a single entry by model name.
   *
   * @param model OEM model identifier (case-insensitive, underscores or spaces accepted)
   * @return matching spec
   * @throws IllegalArgumentException if no model with the given name exists
   */
  public static GasTurbineSpec get(String model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    String key = normalize(model);
    for (Map.Entry<String, GasTurbineSpec> e : all().entrySet()) {
      if (normalize(e.getKey()).equals(key)) {
        return e.getValue();
      }
    }
    throw new IllegalArgumentException(
        "Unknown gas turbine model: " + model + ". Available: " + all().keySet());
  }

  /**
   * Return all entries sorted by ascending rated power.
   *
   * @return sorted list of catalog entries
   */
  public static List<GasTurbineSpec> sortedByPower() {
    List<GasTurbineSpec> list = new ArrayList<GasTurbineSpec>(all().values());
    Collections.sort(list, new java.util.Comparator<GasTurbineSpec>() {
      @Override
      public int compare(GasTurbineSpec a, GasTurbineSpec b) {
        return Double.compare(a.getRatedPowerW(), b.getRatedPowerW());
      }
    });
    return list;
  }

  /**
   * Find the smallest catalog entry whose rated power is at least the requested demand multiplied
   * by the safety / redundancy factor.
   *
   * @param demandW power demand [W]
   * @param redundancyFactor multiplier (e.g. 1.0 for 1×100 %, 2.0 for N+1 with two equal units)
   * @return best-fit spec, or {@code null} if no entry is large enough
   */
  public static GasTurbineSpec findBestFit(double demandW, double redundancyFactor) {
    if (demandW <= 0.0 || redundancyFactor <= 0.0) {
      return null;
    }
    double targetW = demandW * redundancyFactor;
    GasTurbineSpec best = null;
    for (GasTurbineSpec s : sortedByPower()) {
      if (s.getRatedPowerW() >= targetW) {
        best = s;
        break;
      }
    }
    return best;
  }

  /**
   * Find the best-fit single-unit spec for the given demand. When no single catalog entry is large
   * enough and {@code returnLargestIfNoneFits} is {@code true}, the largest available entry is
   * returned instead (indicating a multi-unit dispatch will be required). When the flag is
   * {@code false}, the behaviour matches the two-argument overload and {@code null} is returned for
   * infeasible single-unit cases.
   *
   * @param demandW power demand [W]
   * @param redundancyFactor multiplier (e.g. 1.0 for 1×100 %, 2.0 for N+1 with two equal units)
   * @param returnLargestIfNoneFits if true, fall back to the largest catalog entry when no single
   *        unit covers the demand
   * @return best-fit spec, or the largest spec, or {@code null} if the catalog is empty
   */
  public static GasTurbineSpec findBestFit(double demandW, double redundancyFactor,
      boolean returnLargestIfNoneFits) {
    GasTurbineSpec best = findBestFit(demandW, redundancyFactor);
    if (best != null || !returnLargestIfNoneFits) {
      return best;
    }
    List<GasTurbineSpec> sorted = sortedByPower();
    if (sorted.isEmpty()) {
      return null;
    }
    return sorted.get(sorted.size() - 1);
  }

  private static String normalize(String s) {
    return s.trim().toLowerCase().replace(" ", "_").replace("-", "_");
  }

  private static Map<String, GasTurbineSpec> loadFromResource() {
    Map<String, GasTurbineSpec> map = new LinkedHashMap<String, GasTurbineSpec>();
    InputStream stream = GasTurbineCatalog.class.getResourceAsStream(RESOURCE_PATH);
    if (stream == null) {
      logger.warn("Gas turbine catalog resource not found at {}", RESOURCE_PATH);
      return map;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      String header = reader.readLine();
      if (header == null) {
        return map;
      }
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length < 9) {
          logger.warn("Skipping malformed catalog row: {}", line);
          continue;
        }
        try {
          String model = parts[0].trim();
          GasTurbineSpec.TurbineType type =
              GasTurbineSpec.TurbineType.valueOf(parts[1].trim().toUpperCase());
          double powerMW = Double.parseDouble(parts[2].trim());
          double heatRate = Double.parseDouble(parts[3].trim());
          double exhaustFlow = Double.parseDouble(parts[4].trim());
          double exhaustTC = Double.parseDouble(parts[5].trim());
          double nox = Double.parseDouble(parts[6].trim());
          double mass = Double.parseDouble(parts[7].trim());
          String desc = parts[8].trim();
          map.put(model, new GasTurbineSpec(model, type, powerMW * 1.0e6, heatRate, exhaustFlow,
              exhaustTC + 273.15, nox, mass, desc));
        } catch (RuntimeException ex) {
          logger.warn("Failed to parse catalog row '{}': {}", line, ex.getMessage());
        }
      }
    } catch (IOException ex) {
      logger.error("Failed to read gas turbine catalog", ex);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ignore) {
          // ignore close failures
        }
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
