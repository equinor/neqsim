package neqsim.process.equipment.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.filter.SulfurFilterMechanicalDesign;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.nucleation.ClassicalNucleationTheory;

/**
 * Sulfur filter for removing solid elemental sulfur (S8) from gas streams.
 *
 * <p>
 * This equipment models a cartridge or coalescing filter designed to capture solid S8 particles
 * that form when pipeline gas is cooled below the sulfur solubility limit (e.g., during
 * Joule-Thomson letdown). It extends the basic {@link Filter} class with solid-phase removal
 * tracking, filter capacity calculations, and maintenance interval estimation.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Performs TP-solid flash on inlet to detect solid S8 phase</li>
 * <li>Removes solid S8 from the outlet stream based on removal efficiency</li>
 * <li>Tracks cumulative S8 mass loading (kg/hr) for filter element sizing</li>
 * <li>Calculates filter change interval based on element capacity</li>
 * <li>Integrates with {@link SulfurFilterMechanicalDesign} for vessel sizing and cost</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * SulfurFilter filter = new SulfurFilter("S8 Filter", valveOutletStream);
 * filter.setRemovalEfficiency(0.99); // 99% solid removal
 * filter.setFilterElementCapacity(50.0); // 50 kg S8 per element set
 * filter.setDeltaP(0.5); // 0.5 bar clean pressure drop
 * filter.run();
 *
 * double s8Rate = filter.getSolidSulfurRemovalRate(); // kg/hr removed
 * double interval = filter.getChangeIntervalHours(); // hours until change
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class SulfurFilter extends Filter {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Removal efficiency for solid S8 particles (0 to 1). Default 0.99 (99%). */
  private double removalEfficiency = 0.99;

  /** Filter element capacity in kg of S8 before replacement required. */
  private double filterElementCapacity = 50.0;

  /** Number of filter elements installed in parallel. Default 6. */
  private int numberOfElements = 6;

  /** Solid S8 removal rate calculated during run (kg/hr). */
  private double solidSulfurRemovalRate = 0.0;

  /** Total S8 mass fraction in inlet gas that is in solid phase. */
  private double solidS8MassFractionInlet = 0.0;

  /** Mass flow rate of gas through the filter (kg/hr). */
  private double gasFlowRate = 0.0;

  /** Whether solid S8 was detected in the inlet stream. */
  private boolean solidS8Detected = false;

  /** Filter type description. */
  private String filterType = "Cartridge";

  /** Design filtration rating in micrometres. Default 10. */
  private double filtrationRating = 10.0;

  /** Mechanical design for this filter. */
  private SulfurFilterMechanicalDesign sulfurFilterMechanicalDesign;

  /** Nucleation model for particle size prediction. */
  private transient ClassicalNucleationTheory nucleationModel;

  /**
   * Residence time in the supersaturated zone (seconds). Used for particle size prediction. Default
   * 2.0 s (typical valve-to-filter transit at 5-10 m/s over 10-20 m pipe).
   */
  private double residenceTime = 2.0;

  /**
   * Supersaturation ratio of S8 at inlet conditions. Calculated from flash results or settable.
   */
  private double supersaturationRatio = 1.0;

  /**
   * Creates a new SulfurFilter.
   *
   * @param name name of the sulfur filter
   * @param inStream inlet gas stream (may contain solid S8)
   */
  public SulfurFilter(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();

    // Apply pressure drop
    if (Math.abs(getDeltaP()) > 1e-10) {
      system.setPressure(inStream.getPressure() - getDeltaP());
    }

    // Run TP flash with solid phase check to detect solid S8
    system.setMultiPhaseCheck(true);
    if (system.hasComponent("S8")) {
      system.setSolidPhaseCheck("S8");
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    gasFlowRate = inStream.getFlowRate("kg/hr");
    solidS8Detected = false;
    solidSulfurRemovalRate = 0.0;
    solidS8MassFractionInlet = 0.0;

    // Check if solid phase exists and contains S8
    if (system.hasPhaseType(PhaseType.SOLID) && system.hasComponent("S8")) {
      solidS8Detected = true;

      // Find the solid phase and get S8 mass
      for (int phaseIdx = 0; phaseIdx < system.getNumberOfPhases(); phaseIdx++) {
        if (system.getPhase(phaseIdx).getType() == PhaseType.SOLID) {
          double solidPhaseMass = system.getPhase(phaseIdx).getMass();
          // S8 mass fraction in the total stream that is solid
          double totalMass = system.getMass("kg");
          if (totalMass > 0) {
            solidS8MassFractionInlet = solidPhaseMass / totalMass;
          }
          break;
        }
      }

      // Calculate removal rate: mass flow * solid fraction * efficiency
      solidSulfurRemovalRate = gasFlowRate * solidS8MassFractionInlet * removalEfficiency;

      // Remove solid S8 from outlet: reduce S8 component by removal efficiency
      // Set S8 content in outlet to only the fraction that passes through
      if (system.hasComponent("S8") && removalEfficiency > 0) {
        // Get current S8 moles in gas phase and reduce total S8
        double currentS8Moles = 0.0;
        double solidS8Moles = 0.0;
        for (int phaseIdx = 0; phaseIdx < system.getNumberOfPhases(); phaseIdx++) {
          if (system.getPhase(phaseIdx).getType() == PhaseType.SOLID) {
            solidS8Moles = system.getPhase(phaseIdx).getComponent("S8").getNumberOfmoles();
          }
          currentS8Moles += system.getPhase(phaseIdx).getComponent("S8").getNumberOfmoles();
        }

        // Remove solid S8 from the system (filter captures it)
        double molesToRemove = solidS8Moles * removalEfficiency;
        if (molesToRemove > 0 && currentS8Moles > molesToRemove) {
          double remainingMoles = currentS8Moles - molesToRemove;
          system.addComponent("S8", -molesToRemove);
        }

        // Re-flash without solid check to get clean gas outlet
        ops.TPflash();
        system.initProperties();
      }
    }

    outStream.setThermoSystem(system);
    setCalculationIdentifier(id);

    // Run particle size prediction using CNT
    runParticleSizePrediction(system);
  }

  /**
   * Runs the Classical Nucleation Theory model to predict sulfur particle sizes.
   *
   * <p>
   * Uses the S8 supersaturation and process conditions to predict mean particle diameter,
   * nucleation rate, and particle size distribution. Results are available via
   * {@link #getNucleationModel()}.
   * </p>
   *
   * @param system the thermodynamic system after flash calculation
   */
  private void runParticleSizePrediction(SystemInterface system) {
    nucleationModel = ClassicalNucleationTheory.sulfurS8();
    nucleationModel.setTemperature(system.getTemperature());
    nucleationModel.setTotalPressure(system.getPressure() * 1e5); // bara to Pa

    // Estimate S8 supersaturation from solid phase fraction
    if (solidS8Detected && solidS8MassFractionInlet > 0) {
      // Approximate supersaturation: ratio of total S8 to soluble S8
      // Higher solid fraction => higher supersaturation
      double totalS8moles = 0;
      double solidS8moles = 0;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        if (system.getPhase(i).hasComponent("S8")) {
          double moles = system.getPhase(i).getComponent("S8").getNumberOfmoles();
          totalS8moles += moles;
          if (system.getPhase(i).getType() == PhaseType.SOLID) {
            solidS8moles = moles;
          }
        }
      }
      double gasS8moles = totalS8moles - solidS8moles;
      if (gasS8moles > 0 && solidS8moles > 0) {
        supersaturationRatio = totalS8moles / gasS8moles;
      } else if (solidS8moles > 0) {
        supersaturationRatio = 100.0; // very high if all S8 is solid
      }
    }
    nucleationModel.setSupersaturationRatio(supersaturationRatio);

    // Gas transport properties
    try {
      double gasVisc = system.getPhase("gas").getViscosity("kg/msec");
      if (gasVisc > 0) {
        nucleationModel.setGasViscosity(gasVisc);
      }
    } catch (Exception ex) {
      nucleationModel.setGasViscosity(1.0e-5);
    }

    // S8 diffusivity in natural gas: estimate ~2-5e-6 m2/s
    nucleationModel.setGasDiffusivity(3.0e-6);
    nucleationModel.setCarrierGasMolarMass(system.getMolarMass("kg/mol"));
    nucleationModel.setResidenceTime(residenceTime);

    nucleationModel.calculate();
  }

  /**
   * Returns the solid S8 removal rate in kg/hr.
   *
   * @return solid sulfur removal rate in kg/hr
   */
  public double getSolidSulfurRemovalRate() {
    return solidSulfurRemovalRate;
  }

  /**
   * Returns the solid S8 removal rate in the specified unit.
   *
   * @param unit the mass rate unit ("kg/hr", "kg/day", "g/hr")
   * @return solid sulfur removal rate in specified unit
   */
  public double getSolidSulfurRemovalRate(String unit) {
    if ("kg/day".equals(unit)) {
      return solidSulfurRemovalRate * 24.0;
    } else if ("g/hr".equals(unit)) {
      return solidSulfurRemovalRate * 1000.0;
    }
    return solidSulfurRemovalRate;
  }

  /**
   * Returns the estimated filter element change interval in hours.
   *
   * <p>
   * Calculated as: total element capacity / removal rate.
   * </p>
   *
   * @return change interval in hours, or Double.MAX_VALUE if no S8 removal
   */
  public double getChangeIntervalHours() {
    if (solidSulfurRemovalRate <= 0.0) {
      return Double.MAX_VALUE;
    }
    return (filterElementCapacity * numberOfElements) / solidSulfurRemovalRate;
  }

  /**
   * Returns the estimated filter element change interval in days.
   *
   * @return change interval in days
   */
  public double getChangeIntervalDays() {
    return getChangeIntervalHours() / 24.0;
  }

  /**
   * Returns whether solid S8 was detected in the inlet stream.
   *
   * @return true if solid S8 phase detected
   */
  public boolean isSolidS8Detected() {
    return solidS8Detected;
  }

  /**
   * Sets the solid S8 removal efficiency.
   *
   * @param efficiency removal efficiency (0.0 to 1.0), typically 0.95 to 0.999
   */
  public void setRemovalEfficiency(double efficiency) {
    this.removalEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Returns the solid S8 removal efficiency.
   *
   * @return removal efficiency (0 to 1)
   */
  public double getRemovalEfficiency() {
    return removalEfficiency;
  }

  /**
   * Sets the filter element S8 holding capacity.
   *
   * @param capacityKg capacity in kg of solid S8 per element set
   */
  public void setFilterElementCapacity(double capacityKg) {
    this.filterElementCapacity = capacityKg;
  }

  /**
   * Returns the filter element S8 holding capacity.
   *
   * @return capacity in kg per element
   */
  public double getFilterElementCapacity() {
    return filterElementCapacity;
  }

  /**
   * Sets the number of filter elements.
   *
   * @param count number of filter elements
   */
  public void setNumberOfElements(int count) {
    this.numberOfElements = Math.max(1, count);
  }

  /**
   * Returns the number of filter elements.
   *
   * @return number of elements
   */
  public int getNumberOfElements() {
    return numberOfElements;
  }

  /**
   * Sets the filter type.
   *
   * @param type filter type ("Cartridge", "Coalescing", "Mesh Pad", "Bag")
   */
  public void setFilterType(String type) {
    this.filterType = type;
  }

  /**
   * Returns the filter type.
   *
   * @return filter type string
   */
  public String getFilterType() {
    return filterType;
  }

  /**
   * Sets the filtration rating.
   *
   * @param ratingMicrons filtration rating in micrometres (1 to 100 typical)
   */
  public void setFiltrationRating(double ratingMicrons) {
    this.filtrationRating = ratingMicrons;
  }

  /**
   * Returns the filtration rating in micrometres.
   *
   * @return filtration rating
   */
  public double getFiltrationRating() {
    return filtrationRating;
  }

  /**
   * Returns the gas mass flow rate through the filter.
   *
   * @return mass flow rate in kg/hr
   */
  public double getGasFlowRate() {
    return gasFlowRate;
  }

  /**
   * Returns the solid S8 mass fraction detected in the inlet.
   *
   * @return mass fraction of solid S8 in inlet
   */
  public double getSolidS8MassFractionInlet() {
    return solidS8MassFractionInlet;
  }

  /**
   * Sets the residence time in the supersaturated zone used for particle size prediction.
   *
   * @param seconds residence time in seconds (typical 0.5-10 s)
   */
  public void setResidenceTime(double seconds) {
    this.residenceTime = Math.max(0.01, seconds);
  }

  /**
   * Returns the residence time used for particle size prediction.
   *
   * @return residence time in seconds
   */
  public double getResidenceTime() {
    return residenceTime;
  }

  /**
   * Returns the Classical Nucleation Theory model with particle size results. Available after
   * {@code run()} has been called.
   *
   * @return the nucleation model, or null if not yet calculated
   */
  public ClassicalNucleationTheory getNucleationModel() {
    return nucleationModel;
  }

  /**
   * Returns the predicted mean particle diameter in micrometres.
   *
   * @return mean particle diameter in um, or 0.0 if no solid detected
   */
  public double getMeanParticleDiameterMicrons() {
    if (nucleationModel != null && nucleationModel.isCalculated()) {
      return nucleationModel.getMeanParticleDiameter("um");
    }
    return 0.0;
  }

  /**
   * Returns the predicted particle size percentiles (d10, d50, d90) in micrometres.
   *
   * @return array [d10, d50, d90] in um, or zeros if not calculated
   */
  public double[] getParticleSizePercentilesUM() {
    if (nucleationModel != null && nucleationModel.isCalculated()) {
      double[] pctiles = nucleationModel.getParticleSizePercentiles();
      return new double[] {pctiles[0] * 1e6, pctiles[1] * 1e6, pctiles[2] * 1e6};
    }
    return new double[] {0.0, 0.0, 0.0};
  }

  /**
   * Returns the estimated filter capture efficiency for the predicted particle size distribution,
   * based on the current filtration rating.
   *
   * @return capture fraction (0 to 1)
   */
  public double getEstimatedCaptureEfficiency() {
    if (nucleationModel != null && nucleationModel.isCalculated()) {
      return nucleationModel.getFilterCaptureEfficiency(filtrationRating * 1e-6);
    }
    return removalEfficiency;
  }

  /**
   * Returns the S8 supersaturation ratio calculated from the flash.
   *
   * @return supersaturation ratio (S &gt; 1 when supersaturated)
   */
  public double getSupersaturationRatio() {
    return supersaturationRatio;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    sulfurFilterMechanicalDesign = new SulfurFilterMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    if (sulfurFilterMechanicalDesign == null) {
      initMechanicalDesign();
    }
    return sulfurFilterMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("name", getName());
    result.put("type", "SulfurFilter");
    result.put("filterType", filterType);
    result.put("filtrationRating_um", filtrationRating);

    Map<String, Object> operating = new LinkedHashMap<String, Object>();
    if (inStream != null) {
      operating.put("inletPressure_bara", inStream.getPressure("bara"));
      operating.put("inletTemperature_C", inStream.getTemperature("C"));
      operating.put("gasFlowRate_kghr", gasFlowRate);
    }
    operating.put("pressureDrop_bar", getDeltaP());
    result.put("operatingConditions", operating);

    Map<String, Object> s8data = new LinkedHashMap<String, Object>();
    s8data.put("solidS8Detected", solidS8Detected);
    s8data.put("removalEfficiency", removalEfficiency);
    s8data.put("solidS8MassFractionInlet", solidS8MassFractionInlet);
    s8data.put("solidSulfurRemovalRate_kghr", solidSulfurRemovalRate);
    s8data.put("solidSulfurRemovalRate_kgday", solidSulfurRemovalRate * 24.0);
    result.put("sulfurRemoval", s8data);

    Map<String, Object> elements = new LinkedHashMap<String, Object>();
    elements.put("numberOfElements", numberOfElements);
    elements.put("elementCapacity_kg", filterElementCapacity);
    elements.put("totalCapacity_kg", filterElementCapacity * numberOfElements);
    elements.put("changeIntervalHours", getChangeIntervalHours());
    elements.put("changeIntervalDays", getChangeIntervalDays());
    result.put("filterElements", elements);

    // Particle size prediction results
    if (nucleationModel != null && nucleationModel.isCalculated()) {
      Map<String, Object> particles = new LinkedHashMap<String, Object>();
      particles.put("supersaturationRatio", supersaturationRatio);
      particles.put("residenceTime_s", residenceTime);
      particles.put("meanDiameter_um", nucleationModel.getMeanParticleDiameter("um"));
      particles.put("meanDiameter_nm", nucleationModel.getMeanParticleDiameter("nm"));
      double[] pctiles = nucleationModel.getParticleSizePercentiles();
      particles.put("d10_um", pctiles[0] * 1e6);
      particles.put("d50_um", pctiles[1] * 1e6);
      particles.put("d90_um", pctiles[2] * 1e6);
      particles.put("nucleationRate_per_m3s", nucleationModel.getNucleationRate());
      particles.put("particleNumberDensity_per_m3", nucleationModel.getParticleNumberDensity());
      particles.put("massConcentration_mg_m3",
          nucleationModel.getParticleMassConcentration() * 1e6);
      particles.put("criticalRadius_nm", nucleationModel.getCriticalRadius() * 1e9);
      particles.put("knudsenNumber", nucleationModel.getKnudsenNumber());
      particles.put("estimatedCaptureEfficiency", getEstimatedCaptureEfficiency());
      result.put("particleSizePrediction", particles);
    }

    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(result);
  }
}
