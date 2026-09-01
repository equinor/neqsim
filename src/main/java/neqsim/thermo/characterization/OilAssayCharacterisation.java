package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility for characterising an oil system from refinery-assay cut information.
 *
 * <p>
 * An assay is represented as a set of pre-binned cuts on one declared composition basis: all cuts must use either mass
 * fractions or volume fractions. Volume-basis cuts are converted to a mass basis with the cut densities before moles
 * are calculated. Mixing mass- and volume-basis cuts in one assay is rejected because the missing conversion basis
 * would otherwise be ambiguous.
 * </p>
 *
 * <p>
 * Molar masses are in kg/mol throughout this class. Density inputs used for petroleum characterisation are specific
 * gravity (numerically equivalent to g/cm3 for the supported correlations); explicit kg/m3 input helpers are also
 * provided.
 * </p>
 *
 * <p>
 * Optional total-sulfur and total-nitrogen qualities are stored as mass fractions. Bulk qualities are reconstructed by
 * linear mass-basis mixing rules after the assay basis has been resolved.
 * </p>
 *
 * <p>
 * The TBP cut-boundary helpers preserve cut yields and boiling ranges, then use the midpoint of each boiling interval
 * as the representative boiling point for the existing NeqSim petroleum correlation. They do not convert ASTM D86/D1160
 * or other laboratory distillation methods to TBP.
 * </p>
 */
public class OilAssayCharacterisation implements Cloneable, Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(OilAssayCharacterisation.class);
  private static final double ASSAY_CLOSURE_TOLERANCE = 1e-3;
  private static final double PERCENT_TOLERANCE = 1e-8;
  private static final double KELVIN_OFFSET = 273.15;
  private static final double GRAMS_PER_KILOGRAM = 1000.0;
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;

  private transient SystemInterface system;
  private double totalAssayMass = 1.0;
  private List<AssayCut> cuts = new ArrayList<AssayCut>();

  /**
   * Create an assay characterisation attached to a thermodynamic system.
   *
   * @param system thermodynamic system that will receive generated TBP pseudo-components
   */
  public OilAssayCharacterisation(SystemInterface system) {
    setThermoSystem(system);
  }

  /**
   * Attach the assay characterisation to a thermodynamic system.
   *
   * @param system thermodynamic system
   */
  public void setThermoSystem(SystemInterface system) {
    this.system = Objects.requireNonNull(system, "system");
  }

  /**
   * Return the total assay mass basis.
   *
   * @return total assay mass in kg
   */
  public double getTotalAssayMass() {
    return totalAssayMass;
  }

  /**
   * Set the total assay mass basis used when converting cut mass fractions to moles.
   *
   * @param totalAssayMass total assay mass in kg
   */
  public void setTotalAssayMass(double totalAssayMass) {
    if (!Double.isFinite(totalAssayMass) || !(totalAssayMass > 0.0)) {
      throw new IllegalArgumentException("Total assay mass must be finite and positive");
    }
    this.totalAssayMass = totalAssayMass;
  }

  /** Remove all assay cuts. */
  public void clearCuts() {
    cuts.clear();
  }

  /**
   * Add one pre-binned assay cut.
   *
   * @param cut assay cut
   */
  public void addCut(AssayCut cut) {
    cuts.add(Objects.requireNonNull(cut, "cut"));
  }

  /**
   * Add a collection of pre-binned assay cuts.
   *
   * @param cuts assay cuts; {@code null} is ignored
   */
  public void addCuts(Collection<AssayCut> cuts) {
    if (cuts == null) {
      return;
    }
    for (AssayCut cut : cuts) {
      addCut(cut);
    }
  }

  /**
   * Return the configured assay cuts in insertion order.
   *
   * @return unmodifiable cut list
   */
  public List<AssayCut> getCuts() {
    return Collections.unmodifiableList(cuts);
  }

  /**
   * Add pre-binned true-boiling-point cuts from cumulative volume-percent boundaries.
   *
   * <p>
   * The first cumulative point must be 0 vol% and the last must be 100 vol%. The arrays of cumulative yield and
   * boiling-point boundaries must have the same length. One specific gravity is required for each resulting interval.
   * The interval midpoint is retained as the representative boiling point and the complete lower/upper boiling range is
   * stored on the generated {@link AssayCut}.
   * </p>
   *
   * @param namePrefix component-name prefix; generated names are prefix + 1, prefix + 2, ...
   * @param cumulativeVolumePercent cumulative liquid-volume yield in percent, from 0 to 100
   * @param boilingPointCelsius TBP cut boundaries in degC
   * @param specificGravity specific gravity for each interval
   */
  public void addTBPCutBoundariesCelsius(String namePrefix, double[] cumulativeVolumePercent,
      double[] boilingPointCelsius, double[] specificGravity) {
    if (boilingPointCelsius == null) {
      throw new IllegalArgumentException("TBP boiling-point boundaries cannot be null");
    }
    double[] boilingPointKelvin = new double[boilingPointCelsius.length];
    for (int i = 0; i < boilingPointCelsius.length; i++) {
      if (!Double.isFinite(boilingPointCelsius[i])) {
        throw new IllegalArgumentException("TBP boiling-point boundaries must be finite");
      }
      boilingPointKelvin[i] = boilingPointCelsius[i] + KELVIN_OFFSET;
    }
    addTBPCutBoundariesKelvin(namePrefix, cumulativeVolumePercent, boilingPointKelvin, specificGravity);
  }

  /**
   * Add pre-binned true-boiling-point cuts from cumulative volume-percent boundaries.
   *
   * @param namePrefix component-name prefix; generated names are prefix + 1, prefix + 2, ...
   * @param cumulativeVolumePercent cumulative liquid-volume yield in percent, from 0 to 100
   * @param boilingPointKelvin TBP cut boundaries in K
   * @param specificGravity specific gravity for each interval
   */
  public void addTBPCutBoundariesKelvin(String namePrefix, double[] cumulativeVolumePercent,
      double[] boilingPointKelvin, double[] specificGravity) {
    validateTBPCutBoundaries(namePrefix, cumulativeVolumePercent, boilingPointKelvin, specificGravity);

    for (int i = 0; i < specificGravity.length; i++) {
      double volumeFraction = (cumulativeVolumePercent[i + 1] - cumulativeVolumePercent[i]) / 100.0;
      AssayCut cut = new AssayCut(namePrefix + (i + 1)).withVolumeFraction(volumeFraction)
          .withSpecificGravity(specificGravity[i])
          .withBoilingRangeKelvin(boilingPointKelvin[i], boilingPointKelvin[i + 1]);
      addCut(cut);
    }
  }

  /**
   * Resolve the configured assay to mass fractions without mutating the thermodynamic system.
   *
   * @return mass fractions in the same order as {@link #getCuts()}
   */
  public double[] getResolvedMassFractions() {
    double[] fractions = resolveMassFractions();
    return fractions.clone();
  }

  /**
   * Reconstruct the bulk assay specific gravity from the configured cut yields and densities.
   *
   * <p>
   * The calculation uses ideal additive liquid volumes. For resolved cut mass fractions {@code w_i} and cut specific
   * gravities {@code SG_i}, the bulk value is {@code 1 / sum(w_i / SG_i)}. The same expression is used for mass- and
   * liquid-volume-basis assays after the existing basis and closure validation has been applied. No thermodynamic
   * component is created or modified.
   * </p>
   *
   * <p>
   * This is a screening property at the density reference conditions represented by the cut inputs. It does not apply
   * temperature correction, excess-volume, or blend-contraction models.
   * </p>
   *
   * @return reconstructed dimensionless bulk specific gravity
   * @throws IllegalStateException if the assay is empty, incomplete, mixed-basis, or lacks a cut density
   */
  public double getBulkSpecificGravity() {
    if (cuts.isEmpty()) {
      throw new IllegalStateException("No assay cuts supplied");
    }

    double[] massFractions = resolveMassFractions();
    double reciprocalBulkSpecificGravity = 0.0;
    for (int i = 0; i < cuts.size(); i++) {
      reciprocalBulkSpecificGravity += massFractions[i] / cuts.get(i).resolveDensity();
    }

    if (!Double.isFinite(reciprocalBulkSpecificGravity) || !(reciprocalBulkSpecificGravity > 0.0)) {
      throw new IllegalStateException("Unable to reconstruct bulk specific gravity from assay cuts");
    }
    return 1.0 / reciprocalBulkSpecificGravity;
  }

  /**
   * Reconstruct the bulk assay API gravity from {@link #getBulkSpecificGravity()}.
   *
   * @return reconstructed bulk gravity in degrees API
   * @throws IllegalStateException if bulk specific gravity cannot be reconstructed
   */
  public double getBulkApiGravity() {
    return 141.5 / getBulkSpecificGravity() - 131.5;
  }

  /**
   * Reconstruct the bulk assay density at 60 degF from {@link #getBulkSpecificGravity()}.
   *
   * <p>
   * This method keeps dimensionless specific gravity separate from physical density. It uses a water density of 999.016
   * kg/m3 at 60 degF and inherits the ideal-additive-volume assumptions and validation boundary of
   * {@link #getBulkSpecificGravity()}.
   * </p>
   *
   * @return reconstructed bulk density at 60 degF in kg/m3
   * @throws IllegalStateException if bulk specific gravity cannot be reconstructed
   */
  public double getBulkDensityKgPerCubicMetreAt60F() {
    return getBulkSpecificGravity() * WATER_DENSITY_60F_KG_M3;
  }

  /**
   * Reconstruct the bulk assay total-sulfur mass fraction from assay-cut sulfur data.
   *
   * <p>
   * The calculation is the linear mass-basis mixing rule {@code sum(w_i S_i)}, where {@code w_i} are the resolved assay
   * mass fractions and {@code S_i} are cut sulfur mass fractions. Volume-basis assays therefore use the same
   * density-based conversion as {@link #getResolvedMassFractions()}. Every positive-yield cut must define sulfur;
   * zero-yield cuts do not contribute.
   * </p>
   *
   * @return bulk total-sulfur mass fraction on a 0-1 basis
   * @throws IllegalStateException if the assay is empty, incomplete, mixed-basis, or lacks sulfur data for a
   * positive-yield cut
   */
  public double getBulkSulfurMassFraction() {
    if (cuts.isEmpty()) {
      throw new IllegalStateException("No assay cuts supplied");
    }

    double[] massFractions = resolveMassFractions();
    double bulkSulfurMassFraction = 0.0;
    for (int i = 0; i < cuts.size(); i++) {
      if (massFractions[i] > 0.0) {
        bulkSulfurMassFraction += massFractions[i] * cuts.get(i).getSulfurMassFraction();
      }
    }

    if (!Double.isFinite(bulkSulfurMassFraction) || bulkSulfurMassFraction < 0.0 || bulkSulfurMassFraction > 1.0) {
      throw new IllegalStateException("Unable to reconstruct bulk sulfur mass fraction from assay cuts");
    }
    return bulkSulfurMassFraction;
  }

  /**
   * Reconstruct the bulk assay total sulfur in mass percent.
   *
   * @return bulk total sulfur in mass percent
   * @throws IllegalStateException if bulk sulfur cannot be reconstructed
   */
  public double getBulkSulfurMassPercent() {
    return 100.0 * getBulkSulfurMassFraction();
  }

  /**
   * Reconstruct the bulk assay total-nitrogen mass fraction from assay-cut nitrogen data.
   *
   * <p>
   * The calculation is the linear mass-basis mixing rule {@code sum(w_i N_i)}, where {@code w_i} are the resolved assay
   * mass fractions and {@code N_i} are cut nitrogen mass fractions. Volume-basis assays therefore use the same
   * density-based conversion as {@link #getResolvedMassFractions()}. Every positive-yield cut must define nitrogen;
   * zero-yield cuts do not contribute.
   * </p>
   *
   * @return bulk total-nitrogen mass fraction on a 0-1 basis
   * @throws IllegalStateException if the assay is empty, incomplete, mixed-basis, or lacks nitrogen data for a
   * positive-yield cut
   */
  public double getBulkNitrogenMassFraction() {
    if (cuts.isEmpty()) {
      throw new IllegalStateException("No assay cuts supplied");
    }

    double[] massFractions = resolveMassFractions();
    double bulkNitrogenMassFraction = 0.0;
    for (int i = 0; i < cuts.size(); i++) {
      if (massFractions[i] > 0.0) {
        bulkNitrogenMassFraction += massFractions[i] * cuts.get(i).getNitrogenMassFraction();
      }
    }

    if (!Double.isFinite(bulkNitrogenMassFraction) || bulkNitrogenMassFraction < 0.0
        || bulkNitrogenMassFraction > 1.0) {
      throw new IllegalStateException("Unable to reconstruct bulk nitrogen mass fraction from assay cuts");
    }
    return bulkNitrogenMassFraction;
  }

  /**
   * Reconstruct the bulk assay total nitrogen in mass percent.
   *
   * @return bulk total nitrogen in mass percent
   * @throws IllegalStateException if bulk nitrogen cannot be reconstructed
   */
  public double getBulkNitrogenMassPercent() {
    return 100.0 * getBulkNitrogenMassFraction();
  }

  /**
   * Generate TBP pseudo-components for all configured cuts and add them to the attached system.
   *
   * <p>
   * All inputs are resolved and validated before the first pseudo-component is added. Existing component names are
   * rejected so that repeated calls cannot silently double the assay.
   * </p>
   */
  public void apply() {
    if (system == null) {
      throw new IllegalStateException("Thermodynamic system not attached to assay data");
    }
    if (cuts.isEmpty()) {
      logger.warn("No assay cuts supplied - nothing to characterise");
      return;
    }

    validateUniqueCutNames();
    validateUniqueStandardComponentNames();
    double[] massFractions = resolveMassFractions();
    List<ResolvedCut> resolvedCuts = new ArrayList<ResolvedCut>();
    double reconstructedMass = 0.0;
    SystemInterface validationSystem = null;

    for (int i = 0; i < cuts.size(); i++) {
      AssayCut cut = cuts.get(i);
      double massFraction = massFractions[i];
      if (!(massFraction > 0.0)) {
        continue;
      }

      if (cut.isStandardComponent()) {
        if (cut.hasVolumeFraction()) {
          throw new IllegalStateException(
              "Standard components require a mass-basis assay fraction for cut " + cut.getName());
        }
        if (cut.hasPetroleumPseudoComponentDefinition()) {
          throw new IllegalStateException(
              "Standard component cut cannot also define petroleum pseudo-component properties: " + cut.getName());
        }

        String standardComponentName = cut.getStandardComponentName();
        if (system.hasComponent(standardComponentName, false)) {
          throw new IllegalStateException(
              "Assay standard component already exists in system: " + standardComponentName);
        }

        if (validationSystem == null) {
          validationSystem = system.clone();
        }
        ComponentInterface validationComponent;
        try {
          validationSystem.addComponent(standardComponentName, 1.0);
          validationComponent = validationSystem.getComponent(standardComponentName);
        } catch (Exception ex) {
          throw new IllegalStateException("Unknown or unavailable assay standard component: " + standardComponentName,
              ex);
        }
        if (validationComponent == null) {
          throw new IllegalStateException("Unknown or unavailable assay standard component: " + standardComponentName);
        }
        double molarMass = validationComponent.getMolarMass();
        if (!Double.isFinite(molarMass) || !(molarMass > 0.0)) {
          throw new IllegalStateException("Invalid molar mass for assay standard component: " + standardComponentName);
        }
        double moles = totalAssayMass * massFraction / molarMass;
        if (!Double.isFinite(moles) || !(moles > 0.0)) {
          throw new IllegalStateException(
              "Calculated mole amount for assay cut " + cut.getName() + " is not finite and positive");
        }

        resolvedCuts.add(new ResolvedCut(cut, standardComponentName, Double.NaN, molarMass, moles));
        reconstructedMass += moles * molarMass;
        continue;
      }

      String pseudoComponentName = cut.getName() + "_PC";
      if (system.hasComponent(pseudoComponentName, false)) {
        throw new IllegalStateException("Assay pseudo-component already exists in system: " + pseudoComponentName);
      }

      double density = cut.resolveDensity();
      double molarMass;
      if (cut.hasMolarMass()) {
        molarMass = cut.resolveMolarMass(0.0, 0.0);
      } else {
        double boilingPoint = cut.resolveAverageBoilingPoint();
        molarMass = cut.resolveMolarMass(density, boilingPoint);
      }
      double moles = totalAssayMass * massFraction / molarMass;

      if (!Double.isFinite(moles) || !(moles > 0.0)) {
        throw new IllegalStateException(
            "Calculated mole amount for assay cut " + cut.getName() + " is not finite and positive");
      }

      resolvedCuts.add(new ResolvedCut(cut, null, density, molarMass, moles));
      reconstructedMass += moles * molarMass;
    }

    double relativeMassError = Math.abs(reconstructedMass - totalAssayMass) / Math.max(totalAssayMass, 1.0e-30);
    if (!Double.isFinite(relativeMassError) || relativeMassError > 1.0e-10) {
      throw new IllegalStateException(
          "Resolved assay does not conserve the configured total mass; relative error=" + relativeMassError);
    }

    for (ResolvedCut resolvedCut : resolvedCuts) {
      if (resolvedCut.standardComponentName != null) {
        system.addComponent(resolvedCut.standardComponentName, resolvedCut.moles);
      } else {
        system.addTBPfraction(resolvedCut.cut.getName(), resolvedCut.moles, resolvedCut.molarMass, resolvedCut.density);
      }
    }
  }

  private void validateTBPCutBoundaries(String namePrefix, double[] cumulativeVolumePercent,
      double[] boilingPointKelvin, double[] specificGravity) {
    if (namePrefix == null || namePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("TBP cut name prefix cannot be empty");
    }
    if (cumulativeVolumePercent == null || boilingPointKelvin == null || specificGravity == null) {
      throw new IllegalArgumentException("TBP cut-boundary arrays cannot be null");
    }
    if (cumulativeVolumePercent.length < 2 || cumulativeVolumePercent.length != boilingPointKelvin.length
        || specificGravity.length != cumulativeVolumePercent.length - 1) {
      throw new IllegalArgumentException(
          "TBP boundaries require N cumulative yields, N temperatures, and N-1 densities");
    }
    if (Math.abs(cumulativeVolumePercent[0]) > PERCENT_TOLERANCE
        || Math.abs(cumulativeVolumePercent[cumulativeVolumePercent.length - 1] - 100.0) > PERCENT_TOLERANCE) {
      throw new IllegalArgumentException("TBP cumulative volume yield must span 0 to 100 percent");
    }

    for (int i = 0; i < cumulativeVolumePercent.length; i++) {
      double cumulativeYield = cumulativeVolumePercent[i];
      double boilingPoint = boilingPointKelvin[i];
      if (!Double.isFinite(cumulativeYield) || cumulativeYield < -PERCENT_TOLERANCE
          || cumulativeYield > 100.0 + PERCENT_TOLERANCE) {
        throw new IllegalArgumentException("TBP cumulative volume yields must be finite and between 0 and 100");
      }
      if (!Double.isFinite(boilingPoint) || !(boilingPoint > 0.0)) {
        throw new IllegalArgumentException("TBP boiling-point boundaries must be finite and positive");
      }
      if (i > 0 && !(cumulativeVolumePercent[i] > cumulativeVolumePercent[i - 1])) {
        throw new IllegalArgumentException("TBP cumulative volume yields must be strictly increasing");
      }
      if (i > 0 && !(boilingPointKelvin[i] > boilingPointKelvin[i - 1])) {
        throw new IllegalArgumentException("TBP boiling-point boundaries must be strictly increasing");
      }
    }

    for (double density : specificGravity) {
      validateSpecificGravity(density);
    }
  }

  private void validateUniqueCutNames() {
    Set<String> names = new HashSet<String>();
    for (AssayCut cut : cuts) {
      if (!names.add(cut.getName())) {
        throw new IllegalStateException("Duplicate assay cut name: " + cut.getName());
      }
    }
  }

  private void validateUniqueStandardComponentNames() {
    Set<String> componentNames = new HashSet<String>();
    for (AssayCut cut : cuts) {
      if (cut.isStandardComponent()
          && !componentNames.add(cut.getStandardComponentName().toLowerCase(java.util.Locale.ROOT))) {
        throw new IllegalStateException("Duplicate assay standard component: " + cut.getStandardComponentName());
      }
    }
  }

  private double[] resolveMassFractions() {
    if (cuts.isEmpty()) {
      return new double[0];
    }

    double[] declaredFractions = new double[cuts.size()];
    FractionBasis basis = null;
    double totalDeclaredFraction = 0.0;

    for (int i = 0; i < cuts.size(); i++) {
      AssayCut cut = cuts.get(i);
      boolean hasMass = cut.hasMassFraction();
      boolean hasVolume = cut.hasVolumeFraction();
      if (hasMass == hasVolume) {
        throw new IllegalStateException(
            "Assay cut " + cut.getName() + " must define exactly one mass or volume fraction");
      }

      FractionBasis cutBasis = hasMass ? FractionBasis.MASS : FractionBasis.VOLUME;
      if (basis == null) {
        basis = cutBasis;
      } else if (basis != cutBasis) {
        throw new IllegalStateException("Assay cuts cannot mix mass-fraction and volume-fraction bases");
      }

      double fraction = hasMass ? cut.getMassFraction() : cut.getVolumeFraction();
      declaredFractions[i] = fraction;
      totalDeclaredFraction += fraction;
    }

    if (!Double.isFinite(totalDeclaredFraction) || !(totalDeclaredFraction > 0.0)) {
      throw new IllegalStateException("No valid assay fractions supplied");
    }
    if (Math.abs(totalDeclaredFraction - 1.0) > ASSAY_CLOSURE_TOLERANCE) {
      throw new IllegalStateException("Assay fractions must sum to 1.0 within " + ASSAY_CLOSURE_TOLERANCE
          + "; supplied sum=" + totalDeclaredFraction);
    }

    for (int i = 0; i < declaredFractions.length; i++) {
      declaredFractions[i] /= totalDeclaredFraction;
    }

    if (basis == FractionBasis.MASS) {
      return declaredFractions;
    }

    double[] massFractions = new double[cuts.size()];
    double totalRelativeMass = 0.0;
    for (int i = 0; i < cuts.size(); i++) {
      double relativeMass = declaredFractions[i] * cuts.get(i).resolveDensity();
      massFractions[i] = relativeMass;
      totalRelativeMass += relativeMass;
    }
    if (!Double.isFinite(totalRelativeMass) || !(totalRelativeMass > 0.0)) {
      throw new IllegalStateException("Unable to derive mass fractions from volume-basis assay data");
    }
    for (int i = 0; i < massFractions.length; i++) {
      massFractions[i] /= totalRelativeMass;
    }
    return massFractions;
  }

  @Override
  public OilAssayCharacterisation clone() {
    try {
      OilAssayCharacterisation clone = (OilAssayCharacterisation) super.clone();
      clone.cuts = new ArrayList<AssayCut>();
      for (AssayCut cut : cuts) {
        clone.cuts.add(cut.clone());
      }
      clone.system = system;
      return clone;
    } catch (CloneNotSupportedException ex) {
      throw new IllegalStateException("Clone not supported", ex);
    }
  }

  private enum FractionBasis {
    MASS, VOLUME
  }

  private static final class ResolvedCut {
    private final AssayCut cut;
    private final String standardComponentName;
    private final double density;
    private final double molarMass;
    private final double moles;

    private ResolvedCut(AssayCut cut, String standardComponentName, double density, double molarMass, double moles) {
      this.cut = cut;
      this.standardComponentName = standardComponentName;
      this.density = density;
      this.molarMass = molarMass;
      this.moles = moles;
    }
  }

  /** Representation of one pre-binned petroleum assay cut. */
  public static final class AssayCut implements Cloneable, Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private Double massFraction;
    private Double volumeFraction;
    private Double density;
    private Double apiGravity;
    private Double averageBoilingPointKelvin;
    private Double lowerBoilingPointKelvin;
    private Double upperBoilingPointKelvin;
    private Double molarMass;
    private Double sulfurMassFraction;
    private Double nitrogenMassFraction;
    private Double watsonCharacterizationFactor;
    private String standardComponentName;

    /**
     * Create an assay cut.
     *
     * @param name pseudo-component base name; {@code _PC} is added by NeqSim
     */
    public AssayCut(String name) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Assay cut name cannot be empty");
      }
      if (name.contains("_PC")) {
        throw new IllegalArgumentException("Assay cut name cannot contain reserved _PC pseudo-component marker");
      }
      this.name = name;
    }

    /**
     * Return the cut name.
     *
     * @return cut name
     */
    public String getName() {
      return name;
    }

    /**
     * Set a mass fraction on a 0-1 basis.
     *
     * @param massFraction mass fraction from 0 to 1
     * @return this cut
     */
    public AssayCut withMassFraction(double massFraction) {
      this.massFraction = sanitiseFraction(massFraction);
      return this;
    }

    /**
     * Set a mass fraction in weight percent.
     *
     * @param weightPercent mass percentage from 0 to 100
     * @return this cut
     */
    public AssayCut withWeightPercent(double weightPercent) {
      this.massFraction = sanitisePercent(weightPercent);
      return this;
    }

    /**
     * Mark this mass-basis assay cut as an authoritative NeqSim standard component.
     *
     * <p>
     * Standard components use the attached thermodynamic system's component database and molar mass. They are added
     * with {@link SystemInterface#addComponent(String, double)} rather than the petroleum pseudo-component correlation.
     * Density, boiling-point, Watson-factor, and explicit molar-mass inputs are therefore not applicable and fail
     * during preflight.
     * </p>
     *
     * @param componentName exact NeqSim standard-component identifier
     * @return this cut
     */
    public AssayCut withStandardComponent(String componentName) {
      if (componentName == null || componentName.trim().isEmpty()) {
        throw new IllegalArgumentException("Standard component name cannot be empty");
      }
      this.standardComponentName = componentName.trim();
      return this;
    }

    /**
     * Set a liquid-volume fraction on a 0-1 basis.
     *
     * @param volumeFraction volume fraction from 0 to 1
     * @return this cut
     */
    public AssayCut withVolumeFraction(double volumeFraction) {
      this.volumeFraction = sanitiseFraction(volumeFraction);
      return this;
    }

    /**
     * Set a liquid-volume fraction in volume percent.
     *
     * @param volumePercent volume percentage from 0 to 100
     * @return this cut
     */
    public AssayCut withVolumePercent(double volumePercent) {
      this.volumeFraction = sanitisePercent(volumePercent);
      return this;
    }

    /**
     * Set petroleum density using the legacy flexible input convention.
     *
     * <p>
     * Values up to 1.5 are interpreted as specific gravity (numerically g/cm3). Values above 1.5 are interpreted as
     * kg/m3 and divided by 1000. New code should prefer {@link #withSpecificGravity(double)} or
     * {@link #withDensityKgPerCubicMetre(double)}.
     * </p>
     *
     * @param density specific gravity/g/cm3 or density in kg/m3
     * @return this cut
     */
    public AssayCut withDensity(double density) {
      validatePositiveFinite(density, "Density");
      this.density = density > 1.5 ? density / 1000.0 : density;
      return this;
    }

    /**
     * Set petroleum specific gravity.
     *
     * @param specificGravity dimensionless specific gravity, numerically equivalent to g/cm3
     * @return this cut
     */
    public AssayCut withSpecificGravity(double specificGravity) {
      validateSpecificGravity(specificGravity);
      this.density = specificGravity;
      return this;
    }

    /**
     * Set liquid density in kg/m3.
     *
     * @param densityKgPerCubicMetre liquid density in kg/m3
     * @return this cut
     */
    public AssayCut withDensityKgPerCubicMetre(double densityKgPerCubicMetre) {
      validatePositiveFinite(densityKgPerCubicMetre, "Density");
      this.density = densityKgPerCubicMetre / 1000.0;
      return this;
    }

    /**
     * Set API gravity at the conventional 60 degF reference.
     *
     * <p>
     * Negative API gravities are valid for liquids denser than water; only values at or below -131.5 are rejected
     * because the API conversion becomes undefined.
     * </p>
     *
     * @param apiGravity API gravity in degrees API
     * @return this cut
     */
    public AssayCut withApiGravity(double apiGravity) {
      if (!Double.isFinite(apiGravity) || !(apiGravity > -131.5)) {
        throw new IllegalArgumentException("API gravity must be finite and greater than -131.5");
      }
      this.apiGravity = apiGravity;
      return this;
    }

    /**
     * Set the representative cut boiling point in K.
     *
     * @param temperatureKelvin representative boiling point in K
     * @return this cut
     */
    public AssayCut withAverageBoilingPointKelvin(double temperatureKelvin) {
      validatePositiveFinite(temperatureKelvin, "Boiling point");
      if (watsonCharacterizationFactor != null) {
        throw new IllegalArgumentException(
            "Representative boiling point cannot be combined with an explicit Watson factor");
      }
      if (lowerBoilingPointKelvin != null && temperatureKelvin < lowerBoilingPointKelvin) {
        throw new IllegalArgumentException("Representative boiling point cannot be below lower boundary");
      }
      if (upperBoilingPointKelvin != null && temperatureKelvin > upperBoilingPointKelvin) {
        throw new IllegalArgumentException("Representative boiling point cannot exceed upper boundary");
      }
      this.averageBoilingPointKelvin = temperatureKelvin;
      return this;
    }

    /**
     * Set the representative cut boiling point in degC.
     *
     * @param temperatureCelsius representative boiling point in degC
     * @return this cut
     */
    public AssayCut withAverageBoilingPointCelsius(double temperatureCelsius) {
      if (!Double.isFinite(temperatureCelsius)) {
        throw new IllegalArgumentException("Boiling point must be finite");
      }
      return withAverageBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set the representative cut boiling point in degF.
     *
     * @param temperatureFahrenheit representative boiling point in degF
     * @return this cut
     */
    public AssayCut withAverageBoilingPointFahrenheit(double temperatureFahrenheit) {
      if (!Double.isFinite(temperatureFahrenheit)) {
        throw new IllegalArgumentException("Boiling point must be finite");
      }
      double temperatureCelsius = (temperatureFahrenheit - 32.0) * 5.0 / 9.0;
      return withAverageBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set an explicit UOP/Watson characterization factor for deriving the representative boiling point.
     *
     * <p>
     * When no representative boiling point is supplied, the cut resolves it from {@code T_b = (K_W SG)^3 / 1.8}, where
     * {@code T_b} is in K and {@code SG} is the existing dimensionless specific-gravity view. An explicit
     * representative boiling point and Watson factor are mutually exclusive so the authoritative source is unambiguous.
     * </p>
     *
     * @param watsonFactor dimensionless UOP/Watson characterization factor
     * @return this cut
     */
    public AssayCut withWatsonCharacterizationFactor(double watsonFactor) {
      validatePositiveFinite(watsonFactor, "Watson characterization factor");
      if (averageBoilingPointKelvin != null) {
        throw new IllegalArgumentException(
            "Explicit Watson factor cannot be combined with a representative boiling point");
      }
      this.watsonCharacterizationFactor = watsonFactor;
      return this;
    }

    /**
     * Set and preserve a lower boiling limit in K without inventing an upper limit.
     *
     * @param temperatureKelvin lower cut boundary in K
     * @return this cut
     */
    public AssayCut withLowerBoilingPointKelvin(double temperatureKelvin) {
      validatePositiveFinite(temperatureKelvin, "Lower boiling point");
      if (upperBoilingPointKelvin != null && !(upperBoilingPointKelvin > temperatureKelvin)) {
        throw new IllegalArgumentException("Upper boiling-point boundary must exceed lower boundary");
      }
      if (averageBoilingPointKelvin != null && averageBoilingPointKelvin < temperatureKelvin) {
        throw new IllegalArgumentException("Representative boiling point cannot be below lower boundary");
      }
      this.lowerBoilingPointKelvin = temperatureKelvin;
      return this;
    }

    /**
     * Set and preserve a lower boiling limit in degC without inventing an upper limit.
     *
     * @param temperatureCelsius lower cut boundary in degC
     * @return this cut
     */
    public AssayCut withLowerBoilingPointCelsius(double temperatureCelsius) {
      if (!Double.isFinite(temperatureCelsius)) {
        throw new IllegalArgumentException("Lower boiling point must be finite");
      }
      return withLowerBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set and preserve a lower boiling limit in degF without inventing an upper limit.
     *
     * @param temperatureFahrenheit lower cut boundary in degF
     * @return this cut
     */
    public AssayCut withLowerBoilingPointFahrenheit(double temperatureFahrenheit) {
      if (!Double.isFinite(temperatureFahrenheit)) {
        throw new IllegalArgumentException("Lower boiling point must be finite");
      }
      double temperatureCelsius = (temperatureFahrenheit - 32.0) * 5.0 / 9.0;
      return withLowerBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set and preserve an upper boiling limit in K without inventing a lower limit.
     *
     * @param temperatureKelvin upper cut boundary in K
     * @return this cut
     */
    public AssayCut withUpperBoilingPointKelvin(double temperatureKelvin) {
      validatePositiveFinite(temperatureKelvin, "Upper boiling point");
      if (lowerBoilingPointKelvin != null && !(temperatureKelvin > lowerBoilingPointKelvin)) {
        throw new IllegalArgumentException("Upper boiling-point boundary must exceed lower boundary");
      }
      if (averageBoilingPointKelvin != null && averageBoilingPointKelvin > temperatureKelvin) {
        throw new IllegalArgumentException("Representative boiling point cannot exceed upper boundary");
      }
      this.upperBoilingPointKelvin = temperatureKelvin;
      return this;
    }

    /**
     * Set and preserve an upper boiling limit in degC without inventing a lower limit.
     *
     * @param temperatureCelsius upper cut boundary in degC
     * @return this cut
     */
    public AssayCut withUpperBoilingPointCelsius(double temperatureCelsius) {
      if (!Double.isFinite(temperatureCelsius)) {
        throw new IllegalArgumentException("Upper boiling point must be finite");
      }
      return withUpperBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set and preserve an upper boiling limit in degF without inventing a lower limit.
     *
     * @param temperatureFahrenheit upper cut boundary in degF
     * @return this cut
     */
    public AssayCut withUpperBoilingPointFahrenheit(double temperatureFahrenheit) {
      if (!Double.isFinite(temperatureFahrenheit)) {
        throw new IllegalArgumentException("Upper boiling point must be finite");
      }
      double temperatureCelsius = (temperatureFahrenheit - 32.0) * 5.0 / 9.0;
      return withUpperBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Set and preserve a boiling interval in K.
     *
     * <p>
     * The arithmetic midpoint is used as the representative boiling point for the existing petroleum-characterisation
     * correlation.
     * </p>
     *
     * @param lowerTemperatureKelvin lower cut boundary in K
     * @param upperTemperatureKelvin upper cut boundary in K
     * @return this cut
     */
    public AssayCut withBoilingRangeKelvin(double lowerTemperatureKelvin, double upperTemperatureKelvin) {
      validatePositiveFinite(lowerTemperatureKelvin, "Lower boiling point");
      validatePositiveFinite(upperTemperatureKelvin, "Upper boiling point");
      if (!(upperTemperatureKelvin > lowerTemperatureKelvin)) {
        throw new IllegalArgumentException("Upper boiling-point boundary must exceed lower boundary");
      }
      if (watsonCharacterizationFactor != null) {
        throw new IllegalArgumentException("Boiling range cannot be combined with an explicit Watson factor");
      }
      this.lowerBoilingPointKelvin = lowerTemperatureKelvin;
      this.upperBoilingPointKelvin = upperTemperatureKelvin;
      this.averageBoilingPointKelvin = 0.5 * (lowerTemperatureKelvin + upperTemperatureKelvin);
      return this;
    }

    /**
     * Set and preserve a boiling interval in degC.
     *
     * @param lowerTemperatureCelsius lower cut boundary in degC
     * @param upperTemperatureCelsius upper cut boundary in degC
     * @return this cut
     */
    public AssayCut withBoilingRangeCelsius(double lowerTemperatureCelsius, double upperTemperatureCelsius) {
      if (!Double.isFinite(lowerTemperatureCelsius) || !Double.isFinite(upperTemperatureCelsius)) {
        throw new IllegalArgumentException("Boiling-point boundaries must be finite");
      }
      return withBoilingRangeKelvin(lowerTemperatureCelsius + KELVIN_OFFSET, upperTemperatureCelsius + KELVIN_OFFSET);
    }

    /**
     * Return whether the original cut boiling interval is available.
     *
     * @return true when both lower and upper boundaries are stored
     */
    public boolean hasBoilingRange() {
      return lowerBoilingPointKelvin != null && upperBoilingPointKelvin != null;
    }

    /**
     * Return whether a lower cut boiling boundary is available.
     *
     * @return true when a lower boundary is stored
     */
    public boolean hasLowerBoilingPoint() {
      return lowerBoilingPointKelvin != null;
    }

    /**
     * Return whether an upper cut boiling boundary is available.
     *
     * @return true when an upper boundary is stored
     */
    public boolean hasUpperBoilingPoint() {
      return upperBoilingPointKelvin != null;
    }

    /**
     * Return the lower cut boiling boundary.
     *
     * @return lower boundary in K
     */
    public double getLowerBoilingPointKelvin() {
      if (lowerBoilingPointKelvin == null) {
        throw new IllegalStateException("Lower boiling-point boundary not set for cut " + name);
      }
      return lowerBoilingPointKelvin;
    }

    /**
     * Return the upper cut boiling boundary.
     *
     * @return upper boundary in K
     */
    public double getUpperBoilingPointKelvin() {
      if (upperBoilingPointKelvin == null) {
        throw new IllegalStateException("Upper boiling-point boundary not set for cut " + name);
      }
      return upperBoilingPointKelvin;
    }

    /**
     * Set an explicit molar mass in kg/mol.
     *
     * <p>
     * This historical method is retained for compatibility. New code can use the unit-explicit
     * {@link #withMolarMassKgPerMol(double)} method.
     * </p>
     *
     * @param molarMass molar mass in kg/mol
     * @return this cut
     */
    public AssayCut withMolarMass(double molarMass) {
      return withMolarMassKgPerMol(molarMass);
    }

    /**
     * Set an explicit molar mass in kg/mol.
     *
     * @param molarMassKgPerMol molar mass in kg/mol
     * @return this cut
     */
    public AssayCut withMolarMassKgPerMol(double molarMassKgPerMol) {
      validatePositiveFinite(molarMassKgPerMol, "Molar mass");
      this.molarMass = molarMassKgPerMol;
      return this;
    }

    /**
     * Set an explicit molar mass in g/mol.
     *
     * @param molarMassGramPerMol molar mass in g/mol
     * @return this cut
     */
    public AssayCut withMolarMassGramPerMol(double molarMassGramPerMol) {
      validatePositiveFinite(molarMassGramPerMol, "Molar mass");
      this.molarMass = molarMassGramPerMol / 1000.0;
      return this;
    }

    /**
     * Set total sulfur as a mass fraction on a 0-1 basis.
     *
     * @param sulfurMassFraction total-sulfur mass fraction from 0 to 1
     * @return this cut
     */
    public AssayCut withSulfurMassFraction(double sulfurMassFraction) {
      this.sulfurMassFraction = sanitiseFraction(sulfurMassFraction);
      return this;
    }

    /**
     * Set total sulfur in mass percent.
     *
     * @param sulfurMassPercent total sulfur in mass percent from 0 to 100
     * @return this cut
     */
    public AssayCut withSulfurMassPercent(double sulfurMassPercent) {
      this.sulfurMassFraction = sanitisePercent(sulfurMassPercent);
      return this;
    }

    /**
     * Set total nitrogen as a mass fraction on a 0-1 basis.
     *
     * @param nitrogenMassFraction total-nitrogen mass fraction from 0 to 1
     * @return this cut
     */
    public AssayCut withNitrogenMassFraction(double nitrogenMassFraction) {
      this.nitrogenMassFraction = sanitiseFraction(nitrogenMassFraction);
      return this;
    }

    /**
     * Set total nitrogen in mass percent.
     *
     * @param nitrogenMassPercent total nitrogen in mass percent from 0 to 100
     * @return this cut
     */
    public AssayCut withNitrogenMassPercent(double nitrogenMassPercent) {
      this.nitrogenMassFraction = sanitisePercent(nitrogenMassPercent);
      return this;
    }

    /**
     * Return whether this cut uses a mass fraction.
     *
     * @return true when a mass fraction is set
     */
    public boolean hasMassFraction() {
      return massFraction != null;
    }

    /**
     * Return the mass fraction.
     *
     * @return mass fraction on a 0-1 basis
     */
    public double getMassFraction() {
      if (massFraction == null) {
        throw new IllegalStateException("Mass fraction not set");
      }
      return massFraction;
    }

    /**
     * Return whether this cut uses a volume fraction.
     *
     * @return true when a volume fraction is set
     */
    public boolean hasVolumeFraction() {
      return volumeFraction != null;
    }

    /**
     * Return the volume fraction.
     *
     * @return volume fraction on a 0-1 basis
     */
    public double getVolumeFraction() {
      if (volumeFraction == null) {
        throw new IllegalStateException("Volume fraction not set");
      }
      return volumeFraction;
    }

    /**
     * Return whether an explicit molar mass was supplied.
     *
     * @return true when an explicit molar mass is stored
     */
    public boolean hasMolarMass() {
      return molarMass != null;
    }

    /**
     * Return whether this cut maps to a NeqSim standard component.
     *
     * @return true when a standard-component identifier is stored
     */
    public boolean isStandardComponent() {
      return standardComponentName != null;
    }

    /**
     * Return the configured NeqSim standard-component identifier.
     *
     * @return standard-component identifier
     * @throws IllegalStateException if this cut is a petroleum pseudo-component
     */
    public String getStandardComponentName() {
      if (standardComponentName == null) {
        throw new IllegalStateException("Standard component name not set for cut " + name);
      }
      return standardComponentName;
    }

    private boolean hasPetroleumPseudoComponentDefinition() {
      return density != null || apiGravity != null || averageBoilingPointKelvin != null
          || lowerBoilingPointKelvin != null || upperBoilingPointKelvin != null || molarMass != null
          || watsonCharacterizationFactor != null;
    }

    /**
     * Return whether total-sulfur data are available for this cut.
     *
     * @return true when a sulfur mass fraction is stored
     */
    public boolean hasSulfurMassFraction() {
      return sulfurMassFraction != null;
    }

    /**
     * Return the cut total-sulfur mass fraction.
     *
     * @return total-sulfur mass fraction on a 0-1 basis
     * @throws IllegalStateException if sulfur data are not available
     */
    public double getSulfurMassFraction() {
      if (sulfurMassFraction == null) {
        throw new IllegalStateException("Sulfur mass fraction not set for cut " + name);
      }
      return sulfurMassFraction;
    }

    /**
     * Return whether total-nitrogen data are available for this cut.
     *
     * @return true when a nitrogen mass fraction is stored
     */
    public boolean hasNitrogenMassFraction() {
      return nitrogenMassFraction != null;
    }

    /**
     * Return the cut total-nitrogen mass fraction.
     *
     * @return total-nitrogen mass fraction on a 0-1 basis
     * @throws IllegalStateException if nitrogen data are not available
     */
    public double getNitrogenMassFraction() {
      if (nitrogenMassFraction == null) {
        throw new IllegalStateException("Nitrogen mass fraction not set for cut " + name);
      }
      return nitrogenMassFraction;
    }

    /**
     * Resolve the cut density used by petroleum correlations.
     *
     * @return density in g/cm3 / equivalent specific-gravity numeric value
     */
    public double resolveDensity() {
      if (density != null) {
        return density;
      }
      if (apiGravity != null) {
        return 141.5 / (apiGravity + 131.5);
      }
      throw new IllegalStateException("Density or API gravity required for cut " + name);
    }

    /**
     * Resolve the representative boiling point.
     *
     * @return boiling point in K
     */
    public double resolveAverageBoilingPoint() {
      if (averageBoilingPointKelvin != null) {
        return averageBoilingPointKelvin;
      }
      if (watsonCharacterizationFactor == null) {
        throw new IllegalStateException("Average boiling point missing for cut " + name);
      }

      double specificGravity = resolveDensity();
      double inferredBoilingPointKelvin = Math.pow(watsonCharacterizationFactor * specificGravity, 3.0) / 1.8;
      if (!Double.isFinite(inferredBoilingPointKelvin) || !(inferredBoilingPointKelvin > 0.0)) {
        throw new IllegalStateException("Unable to derive representative boiling point for cut " + name);
      }
      if (lowerBoilingPointKelvin != null && inferredBoilingPointKelvin < lowerBoilingPointKelvin) {
        throw new IllegalStateException(
            "Watson-derived representative boiling point is below lower boundary for cut " + name);
      }
      if (upperBoilingPointKelvin != null && inferredBoilingPointKelvin > upperBoilingPointKelvin) {
        throw new IllegalStateException(
            "Watson-derived representative boiling point exceeds upper boundary for cut " + name);
      }
      return inferredBoilingPointKelvin;
    }

    /**
     * Return whether an explicit UOP/Watson characterization factor is available.
     *
     * @return true when a factor is stored
     */
    public boolean hasWatsonCharacterizationFactor() {
      return watsonCharacterizationFactor != null;
    }

    /**
     * Calculate or return the UOP/Watson characterization factor for this assay cut.
     *
     * <p>
     * The calculation uses {@code K_W = (1.8 T_b)^(1/3) / SG}, where {@code T_b} is the representative normal boiling
     * point in K and {@code SG} is dimensionless specific gravity. This is equivalent to using the boiling point in
     * degrees Rankine. A configured boiling interval uses its arithmetic midpoint through
     * {@link #resolveAverageBoilingPoint()}.
     * </p>
     *
     * @return dimensionless UOP/Watson characterization factor
     * @throws IllegalStateException if density or representative boiling point is unavailable, or the result is invalid
     */
    public double getWatsonCharacterizationFactor() {
      if (watsonCharacterizationFactor != null) {
        return watsonCharacterizationFactor;
      }
      double factor = Math.cbrt(1.8) * Math.cbrt(resolveAverageBoilingPoint()) / resolveDensity();
      if (!Double.isFinite(factor) || !(factor > 0.0)) {
        throw new IllegalStateException("Unable to calculate Watson characterization factor for cut " + name);
      }
      return factor;
    }

    /**
     * Resolve the cut molar mass.
     *
     * <p>
     * If no explicit molar mass is stored, the existing NeqSim inverse petroleum correlation is used with density and
     * representative boiling point. The inverse petroleum correlation returns a g/mol-sized value, which is converted
     * to the kg/mol unit required by {@link SystemInterface#addTBPfraction(String, double, double, double)}.
     * </p>
     *
     * @param density specific gravity / g/cm3 numeric value
     * @param boilingPointKelvin representative boiling point in K
     * @return molar mass in kg/mol
     */
    public double resolveMolarMass(double density, double boilingPointKelvin) {
      if (molarMass != null) {
        return molarMass;
      }
      if (!(density > 0.0) || !(boilingPointKelvin > 0.0)) {
        throw new IllegalStateException("Cannot derive molar mass without density and boiling point");
      }
      double exponent = 2.3776;
      double densityExponent = 0.9371;
      double molarMassGramPerMol = 5.805e-5 * Math.pow(boilingPointKelvin, exponent)
          / Math.pow(density, densityExponent);
      return molarMassGramPerMol / GRAMS_PER_KILOGRAM;
    }

    @Override
    public AssayCut clone() throws CloneNotSupportedException {
      return (AssayCut) super.clone();
    }

    private static double sanitiseFraction(double fraction) {
      if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
        throw new IllegalArgumentException("Fraction must be finite and between 0 and 1");
      }
      return fraction;
    }

    private static double sanitisePercent(double percent) {
      if (!Double.isFinite(percent) || percent < 0.0 || percent > 100.0) {
        throw new IllegalArgumentException("Percent must be finite and between 0 and 100");
      }
      return percent / 100.0;
    }
  }

  private static void validateSpecificGravity(double specificGravity) {
    validatePositiveFinite(specificGravity, "Specific gravity");
  }

  private static void validatePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }
}
