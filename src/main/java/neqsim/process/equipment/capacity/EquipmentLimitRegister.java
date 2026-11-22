package neqsim.process.equipment.capacity;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Register of equipment limits that carry their source and status: one entry per equipment tag, quantity and validity
 * period, with a value, a unit, the document or rule the number comes from, a {@link Status} ({@code VERIFIED} or
 * {@code ASSUMPTION}) and a confidence.
 *
 * <p>
 * The register holds plant equipment limits (per tag, quantity and validity period) with provenance. The file format is
 * a documented JSON layout:
 * </p>
 *
 * <pre>
 * {
 *   "meta": {"title": "...", "built": "..."},
 *   "limits": [
 *     {"equipmentTag": "K-201", "quantity": "n_max", "value": 10278.0, "unit": "rpm",
 *      "validFromYear": null, "validToYear": 2028, "source": "SPD As-Used 2025-07-08",
 *      "status": "VERIFIED", "confidence": "medium", "authority": "VENDOR_DATASHEET", "note": ""}
 *   ]
 * }
 * </pre>
 *
 * <p>
 * Only {@code equipmentTag}, {@code quantity} and {@code value} are required. {@code status} defaults to
 * {@code ASSUMPTION}; {@code confidence} is a fraction in [0, 1] or one of {@code high} (0.9), {@code medium} (0.6) and
 * {@code low} (0.3); {@code validFromYear}/{@code validToYear} are inclusive and may be null (open). {@code authority}
 * is the name of a {@link CapacityConstraint.ConstraintSource} (default {@code USER_RULE}).
 * </p>
 *
 * <p>
 * {@link #fromCompressorLimitsDatabase(String)} reads a nested {@code machines/facts} compressor-limits layout into the
 * same entries, so a database snapshot can be loaded, queried and merged into a process model.
 * {@link #applyTo(ProcessSystem, Integer)} writes the limits into the capacity constraints of the equipment whose name
 * equals the tag as <em>operating limits</em> with the status and source in the source reference, so they appear in the
 * utilisation snapshot with their provenance. A quantity is applied only to a constraint of matching unit; the rest is
 * listed in the {@link ApplyReport} instead of being silently dropped.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentLimitRegister implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Status of a limit value. */
  public enum Status {
    /** Read from a named source or measurement. */
    VERIFIED,
    /** Engineering default or a derived rule. */
    ASSUMPTION
  }

  private final List<Entry> entries = new ArrayList<Entry>();
  private final Map<String, String> meta = new LinkedHashMap<String, String>();
  private final Map<String, String> quantityToConstraint = new LinkedHashMap<String, String>();

  /** Creates an empty register with the default quantity to constraint-name mapping. */
  public EquipmentLimitRegister() {
    quantityToConstraint.put("n_max", "speed");
    quantityToConstraint.put("max_speed", "speed");
    quantityToConstraint.put("n_min", "minSpeed");
    quantityToConstraint.put("min_speed", "minSpeed");
    quantityToConstraint.put("T_out_max", "dischargeTemperature");
  }

  /**
   * Immutable register entry.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class Entry implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final String equipmentTag;
    private final String quantity;
    private final double value;
    private final String unit;
    private final Integer validFromYear;
    private final Integer validToYear;
    private final String source;
    private final Status status;
    private final double confidence;
    private final String authority;
    private final String note;

    /**
     * Creates an entry.
     *
     * @param equipmentTag equipment tag or name (required)
     * @param quantity limit quantity such as {@code n_max}, {@code power_limit} or {@code p_out_max} (required)
     * @param value limit value
     * @param unit unit of the value
     * @param validFromYear first year the limit applies, or null for open
     * @param validToYear last year the limit applies, or null for open
     * @param source document, measurement or rule behind the number
     * @param status VERIFIED or ASSUMPTION (null is treated as ASSUMPTION)
     * @param confidence confidence in [0, 1], NaN if not given
     * @param authority name of a {@link CapacityConstraint.ConstraintSource}, null for USER_RULE
     * @param note free text
     * @throws IllegalArgumentException if the tag or quantity is empty or the value is not finite
     */
    public Entry(String equipmentTag, String quantity, double value, String unit, Integer validFromYear,
        Integer validToYear, String source, Status status, double confidence, String authority, String note) {
      if (equipmentTag == null || equipmentTag.trim().isEmpty() || quantity == null || quantity.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentTag and quantity are required");
      }
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("limit value must be finite for " + equipmentTag + "/" + quantity);
      }
      if (!Double.isNaN(confidence) && (confidence < 0.0 || confidence > 1.0)) {
        throw new IllegalArgumentException("confidence must be in [0, 1]");
      }
      this.equipmentTag = equipmentTag.trim();
      this.quantity = quantity.trim();
      this.value = value;
      this.unit = unit == null ? "" : unit;
      this.validFromYear = validFromYear;
      this.validToYear = validToYear;
      this.source = source == null ? "" : source;
      this.status = status == null ? Status.ASSUMPTION : status;
      this.confidence = confidence;
      this.authority = authority == null || authority.trim().isEmpty() ? "USER_RULE" : authority.trim();
      this.note = note == null ? "" : note;
    }

    /**
     * Get the equipment tag.
     *
     * @return tag as given
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Get the quantity.
     *
     * @return quantity key
     */
    public String getQuantity() {
      return quantity;
    }

    /**
     * Get the value.
     *
     * @return limit value in {@link #getUnit()}
     */
    public double getValue() {
      return value;
    }

    /**
     * Get the unit.
     *
     * @return unit text, may be empty
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Get the first valid year.
     *
     * @return year or null for open
     */
    public Integer getValidFromYear() {
      return validFromYear;
    }

    /**
     * Get the last valid year.
     *
     * @return year or null for open
     */
    public Integer getValidToYear() {
      return validToYear;
    }

    /**
     * Get the source.
     *
     * @return source text
     */
    public String getSource() {
      return source;
    }

    /**
     * Get the status.
     *
     * @return VERIFIED or ASSUMPTION
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Get the confidence.
     *
     * @return confidence in [0, 1] or NaN if not given
     */
    public double getConfidence() {
      return confidence;
    }

    /**
     * Get the authority class of the limit.
     *
     * @return name of a {@link CapacityConstraint.ConstraintSource}
     */
    public String getAuthority() {
      return authority;
    }

    /**
     * Get the note.
     *
     * @return note text
     */
    public String getNote() {
      return note;
    }

    /**
     * Whether the entry applies in a year.
     *
     * @param year the year, or null to match any year
     * @return true if the year is inside the validity period
     */
    public boolean isValidIn(Integer year) {
      if (year == null) {
        return true;
      }
      return (validFromYear == null || year.intValue() >= validFromYear.intValue())
          && (validToYear == null || year.intValue() <= validToYear.intValue());
    }

    /**
     * Whether the validity period is bounded at either end.
     *
     * @return true if a from or to year is set
     */
    boolean isBounded() {
      return validFromYear != null || validToYear != null;
    }

    /**
     * Source reference text written to the capacity constraint: status, source and note.
     *
     * @return text such as {@code [VERIFIED] SPD As-Used 2025-07-08}
     */
    public String toSourceReference() {
      StringBuilder sb = new StringBuilder();
      sb.append('[').append(status.name()).append("] ").append(source);
      if (!note.isEmpty()) {
        sb.append(" (").append(note).append(')');
      }
      return sb.toString();
    }

    /**
     * Convert to a JSON object in the register file format.
     *
     * @return JSON object
     */
    JsonObject toJsonObject() {
      JsonObject o = new JsonObject();
      o.addProperty("equipmentTag", equipmentTag);
      o.addProperty("quantity", quantity);
      o.addProperty("value", value);
      o.addProperty("unit", unit);
      o.addProperty("validFromYear", validFromYear);
      o.addProperty("validToYear", validToYear);
      o.addProperty("source", source);
      o.addProperty("status", status.name());
      if (!Double.isNaN(confidence)) {
        o.addProperty("confidence", confidence);
      }
      o.addProperty("authority", authority);
      o.addProperty("note", note);
      return o;
    }
  }

  /**
   * Outcome of {@link #applyTo(ProcessSystem, Integer)}.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ApplyReport implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final List<String> applied = new ArrayList<String>();
    private final List<String> skipped = new ArrayList<String>();

    /**
     * Get the applied limits.
     *
     * @return lines {@code tag.constraint = value unit [status]}
     */
    public List<String> getApplied() {
      return Collections.unmodifiableList(applied);
    }

    /**
     * Get the entries that could not be applied, with the reason.
     *
     * @return lines {@code tag/quantity: reason}
     */
    public List<String> getSkipped() {
      return Collections.unmodifiableList(skipped);
    }

    /**
     * Serialize to JSON.
     *
     * @return JSON text
     */
    public String toJson() {
      JsonObject o = new JsonObject();
      JsonArray a = new JsonArray();
      for (String s : applied) {
        a.add(s);
      }
      JsonArray k = new JsonArray();
      for (String s : skipped) {
        k.add(s);
      }
      o.add("applied", a);
      o.add("skipped", k);
      return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }
  }

  /**
   * Normalize a tag for lookup: remove blanks, underscores and hyphens, upper case.
   *
   * @param tag tag text
   * @return normalized tag
   */
  public static String normalizeTag(String tag) {
    return tag == null ? "" : tag.replaceAll("[\\s_\\-]+", "").toUpperCase(Locale.ROOT);
  }

  /**
   * Add an entry.
   *
   * @param entry the entry (not null)
   * @return this register
   */
  public EquipmentLimitRegister add(Entry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry must not be null");
    }
    entries.add(entry);
    return this;
  }

  /**
   * Get all entries in insertion order.
   *
   * @return unmodifiable list
   */
  public List<Entry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  /**
   * Number of entries.
   *
   * @return entry count
   */
  public int size() {
    return entries.size();
  }

  /**
   * Get the distinct equipment tags in insertion order.
   *
   * @return tags as given in the entries
   */
  public List<String> getTags() {
    Set<String> tags = new LinkedHashSet<String>();
    for (Entry e : entries) {
      tags.add(e.getEquipmentTag());
    }
    return new ArrayList<String>(tags);
  }

  /**
   * Get the metadata map (title, built date, ...).
   *
   * @return modifiable map of the register metadata
   */
  public Map<String, String> getMeta() {
    return meta;
  }

  /**
   * Map a limit quantity to a capacity-constraint name used by {@link #applyTo(ProcessSystem, Integer)}.
   *
   * @param quantity the limit quantity, for example {@code p_out_max}
   * @param constraintName the capacity constraint name on the equipment, for example {@code dischargePressure}
   * @return this register
   */
  public EquipmentLimitRegister mapQuantityToConstraint(String quantity, String constraintName) {
    quantityToConstraint.put(quantity, constraintName);
    return this;
  }

  /**
   * Find all entries for a tag and quantity valid in a year.
   *
   * @param tag equipment tag (normalized for the comparison)
   * @param quantity quantity key, or null for all quantities
   * @param year year or null for any
   * @return matching entries in insertion order
   */
  public List<Entry> find(String tag, String quantity, Integer year) {
    String key = normalizeTag(tag);
    List<Entry> out = new ArrayList<Entry>();
    for (Entry e : entries) {
      if (normalizeTag(e.getEquipmentTag()).equals(key) && (quantity == null || quantity.equals(e.getQuantity()))
          && e.isValidIn(year)) {
        out.add(e);
      }
    }
    return out;
  }

  /**
   * Look up the limit for a tag, quantity and year. An entry with a bounded validity period wins over an open one;
   * otherwise the first entry added wins.
   *
   * @param tag equipment tag
   * @param quantity quantity key
   * @param year year or null for any
   * @return the entry, or null if there is none
   */
  public Entry lookup(String tag, String quantity, Integer year) {
    Entry best = null;
    for (Entry e : find(tag, quantity, year)) {
      if (best == null || (e.isBounded() && !best.isBounded())) {
        best = e;
      }
    }
    return best;
  }

  /**
   * Merge another register into this one. An entry of the other register replaces an entry with the same tag, quantity
   * and validity period when {@code overwrite} is true, otherwise the existing entry is kept.
   *
   * @param other register to merge in
   * @param overwrite true to let the other register win on a clash
   * @return number of entries added or replaced
   */
  public int merge(EquipmentLimitRegister other, boolean overwrite) {
    int changed = 0;
    for (Entry candidate : other.entries) {
      int clash = -1;
      for (int i = 0; i < entries.size(); i++) {
        Entry e = entries.get(i);
        if (normalizeTag(e.getEquipmentTag()).equals(normalizeTag(candidate.getEquipmentTag()))
            && e.getQuantity().equals(candidate.getQuantity())
            && sameYear(e.getValidFromYear(), candidate.getValidFromYear())
            && sameYear(e.getValidToYear(), candidate.getValidToYear())) {
          clash = i;
          break;
        }
      }
      if (clash < 0) {
        entries.add(candidate);
        changed++;
      } else if (overwrite) {
        entries.set(clash, candidate);
        changed++;
      }
    }
    return changed;
  }

  /**
   * Compare two nullable years.
   *
   * @param a first year
   * @param b second year
   * @return true if both are null or equal
   */
  private static boolean sameYear(Integer a, Integer b) {
    return a == null ? b == null : a.equals(b);
  }

  /**
   * Write the limits into the capacity constraints of a process model. Every equipment whose name equals an entry tag
   * (compared after {@link #normalizeTag(String)}) and that has the mapped constraint receives the value as operating
   * limit, with the status and source in the source reference and the confidence attached. The default mapping is
   * {@code n_max} to {@code speed}, {@code n_min} to {@code minSpeed} and {@code T_out_max} to
   * {@code dischargeTemperature}; add more with {@link #mapQuantityToConstraint(String, String)}. A quantity without a
   * mapping, a missing constraint or a unit that cannot be converted is reported in {@link ApplyReport#getSkipped()}.
   *
   * @param process process system whose unit names are matched against the tags
   * @param year year for the validity selection, or null for any
   * @return report of the applied and skipped limits
   */
  public ApplyReport applyTo(ProcessSystem process, Integer year) {
    ApplyReport report = new ApplyReport();
    Map<String, ProcessEquipmentInterface> byTag = new LinkedHashMap<String, ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      byTag.put(normalizeTag(unit.getName()), unit);
    }
    Set<String> done = new LinkedHashSet<String>();
    for (Entry e : entries) {
      if (!e.isValidIn(year)) {
        continue;
      }
      String key = normalizeTag(e.getEquipmentTag()) + "/" + e.getQuantity();
      ProcessEquipmentInterface unit = byTag.get(normalizeTag(e.getEquipmentTag()));
      if (unit == null || !done.add(key)) {
        continue;
      }
      Entry chosen = lookup(e.getEquipmentTag(), e.getQuantity(), year);
      applyEntry(unit, chosen, report);
    }
    return report;
  }

  /**
   * Apply one entry to one equipment.
   *
   * @param unit the equipment
   * @param e the entry
   * @param report report to append to
   */
  private void applyEntry(ProcessEquipmentInterface unit, Entry e, ApplyReport report) {
    String label = e.getEquipmentTag() + "/" + e.getQuantity();
    String constraintName = quantityToConstraint.get(e.getQuantity());
    if (constraintName == null) {
      report.skipped.add(label + ": no constraint mapping for this quantity");
      return;
    }
    if (!(unit instanceof CapacityConstrainedEquipment)) {
      report.skipped.add(label + ": equipment has no capacity constraints");
      return;
    }
    CapacityConstraint c = ((CapacityConstrainedEquipment) unit).getCapacityConstraints().get(constraintName);
    if (c == null) {
      report.skipped.add(label + ": equipment has no constraint '" + constraintName + "'");
      return;
    }
    double converted = convert(e.getValue(), e.getUnit(), c.getUnit());
    if (Double.isNaN(converted) || converted <= 0.0) {
      report.skipped
          .add(label + ": unit '" + e.getUnit() + "' cannot be applied to constraint unit '" + c.getUnit() + "'");
      return;
    }
    CapacityConstraint.ConstraintSource authority;
    try {
      authority = CapacityConstraint.ConstraintSource.valueOf(e.getAuthority());
    } catch (IllegalArgumentException ex) {
      authority = CapacityConstraint.ConstraintSource.USER_RULE;
    }
    c.setOperatingLimit(converted, authority, e.toSourceReference());
    if (!Double.isNaN(e.getConfidence())) {
      c.setOperatingLimitConfidence(e.getConfidence());
    }
    report.applied.add(unit.getName() + "." + constraintName + " = " + converted + " " + c.getUnit() + " ["
        + e.getStatus().name() + "]");
  }

  /**
   * Convert a value between the units used for equipment limits.
   *
   * @param value value in {@code from}
   * @param from source unit
   * @param to target unit
   * @return converted value, or NaN if the pair is not supported
   */
  static double convert(double value, String from, String to) {
    String f = unitKey(from);
    String t = unitKey(to);
    if (f.equals(t)) {
      return value;
    }
    double fw = powerFactor(f);
    double tw = powerFactor(t);
    if (fw > 0.0 && tw > 0.0) {
      return value * fw / tw;
    }
    if (f.equals("c") && t.equals("k")) {
      return value + 273.15;
    }
    if (f.equals("k") && t.equals("c")) {
      return value - 273.15;
    }
    if ((f.equals("bara") || f.equals("bar")) && (t.equals("bara") || t.equals("bar"))) {
      return value;
    }
    return Double.NaN;
  }

  /**
   * Normalize a unit text for comparison.
   *
   * @param unit unit text
   * @return lower-case unit without degree signs
   */
  private static String unitKey(String unit) {
    if (unit == null) {
      return "";
    }
    return unit.trim().toLowerCase(Locale.ROOT).replace("\u00b0", "").replace("deg", "");
  }

  /**
   * Factor from a power unit to watts.
   *
   * @param key normalized unit
   * @return watts per unit, or 0 if the unit is not a power unit
   */
  private static double powerFactor(String key) {
    if ("w".equals(key)) {
      return 1.0;
    } else if ("kw".equals(key)) {
      return 1.0e3;
    } else if ("mw".equals(key)) {
      return 1.0e6;
    }
    return 0.0;
  }

  /**
   * Serialize the register to JSON in the Java register format.
   *
   * @return pretty-printed JSON text
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    JsonObject m = new JsonObject();
    for (Map.Entry<String, String> me : meta.entrySet()) {
      m.addProperty(me.getKey(), me.getValue());
    }
    root.add("meta", m);
    JsonArray limits = new JsonArray();
    for (Entry e : entries) {
      limits.add(e.toJsonObject());
    }
    root.add("limits", limits);
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    return gson.toJson(root);
  }

  /**
   * Save the register as a JSON file.
   *
   * @param path target file
   * @throws IOException if the file cannot be written
   */
  public void save(Path path) throws IOException {
    Files.write(path, toJson().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Load a register from a JSON file in the Java register format.
   *
   * @param path file to read
   * @return the register
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if the JSON is not a valid register
   */
  public static EquipmentLimitRegister load(Path path) throws IOException {
    return fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
  }

  /**
   * Parse a register from JSON in the Java register format.
   *
   * @param json JSON text with a {@code limits} array
   * @return the register
   * @throws IllegalArgumentException if the JSON is malformed or an entry is invalid
   */
  public static EquipmentLimitRegister fromJson(String json) {
    EquipmentLimitRegister reg = new EquipmentLimitRegister();
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("not a valid limit register JSON: " + ex.getMessage(), ex);
    }
    readMeta(root, reg);
    if (!root.has("limits") || !root.get("limits").isJsonArray()) {
      throw new IllegalArgumentException("register JSON needs a 'limits' array");
    }
    for (JsonElement el : root.getAsJsonArray("limits")) {
      JsonObject o = el.getAsJsonObject();
      reg.add(new Entry(text(o, "equipmentTag"), text(o, "quantity"), number(o, "value", Double.NaN), text(o, "unit"),
          year(o, "validFromYear"), year(o, "validToYear"), text(o, "source"), status(text(o, "status")),
          confidence(o.get("confidence")), text(o, "authority"), text(o, "note")));
    }
    return reg;
  }

  /**
   * Read a register from the nested compressor-limits layout ({@code machines} with a {@code facts} object per machine;
   * each fact has value, unit, source, status and note). The record-level {@code era_from}/{@code era_to} become the
   * validity period and the record {@code confidence} the entry confidence; facts without a numeric value are skipped.
   * The fact names are kept as quantity keys ({@code power_limit}, {@code n_max}, {@code p_out_max}, {@code T_out_max},
   * ...). The tag is the record {@code tag} (the machine key if absent), so the hardware eras of one machine (keys
   * {@code K-201} and {@code K-201_N}) share the tag and are told apart by the year in
   * {@link #lookup(String, String, Integer)}.
   *
   * @param json text of the database file
   * @return the register
   * @throws IllegalArgumentException if the JSON has no {@code machines} object
   */
  public static EquipmentLimitRegister fromCompressorLimitsDatabase(String json) {
    EquipmentLimitRegister reg = new EquipmentLimitRegister();
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("not a valid limits database JSON: " + ex.getMessage(), ex);
    }
    readMeta(root, reg);
    if (!root.has("machines") || !root.get("machines").isJsonObject()) {
      throw new IllegalArgumentException("database JSON needs a 'machines' object");
    }
    for (Map.Entry<String, JsonElement> m : root.getAsJsonObject("machines").entrySet()) {
      JsonObject rec = m.getValue().getAsJsonObject();
      if (!rec.has("facts") || !rec.get("facts").isJsonObject()) {
        continue;
      }
      double conf = confidence(rec.get("confidence"));
      String tag = text(rec, "tag") == null ? m.getKey() : text(rec, "tag");
      for (Map.Entry<String, JsonElement> f : rec.getAsJsonObject("facts").entrySet()) {
        if (!f.getValue().isJsonObject()) {
          continue;
        }
        JsonObject fact = f.getValue().getAsJsonObject();
        double value = number(fact, "value", Double.NaN);
        if (!Double.isFinite(value)) {
          continue;
        }
        reg.add(new Entry(tag, f.getKey(), value, text(fact, "unit"), year(rec, "era_from"), year(rec, "era_to"),
            text(fact, "source"), status(text(fact, "status")), conf, "USER_RULE", text(fact, "note")));
      }
    }
    return reg;
  }

  /**
   * Copy the {@code meta} object into the register metadata.
   *
   * @param root parsed JSON root
   * @param reg register to fill
   */
  private static void readMeta(JsonObject root, EquipmentLimitRegister reg) {
    if (root.has("meta") && root.get("meta").isJsonObject()) {
      for (Map.Entry<String, JsonElement> me : root.getAsJsonObject("meta").entrySet()) {
        if (me.getValue().isJsonPrimitive()) {
          reg.meta.put(me.getKey(), me.getValue().getAsString());
        }
      }
    }
  }

  /**
   * Read a string property.
   *
   * @param o JSON object
   * @param key property name
   * @return the text, or null if missing or null
   */
  private static String text(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e == null || e.isJsonNull() ? null : e.getAsString();
  }

  /**
   * Read a numeric property.
   *
   * @param o JSON object
   * @param key property name
   * @param fallback value if missing, null or not numeric
   * @return the number
   */
  private static double number(JsonObject o, String key, double fallback) {
    JsonElement e = o.get(key);
    if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
      return fallback;
    }
    return e.getAsDouble();
  }

  /**
   * Read a nullable year property.
   *
   * @param o JSON object
   * @param key property name
   * @return the year or null
   */
  private static Integer year(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
      return null;
    }
    return Integer.valueOf(e.getAsInt());
  }

  /**
   * Parse a status text.
   *
   * @param s text such as {@code verified}
   * @return the status; ASSUMPTION for null or any other text
   */
  private static Status status(String s) {
    return s != null && "VERIFIED".equalsIgnoreCase(s.trim()) ? Status.VERIFIED : Status.ASSUMPTION;
  }

  /**
   * Parse a confidence given as a fraction or as high, medium or low.
   *
   * @param e JSON element or null
   * @return confidence in [0, 1], NaN if not given
   */
  private static double confidence(JsonElement e) {
    if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
      return Double.NaN;
    }
    if (e.getAsJsonPrimitive().isNumber()) {
      return e.getAsDouble();
    }
    String s = e.getAsString().trim().toLowerCase(Locale.ROOT);
    if ("high".equals(s)) {
      return 0.9;
    } else if ("medium".equals(s)) {
      return 0.6;
    } else if ("low".equals(s)) {
      return 0.3;
    }
    return Double.NaN;
  }
}
