package neqsim.mcp.runners;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Queries the NeqSim component database for MCP and API integration.
 *
 * <p>
 * Provides fast, stateless lookup of component names for validation, search, and fuzzy matching.
 * All methods return JSON strings for direct use by MCP tool wrappers.
 * </p>
 *
 * <p>
 * Component names are loaded once from the resource file {@code neqsim_component_names.txt} and
 * cached in memory. For richer property queries, the H2 database (COMP table) is used.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ComponentQuery {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ComponentQuery.class);

  /** Cached list of all component names (trimmed, original case). */
  private static volatile List<String> cachedNames;

  /** Cached list of all component names in lower case for matching. */
  private static volatile List<String> cachedNamesLower;

  /**
   * Private constructor to prevent instantiation.
   */
  private ComponentQuery() {}

  /**
   * Returns all known component names.
   *
   * @return unmodifiable list of component names
   */
  public static List<String> getAllNames() {
    ensureLoaded();
    return Collections.unmodifiableList(cachedNames);
  }

  /**
   * Checks whether a component name exists in the database.
   *
   * @param name the component name to check (case-insensitive)
   * @return true if the component is known
   */
  public static boolean isValid(String name) {
    if (name == null || name.trim().isEmpty()) {
      return false;
    }
    ensureLoaded();
    return cachedNamesLower.contains(name.trim().toLowerCase(Locale.ENGLISH));
  }

  /**
   * Searches for components whose name contains the query string (case-insensitive).
   *
   * @param query the search term (substring match)
   * @return JSON string with an array of matching component names
   */
  public static String search(String query) {
    ensureLoaded();

    JsonObject result = new JsonObject();
    JsonArray matches = new JsonArray();

    if (query == null || query.trim().isEmpty()) {
      // Return all names
      for (String name : cachedNames) {
        matches.add(name);
      }
    } else {
      String queryLower = query.trim().toLowerCase(Locale.ENGLISH);
      for (int i = 0; i < cachedNames.size(); i++) {
        if (cachedNamesLower.get(i).contains(queryLower)) {
          matches.add(cachedNames.get(i));
        }
      }
    }

    result.addProperty("status", "success");
    result.addProperty("query", query != null ? query : "");
    result.addProperty("matchCount", matches.size());
    result.add("components", matches);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(result);
  }

  /**
   * Finds the closest matching component name for a possibly misspelled input using edit distance.
   *
   * @param name the possibly misspelled name
   * @return the closest valid component name, or null if no reasonable match
   */
  public static String closestMatch(String name) {
    if (name == null || name.trim().isEmpty()) {
      return null;
    }
    ensureLoaded();

    String inputLower = name.trim().toLowerCase(Locale.ENGLISH);

    // Exact match
    if (cachedNamesLower.contains(inputLower)) {
      int idx = cachedNamesLower.indexOf(inputLower);
      return cachedNames.get(idx);
    }

    // Find closest by Levenshtein distance
    int bestDist = Integer.MAX_VALUE;
    String bestMatch = null;

    for (int i = 0; i < cachedNamesLower.size(); i++) {
      int dist = levenshteinDistance(inputLower, cachedNamesLower.get(i));
      if (dist < bestDist) {
        bestDist = dist;
        bestMatch = cachedNames.get(i);
      }
    }

    // Only suggest if the distance is reasonable (less than half the input length)
    if (bestMatch != null && bestDist <= Math.max(3, inputLower.length() / 2)) {
      return bestMatch;
    }
    return null;
  }

  /**
   * Returns detailed information for a specific component as JSON.
   *
   * <p>
   * Queries the H2 database COMP table for properties like molar mass, critical temperature,
   * critical pressure, and acentric factor.
   * </p>
   *
   * @param name the exact component name
   * @return JSON string with component properties, or error if not found
   */
  public static String getInfo(String name) {
    if (name == null || name.trim().isEmpty()) {
      return errorJson("INVALID_INPUT", "Component name is null or empty",
          "Provide a valid component name");
    }

    String trimmed = name.trim();
    if (!isValid(trimmed)) {
      String suggestion = closestMatch(trimmed);
      String fix = suggestion != null ? "Did you mean '" + suggestion + "'?"
          : "Use search() to find valid component names";
      return errorJson("UNKNOWN_COMPONENT", "Component '" + trimmed + "' not found", fix);
    }

    // Query the H2 database for component properties
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = database
          .getResultSet("SELECT NAME, FORMULA, MOLARMASS, TC, PC, ACSFACT, NORMBOIL FROM comp "
              + "WHERE NAME='" + trimmed.replace("'", "''") + "'");

      if (dataSet.next()) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");

        JsonObject props = new JsonObject();
        props.addProperty("name", dataSet.getString("NAME"));
        props.addProperty("formula", dataSet.getString("FORMULA"));
        props.addProperty("molarMass_g_mol", safeDouble(dataSet.getString("MOLARMASS")));
        props.addProperty("criticalTemperature_C", safeDouble(dataSet.getString("TC")));
        props.addProperty("criticalPressure_bara", safeDouble(dataSet.getString("PC")));
        props.addProperty("acentricFactor", safeDouble(dataSet.getString("ACSFACT")));
        props.addProperty("normalBoilingPoint_C", safeDouble(dataSet.getString("NORMBOIL")));

        result.add("component", props);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);
      } else {
        return errorJson("NOT_FOUND", "Component '" + trimmed + "' not in COMP table",
            "Check component name spelling");
      }
    } catch (Exception ex) {
      logger.error("Error querying component info", ex);
      return errorJson("DATABASE_ERROR", "Failed to query component: " + ex.getMessage(),
          "Ensure the database is initialized");
    }
  }

  /**
   * Loads component names from the resource file if not already cached.
   */
  private static void ensureLoaded() {
    if (cachedNames != null) {
      return;
    }
    synchronized (ComponentQuery.class) {
      if (cachedNames != null) {
        return;
      }
      List<String> names = new ArrayList<>();
      List<String> namesLower = new ArrayList<>();

      try (InputStream is =
          ComponentQuery.class.getClassLoader().getResourceAsStream("neqsim_component_names.txt")) {
        if (is == null) {
          logger.warn("neqsim_component_names.txt not found, falling back to database");
          loadFromDatabase(names, namesLower);
        } else {
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
          String line;
          boolean headerSkipped = false;
          while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!headerSkipped) {
              headerSkipped = true;
              if ("NAME".equalsIgnoreCase(trimmed)) {
                continue; // Skip header row
              }
            }
            if (!trimmed.isEmpty()) {
              names.add(trimmed);
              namesLower.add(trimmed.toLowerCase(Locale.ENGLISH));
            }
          }
        }
      } catch (Exception ex) {
        logger.error("Error loading component names from resource", ex);
        loadFromDatabase(names, namesLower);
      }

      cachedNames = names;
      cachedNamesLower = namesLower;
    }
  }

  /**
   * Loads component names from the H2 database as a fallback.
   *
   * @param names list to populate with component names
   * @param namesLower list to populate with lower-case names
   */
  private static void loadFromDatabase(List<String> names, List<String> namesLower) {
    try {
      String[] dbNames = neqsim.util.database.NeqSimDataBase.getComponentNames();
      for (String n : dbNames) {
        String trimmed = n.trim();
        if (!trimmed.isEmpty()) {
          names.add(trimmed);
          namesLower.add(trimmed.toLowerCase(Locale.ENGLISH));
        }
      }
    } catch (Exception ex) {
      logger.error("Error loading component names from database", ex);
    }
  }

  /**
   * Computes the Levenshtein edit distance between two strings.
   *
   * @param a first string
   * @param b second string
   * @return the edit distance
   */
  static int levenshteinDistance(String a, String b) {
    int lenA = a.length();
    int lenB = b.length();
    int[] prev = new int[lenB + 1];
    int[] curr = new int[lenB + 1];

    for (int j = 0; j <= lenB; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= lenA; i++) {
      curr[0] = i;
      for (int j = 1; j <= lenB; j++) {
        int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] temp = prev;
      prev = curr;
      curr = temp;
    }
    return prev[lenB];
  }

  /**
   * Safely parses a string to double, returning 0.0 on failure.
   *
   * @param value the string value
   * @return the parsed double, or 0.0
   */
  private static double safeDouble(String value) {
    if (value == null || value.trim().isEmpty()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code error code
   * @param message error description
   * @param remediation fix suggestion
   * @return JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);

    result.add("errors", errors);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(result);
  }
}
