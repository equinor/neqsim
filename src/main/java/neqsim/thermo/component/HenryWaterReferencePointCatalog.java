package neqsim.thermo.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Catalog of qualified, exact-CAS Henry reference points for neutral solutes in water. */
public final class HenryWaterReferencePointCatalog {
  private static final String RESOURCE = "/data/HenryWaterReferencePoints.json";
  private static final CatalogData DATA = load();

  private HenryWaterReferencePointCatalog() {
  }

  /**
   * Finds a reference point by exact CAS registry number.
   *
   * @param casNumber exact CAS registry number
   * @return the reference point, or empty when no qualified exact-CAS point exists
   */
  public static Optional<HenryWaterReferencePoint> findByCasNumber(String casNumber) {
    return Optional.ofNullable(DATA.byCasNumber.get(casNumber));
  }

  /**
   * Finds a reference point by the exact NeqSim component name recorded in the inventory.
   *
   * @param componentName exact component name
   * @return the reference point, or empty when no qualified point exists
   */
  public static Optional<HenryWaterReferencePoint> findByComponentName(String componentName) {
    return Optional.ofNullable(DATA.byComponentName.get(componentName));
  }

  /**
   * Returns all qualified reference points in deterministic source-manifest order.
   *
   * @return immutable list of reference points
   */
  public static List<HenryWaterReferencePoint> getAll() {
    return DATA.points;
  }

  private static CatalogData load() {
    try (InputStream stream = HenryWaterReferencePointCatalog.class.getResourceAsStream(RESOURCE);
        InputStreamReader reader = stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      if (stream == null) {
        throw new IllegalStateException("Missing built-in Henry reference-point catalog: " + RESOURCE);
      }
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      List<HenryWaterReferencePoint> points = new ArrayList<>();
      Map<String, HenryWaterReferencePoint> byCasNumber = new LinkedHashMap<>();
      Map<String, HenryWaterReferencePoint> byComponentName = new LinkedHashMap<>();
      for (JsonElement element : root.getAsJsonArray("rows")) {
        JsonObject row = element.getAsJsonObject();
        HenryWaterReferencePoint point = new HenryWaterReferencePoint(text(row, root, "name"),
            text(row, root, "source_species"), text(row, root, "cas"), row.get("Hsbp_mol_kg_atm").getAsDouble(),
            number(row, root, "reference_temperature_K"), number(row, root, "reference_pressure_MPa"),
            text(row, root, "reference"), text(row, root, "status"), text(row, root, "solvent"),
            text(row, root, "convention"), text(row, root, "source"), text(row, root, "doi"),
            text(row, root, "compilation_license"), text(row, root, "original_reference"),
            optionalText(row, root, "original_reference_doi"), text(row, root, "original_reference_url"),
            text(row, root, "original_reference_rights"), optionalText(row, root, "source_inchikey"),
            text(row, root, "identity_basis"), text(row, root, "uncertainty"), text(row, root, "temperature_scope"));
        requirePositiveFinite(point.getSolubilityMolalityPerAtm(), point.getComponentName());
        requirePositiveFinite(point.getReferenceTemperatureK(), point.getComponentName());
        requirePositiveFinite(point.getReferencePressureMPa(), point.getComponentName());
        if (byCasNumber.put(point.getCasNumber(), point) != null) {
          throw new IllegalStateException("Duplicate Henry reference-point CAS: " + point.getCasNumber());
        }
        if (byComponentName.put(point.getComponentName(), point) != null) {
          throw new IllegalStateException("Duplicate Henry reference-point component: " + point.getComponentName());
        }
        points.add(point);
      }
      return new CatalogData(points, byCasNumber, byComponentName);
    } catch (IOException | RuntimeException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static String text(JsonObject row, JsonObject defaults, String member) {
    String value = optionalText(row, defaults, member);
    if (value.trim().isEmpty()) {
      throw new IllegalStateException("Empty Henry reference-point field: " + member);
    }
    return value;
  }

  private static String optionalText(JsonObject row, JsonObject defaults, String member) {
    JsonElement element = row.has(member) ? row.get(member) : defaults.get(member);
    return element == null || element.isJsonNull() ? "" : element.getAsString();
  }

  private static double number(JsonObject row, JsonObject defaults, String member) {
    JsonElement element = row.has(member) ? row.get(member) : defaults.get(member);
    if (element == null || element.isJsonNull()) {
      throw new IllegalStateException("Missing Henry reference-point field: " + member);
    }
    return element.getAsDouble();
  }

  private static void requirePositiveFinite(double value, String componentName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalStateException("Invalid Henry reference-point value for " + componentName);
    }
  }

  private static final class CatalogData {
    private final List<HenryWaterReferencePoint> points;
    private final Map<String, HenryWaterReferencePoint> byCasNumber;
    private final Map<String, HenryWaterReferencePoint> byComponentName;

    private CatalogData(List<HenryWaterReferencePoint> points, Map<String, HenryWaterReferencePoint> byCasNumber,
        Map<String, HenryWaterReferencePoint> byComponentName) {
      this.points = Collections.unmodifiableList(new ArrayList<>(points));
      this.byCasNumber = Collections.unmodifiableMap(new LinkedHashMap<>(byCasNumber));
      this.byComponentName = Collections.unmodifiableMap(new LinkedHashMap<>(byComponentName));
    }
  }
}
