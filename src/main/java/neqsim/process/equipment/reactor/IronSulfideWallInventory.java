package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Stateful inventory of iron-bearing corrosion products on a pipe or equipment wall.
 *
 * <p>
 * The inventory separates material that is attached to the wall from material in a process stream. This distinction is
 * important for old pipelines: iron sulfide can have accumulated during an earlier wet or sour period and can later
 * react when oxygen is introduced during a purge, shutdown, or restart. A conventional steady-state fluid flash has no
 * memory of that history.
 * </p>
 *
 * <p>
 * {@code ironOxideEquivalentMassKg} is reported on an Fe2O3-equivalent basis. Real corrosion products can be mixtures
 * of FeO, Fe3O4, FeOOH, and Fe2O3. The equivalent basis preserves the iron inventory without claiming a unique oxide
 * mineral.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class IronSulfideWallInventory implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Molar mass of sulfur in kg/mol. */
  public static final double SULFUR_MOLAR_MASS_KG_PER_MOL = 0.032065;
  /** Molar mass of stoichiometric FeS in kg/mol. */
  public static final double FES_MOLAR_MASS_KG_PER_MOL = 0.08791;
  /** Molar mass of siderite in kg/mol. */
  public static final double FECO3_MOLAR_MASS_KG_PER_MOL = 0.115854;
  /** Molar mass of hematite in kg/mol. */
  public static final double FE2O3_MOLAR_MASS_KG_PER_MOL = 0.159687;

  /** Screening categories for the dominant iron-sulfide phase. */
  public enum FeSPhase {
    /** Metastable, fine-grained FeS commonly formed at low temperature. */
    MACKINAWITE(4100.0, 1.0),
    /** Non-stoichiometric Fe1-xS, generally less reactive than mackinawite. */
    PYRRHOTITE(4600.0, 0.35),
    /** Crystalline stoichiometric FeS. */
    TROILITE(4840.0, 0.25),
    /** Mixed-valence Fe3S4 intermediate. */
    GREIGITE(4050.0, 0.55),
    /** Unresolved mixture of corrosion-product phases. */
    MIXED(4400.0, 0.60),
    /** Unknown phase; a neutral screening factor is applied. */
    UNKNOWN(4400.0, 0.50);

    private final double densityKgPerM3;
    private final double relativeOxidationReactivity;

    FeSPhase(double densityKgPerM3, double relativeOxidationReactivity) {
      this.densityKgPerM3 = densityKgPerM3;
      this.relativeOxidationReactivity = relativeOxidationReactivity;
    }

    /** @return representative solid density in kg/m3 */
    public double getDensityKgPerM3() {
      return densityKgPerM3;
    }

    /** @return relative oxidation-rate multiplier used for screening */
    public double getRelativeOxidationReactivity() {
      return relativeOxidationReactivity;
    }
  }

  /** Operating-history event types relevant to iron-sulfide formation and oxidation. */
  public enum ExposureType {
    /** Seawater or sulfate-rich water was present. */
    SEAWATER_EXPOSURE,
    /** Other produced, hydrotest, wash, or residual water was present. */
    WATER_EXPOSURE,
    /** Sour gas contacted the wall. */
    SOUR_GAS_EXPOSURE,
    /** Oxygen ingress or air exposure occurred. */
    OXYGEN_INGRESS,
    /** Nitrogen purge, potentially containing oxygen, occurred. */
    NITROGEN_PURGE,
    /** Equipment was shut down. */
    SHUTDOWN,
    /** Equipment was restarted. */
    RESTART,
    /** Inspection or laboratory evidence was recorded. */
    INSPECTION,
    /** User-defined event. */
    OTHER
  }

  /** Immutable operating-history entry. */
  public static class ExposureEvent implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ExposureType type;
    private final double durationHours;
    private final double temperatureC;
    private final double wettedFraction;
    private final String description;

    /**
     * Create an exposure event.
     *
     * @param type event type
     * @param durationHours non-negative duration in hours
     * @param temperatureC representative wall or fluid temperature in Celsius
     * @param wettedFraction wall fraction covered by an aqueous film, from 0 to 1
     * @param description provenance, observation, or engineering note
     */
    public ExposureEvent(ExposureType type, double durationHours, double temperatureC, double wettedFraction,
        String description) {
      if (type == null) {
        throw new IllegalArgumentException("exposure type must not be null");
      }
      if (!Double.isFinite(durationHours) || durationHours < 0.0) {
        throw new IllegalArgumentException("duration must be finite and non-negative");
      }
      if (!Double.isFinite(temperatureC)) {
        throw new IllegalArgumentException("temperature must be finite");
      }
      this.type = type;
      this.durationHours = durationHours;
      this.temperatureC = temperatureC;
      this.wettedFraction = clampFraction(wettedFraction, "wetted fraction");
      this.description = description == null ? "" : description;
    }

    /** @return event type */
    public ExposureType getType() {
      return type;
    }

    /** @return event duration in hours */
    public double getDurationHours() {
      return durationHours;
    }

    /** @return representative temperature in Celsius */
    public double getTemperatureC() {
      return temperatureC;
    }

    /** @return wall fraction covered by an aqueous film */
    public double getWettedFraction() {
      return wettedFraction;
    }

    /** @return provenance or engineering note */
    public String getDescription() {
      return description;
    }

    /** @return map suitable for JSON reporting */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("type", type.name());
      values.put("durationHours", durationHours);
      values.put("temperatureC", temperatureC);
      values.put("wettedFraction", wettedFraction);
      values.put("description", description);
      return values;
    }
  }

  private double feSMassKg;
  private double feCO3MassKg;
  private double ironOxideEquivalentMassKg;
  private double pipeSurfaceAreaM2 = 1.0;
  private double surfaceRoughnessM = 0.0;
  private double porosityFraction = 0.30;
  private double wettedFraction = 0.0;
  private double surfaceAreaMultiplier = 1.0;
  private FeSPhase feSPhase = FeSPhase.UNKNOWN;
  private final List<ExposureEvent> exposureHistory = new ArrayList<ExposureEvent>();

  /** Create an empty wall inventory. */
  public IronSulfideWallInventory() {
  }

  /**
   * Create an inventory with measured or estimated corrosion-product masses.
   *
   * @param feSMassKg FeS mass in kg
   * @param feCO3MassKg siderite mass in kg
   * @param ironOxideEquivalentMassKg iron oxide mass on an Fe2O3-equivalent basis in kg
   */
  public IronSulfideWallInventory(double feSMassKg, double feCO3MassKg, double ironOxideEquivalentMassKg) {
    setFeSMassKg(feSMassKg);
    setFeCO3MassKg(feCO3MassKg);
    setIronOxideEquivalentMassKg(ironOxideEquivalentMassKg);
  }

  /** @return FeS inventory in kg */
  public double getFeSMassKg() {
    return feSMassKg;
  }

  /** @param massKg FeS inventory in kg */
  public void setFeSMassKg(double massKg) {
    feSMassKg = requireNonNegative(massKg, "FeS mass");
  }

  /** @return FeCO3 inventory in kg */
  public double getFeCO3MassKg() {
    return feCO3MassKg;
  }

  /** @param massKg FeCO3 inventory in kg */
  public void setFeCO3MassKg(double massKg) {
    feCO3MassKg = requireNonNegative(massKg, "FeCO3 mass");
  }

  /** @return iron oxide mass in kg on an Fe2O3-equivalent basis */
  public double getIronOxideEquivalentMassKg() {
    return ironOxideEquivalentMassKg;
  }

  /** @param massKg iron oxide mass in kg on an Fe2O3-equivalent basis */
  public void setIronOxideEquivalentMassKg(double massKg) {
    ironOxideEquivalentMassKg = requireNonNegative(massKg, "iron oxide mass");
  }

  /** @return nominal internal surface area in m2 */
  public double getPipeSurfaceAreaM2() {
    return pipeSurfaceAreaM2;
  }

  /** @param areaM2 nominal internal surface area in m2 */
  public void setPipeSurfaceAreaM2(double areaM2) {
    if (!Double.isFinite(areaM2) || areaM2 <= 0.0) {
      throw new IllegalArgumentException("pipe surface area must be finite and positive");
    }
    pipeSurfaceAreaM2 = areaM2;
  }

  /** @return arithmetic wall roughness in m */
  public double getSurfaceRoughnessM() {
    return surfaceRoughnessM;
  }

  /** @param roughnessM arithmetic wall roughness in m */
  public void setSurfaceRoughnessM(double roughnessM) {
    surfaceRoughnessM = requireNonNegative(roughnessM, "surface roughness");
  }

  /** @return corrosion-product porosity fraction */
  public double getPorosityFraction() {
    return porosityFraction;
  }

  /** @param porosityFraction corrosion-product porosity from 0 to less than 1 */
  public void setPorosityFraction(double porosityFraction) {
    if (!Double.isFinite(porosityFraction) || porosityFraction < 0.0 || porosityFraction >= 1.0) {
      throw new IllegalArgumentException("porosity must be finite, non-negative, and less than one");
    }
    this.porosityFraction = porosityFraction;
  }

  /** @return fraction of wall covered by an aqueous film */
  public double getWettedFraction() {
    return wettedFraction;
  }

  /** @param wettedFraction fraction of wall covered by an aqueous film, from 0 to 1 */
  public void setWettedFraction(double wettedFraction) {
    this.wettedFraction = clampFraction(wettedFraction, "wetted fraction");
  }

  /** @return user-supplied multiplier converting nominal to reactive surface area */
  public double getSurfaceAreaMultiplier() {
    return surfaceAreaMultiplier;
  }

  /**
   * Set the reactive-area multiplier. It can represent roughness, cracks, porous scale, and deposits not captured by
   * nominal pipe geometry.
   *
   * @param multiplier positive reactive-area multiplier
   */
  public void setSurfaceAreaMultiplier(double multiplier) {
    if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
      throw new IllegalArgumentException("surface area multiplier must be finite and positive");
    }
    surfaceAreaMultiplier = multiplier;
  }

  /** @return effective wetted reactive area in m2 */
  public double getEffectiveWettedAreaM2() {
    return pipeSurfaceAreaM2 * surfaceAreaMultiplier * wettedFraction;
  }

  /** @return dominant FeS phase category */
  public FeSPhase getFeSPhase() {
    return feSPhase;
  }

  /** @param phase dominant FeS phase category */
  public void setFeSPhase(FeSPhase phase) {
    if (phase == null) {
      throw new IllegalArgumentException("FeS phase must not be null");
    }
    feSPhase = phase;
  }

  /**
   * Set FeS inventory from a measured average layer thickness.
   *
   * @param thicknessM average FeS layer thickness in m
   */
  public void setFeSMassFromThickness(double thicknessM) {
    double thickness = requireNonNegative(thicknessM, "FeS layer thickness");
    feSMassKg = thickness * pipeSurfaceAreaM2 * (1.0 - porosityFraction) * feSPhase.getDensityKgPerM3();
  }

  /**
   * Set all three inventories from a measured mixed-scale thickness and laboratory mass fractions.
   *
   * <p>
   * The supplied density is the skeletal (non-pore) density of the mixed corrosion product. Porosity is applied by the
   * inventory, so do not supply an already porosity-corrected bulk density. This method is useful when an inspection
   * reports one average scale thickness and XRD/SEM-EDS provides approximate FeS, FeCO3, and iron-oxide fractions.
   * </p>
   *
   * @param thicknessM average total scale thickness in m
   * @param skeletalDensityKgPerM3 non-pore mixed-scale density in kg/m3
   * @param feSMassFraction FeS mass fraction from 0 to 1
   * @param feCO3MassFraction FeCO3 mass fraction from 0 to 1
   * @param ironOxideMassFraction iron-oxide-equivalent mass fraction from 0 to 1
   */
  public void setMassesFromMeasuredScaleThickness(double thicknessM, double skeletalDensityKgPerM3,
      double feSMassFraction, double feCO3MassFraction, double ironOxideMassFraction) {
    double thickness = requireNonNegative(thicknessM, "scale thickness");
    if (!Double.isFinite(skeletalDensityKgPerM3) || skeletalDensityKgPerM3 <= 0.0) {
      throw new IllegalArgumentException("scale skeletal density must be finite and positive");
    }
    double feSFraction = clampFraction(feSMassFraction, "FeS mass fraction");
    double feCO3Fraction = clampFraction(feCO3MassFraction, "FeCO3 mass fraction");
    double oxideFraction = clampFraction(ironOxideMassFraction, "iron oxide mass fraction");
    double fractionSum = feSFraction + feCO3Fraction + oxideFraction;
    if (Math.abs(fractionSum - 1.0) > 1.0e-9) {
      throw new IllegalArgumentException("scale mass fractions must sum to one");
    }
    double totalScaleMassKg = thickness * pipeSurfaceAreaM2 * (1.0 - porosityFraction) * skeletalDensityKgPerM3;
    feSMassKg = totalScaleMassKg * feSFraction;
    feCO3MassKg = totalScaleMassKg * feCO3Fraction;
    ironOxideEquivalentMassKg = totalScaleMassKg * oxideFraction;
  }

  /** @return average FeS-equivalent layer thickness in m */
  public double getFeSEquivalentThicknessM() {
    double denominator = pipeSurfaceAreaM2 * (1.0 - porosityFraction) * feSPhase.getDensityKgPerM3();
    return denominator > 0.0 ? feSMassKg / denominator : 0.0;
  }

  /** @return maximum elemental sulfur contained in the FeS inventory in kg */
  public double getMaximumElementalSulfurMassKg() {
    return feSMassKg * SULFUR_MOLAR_MASS_KG_PER_MOL / FES_MOLAR_MASS_KG_PER_MOL;
  }

  /** Add an event to the auditable operating history. */
  public void recordExposure(ExposureEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("exposure event must not be null");
    }
    exposureHistory.add(event);
  }

  /** @return immutable exposure-history view */
  public List<ExposureEvent> getExposureHistory() {
    return Collections.unmodifiableList(exposureHistory);
  }

  /** Remove all recorded history without changing material inventories. */
  public void clearExposureHistory() {
    exposureHistory.clear();
  }

  void addFeSMassKg(double massKg) {
    feSMassKg = Math.max(0.0, feSMassKg + massKg);
  }

  void addFeCO3MassKg(double massKg) {
    feCO3MassKg = Math.max(0.0, feCO3MassKg + massKg);
  }

  void addIronOxideEquivalentMassKg(double massKg) {
    ironOxideEquivalentMassKg = Math.max(0.0, ironOxideEquivalentMassKg + massKg);
  }

  /** @return ordered values suitable for process reporting */
  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("FeS_mass_kg", feSMassKg);
    values.put("FeCO3_mass_kg", feCO3MassKg);
    values.put("ironOxideEquivalent_mass_kg", ironOxideEquivalentMassKg);
    values.put("maximumElementalSulfur_kg", getMaximumElementalSulfurMassKg());
    values.put("FeS_equivalentThickness_m", getFeSEquivalentThicknessM());
    values.put("pipeSurfaceArea_m2", pipeSurfaceAreaM2);
    values.put("surfaceRoughness_m", surfaceRoughnessM);
    values.put("porosityFraction", porosityFraction);
    values.put("wettedFraction", wettedFraction);
    values.put("surfaceAreaMultiplier", surfaceAreaMultiplier);
    values.put("effectiveWettedArea_m2", getEffectiveWettedAreaM2());
    values.put("FeS_phase", feSPhase.name());
    List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
    for (ExposureEvent event : exposureHistory) {
      events.add(event.toMap());
    }
    values.put("exposureHistory", events);
    return values;
  }

  /** @return JSON representation of material inventory, geometry, and history */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(toMap());
  }

  private static double requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return value;
  }

  private static double clampFraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and between zero and one");
    }
    return value;
  }
}
