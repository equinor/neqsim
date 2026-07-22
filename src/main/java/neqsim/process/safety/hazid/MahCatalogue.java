package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of typical threats, consequences and barrier classes for each {@link MahType}, as suggested by ISO 17776.
 *
 * <p>
 * These default lists are intended to bootstrap a HAZID / bow-tie workshop - the engineering team must validate and
 * adapt each entry for the specific facility.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class MahCatalogue implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final Map<MahType, List<String>> THREATS;
  private static final Map<MahType, List<String>> CONSEQUENCES;
  private static final Map<MahType, List<String>> BARRIERS;

  static {
    EnumMap<MahType, List<String>> t = new EnumMap<MahType, List<String>>(MahType.class);
    EnumMap<MahType, List<String>> c = new EnumMap<MahType, List<String>>(MahType.class);
    EnumMap<MahType, List<String>> b = new EnumMap<MahType, List<String>>(MahType.class);

    t.put(MahType.TOPSIDE_HYDROCARBON_RELEASE,
        ulist("Corrosion failure", "Erosion failure", "Mechanical impact", "Operator error", "Process upset"));
    c.put(MahType.TOPSIDE_HYDROCARBON_RELEASE, ulist("Jet fire", "Pool fire", "VCE / flash fire", "Toxic exposure"));
    b.put(MahType.TOPSIDE_HYDROCARBON_RELEASE, ulist("Process containment", "ESD / SDV isolation", "Blowdown",
        "Gas detection", "Fire detection", "Deluge", "PFP", "Emergency response"));

    t.put(MahType.RISER_LEAK,
        ulist("CO2 corrosion", "Fatigue / VIV", "Anchor / dropped object", "External impact", "Material defect"));
    c.put(MahType.RISER_LEAK, ulist("Sub-sea release", "Topside escalation", "Pollution"));
    b.put(MahType.RISER_LEAK,
        ulist("ESV / SSIV", "Topside ESD", "Corrosion inhibition", "Inline inspection", "Riser monitoring"));

    t.put(MahType.WELL_BLOWOUT,
        ulist("Loss of primary barrier", "Loss of secondary barrier", "Annulus integrity loss"));
    c.put(MahType.WELL_BLOWOUT, ulist("Uncontrolled release", "Fire / explosion", "Loss of well"));
    b.put(MahType.WELL_BLOWOUT,
        ulist("Casing", "Cement", "DHSV", "Wellhead", "Christmas tree", "BOP", "Well intervention procedures"));

    t.put(MahType.STRUCTURAL_COLLAPSE, ulist("Fatigue crack growth", "Overload", "Foundation failure", "Corrosion"));
    c.put(MahType.STRUCTURAL_COLLAPSE, ulist("Loss of life", "Total loss of installation"));
    b.put(MahType.STRUCTURAL_COLLAPSE,
        ulist("Design margins", "Inspection", "Monitoring", "Weight control", "Subsidence monitoring"));

    t.put(MahType.DROPPED_OBJECT, ulist("Lifting equipment failure", "Procedure failure", "Wind / motion"));
    c.put(MahType.DROPPED_OBJECT, ulist("Impact on personnel", "Damage to safety critical equipment", "Riser damage"));
    b.put(MahType.DROPPED_OBJECT,
        ulist("Lifting procedures", "Crane integrity", "Drop-zone barriers", "Helideck arrangement"));

    t.put(MahType.HELICOPTER_LOSS, ulist("Mechanical failure", "Weather", "Pilot error", "Bird strike"));
    c.put(MahType.HELICOPTER_LOSS, ulist("Loss of life", "Helideck fire"));
    b.put(MahType.HELICOPTER_LOSS, ulist("Helideck design", "Foam system", "Helideck fire team", "Operating limits"));

    t.put(MahType.SHIP_COLLISION, ulist("Drifting vessel", "Powered vessel", "Loss of position keeping"));
    c.put(MahType.SHIP_COLLISION, ulist("Structural damage", "Riser rupture", "Topside escalation"));
    b.put(MahType.SHIP_COLLISION,
        ulist("Radar / AIS", "500 m zone monitoring", "Standby vessel", "Hull design margins"));

    t.put(MahType.FIRE_EXPLOSION, ulist("Hydrocarbon ignition", "Hot work", "Electrical fault", "Lightning strike"));
    c.put(MahType.FIRE_EXPLOSION, ulist("Personnel injury", "Equipment damage", "Escalation"));
    b.put(MahType.FIRE_EXPLOSION, ulist("Hazardous area classification", "Ignition control", "Gas detection",
        "Fire detection", "PFP", "Blast walls"));

    t.put(MahType.TOXIC_RELEASE, ulist("H2S in production", "Mercury", "MEG / amines"));
    c.put(MahType.TOXIC_RELEASE, ulist("Personnel exposure", "Environmental impact"));
    b.put(MahType.TOXIC_RELEASE,
        ulist("H2S removal", "Mercury removal", "Personal monitors", "Escape masks", "Ventilation"));

    t.put(MahType.LOSS_OF_BUOYANCY,
        ulist("Compartment flooding", "Ballast system failure", "Storm overload", "Mooring failure"));
    c.put(MahType.LOSS_OF_BUOYANCY, ulist("Capsize", "Loss of installation"));
    b.put(MahType.LOSS_OF_BUOYANCY,
        ulist("Compartmentation", "Ballast control", "Mooring redundancy", "Damage stability margins"));

    t.put(MahType.EXTREME_WEATHER, ulist("100-yr storm exceedance", "Hurricane", "Sea-ice / iceberg"));
    c.put(MahType.EXTREME_WEATHER, ulist("Structural overload", "Loss of station keeping", "Operational shutdown"));
    b.put(MahType.EXTREME_WEATHER,
        ulist("Met-ocean monitoring", "Weather operational limits", "Disconnection procedures (turret)"));

    THREATS = Collections.unmodifiableMap(t);
    CONSEQUENCES = Collections.unmodifiableMap(c);
    BARRIERS = Collections.unmodifiableMap(b);
  }

  private MahCatalogue() {
    // utility class
  }

  private static List<String> ulist(String... items) {
    return Collections.unmodifiableList(Arrays.asList(items));
  }

  /**
   * @param type the MAH
   * @return immutable list of typical threats
   */
  public static List<String> threatsFor(MahType type) {
    return THREATS.get(type);
  }

  /**
   * @param type the MAH
   * @return immutable list of typical consequences
   */
  public static List<String> consequencesFor(MahType type) {
    return CONSEQUENCES.get(type);
  }

  /**
   * @param type the MAH
   * @return immutable list of typical barrier classes
   */
  public static List<String> barriersFor(MahType type) {
    return BARRIERS.get(type);
  }
}
