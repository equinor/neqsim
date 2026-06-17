package neqsim.process.equipment.distillation.internals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registry for packed-column packing specifications.
 *
 * <p>
 * The registry combines built-in vendor-style presets with entries loaded from
 * {@code src/main/resources/designdata/Packing.csv}. Names are matched with a forgiving normalized
 * key, so values such as {@code Pall-Ring-50}, {@code pallring 50}, and CSV names all map to the
 * same specification where possible.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PackingSpecificationLibrary {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PackingSpecificationLibrary.class);

  /** Registered specifications keyed by normalized aliases. */
  private static final Map<String, PackingSpecification> PACKINGS =
      new LinkedHashMap<String, PackingSpecification>();

  /** Registered display names in deterministic order. */
  private static final Set<String> DISPLAY_NAMES = new LinkedHashSet<String>();

  static {
    registerBuiltIns();
    loadCsvPackings();
  }

  /**
   * Private constructor for utility class.
   */
  private PackingSpecificationLibrary() {}

  /**
   * Register a packing specification.
   *
   * @param specification specification to register
   * @throws IllegalArgumentException if the specification is null
   */
  public static synchronized void register(PackingSpecification specification) {
    if (specification == null) {
      throw new IllegalArgumentException("specification can not be null");
    }
    registerAlias(specification.getName(), specification);
    DISPLAY_NAMES.add(specification.getName());
  }

  /**
   * Register an extra alias for an existing packing specification.
   *
   * @param alias alias to register
   * @param specification specification to associate with the alias
   * @throws IllegalArgumentException if alias or specification is invalid
   */
  public static synchronized void registerAlias(String alias, PackingSpecification specification) {
    if (alias == null || alias.trim().isEmpty()) {
      throw new IllegalArgumentException("alias must be specified");
    }
    if (specification == null) {
      throw new IllegalArgumentException("specification can not be null");
    }
    PACKINGS.put(normalize(alias), specification);
  }

  /**
   * Get a packing specification by name or alias.
   *
   * @param name packing name or alias
   * @return packing specification, or null if no match exists
   */
  public static PackingSpecification get(String name) {
    if (name == null) {
      return null;
    }
    return PACKINGS.get(normalize(name));
  }

  /**
   * Get a packing specification, returning Pall-Ring-50 when no match exists.
   *
   * @param name packing name or alias
   * @return matched specification or default Pall-Ring-50 specification
   */
  public static PackingSpecification getOrDefault(String name) {
    PackingSpecification specification = get(name);
    if (specification != null) {
      return specification;
    }
    PackingSpecification defaultSpecification = get("Pall-Ring-50");
    if (defaultSpecification == null) {
      throw new IllegalStateException("Default packing Pall-Ring-50 is not registered");
    }
    return defaultSpecification;
  }

  /**
   * Check if a packing name is registered.
   *
   * @param name packing name or alias
   * @return true if the packing can be resolved
   */
  public static boolean contains(String name) {
    return get(name) != null;
  }

  /**
   * Get the display names of registered packings.
   *
   * @return unmodifiable list of packing display names
   */
  public static List<String> getPackingNames() {
    return Collections.unmodifiableList(new ArrayList<String>(DISPLAY_NAMES));
  }

  /**
   * Register built-in random and structured packing data.
   */
  private static void registerBuiltIns() {
    registerRandom("Pall-Ring-25", "metal", 25.0, 210.0, 0.94, 157.0, 1.0, 0.40,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Pall-Ring-38", "metal", 38.0, 164.0, 0.95, 92.0, 1.0, 0.40,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Pall-Ring-50", "metal", 50.0, 120.0, 0.96, 66.0, 1.0, 0.40,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Raschig-Ring-25", "ceramic", 25.0, 190.0, 0.68, 580.0, 1.0, 0.40,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Raschig-Ring-50", "ceramic", 50.0, 95.0, 0.74, 155.0, 1.0, 0.40,
        "built-in Kister/Onda engineering estimate");
    registerRandom("IMTP-25", "metal", 25.0, 226.0, 0.97, 134.0, 1.05, 0.42,
        "built-in vendor-style engineering estimate");
    registerRandom("IMTP-40", "metal", 40.0, 151.0, 0.97, 79.0, 1.05, 0.42,
        "built-in vendor-style engineering estimate");
    registerRandom("IMTP-50", "metal", 50.0, 102.0, 0.98, 56.0, 1.05, 0.42,
        "built-in vendor-style engineering estimate");
    registerRandom("IMTP-70", "metal", 70.0, 72.0, 0.98, 36.0, 1.05, 0.42,
        "built-in vendor-style engineering estimate");
    registerRandom("Berl-Saddle-25", "ceramic", 25.0, 260.0, 0.68, 360.0, 0.95, 0.38,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Berl-Saddle-38", "ceramic", 38.0, 165.0, 0.70, 220.0, 0.95, 0.38,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Berl-Saddle-50", "ceramic", 50.0, 105.0, 0.72, 150.0, 0.95, 0.38,
        "built-in Kister/Onda engineering estimate");
    registerRandom("Intalox-Saddle-25", "ceramic", 25.0, 255.0, 0.78, 200.0, 1.0, 0.40,
        "built-in vendor-style engineering estimate");

    registerStructured("Mellapak-125Y", "metal", 125.0, 0.99, 33.0, 1.2, 0.45,
        "built-in Sulzer-style engineering estimate");
    registerStructured("Mellapak-250Y", "metal", 250.0, 0.98, 66.0, 1.2, 0.45,
        "built-in Sulzer-style engineering estimate");
    registerStructured("Mellapak-350Y", "metal", 350.0, 0.97, 105.0, 1.2, 0.45,
        "built-in Sulzer-style engineering estimate");
    registerStructured("Mellapak-500Y", "metal", 500.0, 0.96, 180.0, 1.2, 0.45,
        "built-in Sulzer-style engineering estimate");
    registerStructured("Flexipac-1Y", "metal", 135.0, 0.99, 36.0, 1.15, 0.44,
        "built-in Koch-Glitsch-style engineering estimate");
    registerStructured("Flexipac-2Y", "metal", 220.0, 0.98, 60.0, 1.15, 0.44,
        "built-in Koch-Glitsch-style engineering estimate");
    registerStructured("Flexipac-3Y", "metal", 340.0, 0.97, 100.0, 1.15, 0.44,
        "built-in Koch-Glitsch-style engineering estimate");
    registerStructured("Sulzer-BX", "metal", 500.0, 0.90, 140.0, 1.3, 0.50,
        "built-in wire-gauze structured packing estimate");
    registerStructured("Sulzer-CY", "metal", 750.0, 0.88, 220.0, 1.3, 0.50,
        "built-in wire-gauze structured packing estimate");
  }

  /**
   * Register a random packing.
   *
   * @param name packing name
   * @param material packing material
   * @param nominalSizeMm nominal size in millimetres
   * @param specificArea specific surface area in m2/m3
   * @param voidFraction void fraction
   * @param packingFactor packing factor in 1/m
   * @param billetLiquidConstant Billet-Schultes liquid constant
   * @param billetGasConstant Billet-Schultes gas constant
   * @param source data source description
   */
  private static void registerRandom(String name, String material, double nominalSizeMm,
      double specificArea, double voidFraction, double packingFactor, double billetLiquidConstant,
      double billetGasConstant, String source) {
    register(new PackingSpecification(name, "random", material, nominalSizeMm, specificArea,
        voidFraction, packingFactor, criticalSurfaceTension(material), billetLiquidConstant,
        billetGasConstant, source));
  }

  /**
   * Register a structured packing.
   *
   * @param name packing name
   * @param material packing material
   * @param specificArea specific surface area in m2/m3
   * @param voidFraction void fraction
   * @param packingFactor packing factor in 1/m
   * @param billetLiquidConstant Billet-Schultes liquid constant
   * @param billetGasConstant Billet-Schultes gas constant
   * @param source data source description
   */
  private static void registerStructured(String name, String material, double specificArea,
      double voidFraction, double packingFactor, double billetLiquidConstant,
      double billetGasConstant, String source) {
    register(new PackingSpecification(name, "structured", material, 0.0, specificArea, voidFraction,
        packingFactor, criticalSurfaceTension(material), billetLiquidConstant, billetGasConstant,
        source));
  }

  /**
   * Load packing specifications from the classpath CSV resource.
   */
  private static void loadCsvPackings() {
    InputStream stream = PackingSpecificationLibrary.class.getClassLoader()
        .getResourceAsStream("designdata/Packing.csv");
    if (stream == null) {
      return;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      String line = reader.readLine();
      while ((line = reader.readLine()) != null) {
        PackingSpecification specification = parseCsvLine(line);
        if (specification != null) {
          register(specification);
        }
      }
    } catch (IOException ex) {
      logger.warn("Could not read packing data resource", ex);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {
          logger.debug("Could not close packing data reader", ex);
        }
      }
    }
  }

  /**
   * Parse one CSV line from the packing design-data table.
   *
   * @param line CSV line to parse
   * @return packing specification, or null if the line cannot be parsed
   */
  private static PackingSpecification parseCsvLine(String line) {
    List<String> fields = splitCsv(line);
    if (fields.size() < 8) {
      return null;
    }
    try {
      String rawName = fields.get(1);
      String category = fields.get(2);
      String material = fields.get(3);
      double size = parseDouble(fields.get(4), 0.0);
      double specificArea = parseDouble(fields.get(5), 0.0);
      double voidFraction = parseDouble(fields.get(6), 0.0);
      double packingFactor = parseDouble(fields.get(7), 0.0);
      double cp = fields.size() > 8 ? parseDouble(fields.get(8), 1.0) : 1.0;
      double ch = fields.size() > 10 ? parseDouble(fields.get(10), 0.4) : 0.4;
      if (specificArea <= 0.0 || voidFraction <= 0.0 || packingFactor <= 0.0) {
        return null;
      }
      String displayName = displayName(rawName, category, size);
      double billetLiquidConstant = cp > 0.0 ? cp : 1.0;
      double billetGasConstant = ch > 0.0 ? ch / 6.0 : 0.4;
      return new PackingSpecification(displayName, category, material, size, specificArea,
          voidFraction, packingFactor, criticalSurfaceTension(material), billetLiquidConstant,
          billetGasConstant, "designdata/Packing.csv");
    } catch (RuntimeException ex) {
      logger.debug("Skipping packing data line: " + line, ex);
      return null;
    }
  }

  /**
   * Split a simple CSV line with quoted fields.
   *
   * @param line CSV line to split
   * @return list of unquoted fields
   */
  private static List<String> splitCsv(String line) {
    List<String> fields = new ArrayList<String>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        inQuotes = !inQuotes;
      } else if (character == ',' && !inQuotes) {
        fields.add(field.toString().trim());
        field.setLength(0);
      } else {
        field.append(character);
      }
    }
    fields.add(field.toString().trim());
    return fields;
  }

  /**
   * Parse a double with fallback.
   *
   * @param text value text to parse
   * @param fallback fallback value when text is empty or invalid
   * @return parsed double or fallback
   */
  private static double parseDouble(String text, double fallback) {
    if (text == null || text.trim().isEmpty()) {
      return fallback;
    }
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  /**
   * Build a display name from CSV fields.
   *
   * @param rawName raw packing name from CSV
   * @param category packing category
   * @param size nominal size in millimetres
   * @return display name used by the registry
   */
  private static String displayName(String rawName, String category, double size) {
    String normalized = normalize(rawName);
    if ("structured".equalsIgnoreCase(category)) {
      return rawName;
    }
    int roundedSize = (int) Math.round(size);
    if (normalized.contains("pallring")) {
      return "Pall-Ring-" + roundedSize;
    }
    if (normalized.contains("rashig") || normalized.contains("raschig")
        || normalized.contains("rachig")) {
      return "Raschig-Ring-" + roundedSize;
    }
    return rawName + "-" + roundedSize;
  }

  /**
   * Estimate critical surface tension from packing material.
   *
   * @param material material text
   * @return critical surface tension in N/m
   */
  private static double criticalSurfaceTension(String material) {
    String normalized = material == null ? "" : material.toLowerCase(Locale.ROOT);
    if (normalized.contains("plastic")) {
      return 0.033;
    }
    if (normalized.contains("ceramic")) {
      return 0.061;
    }
    return 0.075;
  }

  /**
   * Normalize a packing name for forgiving lookup.
   *
   * @param name packing name or alias
   * @return normalized lookup key
   */
  private static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
