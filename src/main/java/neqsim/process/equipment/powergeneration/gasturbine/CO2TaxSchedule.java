package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Year-by-year schedule of effective CO2 cost on offshore fuel gas combustion (combined national
 * CO2 tax + emission trading scheme allowance).
 *
 * <p>
 * Values are loaded from {@code resources/data/powergeneration/co2_tax_norway.csv} and exposed as
 * NOK per tonne CO2. Linear interpolation is used between supplied years and the schedule is held
 * constant before / after the supported range.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public final class CO2TaxSchedule implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CO2TaxSchedule.class);

  private final java.util.NavigableMap<Integer, Double> totalNOKPerTonne;

  /**
   * Construct from a pre-loaded sorted map.
   *
   * @param totalNOKPerTonne year → total CO2 cost [NOK/tonne]
   */
  public CO2TaxSchedule(java.util.NavigableMap<Integer, Double> totalNOKPerTonne) {
    this.totalNOKPerTonne =
        Collections.unmodifiableNavigableMap(new TreeMap<Integer, Double>(totalNOKPerTonne));
  }

  /**
   * Load the bundled Norwegian CO2 tax + EU ETS schedule.
   *
   * @return loaded schedule
   */
  public static CO2TaxSchedule loadDefault() {
    return loadFromResource("/data/powergeneration/co2_tax_norway.csv");
  }

  /**
   * Load a schedule from an arbitrary classpath CSV.
   *
   * @param resourcePath classpath resource path
   * @return loaded schedule
   */
  public static CO2TaxSchedule loadFromResource(String resourcePath) {
    TreeMap<Integer, Double> map = new TreeMap<Integer, Double>();
    InputStream stream = CO2TaxSchedule.class.getResourceAsStream(resourcePath);
    if (stream == null) {
      logger.warn("CO2 tax schedule resource not found at {}", resourcePath);
      return new CO2TaxSchedule(map);
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      String header = reader.readLine();
      if (header == null) {
        return new CO2TaxSchedule(map);
      }
      String[] headers = header.split(",");
      int yearIdx = indexOf(headers, "year");
      int totalIdx = indexOf(headers, "total_NOK_per_tonne");
      if (yearIdx < 0 || totalIdx < 0) {
        logger.warn("CO2 tax CSV missing year or total_NOK_per_tonne column");
        return new CO2TaxSchedule(map);
      }
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length <= Math.max(yearIdx, totalIdx)) {
          continue;
        }
        try {
          int year = Integer.parseInt(parts[yearIdx].trim());
          double total = Double.parseDouble(parts[totalIdx].trim());
          map.put(year, total);
        } catch (NumberFormatException ex) {
          logger.warn("Skipping invalid CO2 tax row: {}", line);
        }
      }
    } catch (IOException ex) {
      logger.error("Failed to read CO2 tax schedule", ex);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ignore) {
          // ignore close failures
        }
      }
    }
    return new CO2TaxSchedule(map);
  }

  /**
   * Get the total CO2 cost for a given calendar year (linearly interpolated).
   *
   * @param year calendar year
   * @return total CO2 cost [NOK/tonne], or 0 if the schedule is empty
   */
  public double getTotalNOKPerTonne(int year) {
    if (totalNOKPerTonne.isEmpty()) {
      return 0.0;
    }
    Double exact = totalNOKPerTonne.get(year);
    if (exact != null) {
      return exact.doubleValue();
    }
    Integer floor = totalNOKPerTonne.floorKey(year);
    Integer ceil = totalNOKPerTonne.ceilingKey(year);
    if (floor == null) {
      return totalNOKPerTonne.firstEntry().getValue().doubleValue();
    }
    if (ceil == null) {
      return totalNOKPerTonne.lastEntry().getValue().doubleValue();
    }
    double yLo = totalNOKPerTonne.get(floor).doubleValue();
    double yHi = totalNOKPerTonne.get(ceil).doubleValue();
    double frac = (year - floor.intValue()) / (double) (ceil.intValue() - floor.intValue());
    return yLo + frac * (yHi - yLo);
  }

  /**
   * Get the underlying year → total NOK/tonne mapping.
   *
   * @return immutable navigable map
   */
  public java.util.NavigableMap<Integer, Double> asMap() {
    return totalNOKPerTonne;
  }

  private static int indexOf(String[] headers, String name) {
    for (int i = 0; i < headers.length; i++) {
      if (headers[i].trim().equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }
}
