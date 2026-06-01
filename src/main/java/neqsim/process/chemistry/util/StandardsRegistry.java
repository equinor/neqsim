package neqsim.process.chemistry.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight registry of industry standards applied by the chemistry models. Each chemistry model
 * exposes {@code getStandardsApplied()} returning a list of {@link StandardReference} so a
 * downstream audit can verify which codes the calculation relies on.
 *
 * <p>
 * The registry is intentionally small and curated: only standards actually used by the chemistry
 * package are listed. The full standards database lives in {@code designdata/standards/}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class StandardsRegistry implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private StandardsRegistry() {}

  /**
   * Reference to a single industry standard.
   */
  public static class StandardReference implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Standard code (e.g. "NACE TM0374"). */
    private final String code;

    /** Issuing organisation. */
    private final String organisation;

    /** Topic / scope. */
    private final String topic;

    /**
     * Constructs a reference.
     *
     * @param code standard code
     * @param organisation issuing organisation
     * @param topic short description of scope
     */
    public StandardReference(String code, String organisation, String topic) {
      this.code = code;
      this.organisation = organisation;
      this.topic = topic;
    }

    /**
     * Returns the standard code.
     *
     * @return code string
     */
    public String getCode() {
      return code;
    }

    /**
     * Returns the issuing organisation.
     *
     * @return organisation name
     */
    public String getOrganisation() {
      return organisation;
    }

    /**
     * Returns the topic.
     *
     * @return topic string
     */
    public String getTopic() {
      return topic;
    }

    /**
     * Returns the reference as a map for JSON output.
     *
     * @return ordered map
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("code", code);
      map.put("organisation", organisation);
      map.put("topic", topic);
      return map;
    }
  }

  // ─── Curated standards used by the chemistry package ────

  /** NACE scale inhibitor static jar test. */
  public static final StandardReference NACE_TM0374 = new StandardReference("NACE TM0374",
      "AMPP/NACE", "Calcium-sulfate and calcium-carbonate scale inhibitor evaluation (jar test)");

  /** NACE wheel test for corrosion inhibitor evaluation. */
  public static final StandardReference NACE_TM0169 = new StandardReference("NACE TM0169",
      "AMPP/NACE", "Laboratory immersion corrosion testing of metals");

  /** NACE selection of materials for use in H2S environments. */
  public static final StandardReference NACE_MR0175 =
      new StandardReference("NACE MR0175 / ISO 15156", "AMPP/NACE / ISO",
          "Materials for use in H2S-containing environments in oil and gas production");

  /** NACE inhibitor selection. */
  public static final StandardReference NACE_SP0775 = new StandardReference("NACE SP0775",
      "AMPP/NACE", "Preparation, installation, analysis, and interpretation of corrosion coupons");

  /** NORSOK CO2 corrosion rate model. */
  public static final StandardReference NORSOK_M506 = new StandardReference("NORSOK M-506",
      "Standards Norway", "CO2 corrosion rate calculation model");

  /** NORSOK material selection. */
  public static final StandardReference NORSOK_M001 =
      new StandardReference("NORSOK M-001", "Standards Norway", "Materials selection");

  /** API stimulation acid recommendations. */
  public static final StandardReference API_RP87 = new StandardReference("API RP 87", "API",
      "Recommended practice for analysis of oil-field waters");

  /** GPSA engineering data book — gas treating chapter. */
  public static final StandardReference GPSA_DB =
      new StandardReference("GPSA Engineering Data Book", "GPSA",
          "Section 21 — Hydrocarbon treating (H2S scavenger sizing)");

  /** ISO standard reference conditions for natural gas. */
  public static final StandardReference ISO_13443 = new StandardReference("ISO 13443", "ISO",
      "Natural gas — Standard reference conditions (15 C, 101.325 kPa)");

  /** ISO 15156 — sour service material qualification. */
  public static final StandardReference ISO_15156 = new StandardReference("ISO 15156", "ISO",
      "Petroleum and natural gas industries — Materials for use in H2S-containing environments");

  /** API erosional velocity. */
  public static final StandardReference API_RP14E = new StandardReference("API RP 14E", "API",
      "Recommended practice for design and installation of offshore production platform piping (erosional velocity)");

  /** ASTM mass-loss corrosion test. */
  public static final StandardReference ASTM_G31 = new StandardReference("ASTM G31", "ASTM",
      "Standard practice for laboratory immersion corrosion testing of metals");

  /** DNV CO2 pipeline materials. */
  public static final StandardReference DNV_RP_O501 =
      new StandardReference("DNV-RP-O501", "DNV", "Managing sand production and erosion");

  /**
   * Returns the registered standards as a list of maps for JSON serialisation.
   *
   * @param refs varargs of standard references
   * @return list of ordered maps
   */
  public static List<Map<String, Object>> toMapList(StandardReference... refs) {
    List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
    for (StandardReference ref : refs) {
      list.add(ref.toMap());
    }
    return list;
  }
}
