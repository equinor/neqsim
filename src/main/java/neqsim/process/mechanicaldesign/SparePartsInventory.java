package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates a recommended spare parts inventory from a {@link ProcessSystem}.
 *
 * <p>
 * Walks all unit operations and maps each equipment type to a list of recommended spare parts with
 * quantities, estimated lead times, and criticality. The inventory follows typical offshore oil
 * &amp; gas practice where critical rotating equipment spares are kept on-platform or at a
 * logistics base.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * SparePartsInventory inventory = new SparePartsInventory(process);
 * inventory.generateInventory();
 * String json = inventory.toJson();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SparePartsInventory implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system to analyze. */
  private ProcessSystem processSystem;

  /** Generated spare parts list. */
  private List<SparePartEntry> entries = new ArrayList<SparePartEntry>();

  /** Standard spare parts mapped by equipment type keyword. */
  private static final Map<String, List<SparePartTemplate>> SPARE_TEMPLATES;

  static {
    Map<String, List<SparePartTemplate>> m = new LinkedHashMap<String, List<SparePartTemplate>>();

    m.put("compressor",
        Arrays.asList(new SparePartTemplate("Mechanical seal set", 2, "Critical", 12),
            new SparePartTemplate("Bearing set (radial + thrust)", 1, "Critical", 16),
            new SparePartTemplate("Coupling element", 1, "Major", 12),
            new SparePartTemplate("Vibration probe set", 2, "Minor", 4),
            new SparePartTemplate("Lube oil filter element", 6, "Minor", 2)));

    m.put("pump",
        Arrays.asList(new SparePartTemplate("Mechanical seal set", 2, "Critical", 10),
            new SparePartTemplate("Impeller", 1, "Major", 20),
            new SparePartTemplate("Bearing set", 2, "Major", 8),
            new SparePartTemplate("Wear ring set", 2, "Minor", 8)));

    m.put("valve",
        Arrays.asList(new SparePartTemplate("Actuator diaphragm", 1, "Critical", 8),
            new SparePartTemplate("Seat ring set", 1, "Major", 6),
            new SparePartTemplate("Stem packing set", 2, "Minor", 4),
            new SparePartTemplate("Positioner module", 1, "Minor", 6)));

    m.put("heatexchanger",
        Arrays.asList(new SparePartTemplate("Gasket set (plate HX)", 2, "Critical", 6),
            new SparePartTemplate("Tube bundle", 1, "Major", 26),
            new SparePartTemplate("Expansion joint (shell)", 1, "Minor", 16)));

    m.put("heater", Arrays.asList(new SparePartTemplate("Gasket set", 2, "Minor", 4),
        new SparePartTemplate("Temperature sensor", 2, "Minor", 2)));

    m.put("cooler", Arrays.asList(new SparePartTemplate("Gasket set", 2, "Minor", 4),
        new SparePartTemplate("Temperature sensor", 2, "Minor", 2)));

    m.put("separator",
        Arrays.asList(new SparePartTemplate("Level transmitter", 1, "Critical", 4),
            new SparePartTemplate("PSV set (spare)", 1, "Critical", 10),
            new SparePartTemplate("Demister pad / vane pack", 1, "Major", 12),
            new SparePartTemplate("Gasket set (manway)", 2, "Minor", 4)));

    m.put("column",
        Arrays.asList(new SparePartTemplate("Tray section (spare)", 2, "Major", 20),
            new SparePartTemplate("Packing element batch", 1, "Major", 16),
            new SparePartTemplate("Level transmitter", 2, "Critical", 4),
            new SparePartTemplate("PSV set (spare)", 1, "Critical", 10)));

    m.put("stream", Collections.<SparePartTemplate>emptyList());

    SPARE_TEMPLATES = Collections.unmodifiableMap(m);
  }

  /**
   * Creates a spare parts inventory generator.
   *
   * @param processSystem the process system to analyze
   */
  public SparePartsInventory(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Generates the spare parts inventory by walking all equipment.
   */
  public void generateInventory() {
    entries.clear();
    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = processSystem.getUnitOperations().get(i);
      String className = equip.getClass().getSimpleName().toLowerCase();

      for (Map.Entry<String, List<SparePartTemplate>> tmplEntry : SPARE_TEMPLATES.entrySet()) {
        if (className.contains(tmplEntry.getKey())) {
          for (SparePartTemplate tmpl : tmplEntry.getValue()) {
            entries.add(new SparePartEntry(equip.getName(), className, tmpl.partName, tmpl.quantity,
                tmpl.criticality, tmpl.leadTimeWeeks));
          }
          break; // first match only
        }
      }
    }
  }

  /**
   * Gets the generated spare parts entries.
   *
   * @return list of spare part entries
   */
  public List<SparePartEntry> getEntries() {
    return new ArrayList<SparePartEntry>(entries);
  }

  /**
   * Gets entries filtered by criticality level.
   *
   * @param criticality criticality level ("Critical", "Major", or "Minor")
   * @return filtered list of entries
   */
  public List<SparePartEntry> getEntriesByCriticality(String criticality) {
    List<SparePartEntry> filtered = new ArrayList<SparePartEntry>();
    for (SparePartEntry e : entries) {
      if (e.getCriticality().equalsIgnoreCase(criticality)) {
        filtered.add(e);
      }
    }
    return filtered;
  }

  /**
   * Exports the spare parts inventory to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("totalEntries", entries.size());

    int critical = 0;
    int major = 0;
    int minor = 0;
    for (SparePartEntry e : entries) {
      if ("Critical".equalsIgnoreCase(e.getCriticality())) {
        critical++;
      } else if ("Major".equalsIgnoreCase(e.getCriticality())) {
        major++;
      } else {
        minor++;
      }
    }
    JsonObject summary = new JsonObject();
    summary.addProperty("critical", critical);
    summary.addProperty("major", major);
    summary.addProperty("minor", minor);
    root.add("summary", summary);

    JsonArray arr = new JsonArray();
    for (SparePartEntry e : entries) {
      JsonObject o = new JsonObject();
      o.addProperty("equipment", e.getEquipmentName());
      o.addProperty("equipmentType", e.getEquipmentType());
      o.addProperty("partName", e.getPartName());
      o.addProperty("quantity", e.getQuantity());
      o.addProperty("criticality", e.getCriticality());
      o.addProperty("leadTimeWeeks", e.getLeadTimeWeeks());
      arr.add(o);
    }
    root.add("spareParts", arr);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * A single spare part entry.
   *
   * @author esol
   * @version 1.0
   */
  public static class SparePartEntry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String equipmentName;
    private String equipmentType;
    private String partName;
    private int quantity;
    private String criticality;
    private int leadTimeWeeks;

    /**
     * Creates a spare part entry.
     *
     * @param equipmentName the equipment this spare is for
     * @param equipmentType type keyword
     * @param partName description of the spare part
     * @param quantity recommended stock quantity
     * @param criticality criticality level (Critical, Major, Minor)
     * @param leadTimeWeeks procurement lead time in weeks
     */
    public SparePartEntry(String equipmentName, String equipmentType, String partName, int quantity,
        String criticality, int leadTimeWeeks) {
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.partName = partName;
      this.quantity = quantity;
      this.criticality = criticality;
      this.leadTimeWeeks = leadTimeWeeks;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the equipment type.
     *
     * @return equipment type keyword
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Gets the part name.
     *
     * @return spare part description
     */
    public String getPartName() {
      return partName;
    }

    /**
     * Gets the recommended quantity.
     *
     * @return stock quantity
     */
    public int getQuantity() {
      return quantity;
    }

    /**
     * Gets the criticality level.
     *
     * @return criticality string
     */
    public String getCriticality() {
      return criticality;
    }

    /**
     * Gets the procurement lead time.
     *
     * @return lead time in weeks
     */
    public int getLeadTimeWeeks() {
      return leadTimeWeeks;
    }
  }

  /**
   * Template for a spare part associated with an equipment type.
   */
  private static class SparePartTemplate implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String partName;
    final int quantity;
    final String criticality;
    final int leadTimeWeeks;

    SparePartTemplate(String partName, int quantity, String criticality, int leadTimeWeeks) {
      this.partName = partName;
      this.quantity = quantity;
      this.criticality = criticality;
      this.leadTimeWeeks = leadTimeWeeks;
    }
  }
}
