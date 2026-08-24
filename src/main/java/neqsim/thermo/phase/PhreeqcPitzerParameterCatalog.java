package neqsim.thermo.phase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazy, immutable reader for the exact public-domain USGS PHREEQC Pitzer parameter block bundled with NeqSim.
 *
 * <p>
 * Only Pitzer interaction families are parsed. Reaction constants, mineral log K values, gas binary parameters, SIT,
 * and electrolyte-EOS parameters are deliberately outside this catalog. Loading occurs only when a caller explicitly
 * applies a PHREEQC dataset, so neutral EOS and legacy Pitzer calculations pay no startup or kernel cost.
 * </p>
 */
public final class PhreeqcPitzerParameterCatalog {
  /** Exact source commit represented by the bundled catalog. */
  public static final String SOURCE_COMMIT = "b0b3be767158ccc3322d2c816625cf470045e67e";

  /** Exact source database blob represented by the bundled catalog. */
  public static final String SOURCE_BLOB = "324f852784be84650b77bd7f07f8316aafd8188b";

  private static final String RESOURCE = "/neqsim/thermo/phase/phreeqc/pitzer-b0b3be767158ccc3322d2c816625cf470045e67e.dat";

  /** Supported Pitzer parameter families. */
  public enum Family {
    B0(2), B1(2), B2(2), C0(2), THETA(2), PSI(3), LAMBDA(2), ZETA(3), MU(3), ETA(3), ALPHAS(2);

    private final int speciesCount;

    Family(int speciesCount) {
      this.speciesCount = speciesCount;
    }
  }

  private final Map<Family, Map<String, double[]>> rows;

  private PhreeqcPitzerParameterCatalog(Map<Family, Map<String, double[]>> rows) {
    this.rows = rows;
  }

  /**
   * Returns the lazily loaded immutable catalog.
   *
   * @return exact-version PHREEQC Pitzer catalog
   */
  public static PhreeqcPitzerParameterCatalog getInstance() {
    return Holder.INSTANCE;
  }

  /**
   * Returns a defensive copy of one row, or {@code null} when the source does not contain that tuple.
   *
   * @param family parameter family
   * @param species PHREEQC or NeqSim species names
   * @return six PHREEQC temperature coefficients or {@code null}
   */
  public double[] find(Family family, String... species) {
    double[] coefficients = rows.get(family).get(key(species));
    return coefficients == null ? null : coefficients.clone();
  }

  /**
   * Returns one row and fails closed when it is absent.
   *
   * @param family parameter family
   * @param species species tuple
   * @return six PHREEQC temperature coefficients
   */
  public double[] require(Family family, String... species) {
    double[] coefficients = find(family, species);
    if (coefficients == null) {
      throw new IllegalArgumentException("PHREEQC Pitzer catalog " + SOURCE_COMMIT + " has no explicit " + family
          + " row for " + Arrays.toString(species));
    }
    return coefficients;
  }

  /**
   * Returns the number of source rows in a family.
   *
   * @param family parameter family
   * @return row count
   */
  public int size(Family family) {
    return rows.get(family).size();
  }

  private static PhreeqcPitzerParameterCatalog load() {
    EnumMap<Family, Map<String, double[]>> mutable = new EnumMap<Family, Map<String, double[]>>(Family.class);
    for (Family family : Family.values()) {
      mutable.put(family, new HashMap<String, double[]>());
    }
    InputStream stream = PhreeqcPitzerParameterCatalog.class.getResourceAsStream(RESOURCE);
    if (stream == null) {
      throw new IllegalStateException("Missing bundled PHREEQC Pitzer catalog " + RESOURCE);
    }
    Family currentFamily = null;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        int comment = line.indexOf('#');
        String data = (comment >= 0 ? line.substring(0, comment) : line).trim();
        if (data.isEmpty() || "PITZER".equalsIgnoreCase(data)) {
          continue;
        }
        if (data.startsWith("-")) {
          String section = data.substring(1).trim().toUpperCase();
          if ("LAMDA".equals(section)) {
            section = "LAMBDA";
          }
          try {
            currentFamily = Family.valueOf(section);
          } catch (IllegalArgumentException ignored) {
            currentFamily = null;
          }
          continue;
        }
        if (currentFamily == null) {
          continue;
        }
        String[] tokens = data.split("\\s+");
        if (tokens.length <= currentFamily.speciesCount) {
          throw new IllegalStateException("Invalid " + currentFamily + " row in bundled PHREEQC catalog: " + data);
        }
        String[] species = Arrays.copyOf(tokens, currentFamily.speciesCount);
        double[] coefficients = new double[6];
        int coefficientCount = Math.min(6, tokens.length - currentFamily.speciesCount);
        for (int index = 0; index < coefficientCount && currentFamily.speciesCount + index < tokens.length; index++) {
          coefficients[index] = Double.parseDouble(tokens[currentFamily.speciesCount + index]);
        }
        String key = key(species);
        if (mutable.get(currentFamily).put(key, coefficients) != null) {
          throw new IllegalStateException("Duplicate " + currentFamily + " row in bundled PHREEQC catalog: " + data);
        }
      }
    } catch (IOException | NumberFormatException exception) {
      throw new IllegalStateException("Could not parse bundled PHREEQC Pitzer catalog", exception);
    }

    EnumMap<Family, Map<String, double[]>> immutable = new EnumMap<Family, Map<String, double[]>>(Family.class);
    for (Family family : Family.values()) {
      immutable.put(family, Collections.unmodifiableMap(mutable.get(family)));
    }
    return new PhreeqcPitzerParameterCatalog(Collections.unmodifiableMap(immutable));
  }

  private static String key(String... species) {
    List<String> canonical = new ArrayList<String>(species.length);
    for (String name : species) {
      canonical.add(canonicalSpeciesName(name));
    }
    Collections.sort(canonical);
    return String.join("|", canonical);
  }

  private static String canonicalSpeciesName(String name) {
    String value = name.trim();
    if (value.endsWith("+2")) {
      return value.substring(0, value.length() - 2) + "++";
    }
    if (value.endsWith("-2")) {
      return value.substring(0, value.length() - 2) + "--";
    }
    if (value.endsWith("+3")) {
      return value.substring(0, value.length() - 2) + "+++";
    }
    if (value.endsWith("-3")) {
      return value.substring(0, value.length() - 2) + "---";
    }
    return value;
  }

  private static final class Holder {
    private static final PhreeqcPitzerParameterCatalog INSTANCE = load();

    private Holder() {
    }
  }
}
