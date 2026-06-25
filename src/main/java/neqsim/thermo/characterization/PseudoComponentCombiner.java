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
import org.apache.commons.math3.special.Gamma;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.characterization.CharacterizationOptions.DelumpBinningBasis;
import neqsim.thermo.characterization.CharacterizationOptions.DelumpConservation;
import neqsim.thermo.characterization.CharacterizationOptions.DelumpGammaScope;
import neqsim.thermo.characterization.CharacterizationOptions.ReferenceBoundaryMode;
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
    return characterizeToReferenceCore(source, reference, false, false, 0, false, DelumpBinningBasis.MOLAR_MASS,
	DelumpGammaScope.NEIGHBOURS, DelumpConservation.BOTH, ReferenceBoundaryMode.MIDPOINT);
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
   * @param binningBasis basis on which delumped sub-fractions are binned onto the reference cuts; only applied when
   * {@code delump} is {@code true} (otherwise the legacy boiling-point key is used to preserve backward compatibility)
   * @param gammaScope scope of the Whitson gamma molar-distribution fit used to shape the delumping
   * @param conservation quantity (moles, mass, or both) conserved exactly when a lump is delumped
   * @param boundaryMode rule used to place the cut edges between adjacent reference pseudo-components; only the
   * {@link ReferenceBoundaryMode#CENTROID_SPAN} mode on the {@link DelumpBinningBasis#MOLAR_MASS} basis changes the
   * legacy midpoint placement
   * @return characterized fluid containing pseudo components compatible with the reference fluid
   */
  private static SystemInterface characterizeToReferenceCore(SystemInterface source, SystemInterface reference,
      boolean inheritReferenceProperties, boolean delump, int delumpResolution, boolean sharedImaginaryBoundaries,
      DelumpBinningBasis binningBasis, DelumpGammaScope gammaScope, DelumpConservation conservation,
      ReferenceBoundaryMode boundaryMode) {
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

    // The molar-mass binning basis is applied only when delumping. With delumping off the legacy boiling-point sorting
    // key is retained so the non-delumped and shared-imaginary paths reproduce their previous results exactly.
    DelumpBinningBasis basis = delump ? binningBasis : DelumpBinningBasis.BOILING_POINT;

    List<PseudoComponentContribution> sourcePseudoComponents = sourceExtraction.pseudoComponents;
    if (delump) {
      sourcePseudoComponents = delumpContributions(sourcePseudoComponents, delumpResolution, gammaScope, conservation);
    }

    List<Double> boundaries = sharedImaginaryBoundaries
	? determineReferenceEqualMassBoundaries(referenceExtraction.pseudoComponents, delumpResolution, basis,
	    gammaScope, conservation)
	: determineReferenceBoundaries(referenceExtraction.pseudoComponents, basis, boundaryMode);
    List<PseudoComponentProfile> profiles = distributeToProfiles(sourcePseudoComponents, boundaries,
	referenceExtraction.pseudoComponents.size(), basis);

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
	options.getDelumpResolution(), options.isSharedImaginaryBoundaries(), options.getDelumpBinningBasis(),
	options.getDelumpGammaScope(), options.getDelumpConservation(), options.getReferenceBoundaryMode());

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
   * Equal-mass quantile boundary placement on an arbitrary binning basis.
   *
   * <p>
   * Identical to {@link #determineQuantileBoundaries(List, int)} but the cut points are placed on the requested
   * {@link DelumpBinningBasis} key (molar mass or boiling point) instead of always on the boiling-point sorting key.
   * The contributions are sorted by the chosen key before the cumulative-mass scan so the boundaries are returned in
   * ascending key order.
   *
   * @param contributions the contributions to cut
   * @param targetPseudoComponents the number of groups (one fewer boundary is returned)
   * @param basis the binning key on which equal-mass cut points are placed
   * @return the ascending equal-mass boundaries (size {@code targetPseudoComponents - 1})
   */
  private static List<Double> determineQuantileBoundaries(List<PseudoComponentContribution> contributions,
      int targetPseudoComponents, DelumpBinningBasis basis) {
    if (targetPseudoComponents <= 1 || contributions.isEmpty()) {
      return Collections.emptyList();
    }

    List<PseudoComponentContribution> sorted = new ArrayList<>(contributions);
    sorted.sort(Comparator.comparingDouble(c -> c.binningKey(basis)));

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
	boundaries.add(contribution.binningKey(basis));
	targetIndex++;
      }
      cumulative = nextCumulative;
    }

    while (boundaries.size() < targets.length) {
      boundaries.add(Double.POSITIVE_INFINITY);
    }

    return boundaries;
  }

  /** Slope of the Pedersen molar-mass / carbon-number relation (Eq. 5.27): M[g/mol] = 14&middot;C - 4. */
  private static final double CARBON_MASS_SLOPE = 14.0;
  /** Offset of the Pedersen molar-mass / carbon-number relation (Eq. 5.27): M[g/mol] = 14&middot;C - 4. */
  private static final double CARBON_MASS_OFFSET = 4.0;
  /** Leading factor of the Katz-Firoozabadi boiling-point correlation (Eq. 5.28). */
  private static final double KATZ_FACTOR = 97.58;
  /** Molar-mass exponent of the Katz-Firoozabadi boiling-point correlation (Eq. 5.28). */
  private static final double KATZ_MASS_EXPONENT = 0.3323;
  /** Density exponent of the Katz-Firoozabadi boiling-point correlation (Eq. 5.28). */
  private static final double KATZ_DENSITY_EXPONENT = 0.04609;
  /**
   * Mild default molar-distribution slope B (Eq. 5.15, {@code ln z = A + B*C}) used only when a lump has no usable
   * neighbour from which to estimate the local slope. A negative value gives the customary light-end bias.
   */
  private static final double DEFAULT_MOLAR_SLOPE = -0.25;
  /** Clamp on the estimated molar-distribution slope B to guard against degenerate neighbour ratios. */
  private static final double MAX_MOLAR_SLOPE = 2.0;
  /** Fallback half-width (in carbon numbers) for a lump that has no neighbour on either side. */
  private static final double DEFAULT_CARBON_HALF_WIDTH = 0.5;
  /** Fallback liquid density (g/cm3) for the Katz-Firoozabadi correlation when a lump density is unavailable. */
  private static final double DEFAULT_LIQUID_DENSITY = 0.8;
  /** Lower clamp on the fitted Whitson gamma shape parameter &alpha; (Eq. 5.27 molar distribution). */
  private static final double MIN_GAMMA_SHAPE = 0.5;
  /** Upper clamp on the fitted Whitson gamma shape parameter &alpha; to guard against a near-delta distribution. */
  private static final double MAX_GAMMA_SHAPE = 50.0;
  /** Minimum cell probability mass below which a gamma slice is treated as empty. */
  private static final double GAMMA_WEIGHT_TOLERANCE = 1e-12;

  /**
   * Split each coarse source pseudo-component into a grid of finer single-carbon-number (SCN) sub-fractions using a
   * neighbour-bounded carbon-number interval and a Whitson gamma molar distribution, conserving the requested quantity
   * (moles, mass, or both).
   *
   * <p>
   * This implements the delumping stage of the Pedersen et al. (Chapter 5) lumping/delumping scheme. Unlike a fixed
   * &plusmn;40&nbsp;% molar-mass window with a hard-coded exponential decay (which smears a lump over carbon numbers it
   * does not contain and fabricates spurious light/heavy mass), every parent lump is delumped <em>only inside its own
   * carbon-number interval</em>, inferred from its ordered neighbours in the sorted contribution list. For every parent
   * lump <em>i</em> with carbon number {@code C_i = (M_i + 4)/14} (Eq. 5.27 inverted) the method:
   * <ol>
   * <li><b>Bounds the sub-fraction range by the neighbours, not a fixed window.</b> The lower edge is the carbon-number
   * midpoint between lumps <em>i-1</em> and <em>i</em>, the upper edge the midpoint between <em>i</em> and
   * <em>i+1</em>. The first and last lumps use a one-sided rule (the available neighbour gap is mirrored to the open
   * side). This guarantees sub-fractions never cross into a neighbour's range and never invent material outside the
   * lump.</li>
   * <li><b>Shapes the molar distribution with a Whitson gamma fitted on molar mass.</b> When
   * {@code gammaScope == GLOBAL} a single gamma is fitted by the method of moments to the whole C7+ lump set; when
   * {@code gammaScope == NEIGHBOURS} (the default) a local gamma is fitted per lump from its immediate neighbours. The
   * gamma is sliced between the SCN cell molar-mass edges: each cell's mole weight is the gamma probability mass and
   * its molar mass is the gamma conditional mean over the cell (closed-form via the regularized lower incomplete gamma
   * function). When a gamma cannot be fitted (degenerate variance, isolated lump) the method falls back to Pedersen's
   * exponential molar distribution (Eq. 5.15, {@code ln z = A + B*C}) on the cell-centred grid. The mole weights are
   * normalized, so the parent moles are conserved.</li>
   * <li><b>Applies the conservation closure.</b> With {@code DelumpConservation.BOTH} (the default) the sub-fraction
   * molar masses are rescaled so that &Sigma;<sub>k</sub> n<sub>k</sub> M<sub>k</sub> = n<sub>parent</sub>
   * M<sub>parent</sub> exactly (parent moles and mass both conserved, Eqs. 5.35-5.37). With
   * {@code DelumpConservation.MOLES} the gamma molar masses are kept and the mass is allowed to float; with
   * {@code DelumpConservation.MASS} the moles are rescaled so the mass is conserved exactly and the moles float.</li>
   * <li><b>Assigns the normal boiling point via the non-linear Katz-Firoozabadi correlation (Eq. 5.28)</b>,
   * {@code Tb = 97.58*M^0.3323*rho^0.04609}. When the parent has a boiling point its Katz-Firoozabadi shape is anchored
   * to the parent value ({@code Tb_k = Tb_parent * katz(M_k)/katz(M_parent)}) so the sub-fractions stay on the
   * parent/reference boiling-point scale while following the non-linear carbon-number dependence; otherwise the
   * absolute Eq. 5.28 value is used. Density and the critical constants are inherited from the parent.</li>
   * </ol>
   *
   * <p>
   * The expanded list is re-sorted by sorting key (normal boiling point, falling back to molar mass) so it can be fed
   * directly to {@link #distributeToProfiles}.
   *
   * @param sourcePseudoComponents the coarse source pseudo-components to delump, sorted ascending by sorting key
   * @param resolution number of sub-fractions per parent lump; values of 1 or less return the input unchanged
   * @param gammaScope scope of the Whitson gamma molar-distribution fit (global, or local per lump)
   * @param conservation quantity conserved exactly (parent moles, parent mass, or both)
   * @return the delumped, sorted list of sub-fractions
   */
  private static List<PseudoComponentContribution> delumpContributions(
      List<PseudoComponentContribution> sourcePseudoComponents, int resolution, DelumpGammaScope gammaScope,
      DelumpConservation conservation) {
    if (resolution <= 1) {
      return sourcePseudoComponents;
    }

    int lumpCount = sourcePseudoComponents.size();
    double[] carbonNumbers = new double[lumpCount];
    for (int i = 0; i < lumpCount; i++) {
      carbonNumbers[i] = carbonNumberFromMolarMass(sourcePseudoComponents.get(i).molarMass);
    }

    // Optional single global Whitson gamma fitted to the whole C7+ lump set (DelumpGammaScope.GLOBAL). The shift eta is
    // the lower molar-mass edge of the lightest lump so every per-lump slice has a non-negative reduced variable.
    double[] globalGamma = null;
    if (gammaScope == DelumpGammaScope.GLOBAL) {
      globalGamma = fitGlobalGamma(sourcePseudoComponents, carbonNumbers);
    }

    List<PseudoComponentContribution> expanded = new ArrayList<>(lumpCount * resolution);

    for (int i = 0; i < lumpCount; i++) {
      PseudoComponentContribution parent = sourcePseudoComponents.get(i);
      double parentMoles = parent.moles;
      double parentMolarMass = parent.molarMass;
      double parentCarbon = carbonNumbers[i];
      if (!(parentMoles > 0.0) || !(parentMolarMass > 0.0) || !Double.isFinite(parentCarbon)) {
	expanded.add(parent);
	continue;
      }

      // 1. Neighbour-bounded carbon-number range (sub-fractions never cross into a neighbour's interval).
      boolean hasLower = i > 0 && Double.isFinite(carbonNumbers[i - 1]) && carbonNumbers[i - 1] < parentCarbon;
      boolean hasUpper = i < lumpCount - 1 && Double.isFinite(carbonNumbers[i + 1])
	  && carbonNumbers[i + 1] > parentCarbon;
      double upperHalf = hasUpper ? 0.5 * (carbonNumbers[i + 1] - parentCarbon)
	  : (hasLower ? 0.5 * (parentCarbon - carbonNumbers[i - 1]) : DEFAULT_CARBON_HALF_WIDTH);
      double lowerHalf = hasLower ? 0.5 * (parentCarbon - carbonNumbers[i - 1])
	  : (hasUpper ? 0.5 * (carbonNumbers[i + 1] - parentCarbon) : DEFAULT_CARBON_HALF_WIDTH);
      double lowerCarbon = parentCarbon - lowerHalf;
      double upperCarbon = parentCarbon + upperHalf;
      if (!(upperCarbon > lowerCarbon)) {
	expanded.add(parent);
	continue;
      }

      double width = upperCarbon - lowerCarbon;
      double[] weights = new double[resolution];
      double[] molarMasses = new double[resolution];
      double weightSum = 0.0;

      // 2. Pick the gamma shape: a single global fit, or a local fit over the {i-1, i, i+1} window.
      double[] gamma = gammaScope == DelumpGammaScope.GLOBAL ? globalGamma
	  : fitLocalGamma(sourcePseudoComponents, carbonNumbers, i, lowerCarbon);

      boolean gammaUsable = false;
      if (gamma != null) {
	double alpha = gamma[0];
	double beta = gamma[1];
	double eta = gamma[2];
	// 3a. Slice the gamma between the SCN cell edges; cell weight = probability mass, MW = conditional mean.
	for (int k = 0; k < resolution; k++) {
	  double edgeLoCarbon = lowerCarbon + width * k / resolution;
	  double edgeHiCarbon = lowerCarbon + width * (k + 1) / resolution;
	  double mwLoG = molarMassFromCarbonNumber(edgeLoCarbon) * 1000.0;
	  double mwHiG = molarMassFromCarbonNumber(edgeHiCarbon) * 1000.0;
	  double xLo = Math.max(0.0, (mwLoG - eta) / beta);
	  double xHi = Math.max(0.0, (mwHiG - eta) / beta);
	  double prob = Gamma.regularizedGammaP(alpha, xHi) - Gamma.regularizedGammaP(alpha, xLo);
	  double condMeanG;
	  if (prob > GAMMA_WEIGHT_TOLERANCE) {
	    double firstMoment = alpha * beta
		* (Gamma.regularizedGammaP(alpha + 1.0, xHi) - Gamma.regularizedGammaP(alpha + 1.0, xLo));
	    condMeanG = eta + firstMoment / prob;
	  } else {
	    prob = 0.0;
	    condMeanG = 0.5 * (mwLoG + mwHiG);
	  }
	  weights[k] = prob;
	  molarMasses[k] = condMeanG / 1000.0;
	  weightSum += prob;
	}
	gammaUsable = weightSum > GAMMA_WEIGHT_TOLERANCE;
      }

      if (!gammaUsable) {
	// 3b. Exponential fallback (Eq. 5.15, ln z = A + B*C) on the cell-centred carbon grid.
	double slope = estimateMolarSlope(sourcePseudoComponents, carbonNumbers, i, hasLower, hasUpper);
	weightSum = 0.0;
	for (int k = 0; k < resolution; k++) {
	  double carbon = lowerCarbon + width * (k + 0.5) / resolution;
	  double weight = Math.exp(slope * (carbon - parentCarbon));
	  weights[k] = weight;
	  molarMasses[k] = molarMassFromCarbonNumber(carbon);
	  weightSum += weight;
	}
      }

      if (!(weightSum > 0.0)) {
	expanded.add(parent);
	continue;
      }

      // 4. Mole weights (normalized => parent moles conserved) and the requested conservation closure.
      double parentMass = parentMoles * parentMolarMass;
      double rawMass = 0.0;
      for (int k = 0; k < resolution; k++) {
	rawMass += parentMoles * (weights[k] / weightSum) * molarMasses[k];
      }
      double massScale = 1.0;
      double moleScale = 1.0;
      if (conservation == DelumpConservation.BOTH) {
	massScale = rawMass > MASS_TOLERANCE ? parentMass / rawMass : 1.0;
      } else if (conservation == DelumpConservation.MASS) {
	moleScale = rawMass > MASS_TOLERANCE ? parentMass / rawMass : 1.0;
      }

      // 5. Build the sub-fractions with Katz-Firoozabadi (Eq. 5.28) boiling points anchored to the parent.
      boolean parentHasTb = Double.isFinite(parent.normalBoilingPoint) && parent.normalBoilingPoint > 0.0;
      double parentKatz = katzFiroozabadiBoilingPoint(parentMolarMass, parent.density);
      boolean anchorTb = parentHasTb && Double.isFinite(parentKatz) && parentKatz > 0.0;
      for (int k = 0; k < resolution; k++) {
	double moleFraction = weights[k] / weightSum;
	double subMoles = parentMoles * moleFraction * moleScale;
	double subMolarMass = molarMasses[k] * massScale;
	double subKatz = katzFiroozabadiBoilingPoint(subMolarMass, parent.density);
	double subTb;
	if (anchorTb && Double.isFinite(subKatz)) {
	  subTb = parent.normalBoilingPoint * (subKatz / parentKatz);
	} else if (Double.isFinite(subKatz) && subKatz > 0.0) {
	  subTb = subKatz;
	} else {
	  subTb = parent.normalBoilingPoint;
	}

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

  /**
   * Package-private test seam that delumps a synthetic set of lumps and returns the resulting single-carbon-number
   * sub-fractions as a plain numeric matrix, so unit tests can assert the neighbour-bounded ranges, the gamma molar
   * decay, the Katz-Firoozabadi molar-mass / boiling-point relation, and the mole/mass conservation closures without
   * needing access to the private {@link PseudoComponentContribution} type.
   *
   * <p>
   * Each input lump is described by its moles, molar mass (kg/mol), liquid density (g/cm3), and normal boiling point
   * (K); the critical constants and other component properties are inherited from the parent and are immaterial to the
   * delumping arithmetic, so they are filled with {@link Double#NaN}. The returned matrix has one row per sub-fraction
   * with columns {@code {moles, molarMass[kg/mol], normalBoilingPoint[K], parentIndex}}, sorted ascending by sorting
   * key. The {@code parentIndex} is the index of the originating lump in the (sorting-key sorted) input, so a test can
   * group the sub-fractions back to their parent without re-deriving the molar-mass interval edges.
   *
   * @param moles per-lump moles
   * @param molarMass per-lump molar mass in kg/mol
   * @param density per-lump liquid density in g/cm3
   * @param boilingPoint per-lump normal boiling point in K (use {@link Double#NaN} for none)
   * @param resolution number of sub-fractions per lump
   * @param gammaScope scope of the Whitson gamma molar-distribution fit
   * @param conservation quantity conserved exactly when a lump is delumped
   * @return a matrix with one row per sub-fraction and columns {@code {moles, molarMass, normalBoilingPoint,
   * parentIndex}}
   */
  static double[][] delumpForTesting(double[] moles, double[] molarMass, double[] density, double[] boilingPoint,
      int resolution, CharacterizationOptions.DelumpGammaScope gammaScope,
      CharacterizationOptions.DelumpConservation conservation) {
    List<PseudoComponentContribution> lumps = new ArrayList<>(moles.length);
    for (int i = 0; i < moles.length; i++) {
      lumps.add(new PseudoComponentContribution("L" + i, moles[i], molarMass[i], density[i], boilingPoint[i],
	  Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
	  Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    }
    lumps.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));
    List<PseudoComponentContribution> result = delumpContributions(lumps, resolution, gammaScope, conservation);
    double[][] out = new double[result.size()][4];
    for (int i = 0; i < result.size(); i++) {
      PseudoComponentContribution sub = result.get(i);
      out[i][0] = sub.moles;
      out[i][1] = sub.molarMass;
      out[i][2] = sub.normalBoilingPoint;
      out[i][3] = parseParentIndex(sub.name);
    }
    return out;
  }

  /**
   * Parse the originating parent lump index from a {@link #delumpForTesting} sub-fraction name of the form
   * {@code L<parent>_d<slice>}; returns {@code -1} when the name does not match.
   *
   * @param name the sub-fraction name
   * @return the parent index, or {@code -1} when the name is not a delumping sub-fraction
   */
  private static double parseParentIndex(String name) {
    if (name == null || name.length() < 2 || name.charAt(0) != 'L') {
      return -1.0;
    }
    int suffix = name.indexOf("_d");
    if (suffix <= 1) {
      return -1.0;
    }
    try {
      return Double.parseDouble(name.substring(1, suffix));
    } catch (NumberFormatException ex) {
      return -1.0;
    }
  }

  /**
   * Package-private test seam exposing the molar-mass / carbon-number conversion (Pedersen Eq. 5.27) so unit tests can
   * compute the expected neighbour-bounded carbon-number midpoints.
   *
   * @param molarMass the molar mass in kg/mol
   * @return the equivalent single-carbon number
   */
  static double carbonNumberFromMolarMassForTesting(double molarMass) {
    return carbonNumberFromMolarMass(molarMass);
  }

  /**
   * Package-private test seam exposing the carbon-number / molar-mass conversion (Pedersen Eq. 5.27) so unit tests can
   * map carbon-number midpoints back onto molar-mass interval edges.
   *
   * @param carbonNumber the single-carbon number
   * @return the equivalent molar mass in kg/mol
   */
  static double molarMassFromCarbonNumberForTesting(double carbonNumber) {
    return molarMassFromCarbonNumber(carbonNumber);
  }

  /**
   * Fit a single Whitson gamma molar distribution (Pedersen et al. Eq. 5.27 / Whitson 1983) to the whole set of valid
   * C7+ lumps by the method of moments. The shift parameter &eta; is anchored at the lower molar-mass edge of the
   * lightest lump's neighbour-bounded interval so that every per-lump gamma slice has a non-negative reduced variable.
   *
   * @param lumps the parent lumps
   * @param carbonNumbers the carbon number of each lump (parallel to {@code lumps})
   * @return {@code {alpha, beta, eta}} in g/mol units, or {@code null} when the distribution is degenerate
   */
  private static double[] fitGlobalGamma(List<PseudoComponentContribution> lumps, double[] carbonNumbers) {
    int n = lumps.size();
    double[] mwG = new double[n];
    double[] moles = new double[n];
    double minEdgeG = Double.POSITIVE_INFINITY;
    int valid = 0;
    for (int i = 0; i < n; i++) {
      PseudoComponentContribution lump = lumps.get(i);
      if (!(lump.moles > 0.0) || !(lump.molarMass > 0.0) || !Double.isFinite(carbonNumbers[i])) {
	continue;
      }
      mwG[valid] = lump.molarMass * 1000.0;
      moles[valid] = lump.moles;
      double lowerCarbon = carbonNumbers[i] - DEFAULT_CARBON_HALF_WIDTH;
      double edgeG = molarMassFromCarbonNumber(lowerCarbon) * 1000.0;
      if (edgeG < minEdgeG) {
	minEdgeG = edgeG;
      }
      valid++;
    }
    if (valid < 2 || !Double.isFinite(minEdgeG)) {
      return null;
    }
    return fitGammaByMoments(mwG, moles, valid, minEdgeG);
  }

  /**
   * Fit a local Whitson gamma molar distribution to lump {@code i} from its immediate neighbours ({@code i-1, i, i+1}).
   * The shift parameter &eta; is anchored at the lump's lower molar-mass edge so the slice reduced variables are
   * non-negative.
   *
   * @param lumps the parent lumps
   * @param carbonNumbers the carbon number of each lump (parallel to {@code lumps})
   * @param i the index of the lump being delumped
   * @param lowerCarbon the lower carbon-number edge of the lump's neighbour-bounded interval
   * @return {@code {alpha, beta, eta}} in g/mol units, or {@code null} when the local distribution is degenerate
   */
  private static double[] fitLocalGamma(List<PseudoComponentContribution> lumps, double[] carbonNumbers, int i,
      double lowerCarbon) {
    double[] mwG = new double[3];
    double[] moles = new double[3];
    int valid = 0;
    for (int j = i - 1; j <= i + 1; j++) {
      if (j < 0 || j >= lumps.size()) {
	continue;
      }
      PseudoComponentContribution lump = lumps.get(j);
      if (!(lump.moles > 0.0) || !(lump.molarMass > 0.0) || !Double.isFinite(carbonNumbers[j])) {
	continue;
      }
      mwG[valid] = lump.molarMass * 1000.0;
      moles[valid] = lump.moles;
      valid++;
    }
    if (valid < 2) {
      return null;
    }
    double etaG = molarMassFromCarbonNumber(lowerCarbon) * 1000.0;
    return fitGammaByMoments(mwG, moles, valid, etaG);
  }

  /**
   * Method-of-moments fit of a shifted gamma distribution to a mole-weighted set of molar masses.
   *
   * <p>
   * Given the mole-weighted mean &mu; and variance &sigma;&sup2; of the molar masses, the shifted gamma with origin
   * &eta; has shape &alpha; = (&mu;-&eta;)&sup2;/&sigma;&sup2; and scale &beta; = &sigma;&sup2;/(&mu;-&eta;). The shape
   * is clamped to {@code [MIN_GAMMA_SHAPE, MAX_GAMMA_SHAPE]} with the scale recomputed from the mean to keep &mu; =
   * &eta; + &alpha;&beta;.
   *
   * @param mwG the molar masses in g/mol (first {@code count} entries used)
   * @param moles the mole weights (first {@code count} entries used)
   * @param count the number of valid entries
   * @param etaG the gamma origin (shift) in g/mol
   * @return {@code {alpha, beta, eta}} in g/mol units, or {@code null} when the fit is degenerate
   */
  private static double[] fitGammaByMoments(double[] mwG, double[] moles, int count, double etaG) {
    double z = 0.0;
    double mean = 0.0;
    for (int k = 0; k < count; k++) {
      z += moles[k];
      mean += moles[k] * mwG[k];
    }
    if (!(z > 0.0)) {
      return null;
    }
    mean /= z;
    double variance = 0.0;
    for (int k = 0; k < count; k++) {
      double d = mwG[k] - mean;
      variance += moles[k] * d * d;
    }
    variance /= z;
    double shifted = mean - etaG;
    if (!(variance > MASS_TOLERANCE) || !(shifted > 0.0)) {
      return null;
    }
    double alpha = shifted * shifted / variance;
    if (alpha < MIN_GAMMA_SHAPE) {
      alpha = MIN_GAMMA_SHAPE;
    } else if (alpha > MAX_GAMMA_SHAPE) {
      alpha = MAX_GAMMA_SHAPE;
    }
    double beta = shifted / alpha;
    if (!(beta > 0.0)) {
      return null;
    }
    return new double[] { alpha, beta, etaG };
  }

  /**
   * Convert a molar mass to an equivalent single-carbon number using the Pedersen molar-mass relation (Eq. 5.27)
   * inverted: {@code C = (M[g/mol] + 4) / 14}.
   *
   * @param molarMassKgPerMol molar mass in kg/mol (NeqSim's internal unit)
   * @return the equivalent carbon number, or {@link Double#NaN} when the molar mass is non-finite or non-positive
   */
  private static double carbonNumberFromMolarMass(double molarMassKgPerMol) {
    if (!Double.isFinite(molarMassKgPerMol) || molarMassKgPerMol <= 0.0) {
      return Double.NaN;
    }
    double molarMassGramPerMol = molarMassKgPerMol * 1000.0;
    return (molarMassGramPerMol + CARBON_MASS_OFFSET) / CARBON_MASS_SLOPE;
  }

  /**
   * Convert a carbon number to a molar mass using the Pedersen molar-mass relation (Eq. 5.27):
   * {@code M[g/mol] = 14*C - 4}.
   *
   * @param carbonNumber the (possibly fractional) carbon number
   * @return the molar mass in kg/mol (NeqSim's internal unit)
   */
  private static double molarMassFromCarbonNumber(double carbonNumber) {
    double molarMassGramPerMol = CARBON_MASS_SLOPE * carbonNumber - CARBON_MASS_OFFSET;
    return molarMassGramPerMol / 1000.0;
  }

  /**
   * Evaluate the Katz-Firoozabadi boiling-point correlation (Pedersen et al. Eq. 5.28):
   * {@code Tb = 97.58*M^0.3323*rho^0.04609}, with the molar mass in g/mol and the density in g/cm3.
   *
   * @param molarMassKgPerMol molar mass in kg/mol (NeqSim's internal unit)
   * @param density liquid density in g/cm3; a default of 0.8 is used when it is non-finite or non-positive
   * @return the correlated normal boiling point in kelvin, or {@link Double#NaN} when the molar mass is invalid
   */
  private static double katzFiroozabadiBoilingPoint(double molarMassKgPerMol, double density) {
    if (!Double.isFinite(molarMassKgPerMol) || molarMassKgPerMol <= 0.0) {
      return Double.NaN;
    }
    double molarMassGramPerMol = molarMassKgPerMol * 1000.0;
    double rho = Double.isFinite(density) && density > 0.0 ? density : DEFAULT_LIQUID_DENSITY;
    return KATZ_FACTOR * Math.pow(molarMassGramPerMol, KATZ_MASS_EXPONENT) * Math.pow(rho, KATZ_DENSITY_EXPONENT);
  }

  /**
   * Estimate the Pedersen molar-distribution slope {@code B} of Eq. 5.15 ({@code ln z = A + B*C}) for lump {@code i}
   * from the local lump-to-lump mole ratio over the carbon-number gap to its neighbours. Using both neighbours gives a
   * centred estimate; a single neighbour gives a one-sided estimate; with no usable neighbour the mild
   * {@link #DEFAULT_MOLAR_SLOPE} is returned. The result is clamped to &plusmn;{@link #MAX_MOLAR_SLOPE} to guard
   * against degenerate ratios.
   *
   * @param lumps the sorted parent lumps
   * @param carbonNumbers the carbon number of each lump (parallel to {@code lumps})
   * @param i the index of the lump whose slope is estimated
   * @param hasLower whether lump {@code i-1} is a usable lighter neighbour
   * @param hasUpper whether lump {@code i+1} is a usable heavier neighbour
   * @return the estimated, clamped molar-distribution slope {@code B}
   */
  private static double estimateMolarSlope(List<PseudoComponentContribution> lumps, double[] carbonNumbers, int i,
      boolean hasLower, boolean hasUpper) {
    double slope = DEFAULT_MOLAR_SLOPE;
    if (hasLower && hasUpper) {
      double molesLow = lumps.get(i - 1).moles;
      double molesHigh = lumps.get(i + 1).moles;
      double deltaCarbon = carbonNumbers[i + 1] - carbonNumbers[i - 1];
      if (molesLow > 0.0 && molesHigh > 0.0 && deltaCarbon > 0.0) {
	slope = (Math.log(molesHigh) - Math.log(molesLow)) / deltaCarbon;
      }
    } else if (hasUpper) {
      double molesMid = lumps.get(i).moles;
      double molesHigh = lumps.get(i + 1).moles;
      double deltaCarbon = carbonNumbers[i + 1] - carbonNumbers[i];
      if (molesMid > 0.0 && molesHigh > 0.0 && deltaCarbon > 0.0) {
	slope = (Math.log(molesHigh) - Math.log(molesMid)) / deltaCarbon;
      }
    } else if (hasLower) {
      double molesLow = lumps.get(i - 1).moles;
      double molesMid = lumps.get(i).moles;
      double deltaCarbon = carbonNumbers[i] - carbonNumbers[i - 1];
      if (molesLow > 0.0 && molesMid > 0.0 && deltaCarbon > 0.0) {
	slope = (Math.log(molesMid) - Math.log(molesLow)) / deltaCarbon;
      }
    }
    if (!Double.isFinite(slope)) {
      slope = DEFAULT_MOLAR_SLOPE;
    }
    if (slope > MAX_MOLAR_SLOPE) {
      slope = MAX_MOLAR_SLOPE;
    } else if (slope < -MAX_MOLAR_SLOPE) {
      slope = -MAX_MOLAR_SLOPE;
    }
    return slope;
  }

  private static List<Double> determineReferenceBoundaries(List<PseudoComponentContribution> referenceContributions,
      DelumpBinningBasis basis, ReferenceBoundaryMode boundaryMode) {
    if (referenceContributions.size() <= 1) {
      return Collections.emptyList();
    }

    if (boundaryMode == ReferenceBoundaryMode.CENTROID_SPAN && basis == DelumpBinningBasis.MOLAR_MASS) {
      return determineReferenceCentroidSpanBoundaries(referenceContributions, basis);
    }

    List<Double> boundaries = new ArrayList<>(referenceContributions.size() - 1);
    for (int i = 0; i < referenceContributions.size() - 1; i++) {
      double key1 = referenceContributions.get(i).binningKey(basis);
      double key2 = referenceContributions.get(i + 1).binningKey(basis);
      double boundary = Double.isFinite(key1) && Double.isFinite(key2) ? 0.5 * (key1 + key2) : Math.max(key1, key2);
      boundaries.add(boundary);
    }
    return boundaries;
  }

  /**
   * Place the reference cut boundaries so that each reference cut key is the centroid of its own span.
   *
   * <p>
   * The default {@link #determineReferenceBoundaries} places each edge at the arithmetic midpoint of two adjacent cut
   * keys, which implicitly assumes every cut is equally wide. When the reference slate mixes very narrow and very wide
   * cuts (e.g. a one-carbon cut next to a fifteen-carbon cut) the midpoint of the means is not the true span edge, so
   * material is mis-binned into the narrower neighbour. Requiring each key to sit at the centroid of its span gives the
   * recurrence {@code b_i = 2*key_i - b_(i-1)}, anchored at {@code b_0 = key_0 - 0.5*(key_1 - key_0)} and walked from
   * the lightest to the heaviest cut. Each candidate is clamped to lie strictly between its two adjacent keys (and to
   * stay monotone), falling back to the arithmetic midpoint otherwise, so the strict one-to-one inheritance ordering is
   * preserved. The recurrence is linear in the cut key, so it is only valid on a basis that is itself linear in the
   * binning quantity ({@link DelumpBinningBasis#MOLAR_MASS}).
   *
   * @param referenceContributions the reference pseudo-components, sorted ascending by binning key
   * @param basis the (molar-mass) binning key on which the centroid recurrence and clamping bounds are evaluated
   * @return the clamped centroid-span cut boundaries (size {@code referenceContributions.size() - 1})
   */
  private static List<Double> determineReferenceCentroidSpanBoundaries(
      List<PseudoComponentContribution> referenceContributions, DelumpBinningBasis basis) {
    int n = referenceContributions.size();
    double[] key = new double[n];
    for (int i = 0; i < n; i++) {
      key[i] = referenceContributions.get(i).binningKey(basis);
    }
    List<Double> boundaries = new ArrayList<>(n - 1);
    double prev = Double.isFinite(key[0]) && Double.isFinite(key[1]) ? key[0] - 0.5 * (key[1] - key[0]) : key[0];
    for (int i = 0; i < n - 1; i++) {
      double midpoint = Double.isFinite(key[i]) && Double.isFinite(key[i + 1]) ? 0.5 * (key[i] + key[i + 1])
	  : Math.max(key[i], key[i + 1]);
      double candidate = 2.0 * key[i] - prev;
      double boundary;
      if (Double.isFinite(candidate) && candidate > key[i] && candidate < key[i + 1]) {
	boundary = candidate;
      } else {
	boundary = midpoint;
      }
      if (!boundaries.isEmpty() && boundary <= boundaries.get(boundaries.size() - 1)) {
	boundary = midpoint;
      }
      boundaries.add(boundary);
      prev = boundary;
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
   * @param basis the binning key on which the equal-mass cut points and clamping bounds are evaluated
   * @param gammaScope scope of the Whitson gamma fit used to rebuild the reference imaginary composition
   * @param conservation quantity conserved when rebuilding the reference imaginary composition
   * @return the clamped equal-mass cut boundaries (size {@code referenceContributions.size() - 1})
   */
  private static List<Double> determineReferenceEqualMassBoundaries(
      List<PseudoComponentContribution> referenceContributions, int resolution, DelumpBinningBasis basis,
      DelumpGammaScope gammaScope, DelumpConservation conservation) {
    int cuts = referenceContributions.size() - 1;
    if (cuts < 1) {
      return Collections.emptyList();
    }
    if (resolution <= 1) {
      return determineReferenceBoundaries(referenceContributions, basis, ReferenceBoundaryMode.MIDPOINT);
    }

    List<PseudoComponentContribution> imaginary = delumpContributions(referenceContributions, resolution, gammaScope,
	conservation);
    List<Double> equalMass = determineQuantileBoundaries(imaginary, referenceContributions.size(), basis);

    List<Double> boundaries = new ArrayList<>(cuts);
    for (int i = 0; i < cuts; i++) {
      double lower = referenceContributions.get(i).binningKey(basis);
      double upper = referenceContributions.get(i + 1).binningKey(basis);
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

  /**
   * Distribute contributions onto a fixed number of groups using an arbitrary binning basis.
   *
   * <p>
   * Identical to {@link #distributeToProfiles(List, List, int)} but the contributions are binned on the requested
   * {@link DelumpBinningBasis} key. With {@link DelumpBinningBasis#BOILING_POINT} the legacy boiling-point sorting key
   * and the original (assumed pre-sorted) behaviour are retained exactly; with {@link DelumpBinningBasis#MOLAR_MASS} a
   * copy is first sorted ascending by molar mass so the monotonic boundary scan bins on molar mass.
   *
   * @param contributions the contributions to distribute
   * @param boundaries the ascending cut boundaries on the same key as {@code basis}
   * @param targetPseudoComponents the number of groups
   * @param basis the binning key
   * @return one profile per group (size {@code targetPseudoComponents})
   */
  private static List<PseudoComponentProfile> distributeToProfiles(List<PseudoComponentContribution> contributions,
      List<Double> boundaries, int targetPseudoComponents, DelumpBinningBasis basis) {
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

    List<PseudoComponentContribution> ordered = contributions;
    if (basis != DelumpBinningBasis.BOILING_POINT) {
      ordered = new ArrayList<>(contributions);
      ordered.sort(Comparator.comparingDouble(c -> c.binningKey(basis)));
    }

    int groupIndex = 0;
    double currentBoundary = boundaries.isEmpty() ? Double.POSITIVE_INFINITY : boundaries.get(0);

    for (PseudoComponentContribution contribution : ordered) {
      double key = contribution.binningKey(basis);
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
     * Return the key on which this contribution is binned for the requested basis. The molar-mass basis returns the
     * molar mass directly (the conserved, monotonic quantity); the boiling-point basis returns the legacy
     * {@link #sortingKey()} (normal boiling point, falling back to molar mass).
     *
     * @param basis the binning basis
     * @return the binning key value
     */
    private double binningKey(DelumpBinningBasis basis) {
      if (basis == DelumpBinningBasis.MOLAR_MASS) {
	return molarMass;
      }
      return sortingKey();
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
