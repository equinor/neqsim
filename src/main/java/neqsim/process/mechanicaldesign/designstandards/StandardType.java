package neqsim.process.mechanicaldesign.designstandards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Enumeration of supported international and industry design standards for mechanical design.
 *
 * <p>
 * Each standard type includes metadata about its code, name, applicable equipment types, default
 * version, and the design standard category it belongs to. Standards can be looked up by code or
 * filtered by equipment type.
 * </p>
 *
 * <p>
 * The design standard categories align with the existing NeqSim mechanical design framework:
 * </p>
 * <ul>
 * <li>{@code pressure vessel design code} - For pressure vessel wall thickness calculations</li>
 * <li>{@code separator process design} - For separator sizing</li>
 * <li>{@code gas scrubber process design} - For scrubber sizing</li>
 * <li>{@code pipeline design codes} - For pipeline wall thickness</li>
 * <li>{@code compressor design codes} - For compressor design</li>
 * <li>{@code material plate design codes} - For plate material selection</li>
 * <li>{@code material pipe design codes} - For pipe material selection</li>
 * <li>{@code plate Joint Efficiency design codes} - For weld joint efficiency</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public enum StandardType {

  // NORSOK Standards (Norwegian Shelf)
  NORSOK_L_001("NORSOK-L-001", "Pipeline systems", "Rev 6",
      new String[] {"Pipeline", "AdiabaticPipe", "Pipe"},
      "pipeline design codes"), NORSOK_P_001("NORSOK-P-001", "Process design", "Rev 5",
          new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber", "Scrubber"},
          "separator process design"), NORSOK_P_002("NORSOK-P-002", "Process system design",
              "Rev 5", new String[] {"Separator", "ThreePhaseSeparator", "Compressor", "Pump"},
              "separator process design"), NORSOK_M_001("NORSOK-M-001", "Materials selection",
                  "Rev 6", new String[] {"Pipeline", "Separator", "ThreePhaseSeparator"},
                  "material plate design codes"), NORSOK_M_630("NORSOK-M-630",
                      "Material data sheets for piping", "Rev 7",
                      new String[] {"AdiabaticPipe", "Pipe", "Valve"},
                      "material pipe design codes"),

  // ASME Standards (American Society of Mechanical Engineers)
  ASME_VIII_DIV1("ASME-VIII-Div1", "Pressure Vessels Division 1", "2021",
      new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber", "Scrubber", "Adsorber"},
      "pressure vessel design code"), ASME_VIII_DIV2("ASME-VIII-Div2",
          "Pressure Vessels Division 2", "2021",
          new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber", "Scrubber"},
          "pressure vessel design code"), ASME_B31_3("ASME-B31.3", "Process Piping", "2022",
              new String[] {"AdiabaticPipe", "Pipe", "Valve"}, "pipeline design codes"), ASME_B31_4(
                  "ASME-B31.4", "Pipeline Transportation Liquid Hydrocarbons", "2022",
                  new String[] {"Pipeline", "AdiabaticPipe"}, "pipeline design codes"), ASME_B31_8(
                      "ASME-B31.8", "Gas Transmission and Distribution Piping", "2022",
                      new String[] {"Pipeline", "AdiabaticPipe"}, "pipeline design codes"),

  // API Standards (American Petroleum Institute)
  API_617("API-617", "Axial and Centrifugal Compressors", "8th Ed", new String[] {"Compressor"},
      "compressor design codes"), API_610("API-610", "Centrifugal Pumps", "12th Ed",
          new String[] {"Pump"}, "pump design codes"), API_650("API-650",
              "Welded Tanks for Oil Storage", "13th Ed", new String[] {"Tank", "SimpleTankFiller"},
              "pressure vessel design code"), API_620("API-620",
                  "Large Welded Low-Pressure Storage Tanks", "13th Ed",
                  new String[] {"Tank", "SimpleTankFiller"},
                  "pressure vessel design code"), API_660("API-660",
                      "Shell and Tube Heat Exchangers", "9th Ed",
                      new String[] {"HeatExchanger", "Heater", "Cooler"},
                      "heat exchanger design codes"), API_661("API-661",
                          "Air-Cooled Heat Exchangers", "7th Ed",
                          new String[] {"HeatExchanger", "Cooler"},
                          "heat exchanger design codes"), API_521("API-521",
                              "Pressure-relieving and Depressuring Systems", "7th Ed",
                              new String[] {"Valve", "ThrottlingValve"},
                              "valve design codes"), API_526("API-526",
                                  "Flanged Steel Pressure Relief Valves", "7th Ed",
                                  new String[] {"Valve", "ThrottlingValve"},
                                  "valve design codes"), API_5L("API-5L", "Line Pipe", "46th Ed",
                                      new String[] {"Pipeline", "AdiabaticPipe", "Pipe"},
                                      "material pipe design codes"), API_12J("API-12J",
                                          "Oil and Gas Separators", "8th Ed", new String[] {
                                              "Separator", "ThreePhaseSeparator", "GasScrubber"},
                                          "separator process design"),

  // DNV Standards (Det Norske Veritas)
  DNV_ST_F101("DNV-ST-F101", "Submarine Pipeline Systems", "2021",
      new String[] {"Pipeline", "AdiabaticPipe"},
      "pipeline design codes"), DNV_OS_F101("DNV-OS-F101", "Submarine Pipeline Systems (Legacy)",
          "2013", new String[] {"Pipeline", "AdiabaticPipe"},
          "pipeline design codes"), DNV_RP_F105("DNV-RP-F105", "Free Spanning Pipelines", "2021",
              new String[] {"Pipeline", "AdiabaticPipe"}, "pipeline design codes"),

  // ISO Standards
  ISO_13623("ISO-13623", "Pipeline Transportation Systems", "2017",
      new String[] {"Pipeline", "AdiabaticPipe"}, "pipeline design codes"), ISO_15649("ISO-15649",
          "Petroleum and Natural Gas Process Piping", "2001",
          new String[] {"AdiabaticPipe", "Pipe"}, "pipeline design codes"), ISO_16812("ISO-16812",
              "Shell and Tube Heat Exchangers", "2019",
              new String[] {"HeatExchanger", "Heater", "Cooler"}, "heat exchanger design codes"),

  // ASTM Standards (American Society for Testing and Materials)
  ASTM_A106("ASTM-A106", "Seamless Carbon Steel Pipe", "2022",
      new String[] {"AdiabaticPipe", "Pipe", "Pipeline"},
      "material pipe design codes"), ASTM_A516("ASTM-A516", "Pressure Vessel Plates Carbon Steel",
          "2022", new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber", "Adsorber"},
          "material plate design codes"), ASTM_A333("ASTM-A333",
              "Seamless Pipe for Low-Temperature Service", "2022",
              new String[] {"AdiabaticPipe", "Pipe", "Pipeline"}, "material pipe design codes"),

  // EN Standards (European)
  EN_13480("EN-13480", "Metallic Industrial Piping", "2017", new String[] {"AdiabaticPipe", "Pipe"},
      "pipeline design codes"), EN_13445("EN-13445", "Unfired Pressure Vessels", "2021",
          new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber"},
          "pressure vessel design code"),

  // PD Standards (Published Document - UK)
  PD_5500("PD-5500", "Specification for Unfired Pressure Vessels", "2021",
      new String[] {"Separator", "ThreePhaseSeparator", "GasScrubber"},
      "pressure vessel design code");

  private final String code;
  private final String name;
  private final String defaultVersion;
  private final String[] applicableEquipmentTypes;
  private final String designStandardCategory;

  /**
   * Constructor for StandardType.
   *
   * @param code the standard code identifier
   * @param name the full name of the standard
   * @param defaultVersion the default version to use
   * @param applicableEquipmentTypes equipment types this standard applies to
   * @param designStandardCategory the NeqSim design standard category key
   */
  StandardType(String code, String name, String defaultVersion, String[] applicableEquipmentTypes,
      String designStandardCategory) {
    this.code = code;
    this.name = name;
    this.defaultVersion = defaultVersion;
    this.applicableEquipmentTypes = applicableEquipmentTypes;
    this.designStandardCategory = designStandardCategory;
  }

  /**
   * Get the standard code identifier.
   *
   * @return the code (e.g., "NORSOK-L-001")
   */
  public String getCode() {
    return code;
  }

  /**
   * Get the full name of the standard.
   *
   * @return the standard name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the default version for this standard.
   *
   * @return the default version string
   */
  public String getDefaultVersion() {
    return defaultVersion;
  }

  /**
   * Get the equipment types this standard applies to.
   *
   * @return array of applicable equipment type names
   */
  public String[] getApplicableEquipmentTypes() {
    return Arrays.copyOf(applicableEquipmentTypes, applicableEquipmentTypes.length);
  }

  /**
   * Get the NeqSim design standard category key.
   *
   * <p>
   * This corresponds to the keys used in {@code MechanicalDesign.getDesignStandard()} hashtable,
   * such as "pressure vessel design code", "separator process design", "pipeline design codes".
   * </p>
   *
   * @return the design standard category key
   */
  public String getDesignStandardCategory() {
    return designStandardCategory;
  }

  /**
   * Check if this standard applies to a given equipment type.
   *
   * @param equipmentType the equipment type to check
   * @return true if this standard is applicable
   */
  public boolean appliesTo(String equipmentType) {
    if (equipmentType == null) {
      return false;
    }
    String normalizedType = equipmentType.trim().toLowerCase();
    for (String type : applicableEquipmentTypes) {
      if (type.toLowerCase().equals(normalizedType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find a StandardType by its code.
   *
   * @param code the standard code to search for
   * @return the matching StandardType or null if not found
   */
  public static StandardType fromCode(String code) {
    if (code == null) {
      return null;
    }
    String normalizedCode = code.trim().toUpperCase().replace("_", "-");
    for (StandardType type : values()) {
      if (type.getCode().toUpperCase().replace("_", "-").equals(normalizedCode)) {
        return type;
      }
    }
    return null;
  }

  /**
   * Get all standards applicable to a given equipment type.
   *
   * @param equipmentType the equipment type to filter by
   * @return list of applicable standards
   */
  public static List<StandardType> getApplicableStandards(String equipmentType) {
    List<StandardType> applicable = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.appliesTo(equipmentType)) {
        applicable.add(type);
      }
    }
    return applicable;
  }

  /**
   * Get all NORSOK standards.
   *
   * @return list of NORSOK standards
   */
  public static List<StandardType> getNorsokStandards() {
    List<StandardType> norsok = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("NORSOK")) {
        norsok.add(type);
      }
    }
    return norsok;
  }

  /**
   * Get all ASME standards.
   *
   * @return list of ASME standards
   */
  public static List<StandardType> getAsmeStandards() {
    List<StandardType> asme = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("ASME")) {
        asme.add(type);
      }
    }
    return asme;
  }

  /**
   * Get all API standards.
   *
   * @return list of API standards
   */
  public static List<StandardType> getApiStandards() {
    List<StandardType> api = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("API")) {
        api.add(type);
      }
    }
    return api;
  }

  /**
   * Get all DNV standards.
   *
   * @return list of DNV standards
   */
  public static List<StandardType> getDnvStandards() {
    List<StandardType> dnv = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("DNV")) {
        dnv.add(type);
      }
    }
    return dnv;
  }

  /**
   * Get all standards for a specific design standard category.
   *
   * <p>
   * Categories include: "pressure vessel design code", "separator process design", "pipeline design
   * codes", "compressor design codes", etc.
   * </p>
   *
   * @param category the design standard category key
   * @return list of standards in that category
   */
  public static List<StandardType> getByCategory(String category) {
    List<StandardType> result = new ArrayList<StandardType>();
    if (category == null) {
      return result;
    }
    String normalizedCategory = category.trim().toLowerCase();
    for (StandardType type : values()) {
      if (type.getDesignStandardCategory().toLowerCase().equals(normalizedCategory)) {
        result.add(type);
      }
    }
    return result;
  }

  /**
   * Get all ISO standards.
   *
   * @return list of ISO standards
   */
  public static List<StandardType> getIsoStandards() {
    List<StandardType> iso = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("ISO")) {
        iso.add(type);
      }
    }
    return iso;
  }

  /**
   * Get all ASTM standards.
   *
   * @return list of ASTM standards
   */
  public static List<StandardType> getAstmStandards() {
    List<StandardType> astm = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("ASTM")) {
        astm.add(type);
      }
    }
    return astm;
  }

  /**
   * Get all EN (European) standards.
   *
   * @return list of EN standards
   */
  public static List<StandardType> getEnStandards() {
    List<StandardType> en = new ArrayList<StandardType>();
    for (StandardType type : values()) {
      if (type.getCode().startsWith("EN")) {
        en.add(type);
      }
    }
    return en;
  }

  /**
   * Get all unique design standard categories.
   *
   * @return list of category keys
   */
  public static List<String> getAllCategories() {
    List<String> categories = new ArrayList<String>();
    for (StandardType type : values()) {
      if (!categories.contains(type.getDesignStandardCategory())) {
        categories.add(type.getDesignStandardCategory());
      }
    }
    return categories;
  }

  @Override
  public String toString() {
    return code + " - " + name + " (" + defaultVersion + ")";
  }
}
