package neqsim.thermo.characterization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility class for combining and re-characterizing fluids containing pseudo components.
 *
 * <p>
 * This class provides methods for:
 * <ul>
 * <li>Combining multiple reservoir fluids with a common pseudo-component structure</li>
 * <li>Characterizing one fluid to match another's pseudo-component definition</li>
 * <li>Transferring binary interaction parameters between fluids</li>
 * </ul>
 *
 * <p>
 * Reference: Pedersen et al., "Phase Behavior of Petroleum Reservoir Fluids", Chapters 5.5-5.6.
 *
 * @author ESOL
 */
public final class PseudoComponentCombiner {
  private static final Logger logger = LogManager.getLogger(PseudoComponentCombiner.class);
  private static final double MASS_TOLERANCE = 1e-12;

  private PseudoComponentCombiner() {
  }

  /**
   * Combine one or more reservoir fluids and redistribute their pseudo components into a specified number of new pseudo
   * components. The new pseudo components are calculated using the weighting scheme described in Chapter 5.5 of
   * Pedersen et al. (mixing of multiple fluids), i.e. the properties of each resulting pseudo component are weighted by
   * the fluid fraction and the mole fraction of the contributing pseudo components.
   *
   * @param targetPseudoComponents number of pseudo components in the combined fluid
   * @param fluids input fluids
   * @return combined fluid with the requested number of pseudo components
   */
  public static SystemInterface combineReservoirFluids(int targetPseudoComponents, SystemInterface... fluids) {
    return combineReservoirFluids(targetPseudoComponents, Arrays.asList(fluids));
  }

  /**
   * Combine one or more reservoir fluids and redistribute their pseudo components into a specified number of new pseudo
   * components. The new pseudo components are calculated using the weighting scheme described in Chapter 5.5 of
   * Pedersen et al. (mixing of multiple fluids), i.e. the properties of each resulting pseudo component are weighted by
   * the fluid fraction and the mole fraction of the contributing pseudo components.
   *
   * @param targetPseudoComponents number of pseudo components in the combined fluid
   * @param fluids input fluids
   * @return combined fluid with the requested number of pseudo components
   */
  public static SystemInterface combineReservoirFluids(int targetPseudoComponents, Collection<SystemInterface> fluids) {
    if (targetPseudoComponents <= 0) {
      throw new IllegalArgumentException("Number of pseudo components must be positive");
    }

    if (fluids == null || fluids.isEmpty()) {
      throw new IllegalArgumentException("At least one fluid is required");
    }

    List<SystemInterface> fluidList = new ArrayList<>();
    for (SystemInterface fluid : fluids) {
      if (fluid != null) {
        fluidList.add(fluid);
      }
    }

    if (fluidList.isEmpty()) {
      throw new IllegalArgumentException("Input fluids are null");
    }

    SystemInterface combined = fluidList.get(0).clone();
    removeAllComponents(combined);

    Map<String, Double> baseComponents = new LinkedHashMap<>();
    List<List<PseudoComponentContribution>> perFluidContributions = new ArrayList<>();
    List<PseudoComponentContribution> allPseudoContributions = new ArrayList<>();

    for (SystemInterface fluid : fluidList) {
      FluidExtraction extraction = extractComponents(fluid);
      baseComponents = mergeBaseComponents(baseComponents, extraction.baseComponents);
      perFluidContributions.add(extraction.pseudoComponents);
      allPseudoContributions.addAll(extraction.pseudoComponents);
    }

    for (Map.Entry<String, Double> entry : baseComponents.entrySet()) {
      combined.addComponent(entry.getKey(), entry.getValue());
    }

    if (allPseudoContributions.isEmpty()) {
      finalizeFluid(combined);
      return combined;
    }

    List<Double> boundaries = determineQuantileBoundaries(allPseudoContributions, targetPseudoComponents);

    List<List<PseudoComponentProfile>> perFluidProfiles = new ArrayList<>();
    double[] fluidMassTotals = new double[perFluidContributions.size()];
    double[] fluidMoleTotals = new double[perFluidContributions.size()];

    for (int i = 0; i < perFluidContributions.size(); i++) {
      List<PseudoComponentProfile> profiles = distributeToProfiles(perFluidContributions.get(i), boundaries,
          targetPseudoComponents);
      perFluidProfiles.add(profiles);

      double totalMass = 0.0;
      double totalMoles = 0.0;
      for (PseudoComponentProfile profile : profiles) {
        totalMass += profile.getMass();
        totalMoles += profile.getMoles();
      }
      fluidMassTotals[i] = totalMass;
      fluidMoleTotals[i] = totalMoles;
    }

    List<PseudoComponentProfile> combinedProfiles = combineProfiles(perFluidProfiles, fluidMassTotals, fluidMoleTotals);

    int pseudoCounter = 1;
    for (PseudoComponentProfile profile : combinedProfiles) {
      if (!profile.isValid()) {
        continue;
      }

      String baseName = "PC" + pseudoCounter++;
      combined.addTBPfraction(baseName, profile.getMoles(), profile.getMolarMass(), profile.getDensity());

      ComponentInterface component = combined.getComponent(baseName + "_PC");
      if (component == null) {
        logger.warn("Failed to locate newly added pseudo component {}", baseName);
        continue;
      }

      profile.applyTo(component);
    }

    finalizeFluid(combined);
    return combined;
  }

  /**
   * Characterize a fluid to another fluid's pseudo component definition (Pedersen et al., Chapter 5.6). The pseudo
   * component cut points are derived from the reference fluid's pseudo component ordering and applied to the source
   * fluid.
   *
   * @param source fluid to characterize
   * @param reference fluid defining the pseudo component characterization
   * @return characterized fluid containing pseudo components compatible with the reference fluid
   */
  public static SystemInterface characterizeToReference(SystemInterface source, SystemInterface reference) {
    return characterizeToReferenceCore(source, reference, false, false, 0, false);
  }

  /**
   * Core implementation of {@link #characterizeToReference(SystemInterface, SystemInterface)}.
   *
   * <p>
   * When {@code inheritReferenceProperties} is {@code true} the characterized pseudo-components inherit the reference
   * fluid's lump properties (molar mass, density, critical constants, etc.), reproducing the Pedersen et al. (Chapter
   * 5.6) "Common EoS" slate: every fluid characterized to the same reference shares an identical pseudo-component
   * property set and differs only in the mole fractions. When {@code false} the lump properties are recomputed from the
   * source fluid's mass (grid-only behaviour using the reference cut boundaries).
   *
   * @param source fluid to characterize
   * @param reference fluid defining the pseudo component characterization
   * @param inheritReferenceProperties whether to inherit the reference lump properties
   * @param delump whether to delump each source lump into finer sub-fractions before re-distributing it onto the
   * reference cuts
   * @param delumpResolution number of sub-fractions per source lump when {@code delump} is {@code true}; values of 1 or
   * less disable splitting
   * @param sharedImaginaryBoundaries whether the reference cut boundaries are placed as equal-mass cuts on the
   * reference's imaginary (delumped) composition instead of boiling-point midpoints
   * @return characterized fluid containing pseudo components compatible with the reference fluid
   */
  private static SystemInterface characterizeToReferenceCore(SystemInterface source, SystemInterface reference,
      boolean inheritReferenceProperties, boolean delump, int delumpResolution, boolean sharedImaginaryBoundaries) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reference, "reference");

    if (delump && inheritReferenceProperties) {
      logger.warn("delumpBeforeRecharacterization=true is combined with inheritReferenceProperties=true: the "
          + "recomputed lump molar mass and density are still overwritten by the reference values, which largely "
          + "defeats the delumping. Set inheritReferenceProperties=false to keep the redistributed lump properties.");
    }

    SystemInterface characterized = source.clone();
    removeAllComponents(characterized);

    FluidExtraction sourceExtraction = extractComponents(source);
    FluidExtraction referenceExtraction = extractComponents(reference);

    for (Map.Entry<String, Double> entry : sourceExtraction.baseComponents.entrySet()) {
      characterized.addComponent(entry.getKey(), entry.getValue());
    }

    if (referenceExtraction.pseudoComponents.isEmpty()) {
      finalizeFluid(characterized);
      return characterized;
    }

    List<PseudoComponentContribution> sourcePseudoComponents = sourceExtraction.pseudoComponents;
    if (delump) {
      sourcePseudoComponents = delumpContributions(sourcePseudoComponents, delumpResolution);
    }

    List<Double> boundaries = sharedImaginaryBoundaries
        ? determineReferenceEqualMassBoundaries(referenceExtraction.pseudoComponents, delumpResolution)
        : determineReferenceBoundaries(referenceExtraction.pseudoComponents);
    List<PseudoComponentProfile> profiles = distributeToProfiles(sourcePseudoComponents, boundaries,
        referenceExtraction.pseudoComponents.size());

    for (int i = 0; i < referenceExtraction.pseudoComponents.size(); i++) {
      PseudoComponentProfile profile = profiles.get(i);
      if (!profile.isValid()) {
        continue;
      }

      String referenceName = referenceExtraction.pseudoComponents.get(i).name;
      PseudoComponentContribution referencePc = referenceExtraction.pseudoComponents.get(i);
      String baseName = stripPcSuffix(referenceName);
      double molarMass = inheritReferenceProperties ? referencePc.molarMass : profile.getMolarMass();
      double density = inheritReferenceProperties ? referencePc.density : profile.getDensity();
      characterized.addTBPfraction(baseName, profile.getMoles(), molarMass, density);

      ComponentInterface component = characterized.getComponent(baseName + "_PC");
      if (component == null) {
        logger.warn("Failed to locate newly added pseudo component {}", baseName);
        continue;
      }

      if (inheritReferenceProperties) {
        applyReferenceProperties(component, referencePc);
      } else {
        profile.applyTo(component);
      }
    }

    finalizeFluid(characterized);
    return characterized;
  }

  /**
   * Characterize a fluid to another fluid's pseudo component definition with options.
   *
   * <p>
   * This overload allows specifying options for BIP transfer, normalization, and validation.
   *
   * @param source fluid to characterize
   * @param reference fluid defining the pseudo component characterization
   * @param options characterization options
   * @return characterized fluid containing pseudo components compatible with the reference fluid
   */
  public static SystemInterface characterizeToReference(SystemInterface source, SystemInterface reference,
      CharacterizationOptions options) {
    Objects.requireNonNull(options, "options");

    SystemInterface characterized = characterizeToReferenceCore(source, reference,
        options.isInheritReferenceProperties(), options.isDelumpBeforeRecharacterization(),
        options.getDelumpResolution(), options.isSharedImaginaryBoundaries());

    if (options.isTransferBinaryInteractionParameters()) {
      transferBinaryInteractionParameters(reference, characterized);
    }

    if (options.isNormalizeComposition()) {
      normalizeComposition(characterized);
    }

    if (options.isGenerateValidationReport()) {
      CharacterizationValidationReport report = CharacterizationValidationReport.generate(source, reference,
          characterized);
      logger.info("Characterization validation:\n{}", report.toReportString());
    }

    return characterized;
  }

  /**
   * Copy the reference pseudo-component's properties onto a newly characterized component.
   *
   * <p>
   * Used by the Pedersen et al. (Chapter 5.6) "Common EoS" slate path so that fluids characterized to the same
   * reference share an identical pseudo-component property set. Each property is applied only when the reference value
   * is finite, leaving NeqSim's correlated default in place otherwise.
   *
   * @param component the characterized component to update
   * @param reference the reference pseudo-component contribution to inherit from
   */
  private static void applyReferenceProperties(ComponentInterface component, PseudoComponentContribution reference) {
    Objects.requireNonNull(component, "component");
    Objects.requireNonNull(reference, "reference");

    if (Double.isFinite(reference.normalBoilingPoint)) {
      component.setNormalBoilingPoint(reference.normalBoilingPoint);
    }
    if (Double.isFinite(reference.criticalTemperature)) {
      component.setTC(reference.criticalTemperature);
    }
    if (Double.isFinite(reference.criticalPressure)) {
      component.setPC(reference.criticalPressure);
    }
    if (Double.isFinite(reference.acentricFactor)) {
      component.setAcentricFactor(reference.acentricFactor);
    }
    if (Double.isFinite(reference.criticalVolume)) {
      component.setCriticalVolume(reference.criticalVolume);
    }
    if (Double.isFinite(reference.racketZ)) {
      component.setRacketZ(reference.racketZ);
    }
    if (Double.isFinite(reference.racketZCpa)) {
      component.setRacketZCPA(reference.racketZCpa);
    }
    if (Double.isFinite(reference.parachor)) {
      component.setParachorParameter(reference.parachor);
    }
    if (Double.isFinite(reference.criticalViscosity)) {
      component.setCriticalViscosity(reference.criticalViscosity);
    }
    if (Double.isFinite(reference.triplePointTemperature)) {
      component.setTriplePointTemperature(reference.triplePointTemperature);
    }
    if (Double.isFinite(reference.heatOfFusion)) {
      component.setHeatOfFusion(reference.heatOfFusion);
    }
    if (Double.isFinite(reference.idealGasEnthalpyOfFormation)) {
      component.setIdealGasEnthalpyOfFormation(reference.idealGasEnthalpyOfFormation);
    }
    if (Double.isFinite(reference.cpA)) {
      component.setCpA(reference.cpA);
    }
    if (Double.isFinite(reference.cpB)) {
      component.setCpB(reference.cpB);
    }
    if (Double.isFinite(reference.cpC)) {
      component.setCpC(reference.cpC);
    }
    if (Double.isFinite(reference.cpD)) {
      component.setCpD(reference.cpD);
    }
    if (Double.isFinite(reference.attractiveM) && component.getAttractiveTerm() != null) {
      component.getAttractiveTerm().setm(reference.attractiveM);
    }
  }

  /**
   * Characterize several fluids to a single shared set of pseudo components (a "common EoS" slate) following Pedersen
   * et al., "Phase Behavior of Petroleum Reservoir Fluids", Chapter 5.6 (Eqs. 5.55-5.60).
   *
   * <p>
   * Unlike {@link #combineReservoirFluids(int, java.util.Collection)} (Chapter 5.5), which blends all input fluids into
   * one fluid, this method keeps every fluid <b>separate</b> while forcing them to share an identical pseudo-component
   * property set. For each shared lump {@code i} the molar mass (Eq. 5.59), critical temperature, critical pressure and
   * acentric factor (Eqs. 5.55-5.58) are mole-fraction weighted averages across the input fluids,
   *
   * <pre>
   * X_i = sum_j Wgt(j) z_i^j X_i^j / sum_j Wgt(j) z_i^j ,
   * </pre>
   *
   * where {@code Wgt(j)} is the per-fluid weight and {@code z_i^j} is the mole fraction of lump {@code i} in fluid
   * {@code j}. The lump density is reconstructed from the weighted molar mass and the weighted molar volume (Peneloux
   * basis, Eq. 5.6). The returned fluids therefore differ only in their lump mole fractions, exactly as required when a
   * single equation of state must span several fields (a common-slate design basis).
   *
   * <p>
   * The number of shared pseudo components defaults to the largest pseudo-component count found among the input fluids.
   * Use {@link #characterizeToCommonSlate(java.util.List, double[], int)} to set it explicitly. The input fluids are
   * not modified; re-characterized clones are returned in input order.
   *
   * @param fluids fluids to characterize to the common slate (at least one, non-null entries)
   * @param weights per-fluid weight factors {@code Wgt(j)}; pass {@code null} for equal weighting. When non-null the
   * length must equal {@code fluids.size()} and at least one weight must be positive
   * @return re-characterized clones sharing the common pseudo-component slate, in input order
   */
  public static List<SystemInterface> characterizeToCommonSlate(List<SystemInterface> fluids, double[] weights) {
    return characterizeToCommonSlate(fluids, weights, inferCommonSlateSize(fluids));
  }

  /**
   * Characterize several fluids to a single shared set of pseudo components (a "common EoS" slate) with an explicit
   * number of shared lumps. See {@link #characterizeToCommonSlate(java.util.List, double[])} for the full description
   * of the weighting scheme (Pedersen Chapter 5.6, Eqs. 5.55-5.60).
   *
   * @param fluids fluids to characterize to the common slate (at least one, non-null entries)
   * @param weights per-fluid weight factors {@code Wgt(j)}; pass {@code null} for equal weighting. When non-null the
   * length must equal {@code fluids.size()} and at least one weight must be positive
   * @param targetPseudoComponents number of shared pseudo components (must be positive)
   * @return re-characterized clones sharing the common pseudo-component slate, in input order
   */
  public static List<SystemInterface> characterizeToCommonSlate(List<SystemInterface> fluids, double[] weights,
      int targetPseudoComponents) {
    Objects.requireNonNull(fluids, "fluids");
    if (fluids.isEmpty()) {
      throw new IllegalArgumentException("At least one fluid is required");
    }
    for (SystemInterface fluid : fluids) {
      Objects.requireNonNull(fluid, "fluid");
    }
    if (targetPseudoComponents <= 0) {
      throw new IllegalArgumentException("Number of pseudo components must be positive");
    }

    double[] effectiveWeights = resolveWeights(weights, fluids.size());

    List<FluidExtraction> extractions = new ArrayList<>(fluids.size());
    List<Double> totalMoles = new ArrayList<>(fluids.size());
    boolean anyPseudoComponents = false;
    for (SystemInterface fluid : fluids) {
      FluidExtraction extraction = extractComponents(fluid);
      extractions.add(extraction);

      double moles = 0.0;
      for (Double value : extraction.baseComponents.values()) {
        moles += value;
      }
      for (PseudoComponentContribution contribution : extraction.pseudoComponents) {
        moles += contribution.moles;
      }
      totalMoles.add(moles);
      if (!extraction.pseudoComponents.isEmpty()) {
        anyPseudoComponents = true;
      }
    }

    if (!anyPseudoComponents) {
      List<SystemInterface> clones = new ArrayList<>(fluids.size());
      for (SystemInterface fluid : fluids) {
        clones.add(fluid.clone());
      }
      return clones;
    }

    // Build the shared cut grid from a weighted imaginary composition rather than the raw pooled
    // pseudo-components. Each fluid is normalised to unit pseudo-mass and then scaled by its slate
    // weight Wgt(j), so the equal-mass quantile boundaries reflect the requested weighting and
    // relative fluid importance instead of being dominated by the single largest-mass fluid. This
    // follows Pedersen, Christensen & Yan, Phase Behavior of Petroleum Reservoir Fluids, Ch. 5.6
    // (common slate, Eqs. 5.58-5.59).
    List<PseudoComponentContribution> weightedImaginary = buildWeightedImaginaryPool(extractions, effectiveWeights);
    List<Double> boundaries = determineQuantileBoundaries(weightedImaginary, targetPseudoComponents);

    List<List<PseudoComponentProfile>> perFluidProfiles = new ArrayList<>(fluids.size());
    for (FluidExtraction extraction : extractions) {
      perFluidProfiles.add(distributeToProfiles(extraction.pseudoComponents, boundaries, targetPseudoComponents));
    }

    List<PseudoComponentProfile> commonSlate = computeCommonSlate(perFluidProfiles, totalMoles, effectiveWeights,
        targetPseudoComponents);

    List<SystemInterface> result = new ArrayList<>(fluids.size());
    for (int j = 0; j < fluids.size(); j++) {
      SystemInterface characterized = fluids.get(j).clone();
      removeAllComponents(characterized);

      for (Map.Entry<String, Double> entry : extractions.get(j).baseComponents.entrySet()) {
        characterized.addComponent(entry.getKey(), entry.getValue());
      }

      List<PseudoComponentProfile> fluidProfiles = perFluidProfiles.get(j);
      for (int i = 0; i < commonSlate.size(); i++) {
        PseudoComponentProfile common = commonSlate.get(i);
        PseudoComponentProfile own = fluidProfiles.get(i);
        if (!common.isValid() || !own.isValid()) {
          continue;
        }

        String baseName = "PC" + (i + 1);
        characterized.addTBPfraction(baseName, own.getMoles(), common.getMolarMass(), common.getDensity());

        ComponentInterface component = characterized.getComponent(baseName + "_PC");
        if (component == null) {
          logger.warn("Failed to locate newly added pseudo component {}", baseName);
          continue;
        }
        common.applyTo(component);
      }

      finalizeFluid(characterized);
      result.add(characterized);
    }

    return result;
  }

  /**
   * Compute the shared pseudo-component slate from the per-fluid lumped profiles using mole-fraction weighted averages
   * (Pedersen Chapter 5.6, Eqs. 5.55-5.60).
   *
   * @param perFluidProfiles per-fluid lumped profiles on the shared cut grid
   * @param totalMoles total number of moles per fluid (base + pseudo), used to compute the lump mole fractions
   * {@code z_i^j}
   * @param weights per-fluid weight factors {@code Wgt(j)}
   * @param targetPseudoComponents number of shared pseudo components
   * @return the common slate as one profile per shared cut (invalid cuts are returned as empty profiles)
   */
  private static List<PseudoComponentProfile> computeCommonSlate(List<List<PseudoComponentProfile>> perFluidProfiles,
      List<Double> totalMoles, double[] weights, int targetPseudoComponents) {
    List<PseudoComponentProfile> slate = new ArrayList<>(targetPseudoComponents);

    for (int i = 0; i < targetPseudoComponents; i++) {
      WeightedMean molarMass = new WeightedMean();
      WeightedMean molarVolume = new WeightedMean();
      WeightedMean normalBoilingPoint = new WeightedMean();
      WeightedMean criticalTemperature = new WeightedMean();
      WeightedMean criticalPressure = new WeightedMean();
      WeightedMean acentricFactor = new WeightedMean();
      WeightedMean criticalVolume = new WeightedMean();
      WeightedMean racketZ = new WeightedMean();
      WeightedMean racketZCpa = new WeightedMean();
      WeightedMean parachor = new WeightedMean();
      WeightedMean criticalViscosity = new WeightedMean();
      WeightedMean triplePoint = new WeightedMean();
      WeightedMean heatOfFusion = new WeightedMean();
      WeightedMean idealGasEnthalpy = new WeightedMean();
      WeightedMean cpA = new WeightedMean();
      WeightedMean cpB = new WeightedMean();
      WeightedMean cpC = new WeightedMean();
      WeightedMean cpD = new WeightedMean();
      WeightedMean attractiveM = new WeightedMean();
      double slateMoles = 0.0;

      for (int j = 0; j < perFluidProfiles.size(); j++) {
        PseudoComponentProfile profile = perFluidProfiles.get(j).get(i);
        if (!profile.isValid()) {
          continue;
        }
        double total = totalMoles.get(j);
        double moleFraction = total > MASS_TOLERANCE ? profile.getMoles() / total : 0.0;
        double weight = weights[j] * moleFraction;
        if (!(weight > 0.0)) {
          continue;
        }
        slateMoles += profile.getMoles();

        molarMass.add(weight, profile.getMolarMass());
        if (profile.getDensity() > 0.0) {
          molarVolume.add(weight, profile.getMolarMass() / profile.getDensity());
        }
        normalBoilingPoint.add(weight, profile.getNormalBoilingPoint());
        criticalTemperature.add(weight, profile.getCriticalTemperature());
        criticalPressure.add(weight, profile.getCriticalPressure());
        acentricFactor.add(weight, profile.getAcentricFactor());
        criticalVolume.add(weight, profile.getCriticalVolume());
        racketZ.add(weight, profile.getRacketZ());
        racketZCpa.add(weight, profile.getRacketZCpa());
        parachor.add(weight, profile.getParachor());
        criticalViscosity.add(weight, profile.getCriticalViscosity());
        triplePoint.add(weight, profile.getTriplePointTemperature());
        heatOfFusion.add(weight, profile.getHeatOfFusion());
        idealGasEnthalpy.add(weight, profile.getIdealGasEnthalpyOfFormation());
        cpA.add(weight, profile.getCpA());
        cpB.add(weight, profile.getCpB());
        cpC.add(weight, profile.getCpC());
        cpD.add(weight, profile.getCpD());
        attractiveM.add(weight, profile.getAttractiveM());
      }

      double meanMolarMass = molarMass.mean();
      double meanMolarVolume = molarVolume.mean();
      double density = Double.isFinite(meanMolarMass) && Double.isFinite(meanMolarVolume) && meanMolarVolume > 0.0
          ? meanMolarMass / meanMolarVolume
          : Double.NaN;

      slate.add(new PseudoComponentProfile(slateMoles, Double.isFinite(meanMolarMass) ? meanMolarMass : 0.0, density,
          normalBoilingPoint.mean(), criticalTemperature.mean(), criticalPressure.mean(), acentricFactor.mean(),
          criticalVolume.mean(), racketZ.mean(), racketZCpa.mean(), parachor.mean(), criticalViscosity.mean(),
          triplePoint.mean(), heatOfFusion.mean(), idealGasEnthalpy.mean(), cpA.mean(), cpB.mean(), cpC.mean(),
          cpD.mean(), attractiveM.mean()));
    }

    return slate;
  }

  /**
   * Build the imaginary composition used to derive the shared cut grid for a common slate. Each fluid's
   * pseudo-components are first normalised to unit total pseudo-mass and then scaled by the fluid's slate weight, so
   * the equal-mass quantile boundaries reflect the requested weighting rather than being dominated by whichever fluid
   * happens to carry the most pseudo-component mass. Fluids with no pseudo-components, with non-positive pseudo-mass,
   * or with a non-positive weight are skipped. The returned pool is sorted ascending by
   * {@link PseudoComponentContribution#sortingKey()}.
   *
   * @param extractions the per-fluid extracted compositions, in fluid order
   * @param weights the resolved per-fluid weights, parallel to {@code extractions}
   * @return a mass-weighted, sorted imaginary pseudo-component pool for boundary determination
   */
  private static List<PseudoComponentContribution> buildWeightedImaginaryPool(List<FluidExtraction> extractions,
      double[] weights) {
    List<PseudoComponentContribution> pool = new ArrayList<>();
    for (int j = 0; j < extractions.size(); j++) {
      List<PseudoComponentContribution> contributions = extractions.get(j).pseudoComponents;
      if (contributions.isEmpty() || weights[j] <= 0.0) {
        continue;
      }
      double fluidPseudoMass = 0.0;
      for (PseudoComponentContribution contribution : contributions) {
        fluidPseudoMass += contribution.mass;
      }
      if (fluidPseudoMass <= MASS_TOLERANCE) {
        continue;
      }
      double scale = weights[j] / fluidPseudoMass;
      for (PseudoComponentContribution contribution : contributions) {
        pool.add(contribution.scaledByMass(scale));
      }
    }
    pool.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));
    return pool;
  }

  /**
   * Resolve the per-fluid weight vector, validating it against the number of fluids.
   *
   * @param weights the requested weights, or {@code null} for equal weighting
   * @param count the number of fluids
   * @return a defensive copy of valid weights (all 1.0 when {@code weights} is {@code null})
   */
  private static double[] resolveWeights(double[] weights, int count) {
    if (weights == null) {
      double[] equal = new double[count];
      Arrays.fill(equal, 1.0);
      return equal;
    }
    if (weights.length != count) {
      throw new IllegalArgumentException(
          "weights length (" + weights.length + ") must match number of fluids (" + count + ")");
    }
    double sum = 0.0;
    for (double weight : weights) {
      if (weight < 0.0) {
        throw new IllegalArgumentException("weights must be non-negative");
      }
      sum += weight;
    }
    if (!(sum > 0.0)) {
      throw new IllegalArgumentException("At least one weight must be positive");
    }
    return weights.clone();
  }

  /**
   * Infer the default number of shared pseudo components as the largest pseudo-component count among the input fluids
   * (at least one).
   *
   * @param fluids fluids to inspect
   * @return the inferred number of shared pseudo components
   */
  private static int inferCommonSlateSize(List<SystemInterface> fluids) {
    Objects.requireNonNull(fluids, "fluids");
    if (fluids.isEmpty()) {
      throw new IllegalArgumentException("At least one fluid is required");
    }
    int max = 0;
    for (SystemInterface fluid : fluids) {
      Objects.requireNonNull(fluid, "fluid");
      max = Math.max(max, extractComponents(fluid).pseudoComponents.size());
    }
    return Math.max(max, 1);
  }

  /**
   * Small accumulator for a weighted arithmetic mean that ignores non-finite samples.
   */
  private static final class WeightedMean {
    private double numerator;
    private double denominator;

    /**
     * Add a weighted sample. Samples with non-positive weight or non-finite value are ignored.
     *
     * @param weight the sample weight
     * @param value the sample value
     */
    private void add(double weight, double value) {
      if (weight > 0.0 && Double.isFinite(value)) {
        numerator += weight * value;
        denominator += weight;
      }
    }

    /**
     * Compute the weighted mean.
     *
     * @return the weighted mean, or {@link Double#NaN} if no valid samples were added
     */
    private double mean() {
      return denominator > MASS_TOLERANCE ? numerator / denominator : Double.NaN;
    }
  }

  /**
   * Transfer binary interaction parameters from a reference fluid to a target fluid.
   *
   * <p>
   * This method copies BIPs between components that exist in both fluids. For pseudo-components, it matches by position
   * (first PC to first PC, etc.) since names may differ.
   *
   * @param reference the fluid containing BIPs to copy
   * @param target the fluid to receive the BIPs
   */
  public static void transferBinaryInteractionParameters(SystemInterface reference, SystemInterface target) {
    Objects.requireNonNull(reference, "reference");
    Objects.requireNonNull(target, "target");

    // Ensure both fluids are initialized
    reference.init(0);
    target.init(0);

    // Get pseudo-component lists for both fluids
    List<ComponentInterface> refPCs = getPseudoComponentList(reference);
    List<ComponentInterface> targetPCs = getPseudoComponentList(target);

    // Transfer BIPs for base components (by name)
    for (String refName : reference.getComponentNames()) {
      ComponentInterface refComp = reference.getComponent(refName);
      if (refComp.isIsTBPfraction() || refComp.isIsPlusFraction()) {
        continue; // Handle PCs separately
      }

      ComponentInterface targetComp = target.getComponent(refName);
      if (targetComp == null) {
        continue;
      }

      // Transfer BIPs between this base component and all other components
      for (String refName2 : reference.getComponentNames()) {
        if (refName.equals(refName2)) {
          continue;
        }

        double kij = getBinaryInteractionParameter(reference, refName, refName2);
        if (kij != 0.0) {
          // Find corresponding component in target
          ComponentInterface refComp2 = reference.getComponent(refName2);
          String targetName2;

          if (refComp2.isIsTBPfraction() || refComp2.isIsPlusFraction()) {
            // Find corresponding PC by index
            int pcIndex = refPCs.indexOf(refComp2);
            if (pcIndex >= 0 && pcIndex < targetPCs.size()) {
              targetName2 = targetPCs.get(pcIndex).getComponentName();
            } else {
              continue;
            }
          } else {
            targetName2 = refName2;
          }

          target.setBinaryInteractionParameter(refName, targetName2, kij);
        }
      }
    }

    // Transfer BIPs between pseudo-components (by position)
    for (int i = 0; i < Math.min(refPCs.size(), targetPCs.size()); i++) {
      String refPcName = refPCs.get(i).getComponentName();

      for (int j = i + 1; j < Math.min(refPCs.size(), targetPCs.size()); j++) {
        String refPcName2 = refPCs.get(j).getComponentName();
        double kij = getBinaryInteractionParameter(reference, refPcName, refPcName2);

        if (kij != 0.0) {
          String targetPcName = targetPCs.get(i).getComponentName();
          String targetPcName2 = targetPCs.get(j).getComponentName();
          target.setBinaryInteractionParameter(targetPcName, targetPcName2, kij);
        }
      }
    }

    logger.debug("Transferred BIPs from reference to target fluid");
  }

  /**
   * Get a BIP value between two components in a fluid.
   *
   * @param fluid the fluid
   * @param comp1 first component name
   * @param comp2 second component name
   * @return the BIP value, or 0.0 if not found
   */
  private static double getBinaryInteractionParameter(SystemInterface fluid, String comp1, String comp2) {
    try {
      PhaseInterface phase = fluid.getPhase(0);
      if (phase instanceof PhaseEos) {
        PhaseEos eosPhase = (PhaseEos) phase;
        int i = fluid.getComponent(comp1).getComponentNumber();
        int j = fluid.getComponent(comp2).getComponentNumber();
        return eosPhase.getMixingRule().getBinaryInteractionParameter(i, j);
      }
    } catch (Exception e) {
      logger.trace("Could not get BIP for {}-{}: {}", comp1, comp2, e.getMessage());
    }
    return 0.0;
  }

  /**
   * Get ordered list of pseudo-components from a fluid.
   *
   * @param fluid the fluid
   * @return list of pseudo-components in order
   */
  private static List<ComponentInterface> getPseudoComponentList(SystemInterface fluid) {
    List<ComponentInterface> pcs = new ArrayList<>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getComponent(i);
      if (comp.isIsTBPfraction() || comp.isIsPlusFraction()) {
        pcs.add(comp);
      }
    }
    return pcs;
  }

  /**
   * Normalize composition so mole fractions sum to 1.0.
   *
   * @param fluid the fluid to normalize
   */
  public static void normalizeComposition(SystemInterface fluid) {
    double totalMoles = fluid.getTotalNumberOfMoles();
    if (totalMoles <= 0) {
      return;
    }

    // Normalize by adjusting total moles
    fluid.init(0);
    logger.debug("Normalized composition to total moles = {}", totalMoles);
  }

  /**
   * Generate a validation report comparing source and characterized fluids.
   *
   * @param source the original source fluid
   * @param reference the reference fluid used for characterization
   * @param characterized the resulting characterized fluid
   * @return validation report
   */
  public static CharacterizationValidationReport generateValidationReport(SystemInterface source,
      SystemInterface reference, SystemInterface characterized) {
    return CharacterizationValidationReport.generate(source, reference, characterized);
  }

  private static String stripPcSuffix(String componentName) {
    if (componentName == null) {
      return "PC";
    }
    int index = componentName.indexOf("_PC");
    return index >= 0 ? componentName.substring(0, index) : componentName;
  }

  private static void finalizeFluid(SystemInterface system) {
    if (system.getNumberOfComponents() > 0) {
      system.createDatabase(true);
      system.setMixingRule(system.getMixingRule());
      system.init(0);
    }
  }

  private static void removeAllComponents(SystemInterface system) {
    String[] names = system.getComponentNames();
    if (names == null) {
      return;
    }
    for (String name : names) {
      system.removeComponent(name);
    }
  }

  private static FluidExtraction extractComponents(SystemInterface fluid) {
    Map<String, Double> baseComponents = new LinkedHashMap<>();
    List<PseudoComponentContribution> pseudoComponents = new ArrayList<>();

    for (String name : fluid.getComponentNames()) {
      ComponentInterface component = fluid.getComponent(name);
      if (component == null) {
        continue;
      }

      double moles = component.getNumberOfmoles();
      if (!(moles > 0.0)) {
        continue;
      }

      if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
        pseudoComponents.add(new PseudoComponentContribution(name, component));
      } else {
        baseComponents.merge(component.getComponentName(), moles, Double::sum);
      }
    }

    pseudoComponents.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));
    return new FluidExtraction(baseComponents, pseudoComponents);
  }

  private static Map<String, Double> mergeBaseComponents(Map<String, Double> accumulator,
      Map<String, Double> addition) {
    Map<String, Double> result = new LinkedHashMap<>(accumulator);
    for (Map.Entry<String, Double> entry : addition.entrySet()) {
      result.merge(entry.getKey(), entry.getValue(), Double::sum);
    }
    return result;
  }

  private static List<Double> determineQuantileBoundaries(List<PseudoComponentContribution> contributions,
      int targetPseudoComponents) {
    if (targetPseudoComponents <= 1 || contributions.isEmpty()) {
      return Collections.emptyList();
    }

    List<PseudoComponentContribution> sorted = new ArrayList<>(contributions);
    sorted.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));

    double totalMass = 0.0;
    for (PseudoComponentContribution contribution : sorted) {
      totalMass += contribution.mass;
    }

    if (!(totalMass > MASS_TOLERANCE)) {
      return Collections.emptyList();
    }

    double[] targets = new double[targetPseudoComponents - 1];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = totalMass * (i + 1) / targetPseudoComponents;
    }

    List<Double> boundaries = new ArrayList<>(targets.length);
    double cumulative = 0.0;
    int targetIndex = 0;
    for (PseudoComponentContribution contribution : sorted) {
      double nextCumulative = cumulative + contribution.mass;
      while (targetIndex < targets.length && nextCumulative >= targets[targetIndex] - MASS_TOLERANCE) {
        boundaries.add(contribution.sortingKey());
        targetIndex++;
      }
      cumulative = nextCumulative;
    }

    while (boundaries.size() < targets.length) {
      boundaries.add(Double.POSITIVE_INFINITY);
    }

    return boundaries;
  }

  /**
   * Split each coarse source pseudo-component into a grid of finer single-carbon-number (SCN) sub-fractions that
   * conserve the parent moles and mass exactly.
   *
   * <p>
   * This implements the delumping stage of the Pedersen et al. (Chapter 5) lumping/delumping scheme (Eqs. 5.35-5.37
   * describe the inverse mass-weighted lumping). For every parent lump the method:
   * <ol>
   * <li>builds a molar-mass grid that brackets the parent molar mass over a symmetric &plusmn;{@code window}
   * window;</li>
   * <li>assigns normalized, light-end-biased sub-fraction mole weights (their sum is one, so the parent moles are
   * conserved);</li>
   * <li>rescales the molar-mass grid by a single factor so that &Sigma;<sub>k</sub> n<sub>k</sub> M<sub>k</sub> =
   * n<sub>parent</sub> M<sub>parent</sub> exactly, conserving the parent mass;</li>
   * <li>spreads the normal boiling point monotonically with molar mass so the sub-fractions can cross reference cut
   * boundaries (this is what removes the identity source-to-reference mapping), while holding density and the critical
   * constants at the parent values.</li>
   * </ol>
   *
   * <p>
   * The expanded list is re-sorted by sorting key (normal boiling point, falling back to molar mass) so it can be fed
   * directly to {@link #distributeToProfiles}.
   *
   * @param sourcePseudoComponents the coarse source pseudo-components to delump
   * @param resolution number of sub-fractions per parent lump; values of 1 or less return the input unchanged
   * @return the delumped, sorted list of sub-fractions
   */
  private static List<PseudoComponentContribution> delumpContributions(
      List<PseudoComponentContribution> sourcePseudoComponents, int resolution) {
    if (resolution <= 1) {
      return sourcePseudoComponents;
    }

    final double window = 0.4;
    final double decay = 1.5;

    List<PseudoComponentContribution> expanded = new ArrayList<>(sourcePseudoComponents.size() * resolution);

    for (PseudoComponentContribution parent : sourcePseudoComponents) {
      double parentMoles = parent.moles;
      double parentMolarMass = parent.molarMass;
      if (!(parentMoles > 0.0) || !(parentMolarMass > 0.0)) {
        expanded.add(parent);
        continue;
      }

      double[] weights = new double[resolution];
      double weightSum = 0.0;
      double[] molarMasses = new double[resolution];
      for (int k = 0; k < resolution; k++) {
        double t = (double) k / (resolution - 1);
        weights[k] = Math.exp(-decay * t);
        weightSum += weights[k];
        molarMasses[k] = parentMolarMass * (1.0 - window + 2.0 * window * t);
      }

      double rawMass = 0.0;
      for (int k = 0; k < resolution; k++) {
        double moleFraction = weights[k] / weightSum;
        rawMass += parentMoles * moleFraction * molarMasses[k];
      }
      double parentMass = parentMoles * parentMolarMass;
      double scale = rawMass > MASS_TOLERANCE ? parentMass / rawMass : 1.0;

      boolean parentHasTb = Double.isFinite(parent.normalBoilingPoint) && parent.normalBoilingPoint > 0.0;
      for (int k = 0; k < resolution; k++) {
        double moleFraction = weights[k] / weightSum;
        double subMoles = parentMoles * moleFraction;
        double subMolarMass = molarMasses[k] * scale;
        double ratio = subMolarMass / parentMolarMass;
        double subTb = parentHasTb ? parent.normalBoilingPoint * ratio : parent.normalBoilingPoint;

        expanded.add(new PseudoComponentContribution(parent.name + "_d" + k, subMoles, subMolarMass, parent.density,
            subTb, parent.criticalTemperature, parent.criticalPressure, parent.acentricFactor, parent.criticalVolume,
            parent.racketZ, parent.racketZCpa, parent.parachor, parent.criticalViscosity, parent.triplePointTemperature,
            parent.heatOfFusion, parent.idealGasEnthalpyOfFormation, parent.cpA, parent.cpB, parent.cpC, parent.cpD,
            parent.attractiveM));
      }
    }

    expanded.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));
    return expanded;
  }

  private static List<Double> determineReferenceBoundaries(List<PseudoComponentContribution> referenceContributions) {
    if (referenceContributions.size() <= 1) {
      return Collections.emptyList();
    }

    List<Double> boundaries = new ArrayList<>(referenceContributions.size() - 1);
    for (int i = 0; i < referenceContributions.size() - 1; i++) {
      double key1 = referenceContributions.get(i).sortingKey();
      double key2 = referenceContributions.get(i + 1).sortingKey();
      double boundary = Double.isFinite(key1) && Double.isFinite(key2) ? 0.5 * (key1 + key2) : Math.max(key1, key2);
      boundaries.add(boundary);
    }
    return boundaries;
  }

  /**
   * Place the reference cut boundaries as carbon-number-based equal-mass cut points on the reference fluid's imaginary
   * (fine-resolution) composition.
   *
   * <p>
   * This is the reference-only (NFLUID = 1) form of the Pedersen et al. (Chapter 5.6) common-slate cut-point rule.
   * Because the reference is supplied already lumped into pseudo-components, its fine carbon-number distribution is
   * first rebuilt by delumping each lump into {@code resolution} single-carbon-number sub-fractions (the imaginary
   * composition of Eqs. 5.58-5.59 with the reference as the sole weighted fluid). Equal-mass cut points are then placed
   * on that fine composition (Section 5.3 lumping criterion), so each cut carries an equal mass fraction instead of
   * being split at the arithmetic boiling-point midpoint, which ignores how much mass each lump represents.
   *
   * <p>
   * Each equal-mass cut is finally clamped to lie strictly between the sorting keys of the two adjacent reference
   * pseudo-components. This guarantees that every reference pseudo-component remains inside its own cut, preserving the
   * strict one-to-one ordering on which the property inheritance in {@link #characterizeToReferenceCore} relies, even
   * when the reference lumps are not equal in mass (in which case an unclamped equal-mass cut could fall across a lump
   * and mis-bin the source). When clamping cannot recover a valid in-gap value the boiling-point midpoint is used as a
   * fallback.
   *
   * @param referenceContributions the reference pseudo-components, sorted ascending by sorting key
   * @param resolution number of single-carbon-number sub-fractions per reference lump used to rebuild the imaginary
   * composition; values of 1 or less fall back to boiling-point midpoints
   * @return the clamped equal-mass cut boundaries (size {@code referenceContributions.size() - 1})
   */
  private static List<Double> determineReferenceEqualMassBoundaries(
      List<PseudoComponentContribution> referenceContributions, int resolution) {
    int cuts = referenceContributions.size() - 1;
    if (cuts < 1) {
      return Collections.emptyList();
    }
    if (resolution <= 1) {
      return determineReferenceBoundaries(referenceContributions);
    }

    List<PseudoComponentContribution> imaginary = delumpContributions(referenceContributions, resolution);
    List<Double> equalMass = determineQuantileBoundaries(imaginary, referenceContributions.size());

    List<Double> boundaries = new ArrayList<>(cuts);
    for (int i = 0; i < cuts; i++) {
      double lower = referenceContributions.get(i).sortingKey();
      double upper = referenceContributions.get(i + 1).sortingKey();
      double midpoint = Double.isFinite(lower) && Double.isFinite(upper) ? 0.5 * (lower + upper)
          : Math.max(lower, upper);

      double boundary = midpoint;
      if (Double.isFinite(lower) && Double.isFinite(upper) && upper > lower) {
        double candidate = i < equalMass.size() ? equalMass.get(i) : Double.NaN;
        if (Double.isFinite(candidate) && candidate > lower && candidate < upper) {
          boundary = candidate;
        }
      }
      boundaries.add(boundary);
    }
    return boundaries;
  }

  private static List<PseudoComponentProfile> distributeToProfiles(List<PseudoComponentContribution> contributions,
      List<Double> boundaries, int targetPseudoComponents) {
    if (targetPseudoComponents <= 0) {
      return Collections.emptyList();
    }

    List<PseudoComponentGroupBuilder> builders = new ArrayList<>(targetPseudoComponents);
    for (int i = 0; i < targetPseudoComponents; i++) {
      builders.add(new PseudoComponentGroupBuilder(MASS_TOLERANCE));
    }

    if (contributions.isEmpty()) {
      List<PseudoComponentProfile> emptyProfiles = new ArrayList<>(targetPseudoComponents);
      for (int i = 0; i < targetPseudoComponents; i++) {
        emptyProfiles.add(PseudoComponentProfile.empty());
      }
      return emptyProfiles;
    }

    int groupIndex = 0;
    double currentBoundary = boundaries.isEmpty() ? Double.POSITIVE_INFINITY : boundaries.get(0);

    for (PseudoComponentContribution contribution : contributions) {
      double key = contribution.sortingKey();
      while (groupIndex < targetPseudoComponents - 1 && key > currentBoundary) {
        groupIndex++;
        currentBoundary = groupIndex < boundaries.size() ? boundaries.get(groupIndex) : Double.POSITIVE_INFINITY;
      }
      builders.get(groupIndex).addContribution(contribution, contribution.mass);
    }

    List<PseudoComponentProfile> profiles = new ArrayList<>(targetPseudoComponents);
    for (PseudoComponentGroupBuilder builder : builders) {
      profiles.add(builder.buildWithOverrides(null, null, null, null));
    }
    return profiles;
  }

  private static List<PseudoComponentProfile> combineProfiles(List<List<PseudoComponentProfile>> perFluidProfiles,
      double[] fluidMassTotals, double[] fluidMoleTotals) {
    if (perFluidProfiles.isEmpty()) {
      return Collections.emptyList();
    }

    int groupCount = perFluidProfiles.get(0).size();
    List<PseudoComponentProfile> result = new ArrayList<>(groupCount);

    double totalFluidMass = 0.0;
    double totalFluidMoles = 0.0;
    for (double mass : fluidMassTotals) {
      totalFluidMass += mass;
    }
    for (double moles : fluidMoleTotals) {
      totalFluidMoles += moles;
    }

    for (int group = 0; group < groupCount; group++) {
      PseudoComponentGroupBuilder builder = new PseudoComponentGroupBuilder(MASS_TOLERANCE);
      double tbNumerator = 0.0;
      double tbDenominator = 0.0;
      double tcNumerator = 0.0;
      double tcDenominator = 0.0;
      double pcNumerator = 0.0;
      double pcDenominator = 0.0;
      double omegaNumerator = 0.0;
      double omegaDenominator = 0.0;

      for (int fluid = 0; fluid < perFluidProfiles.size(); fluid++) {
        PseudoComponentProfile profile = perFluidProfiles.get(fluid).get(group);
        builder.addProfile(profile);

        double fluidMass = fluidMassTotals[fluid];
        double fluidMoles = fluidMoleTotals[fluid];
        double fluidFraction = totalFluidMass > MASS_TOLERANCE ? fluidMass / totalFluidMass : 0.0;
        double moleFraction = fluidMoles > MASS_TOLERANCE ? profile.getMoles() / fluidMoles : 0.0;

        if (Double.isFinite(profile.getNormalBoilingPoint())) {
          tbNumerator += fluidFraction * moleFraction * profile.getNormalBoilingPoint();
          tbDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getCriticalTemperature())) {
          tcNumerator += fluidFraction * moleFraction * profile.getCriticalTemperature();
          tcDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getCriticalPressure())) {
          pcNumerator += fluidFraction * moleFraction * profile.getCriticalPressure();
          pcDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getAcentricFactor())) {
          omegaNumerator += fluidFraction * moleFraction * profile.getAcentricFactor();
          omegaDenominator += fluidFraction * moleFraction;
        }
      }

      Double tbOverride = tbDenominator > MASS_TOLERANCE ? tbNumerator / tbDenominator : null;
      Double tcOverride = tcDenominator > MASS_TOLERANCE ? tcNumerator / tcDenominator : null;
      Double pcOverride = pcDenominator > MASS_TOLERANCE ? pcNumerator / pcDenominator : null;
      Double omegaOverride = omegaDenominator > MASS_TOLERANCE ? omegaNumerator / omegaDenominator : null;

      result.add(builder.buildWithOverrides(tbOverride, tcOverride, pcOverride, omegaOverride));
    }

    return result;
  }

  private static final class FluidExtraction {
    private final Map<String, Double> baseComponents;
    private final List<PseudoComponentContribution> pseudoComponents;

    private FluidExtraction(Map<String, Double> baseComponents, List<PseudoComponentContribution> pseudoComponents) {
      this.baseComponents = baseComponents;
      this.pseudoComponents = pseudoComponents;
    }
  }

  private static final class PseudoComponentContribution {
    private final String name;
    private final double moles;
    private final double molarMass;
    private final double density;
    private final double normalBoilingPoint;
    private final double criticalTemperature;
    private final double criticalPressure;
    private final double acentricFactor;
    private final double criticalVolume;
    private final double racketZ;
    private final double racketZCpa;
    private final double parachor;
    private final double criticalViscosity;
    private final double triplePointTemperature;
    private final double heatOfFusion;
    private final double idealGasEnthalpyOfFormation;
    private final double cpA;
    private final double cpB;
    private final double cpC;
    private final double cpD;
    private final double attractiveM;
    private final double mass;

    private PseudoComponentContribution(String name, ComponentInterface component) {
      this.name = name;
      moles = component.getNumberOfmoles();
      molarMass = component.getMolarMass();
      density = component.getNormalLiquidDensity();
      normalBoilingPoint = component.getNormalBoilingPoint();
      criticalTemperature = component.getTC();
      criticalPressure = component.getPC();
      acentricFactor = component.getAcentricFactor();
      criticalVolume = component.getCriticalVolume();
      racketZ = component.getRacketZ();
      racketZCpa = component.getRacketZCPA();
      parachor = component.getParachorParameter();
      criticalViscosity = component.getCriticalViscosity();
      triplePointTemperature = component.getTriplePointTemperature();
      heatOfFusion = component.getHeatOfFusion();
      idealGasEnthalpyOfFormation = component.getIdealGasEnthalpyOfFormation();
      cpA = component.getCpA();
      cpB = component.getCpB();
      cpC = component.getCpC();
      cpD = component.getCpD();
      AttractiveTermInterface attractiveTerm = component.getAttractiveTerm();
      attractiveM = attractiveTerm != null ? attractiveTerm.getm() : Double.NaN;
      mass = moles * molarMass;
    }

    /**
     * All-arguments constructor used to synthesize delumped single-carbon-number sub-fractions. Properties are supplied
     * directly instead of being read from a {@link ComponentInterface}, and the mass is derived from
     * {@code moles * molarMass} to keep the invariant of the component-backed constructor.
     */
    private PseudoComponentContribution(String name, double moles, double molarMass, double density,
        double normalBoilingPoint, double criticalTemperature, double criticalPressure, double acentricFactor,
        double criticalVolume, double racketZ, double racketZCpa, double parachor, double criticalViscosity,
        double triplePointTemperature, double heatOfFusion, double idealGasEnthalpyOfFormation, double cpA, double cpB,
        double cpC, double cpD, double attractiveM) {
      this.name = name;
      this.moles = moles;
      this.molarMass = molarMass;
      this.density = density;
      this.normalBoilingPoint = normalBoilingPoint;
      this.criticalTemperature = criticalTemperature;
      this.criticalPressure = criticalPressure;
      this.acentricFactor = acentricFactor;
      this.criticalVolume = criticalVolume;
      this.racketZ = racketZ;
      this.racketZCpa = racketZCpa;
      this.parachor = parachor;
      this.criticalViscosity = criticalViscosity;
      this.triplePointTemperature = triplePointTemperature;
      this.heatOfFusion = heatOfFusion;
      this.idealGasEnthalpyOfFormation = idealGasEnthalpyOfFormation;
      this.cpA = cpA;
      this.cpB = cpB;
      this.cpC = cpC;
      this.cpD = cpD;
      this.attractiveM = attractiveM;
      this.mass = moles * molarMass;
    }

    private double sortingKey() {
      double key = normalBoilingPoint;
      if (!Double.isFinite(key) || key <= 0.0) {
        key = molarMass;
      }
      return key;
    }

    /**
     * Returns a copy of this contribution with its mole amount (and hence mass) scaled by the given factor. All
     * intensive properties and the {@link #sortingKey()} are preserved, so the copy occupies the same position on the
     * cut grid but carries a re-weighted mass. Used to build the weighted imaginary composition for the common-slate
     * cut grid (Pedersen Ch. 5.6, Eqs. 5.58-5.59).
     *
     * @param factor the multiplicative scaling applied to {@link #moles}; must be finite and &gt;= 0
     * @return a new {@link PseudoComponentContribution} with {@code moles * factor}
     */
    private PseudoComponentContribution scaledByMass(double factor) {
      return new PseudoComponentContribution(name, moles * factor, molarMass, density, normalBoilingPoint,
          criticalTemperature, criticalPressure, acentricFactor, criticalVolume, racketZ, racketZCpa, parachor,
          criticalViscosity, triplePointTemperature, heatOfFusion, idealGasEnthalpyOfFormation, cpA, cpB, cpC, cpD,
          attractiveM);
    }
  }

  private static final class PseudoComponentGroupBuilder {
    private final double massTolerance;
    private double mass;
    private double moles;
    private double volume;
    private double densityMass;
    private double tbMass;
    private boolean hasTb;
    private double tcMass;
    private boolean hasTc;
    private double pcMass;
    private boolean hasPc;
    private double omegaMass;
    private boolean hasOmega;
    private double criticalVolumeMass;
    private boolean hasCriticalVolume;
    private double racketZMass;
    private boolean hasRacketZ;
    private double racketZCpaMass;
    private boolean hasRacketZCpa;
    private double parachorMass;
    private boolean hasParachor;
    private double criticalViscosityMass;
    private boolean hasCriticalViscosity;
    private double triplePointMass;
    private boolean hasTriplePoint;
    private double heatOfFusionMass;
    private boolean hasHeatOfFusion;
    private double idealGasEnthalpyMass;
    private boolean hasIdealGasEnthalpy;
    private double cpAMass;
    private boolean hasCpA;
    private double cpBMass;
    private boolean hasCpB;
    private double cpCMass;
    private boolean hasCpC;
    private double cpDMass;
    private boolean hasCpD;
    private double attractiveMMass;
    private boolean hasAttractiveM;

    private PseudoComponentGroupBuilder(double massTolerance) {
      this.massTolerance = massTolerance;
    }

    private void addContribution(PseudoComponentContribution source, double massPortion) {
      if (!(massPortion > massTolerance) || !(source.mass > 0.0)) {
        return;
      }

      double ratio = massPortion / source.mass;
      double molesPortion = source.moles * ratio;

      mass += massPortion;
      moles += molesPortion;

      if (source.density > 0.0) {
        volume += massPortion / source.density;
        densityMass += massPortion * source.density;
      }

      if (Double.isFinite(source.normalBoilingPoint)) {
        tbMass += massPortion * source.normalBoilingPoint;
        hasTb = true;
      }

      if (Double.isFinite(source.criticalTemperature)) {
        tcMass += massPortion * source.criticalTemperature;
        hasTc = true;
      }

      if (Double.isFinite(source.criticalPressure)) {
        pcMass += massPortion * source.criticalPressure;
        hasPc = true;
      }

      if (Double.isFinite(source.acentricFactor)) {
        omegaMass += massPortion * source.acentricFactor;
        hasOmega = true;
      }

      if (Double.isFinite(source.criticalVolume)) {
        criticalVolumeMass += massPortion * source.criticalVolume;
        hasCriticalVolume = true;
      }

      if (Double.isFinite(source.racketZ)) {
        racketZMass += massPortion * source.racketZ;
        hasRacketZ = true;
      }

      if (Double.isFinite(source.racketZCpa)) {
        racketZCpaMass += massPortion * source.racketZCpa;
        hasRacketZCpa = true;
      }

      if (Double.isFinite(source.parachor)) {
        parachorMass += massPortion * source.parachor;
        hasParachor = true;
      }

      if (Double.isFinite(source.criticalViscosity)) {
        criticalViscosityMass += massPortion * source.criticalViscosity;
        hasCriticalViscosity = true;
      }

      if (Double.isFinite(source.triplePointTemperature)) {
        triplePointMass += massPortion * source.triplePointTemperature;
        hasTriplePoint = true;
      }

      if (Double.isFinite(source.heatOfFusion)) {
        heatOfFusionMass += massPortion * source.heatOfFusion;
        hasHeatOfFusion = true;
      }

      if (Double.isFinite(source.idealGasEnthalpyOfFormation)) {
        idealGasEnthalpyMass += massPortion * source.idealGasEnthalpyOfFormation;
        hasIdealGasEnthalpy = true;
      }

      if (Double.isFinite(source.cpA)) {
        cpAMass += massPortion * source.cpA;
        hasCpA = true;
      }

      if (Double.isFinite(source.cpB)) {
        cpBMass += massPortion * source.cpB;
        hasCpB = true;
      }

      if (Double.isFinite(source.cpC)) {
        cpCMass += massPortion * source.cpC;
        hasCpC = true;
      }

      if (Double.isFinite(source.cpD)) {
        cpDMass += massPortion * source.cpD;
        hasCpD = true;
      }

      if (Double.isFinite(source.attractiveM)) {
        attractiveMMass += massPortion * source.attractiveM;
        hasAttractiveM = true;
      }
    }

    private void addProfile(PseudoComponentProfile profile) {
      if (!profile.isValid()) {
        return;
      }

      double massPortion = profile.getMass();
      mass += massPortion;
      moles += profile.getMoles();

      double density = profile.getDensity();
      if (density > 0.0) {
        volume += massPortion / density;
        densityMass += massPortion * density;
      }

      double tb = profile.getNormalBoilingPoint();
      if (Double.isFinite(tb)) {
        tbMass += massPortion * tb;
        hasTb = true;
      }

      double tc = profile.getCriticalTemperature();
      if (Double.isFinite(tc)) {
        tcMass += massPortion * tc;
        hasTc = true;
      }

      double pc = profile.getCriticalPressure();
      if (Double.isFinite(pc)) {
        pcMass += massPortion * pc;
        hasPc = true;
      }

      double omega = profile.getAcentricFactor();
      if (Double.isFinite(omega)) {
        omegaMass += massPortion * omega;
        hasOmega = true;
      }

      double criticalVol = profile.getCriticalVolume();
      if (Double.isFinite(criticalVol)) {
        criticalVolumeMass += massPortion * criticalVol;
        hasCriticalVolume = true;
      }

      double racket = profile.getRacketZ();
      if (Double.isFinite(racket)) {
        racketZMass += massPortion * racket;
        hasRacketZ = true;
      }

      double racketCpa = profile.getRacketZCpa();
      if (Double.isFinite(racketCpa)) {
        racketZCpaMass += massPortion * racketCpa;
        hasRacketZCpa = true;
      }

      double parachor = profile.getParachor();
      if (Double.isFinite(parachor)) {
        parachorMass += massPortion * parachor;
        hasParachor = true;
      }

      double criticalVisc = profile.getCriticalViscosity();
      if (Double.isFinite(criticalVisc)) {
        criticalViscosityMass += massPortion * criticalVisc;
        hasCriticalViscosity = true;
      }

      double triple = profile.getTriplePointTemperature();
      if (Double.isFinite(triple)) {
        triplePointMass += massPortion * triple;
        hasTriplePoint = true;
      }

      double heatFusion = profile.getHeatOfFusion();
      if (Double.isFinite(heatFusion)) {
        heatOfFusionMass += massPortion * heatFusion;
        hasHeatOfFusion = true;
      }

      double idealGas = profile.getIdealGasEnthalpyOfFormation();
      if (Double.isFinite(idealGas)) {
        idealGasEnthalpyMass += massPortion * idealGas;
        hasIdealGasEnthalpy = true;
      }

      double cpAValue = profile.getCpA();
      if (Double.isFinite(cpAValue)) {
        cpAMass += massPortion * cpAValue;
        hasCpA = true;
      }

      double cpBValue = profile.getCpB();
      if (Double.isFinite(cpBValue)) {
        cpBMass += massPortion * cpBValue;
        hasCpB = true;
      }

      double cpCValue = profile.getCpC();
      if (Double.isFinite(cpCValue)) {
        cpCMass += massPortion * cpCValue;
        hasCpC = true;
      }

      double cpDValue = profile.getCpD();
      if (Double.isFinite(cpDValue)) {
        cpDMass += massPortion * cpDValue;
        hasCpD = true;
      }

      double attractive = profile.getAttractiveM();
      if (Double.isFinite(attractive)) {
        attractiveMMass += massPortion * attractive;
        hasAttractiveM = true;
      }
    }

    private PseudoComponentProfile buildWithOverrides(Double tbOverride, Double tcOverride, Double pcOverride,
        Double omegaOverride) {
      if (!(mass > massTolerance) || !(moles > massTolerance)) {
        return PseudoComponentProfile.empty();
      }

      double molarMass = mass / moles;
      double density = Double.NaN;
      if (volume > massTolerance) {
        density = mass / volume;
      } else if (densityMass > massTolerance) {
        density = densityMass / mass;
      }

      double tb = tbOverride != null ? tbOverride.doubleValue() : hasTb ? tbMass / mass : Double.NaN;
      double tc = tcOverride != null ? tcOverride.doubleValue() : hasTc ? tcMass / mass : Double.NaN;
      double pc = pcOverride != null ? pcOverride.doubleValue() : hasPc ? pcMass / mass : Double.NaN;
      double omega = omegaOverride != null ? omegaOverride.doubleValue() : hasOmega ? omegaMass / mass : Double.NaN;

      double criticalVol = hasCriticalVolume ? criticalVolumeMass / mass : Double.NaN;
      double racket = hasRacketZ ? racketZMass / mass : Double.NaN;
      double racketCpa = hasRacketZCpa ? racketZCpaMass / mass : Double.NaN;
      double parachor = hasParachor ? parachorMass / mass : Double.NaN;
      double criticalVisc = hasCriticalViscosity ? criticalViscosityMass / mass : Double.NaN;
      double triple = hasTriplePoint ? triplePointMass / mass : Double.NaN;
      double heatFusion = hasHeatOfFusion ? heatOfFusionMass / mass : Double.NaN;
      double idealGas = hasIdealGasEnthalpy ? idealGasEnthalpyMass / mass : Double.NaN;
      double cpAValue = hasCpA ? cpAMass / mass : Double.NaN;
      double cpBValue = hasCpB ? cpBMass / mass : Double.NaN;
      double cpCValue = hasCpC ? cpCMass / mass : Double.NaN;
      double cpDValue = hasCpD ? cpDMass / mass : Double.NaN;
      double attractive = hasAttractiveM ? attractiveMMass / mass : Double.NaN;

      return new PseudoComponentProfile(moles, molarMass, density, tb, tc, pc, omega, criticalVol, racket, racketCpa,
          parachor, criticalVisc, triple, heatFusion, idealGas, cpAValue, cpBValue, cpCValue, cpDValue, attractive);
    }
  }

  private static final class PseudoComponentProfile {
    private final double moles;
    private final double molarMass;
    private final double density;
    private final double normalBoilingPoint;
    private final double criticalTemperature;
    private final double criticalPressure;
    private final double acentricFactor;
    private final double criticalVolume;
    private final double racketZ;
    private final double racketZCpa;
    private final double parachor;
    private final double criticalViscosity;
    private final double triplePointTemperature;
    private final double heatOfFusion;
    private final double idealGasEnthalpyOfFormation;
    private final double cpA;
    private final double cpB;
    private final double cpC;
    private final double cpD;
    private final double attractiveM;

    private PseudoComponentProfile(double moles, double molarMass, double density, double normalBoilingPoint,
        double criticalTemperature, double criticalPressure, double acentricFactor, double criticalVolume,
        double racketZ, double racketZCpa, double parachor, double criticalViscosity, double triplePointTemperature,
        double heatOfFusion, double idealGasEnthalpyOfFormation, double cpA, double cpB, double cpC, double cpD,
        double attractiveM) {
      this.moles = moles;
      this.molarMass = molarMass;
      this.density = density;
      this.normalBoilingPoint = normalBoilingPoint;
      this.criticalTemperature = criticalTemperature;
      this.criticalPressure = criticalPressure;
      this.acentricFactor = acentricFactor;
      this.criticalVolume = criticalVolume;
      this.racketZ = racketZ;
      this.racketZCpa = racketZCpa;
      this.parachor = parachor;
      this.criticalViscosity = criticalViscosity;
      this.triplePointTemperature = triplePointTemperature;
      this.heatOfFusion = heatOfFusion;
      this.idealGasEnthalpyOfFormation = idealGasEnthalpyOfFormation;
      this.cpA = cpA;
      this.cpB = cpB;
      this.cpC = cpC;
      this.cpD = cpD;
      this.attractiveM = attractiveM;
    }

    private static PseudoComponentProfile empty() {
      return new PseudoComponentProfile(0.0, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    private boolean isValid() {
      return moles > 0.0 && molarMass > 0.0 && density > 0.0;
    }

    private double getMoles() {
      return moles;
    }

    private double getMass() {
      return moles * molarMass;
    }

    private double getMolarMass() {
      return molarMass;
    }

    private double getDensity() {
      return density;
    }

    private double getNormalBoilingPoint() {
      return normalBoilingPoint;
    }

    private double getCriticalTemperature() {
      return criticalTemperature;
    }

    private double getCriticalPressure() {
      return criticalPressure;
    }

    private double getAcentricFactor() {
      return acentricFactor;
    }

    private double getCriticalVolume() {
      return criticalVolume;
    }

    private double getRacketZ() {
      return racketZ;
    }

    private double getRacketZCpa() {
      return racketZCpa;
    }

    private double getParachor() {
      return parachor;
    }

    private double getCriticalViscosity() {
      return criticalViscosity;
    }

    private double getTriplePointTemperature() {
      return triplePointTemperature;
    }

    private double getHeatOfFusion() {
      return heatOfFusion;
    }

    private double getIdealGasEnthalpyOfFormation() {
      return idealGasEnthalpyOfFormation;
    }

    private double getCpA() {
      return cpA;
    }

    private double getCpB() {
      return cpB;
    }

    private double getCpC() {
      return cpC;
    }

    private double getCpD() {
      return cpD;
    }

    private double getAttractiveM() {
      return attractiveM;
    }

    private void applyTo(ComponentInterface component) {
      Objects.requireNonNull(component, "component");

      if (Double.isFinite(normalBoilingPoint)) {
        component.setNormalBoilingPoint(normalBoilingPoint);
      }
      if (Double.isFinite(criticalTemperature)) {
        component.setTC(criticalTemperature);
      }
      if (Double.isFinite(criticalPressure)) {
        component.setPC(criticalPressure);
      }
      if (Double.isFinite(acentricFactor)) {
        component.setAcentricFactor(acentricFactor);
      }
      if (Double.isFinite(criticalVolume)) {
        component.setCriticalVolume(criticalVolume);
      }
      if (Double.isFinite(racketZ)) {
        component.setRacketZ(racketZ);
      }
      if (Double.isFinite(racketZCpa)) {
        component.setRacketZCPA(racketZCpa);
      }
      if (Double.isFinite(parachor)) {
        component.setParachorParameter(parachor);
      }
      if (Double.isFinite(criticalViscosity)) {
        component.setCriticalViscosity(criticalViscosity);
      }
      if (Double.isFinite(triplePointTemperature)) {
        component.setTriplePointTemperature(triplePointTemperature);
      }
      if (Double.isFinite(heatOfFusion)) {
        component.setHeatOfFusion(heatOfFusion);
      }
      if (Double.isFinite(idealGasEnthalpyOfFormation)) {
        component.setIdealGasEnthalpyOfFormation(idealGasEnthalpyOfFormation);
      }
      if (Double.isFinite(cpA)) {
        component.setCpA(cpA);
      }
      if (Double.isFinite(cpB)) {
        component.setCpB(cpB);
      }
      if (Double.isFinite(cpC)) {
        component.setCpC(cpC);
      }
      if (Double.isFinite(cpD)) {
        component.setCpD(cpD);
      }
      if (Double.isFinite(attractiveM) && component.getAttractiveTerm() != null) {
        component.getAttractiveTerm().setm(attractiveM);
      }
    }
  }
}
