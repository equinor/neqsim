package neqsim.process.chemistry;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a production chemical used in oil and gas operations.
 *
 * <p>
 * A production chemical is characterised by its type (function), active ingredient family
 * (chemistry), concentration of the active ingredient, ionic nature, pH, and a temperature
 * stability range. These properties drive compatibility evaluation against other chemicals and the
 * produced fluid (water, oil, gas, solids) by {@link ChemicalCompatibilityAssessor}.
 * </p>
 *
 * <p>
 * Use the static factory methods (e.g. {@link #scaleInhibitor(String, double)},
 * {@link #corrosionInhibitor(String, double)}) for typical defaults. The instance can be tuned with
 * the setters before evaluation.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProductionChemical implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Functional category of a production chemical.
   */
  public enum ChemicalType {
    /** Scale inhibitor (phosphonate, polymaleate, polyacrylate). */
    SCALE_INHIBITOR,
    /** Corrosion inhibitor (film-forming amine, imidazoline, quaternary). */
    CORROSION_INHIBITOR,
    /** Thermodynamic hydrate inhibitor (MEG, MeOH, DEG). */
    HYDRATE_INHIBITOR_THERMODYNAMIC,
    /** Low dosage hydrate inhibitor: kinetic (KHI) or anti-agglomerant (AA). */
    HYDRATE_INHIBITOR_LDHI,
    /** Demulsifier / emulsion breaker. */
    DEMULSIFIER,
    /** Wax / paraffin inhibitor. */
    WAX_INHIBITOR,
    /** Asphaltene inhibitor or dispersant. */
    ASPHALTENE_INHIBITOR,
    /** Biocide for SRB / general microbial control. */
    BIOCIDE,
    /** Oxygen scavenger (bisulphite-based). */
    OXYGEN_SCAVENGER,
    /** H2S scavenger (triazine, iron-based). */
    H2S_SCAVENGER,
    /** Antifoam (silicone, polyglycol). */
    ANTIFOAM,
    /** Drag reducer (polymer-based). */
    DRAG_REDUCER,
    /** pH adjuster (caustic, soda ash, mineral acid). */
    PH_ADJUSTER,
    /** Mineral or organic acid for stimulation / cleaning. */
    ACID,
    /** Chelating agent (EDTA, DTPA, NTA). */
    CHELANT,
    /** Other / custom chemical. */
    OTHER;
  }

  /**
   * Ionic nature of the active ingredient. Used by interaction rules to flag known
   * incompatibilities (e.g. cationic CI + anionic SI co-injection precipitation).
   */
  public enum IonicNature {
    /** Cationic active ingredient (positively charged in solution). */
    CATIONIC,
    /** Anionic active ingredient (negatively charged in solution). */
    ANIONIC,
    /** Non-ionic active ingredient. */
    NON_IONIC,
    /** Zwitterionic / amphoteric. */
    AMPHOTERIC,
    /** Unknown / not specified. */
    UNKNOWN;
  }

  // ─── Fields ─────────────────────────────────────────────

  /** Chemical name / commercial identifier. */
  private String name = "";

  /** Functional type. */
  private ChemicalType type = ChemicalType.OTHER;

  /** Active ingredient family (free text, e.g. "phosphonate", "imidazoline"). */
  private String activeIngredient = "";

  /** Active ingredient concentration in the neat product (wt%). */
  private double activeWtPct = 100.0;

  /** Injected dose or concentration in the produced fluid (ppm v/v unless noted). */
  private double dosagePpm = 0.0;

  /** pH of the neat or diluted product. */
  private double pH = 7.0;

  /** Ionic nature. */
  private IonicNature ionicNature = IonicNature.UNKNOWN;

  /** Lower temperature stability limit in Celsius. */
  private double minTemperatureC = -20.0;

  /** Upper temperature stability limit in Celsius. */
  private double maxTemperatureC = 150.0;

  /** Solvent / carrier (e.g. "water", "methanol", "aromatic"). */
  private String solvent = "water";

  /** Density of the neat product in kg/m3. */
  private double densityKgM3 = 1000.0;

  /** Free-form notes (vendor, batch, special handling). */
  private String notes = "";

  // ─── Constructors ───────────────────────────────────────

  /**
   * Default constructor.
   */
  public ProductionChemical() {}

  /**
   * Constructor with name and type.
   *
   * @param name chemical name
   * @param type functional type
   */
  public ProductionChemical(String name, ChemicalType type) {
    this.name = name;
    this.type = type;
  }

  // ─── Factory methods ────────────────────────────────────

  /**
   * Creates a phosphonate-based scale inhibitor with default properties.
   *
   * @param name commercial name
   * @param dosagePpm dose in ppm
   * @return configured ProductionChemical
   */
  public static ProductionChemical scaleInhibitor(String name, double dosagePpm) {
    ProductionChemical c = new ProductionChemical(name, ChemicalType.SCALE_INHIBITOR);
    c.activeIngredient = "phosphonate";
    c.activeWtPct = 30.0;
    c.dosagePpm = dosagePpm;
    c.pH = 4.0;
    c.ionicNature = IonicNature.ANIONIC;
    c.minTemperatureC = 0.0;
    c.maxTemperatureC = 175.0;
    return c;
  }

  /**
   * Creates an imidazoline-based corrosion inhibitor with default properties.
   *
   * @param name commercial name
   * @param dosagePpm dose in ppm
   * @return configured ProductionChemical
   */
  public static ProductionChemical corrosionInhibitor(String name, double dosagePpm) {
    ProductionChemical c = new ProductionChemical(name, ChemicalType.CORROSION_INHIBITOR);
    c.activeIngredient = "imidazoline";
    c.activeWtPct = 20.0;
    c.dosagePpm = dosagePpm;
    c.pH = 6.0;
    c.ionicNature = IonicNature.CATIONIC;
    c.minTemperatureC = 0.0;
    c.maxTemperatureC = 150.0;
    c.solvent = "aromatic";
    return c;
  }

  /**
   * Creates a thermodynamic hydrate inhibitor (MEG by default).
   *
   * @param name commercial name
   * @param dosagePpm dose in ppm (for trace) — for bulk MEG injection use dosagePpm = 0 and set the
   *        actual mass flow on the injection stream
   * @return configured ProductionChemical
   */
  public static ProductionChemical thermodynamicHydrateInhibitor(String name, double dosagePpm) {
    ProductionChemical c =
        new ProductionChemical(name, ChemicalType.HYDRATE_INHIBITOR_THERMODYNAMIC);
    c.activeIngredient = "MEG";
    c.activeWtPct = 90.0;
    c.dosagePpm = dosagePpm;
    c.pH = 7.5;
    c.ionicNature = IonicNature.NON_IONIC;
    c.minTemperatureC = -50.0;
    c.maxTemperatureC = 200.0;
    return c;
  }

  /**
   * Creates a triazine-based H2S scavenger.
   *
   * @param name commercial name
   * @param dosagePpm dose in ppm
   * @return configured ProductionChemical
   */
  public static ProductionChemical h2sScavenger(String name, double dosagePpm) {
    ProductionChemical c = new ProductionChemical(name, ChemicalType.H2S_SCAVENGER);
    c.activeIngredient = "MEA-triazine";
    c.activeWtPct = 40.0;
    c.dosagePpm = dosagePpm;
    c.pH = 10.5;
    c.ionicNature = IonicNature.NON_IONIC;
    c.minTemperatureC = 0.0;
    c.maxTemperatureC = 80.0;
    return c;
  }

  /**
   * Creates a mineral acid for stimulation / cleaning (HCl by default).
   *
   * @param name commercial name
   * @param activeWtPct acid concentration in wt%
   * @return configured ProductionChemical
   */
  public static ProductionChemical acid(String name, double activeWtPct) {
    ProductionChemical c = new ProductionChemical(name, ChemicalType.ACID);
    c.activeIngredient = "HCl";
    c.activeWtPct = activeWtPct;
    c.pH = 0.5;
    c.ionicNature = IonicNature.ANIONIC;
    c.minTemperatureC = 0.0;
    c.maxTemperatureC = 120.0;
    return c;
  }

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the chemical name.
   *
   * @param name commercial name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Sets the chemical type.
   *
   * @param type functional category
   */
  public void setType(ChemicalType type) {
    this.type = type;
  }

  /**
   * Sets the active ingredient family.
   *
   * @param activeIngredient free-text identifier
   */
  public void setActiveIngredient(String activeIngredient) {
    this.activeIngredient = activeIngredient;
  }

  /**
   * Sets the active ingredient weight percent in the neat product.
   *
   * @param wtPct weight percent (0-100)
   */
  public void setActiveWtPct(double wtPct) {
    this.activeWtPct = wtPct;
  }

  /**
   * Sets the injected dose in ppm.
   *
   * @param dosagePpm dose in ppm
   */
  public void setDosagePpm(double dosagePpm) {
    this.dosagePpm = dosagePpm;
  }

  /**
   * Sets the pH.
   *
   * @param pH pH value (0-14)
   */
  public void setPH(double pH) {
    this.pH = pH;
  }

  /**
   * Sets the ionic nature.
   *
   * @param ionicNature ionic classification
   */
  public void setIonicNature(IonicNature ionicNature) {
    this.ionicNature = ionicNature;
  }

  /**
   * Sets the temperature stability range.
   *
   * @param minC lower limit in Celsius
   * @param maxC upper limit in Celsius
   */
  public void setTemperatureRangeC(double minC, double maxC) {
    this.minTemperatureC = minC;
    this.maxTemperatureC = maxC;
  }

  /**
   * Sets the solvent / carrier.
   *
   * @param solvent solvent name
   */
  public void setSolvent(String solvent) {
    this.solvent = solvent;
  }

  /**
   * Sets the density.
   *
   * @param kgM3 density in kg/m3
   */
  public void setDensityKgM3(double kgM3) {
    this.densityKgM3 = kgM3;
  }

  /**
   * Sets free-form notes.
   *
   * @param notes notes text
   */
  public void setNotes(String notes) {
    this.notes = notes;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets the chemical name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the chemical type.
   *
   * @return functional type
   */
  public ChemicalType getType() {
    return type;
  }

  /**
   * Gets the active ingredient identifier.
   *
   * @return active ingredient
   */
  public String getActiveIngredient() {
    return activeIngredient;
  }

  /**
   * Gets the active ingredient concentration.
   *
   * @return wt% active
   */
  public double getActiveWtPct() {
    return activeWtPct;
  }

  /**
   * Gets the injected dose.
   *
   * @return dose in ppm
   */
  public double getDosagePpm() {
    return dosagePpm;
  }

  /**
   * Gets the pH.
   *
   * @return pH value
   */
  public double getPH() {
    return pH;
  }

  /**
   * Gets the ionic nature.
   *
   * @return ionic classification
   */
  public IonicNature getIonicNature() {
    return ionicNature;
  }

  /**
   * Gets the lower temperature stability limit.
   *
   * @return minimum temperature in Celsius
   */
  public double getMinTemperatureC() {
    return minTemperatureC;
  }

  /**
   * Gets the upper temperature stability limit.
   *
   * @return maximum temperature in Celsius
   */
  public double getMaxTemperatureC() {
    return maxTemperatureC;
  }

  /**
   * Gets the solvent.
   *
   * @return solvent identifier
   */
  public String getSolvent() {
    return solvent;
  }

  /**
   * Gets the density.
   *
   * @return density in kg/m3
   */
  public double getDensityKgM3() {
    return densityKgM3;
  }

  /**
   * Gets the notes.
   *
   * @return notes string
   */
  public String getNotes() {
    return notes;
  }

  // ─── Output ─────────────────────────────────────────────

  /**
   * Returns whether this chemical is stable at the given operating temperature.
   *
   * @param temperatureC operating temperature in Celsius
   * @return true if temperatureC is within the stability range
   */
  public boolean isStableAt(double temperatureC) {
    return temperatureC >= minTemperatureC && temperatureC <= maxTemperatureC;
  }

  /**
   * Returns a map representation of the chemical (for JSON serialisation).
   *
   * @return ordered map of fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("type", type.name());
    map.put("activeIngredient", activeIngredient);
    map.put("activeWtPct", activeWtPct);
    map.put("dosagePpm", dosagePpm);
    map.put("pH", pH);
    map.put("ionicNature", ionicNature.name());
    map.put("minTemperatureC", minTemperatureC);
    map.put("maxTemperatureC", maxTemperatureC);
    map.put("solvent", solvent);
    map.put("densityKgM3", densityKgM3);
    if (notes != null && !notes.isEmpty()) {
      map.put("notes", notes);
    }
    return map;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return name + " [" + type.name() + ", " + activeIngredient + ", " + dosagePpm + " ppm]";
  }
}
