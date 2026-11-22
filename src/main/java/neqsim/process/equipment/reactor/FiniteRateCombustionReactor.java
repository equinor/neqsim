package neqsim.process.equipment.reactor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.combustion.CombustionKineticsBackend;
import neqsim.thermo.atomelement.Element;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Detailed finite-rate chemistry connected to a NeqSim flowsheet through an optional solver backend.
 *
 * <p>
 * The inlet is an already mixed reactant or post-flame stream. Chemistry and the energy balance belong to the backend
 * mechanism. The outlet stream is an explicitly bounded projection onto NeqSim database components; the full mechanism
 * state is retained separately, including radicals and intermediates. No equilibrium flash or emission factor replaces
 * the backend species. Backend objects are transient and must be reattached after Java deserialization. NeqSim stream
 * enthalpy is not used to claim closure of the mechanism energy balance.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FiniteRateCombustionReactor extends TwoPortEquipment {
  private static final long serialVersionUID = 1L;
  private transient CombustionKineticsBackend backend;
  private ReactorModel reactorModel = ReactorModel.CONSTANT_PRESSURE_PFR;
  private double residenceTimeSeconds = Double.NaN;
  private double ignitionTemperatureK = Double.NaN;
  private double heatLossCoefficientWPerKgK = 0.0;
  private double surroundingsTemperatureK = 298.15;
  private double projectionTolerance = 1.0e-6;
  private String resultJson;
  private boolean outletProjectionValid;
  private double projectionMassFraction;
  private double projectedEosMassResidual;
  /** Whether execution has been attempted, to prevent reuse of a stale outlet after failure. */
  private boolean executionAttempted;
  /** Maximum accepted relative mechanism energy residual. */
  private double energyBalanceTolerance = 1.0e-5;

  /** Ideal homogeneous reactor models supported by the backend contract. */
  public enum ReactorModel {
    /** Constant-pressure finite-age parcel, equivalent to a homogeneous ideal PFR. */
    CONSTANT_PRESSURE_PFR,
    /** Steady perfectly stirred reactor with a true inlet and an independently initialized ignition state. */
    PERFECTLY_STIRRED
  }

  /**
   * Create a reactor with a mixed inlet stream.
   *
   * @param name equipment name
   * @param inlet mixed inlet stream
   */
  public FiniteRateCombustionReactor(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  /**
   * Attach a detailed kinetics backend. No backend is selected implicitly.
   *
   * @param solver mechanism solver
   */
  public void setKineticsBackend(CombustionKineticsBackend solver) {
    if (solver == null) {
      throw new IllegalArgumentException("A non-null detailed kinetics backend is required");
    }
    backend = solver;
  }

  /**
   * Set the ideal reactor model.
   *
   * @param model reactor model
   */
  public void setReactorModel(ReactorModel model) {
    if (model == null) {
      throw new IllegalArgumentException("Reactor model must be specified");
    }
    reactorModel = model;
  }

  /**
   * Set finite parcel age or steady PSR mass residence time.
   *
   * @param seconds positive time [s]
   */
  public void setResidenceTime(double seconds) {
    requirePositive(seconds, "Residence time");
    residenceTimeSeconds = seconds;
  }

  /**
   * Set the initial PSR temperature used to select an ignition branch; the inlet temperature is unchanged. This is an
   * initialization input and does not impose the converged temperature or sustained combustion.
   *
   * @param kelvin positive initial PSR temperature [K]
   */
  public void setIgnitionTemperature(double kelvin) {
    requirePositive(kelvin, "Ignition temperature");
    ignitionTemperatureK = kelvin;
  }

  /**
   * Set physical heat removal per instantaneous reactor mass, with positive heat flowing to the surroundings. The
   * backend uses {@code Q = coefficient * mass * (T - surroundingsTemperature)}.
   *
   * @param coefficient nonnegative coefficient [W/(kg K)], zero for adiabatic operation
   * @param kelvin positive surroundings temperature [K]
   */
  public void setHeatLoss(double coefficient, double kelvin) {
    if (!Double.isFinite(coefficient) || coefficient < 0.0) {
      throw new IllegalArgumentException("Heat-loss coefficient must be finite and nonnegative");
    }
    requirePositive(kelvin, "Surroundings temperature");
    heatLossCoefficientWPerKgK = coefficient;
    surroundingsTemperatureK = kelvin;
  }

  /**
   * Set the maximum fraction of total mass and of each inlet element allowed to be absent from the EOS projection.
   * Missing species remain in the exact mechanism result and are never renormalized into mapped species.
   *
   * @param fraction finite fraction from zero up to, but excluding, one
   */
  public void setProjectionTolerance(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction >= 1.0) {
      throw new IllegalArgumentException("Projection tolerance must be in [0, 1)");
    }
    projectionTolerance = fraction;
  }

  /**
   * Set the acceptance limit for independently calculated mechanism energy closure.
   *
   * @param tolerance finite positive relative residual, less than one
   */
  public void setEnergyBalanceTolerance(double tolerance) {
    requirePositive(tolerance, "Energy-balance tolerance");
    if (tolerance >= 1.0) {
      throw new IllegalArgumentException("Energy-balance tolerance must be less than one");
    }
    energyBalanceTolerance = tolerance;
  }

  /**
   * Return the projected outlet, rejecting a stale stream after any failed execution. The initial outlet remains
   * accessible when constructing a flowsheet before the first run.
   *
   * @return accepted outlet or initial flowsheet connection stream
   */
  @Override
  public StreamInterface getOutletStream() {
    if (executionAttempted && !outletProjectionValid) {
      throw new IllegalStateException("No accepted kinetics outlet exists after the latest execution");
    }
    return super.getOutletStream();
  }

  /**
   * Get the exact backend state, including species absent from the EOS projection, after a solve. It is also retained
   * after a projection rejection, but not after a backend failure.
   *
   * @return versioned detailed result JSON, or null before a backend returns a result
   */
  public String getKineticsResultJson() {
    return resultJson;
  }

  /**
   * Report whether the most recent execution produced an accepted EOS projection.
   *
   * @return true after successful conservation and projection checks
   */
  public boolean isOutletProjectionValid() {
    return outletProjectionValid;
  }

  /**
   * Let multi-port subclasses distinguish initial connection streams from failed calculation results.
   *
   * @return whether a calculation has been attempted
   */
  protected boolean hasExecutionBeenAttempted() {
    return executionAttempted;
  }

  /**
   * Invalidate result streams before a subclass performs mixing or utility calculations that may fail. Exact mechanism
   * diagnostics are retained for inspection until the next backend execution.
   */
  protected void invalidateOutletProjection() {
    executionAttempted = true;
    outletProjectionValid = false;
  }

  /**
   * Get the unmapped exact-mechanism mass fraction.
   *
   * @return discarded projection mass divided by total mechanism outlet mass
   */
  public double getUnmappedMassFraction() {
    return projectionMassFraction;
  }

  /**
   * Get the separate EOS mass residual, including the molecular-mass conventions of the two packages.
   *
   * @return (projected NeqSim outlet mass minus NeqSim inlet mass) divided by inlet mass
   */
  public double getProjectedEosMassResidual() {
    return projectedEosMassResidual;
  }

  /**
   * Get an exact mechanism species molar flow.
   *
   * @param species mechanism species name, for example CO or C3H8
   * @return molar flow [mol/s], including zero for a supported species with zero calculated flow
   */
  public double getSpeciesMolarFlow(String species) {
    if (resultJson == null) {
      throw new IllegalStateException("No kinetics result is available");
    }
    JsonObject flows = parseObject(resultJson).getAsJsonObject("speciesMolarFlows");
    if (!flows.has(species)) {
      throw new IllegalArgumentException("Species is unavailable in the selected mechanism: " + species);
    }
    return number(flows, species);
  }

  /**
   * Distinguish an unsupported chemical species from a supported species with zero calculated flow.
   *
   * @param species exact mechanism species name
   * @return whether the latest detailed mechanism state includes the species
   */
  public boolean isSpeciesAvailable(String species) {
    if (resultJson == null) {
      throw new IllegalStateException("No kinetics result is available");
    }
    return parseObject(resultJson).getAsJsonObject("speciesMolarFlows").has(species);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult validation = super.validateSetup();
    if (backend == null) {
      validation.addError("kinetics", "No detailed kinetics backend is attached",
          "Call setKineticsBackend; reattach transient backends after deserialization");
    }
    if (!Double.isFinite(residenceTimeSeconds) || residenceTimeSeconds <= 0.0) {
      validation.addError("kinetics", "Residence time is not positive", "Call setResidenceTime in seconds");
    }
    if (inStream != null && inStream.getThermoSystem() != null && inStream.getThermoSystem().isChemicalSystem()) {
      validation.addError("kinetics", "Chemical-equilibrium inlet cannot preserve a finite-rate projection",
          "Use a nonreactive EOS inlet; the detailed kinetics backend owns all chemical reactions");
    }
    return validation;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    invalidateOutletProjection();
    resultJson = null;
    projectionMassFraction = Double.NaN;
    projectedEosMassResidual = Double.NaN;
    if (!validateSetup().isValid()) {
      throw new IllegalStateException(validateSetup().getReport());
    }
    SystemInterface inlet = inStream.getThermoSystem();
    JsonObject request = createRequest(inlet);
    JsonObject inletFlows = request.getAsJsonObject("componentMolarFlows");
    String returned = backend.solve(request.toString());
    JsonObject result = parseObject(returned);
    resultJson = returned;
    if (number(result, "schemaVersion") != 1.0 || !result.has("converged") || !result.get("converged").getAsBoolean()) {
      throw new IllegalStateException("Backend schema or solver convergence is invalid");
    }
    if (!text(request, "reactorModel").equals(text(result, "reactorModel"))) {
      throw new IllegalStateException("Backend result does not describe the requested reactor model");
    }
    verifyEnergyAndProvenance(result);
    double temperature = number(result, "temperatureK");
    double pressurePa = number(result, "pressurePa");
    requirePositive(temperature, "Backend temperature");
    requirePositive(pressurePa, "Backend pressure");
    if (Math.abs(pressurePa / (inlet.getPressure() * 100000.0) - 1.0) > 1.0e-5) {
      throw new IllegalStateException("Backend did not preserve prescribed absolute pressure");
    }
    JsonObject speciesFlows = result.getAsJsonObject("speciesMolarFlows");
    JsonObject molecularMasses = result.getAsJsonObject("speciesMolecularMassesKgPerMol");
    JsonObject speciesAtoms = result.getAsJsonObject("speciesAtomCounts");
    JsonObject mapping = result.getAsJsonObject("componentToSpecies");
    verifyMapping(inlet, inletFlows, speciesFlows, mapping, molecularMasses, speciesAtoms);
    Map<String, Double> inletElements = new HashMap<>();
    double inletMechanismMass = accumulate(inletFlows, mapping, molecularMasses, speciesAtoms, inletElements);
    requirePositive(inletMechanismMass, "Mechanism inlet mass flow");
    Map<String, Double> outletElements = new HashMap<>();
    double outletMechanismMass = accumulate(speciesFlows, null, molecularMasses, speciesAtoms, outletElements);
    if (Math.abs(outletMechanismMass / inletMechanismMass - 1.0) > 1.0e-7) {
      throw new IllegalStateException("Detailed mechanism outlet does not conserve mass");
    }
    verifyElements(inletElements, outletElements, 1.0e-7, "Detailed mechanism");
    JsonObject projectedFlows = new JsonObject();
    Map<String, Double> projectedElements = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
      String species = entry.getValue().getAsString();
      if (speciesFlows.has(species)) {
        projectedFlows.addProperty(entry.getKey(), number(speciesFlows, species));
      }
    }
    double projectedMass = accumulate(projectedFlows, mapping, molecularMasses, speciesAtoms, projectedElements);
    projectionMassFraction = Math.max(0.0, 1.0 - projectedMass / outletMechanismMass);
    if (projectionMassFraction > projectionTolerance + 1.0e-12) {
      throw new IllegalStateException(
          "Unmapped species exceed the requested mass projection tolerance; inspect exact result");
    }
    verifyElements(inletElements, projectedElements, projectionTolerance + 1.0e-10, "EOS projection");
    SystemInterface projected = inlet.clone();
    projected.setEmptyFluid();
    for (Map.Entry<String, JsonElement> entry : projectedFlows.entrySet()) {
      double flow = entry.getValue().getAsDouble();
      if (flow > 0.0) {
        projected.addComponent(entry.getKey(), flow, "mole/sec");
      }
    }
    projected.setTemperature(temperature);
    projected.setPressure(pressurePa / 100000.0);
    projected.createDatabase(true);
    projected.init(0);
    projectedEosMassResidual = projected.getFlowRate("kg/sec") / inlet.getFlowRate("kg/sec") - 1.0;
    if (!Double.isFinite(projectedEosMassResidual)
        || Math.abs(projectedEosMassResidual) > projectionTolerance + 1.0e-3) {
      throw new IllegalStateException(
          "EOS projection mass differs beyond molecular-mass conventions and projection tolerance");
    }
    outStream.setThermoSystem(projected);
    outStream.run(id);
    outletProjectionValid = true;
    setCalculationIdentifier(id);
  }

  /**
   * Build a version-1 request. Subclasses can add network geometry and stream-routing fields.
   *
   * @param inlet nonreactive EOS inlet containing the exact combined component flows
   * @return mechanism request with explicit SI units
   */
  protected JsonObject createRequest(SystemInterface inlet) {
    JsonObject request = new JsonObject();
    request.addProperty("schemaVersion", 1);
    request.addProperty("reactorModel", reactorModel.name());
    request.addProperty("temperatureK", inlet.getTemperature());
    request.addProperty("pressurePa", inlet.getPressure() * 100000.0);
    request.addProperty("residenceTimeSeconds", residenceTimeSeconds);
    request.addProperty("heatLossCoefficientWPerKgK", heatLossCoefficientWPerKgK);
    request.addProperty("surroundingsTemperatureK", surroundingsTemperatureK);
    if (Double.isFinite(ignitionTemperatureK)) {
      request.addProperty("ignitionTemperatureK", ignitionTemperatureK);
    }
    JsonObject inletFlows = new JsonObject();
    for (int i = 0; i < inlet.getNumberOfComponents(); i++) {
      double flow = inlet.getComponent(i).getFlowRate("mole/sec");
      if (!Double.isFinite(flow) || flow < 0.0) {
        throw new IllegalArgumentException("Inlet component flows must be finite and nonnegative");
      }
      if (flow > 0.0) {
        inletFlows.addProperty(inlet.getComponent(i).getComponentName(), flow);
      }
    }
    request.add("componentMolarFlows", inletFlows);
    return request;
  }

  /**
   * Independently verify component aliases against the NeqSim element database. Molecular masses allow a small
   * discrepancy between package atomic-mass conventions.
   *
   * @param inlet nonreactive EOS inlet
   * @param inletFlows requested component flows
   * @param outletFlows exact species flows
   * @param mapping component-to-species aliases
   * @param masses mechanism molecular masses
   * @param atoms mechanism species atom counts
   */
  private static void verifyMapping(SystemInterface inlet, JsonObject inletFlows, JsonObject outletFlows,
      JsonObject mapping, JsonObject masses, JsonObject atoms) {
    if (mapping == null || inletFlows == null || outletFlows == null || atoms == null) {
      throw new IllegalStateException("Missing species or component mapping contract");
    }
    Set<String> mappedSpecies = new HashSet<>();
    for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
      String species = text(mapping, entry.getKey());
      if (!mappedSpecies.add(species)) {
        throw new IllegalStateException("Multiple EOS aliases map to the same mechanism species: " + species);
      }
      boolean inputPresent = inletFlows.has(entry.getKey()) && number(inletFlows, entry.getKey()) > 0.0;
      boolean outputPresent = outletFlows.has(species) && number(outletFlows, species) > 0.0;
      if (!inputPresent && !outputPresent) {
        continue;
      }
      Element database = new Element(entry.getKey());
      String[] names = database.getElementNames();
      double[] coefficients = database.getElementCoefs();
      if (names == null || coefficients == null || names.length == 0) {
        throw new IllegalStateException("No independent EOS elemental data for component " + entry.getKey());
      }
      Map<String, Double> expected = new HashMap<>();
      for (int i = 0; i < names.length; i++) {
        expected.put(names[i], coefficients[i]);
      }
      JsonObject mechanismAtoms = atoms.getAsJsonObject(species);
      if (mechanismAtoms == null) {
        throw new IllegalStateException("Missing atom counts for mapped species " + species);
      }
      Map<String, Double> reported = new HashMap<>();
      for (Map.Entry<String, JsonElement> atom : mechanismAtoms.entrySet()) {
        reported.put(atom.getKey(), number(mechanismAtoms, atom.getKey()));
      }
      verifyElements(expected, reported, 1.0e-12, "Component alias " + entry.getKey());
      if (inputPresent) {
        double databaseMass = inlet.getComponent(entry.getKey()).getMolarMass();
        if (Math.abs(number(masses, species) / databaseMass - 1.0) > 1.0e-3) {
          throw new IllegalStateException("Mechanism molecular mass disagrees with EOS component " + entry.getKey());
        }
      }
    }
    for (Map.Entry<String, JsonElement> entry : inletFlows.entrySet()) {
      if (!mapping.has(entry.getKey())) {
        throw new IllegalStateException("Inlet component is absent from mechanism mapping: " + entry.getKey());
      }
    }
  }

  /**
   * Gate physical heat transfer, energy residuals and mechanism provenance before accepting any stream.
   *
   * @param result exact backend result
   */
  private void verifyEnergyAndProvenance(JsonObject result) {
    number(result, "heatTransferToSurroundingsW");
    number(result, "mechanismInletEnthalpyJPerKg");
    number(result, "mechanismOutletEnthalpyJPerKg");
    text(result, "energyReference");
    if (Math.abs(number(result, "energyBalanceRelativeResidual")) > energyBalanceTolerance) {
      throw new IllegalStateException("Mechanism energy residual exceeds acceptance tolerance");
    }
    if (result.has("fullEnergyBalanceRelativeResidual")
        && Math.abs(number(result, "fullEnergyBalanceRelativeResidual")) > energyBalanceTolerance) {
      throw new IllegalStateException("Fired-heater energy residual exceeds acceptance tolerance");
    }
    JsonObject provenance = result.getAsJsonObject("provenance");
    text(provenance, "solver");
    text(provenance, "solverVersion");
    text(provenance, "mechanism");
    if (!text(provenance, "resolvedMechanismSha256").matches("[0-9a-fA-F]{64}")) {
      throw new IllegalStateException("Mechanism fingerprint must be an explicit SHA-256 digest");
    }
    requirePositive(number(provenance, "speciesCount"), "Mechanism species count");
    requirePositive(number(provenance, "reactionCount"), "Mechanism reaction count");
  }

  /**
   * Calculate mass and elemental molar flows using the mechanism's explicit molecular data.
   *
   * @param flows species or component flows [mol/s]
   * @param mapping component-to-species mapping, or null for mechanism species flows
   * @param masses molecular masses [kg/mol]
   * @param atoms species atom counts
   * @param elements destination elemental flows [mol atoms/s]
   * @return total mass flow [kg/s]
   */
  private static double accumulate(JsonObject flows, JsonObject mapping, JsonObject masses, JsonObject atoms,
      Map<String, Double> elements) {
    double mass = 0.0;
    for (Map.Entry<String, JsonElement> entry : flows.entrySet()) {
      String species = mapping == null ? entry.getKey() : mapping.get(entry.getKey()).getAsString();
      double flow = number(flows, entry.getKey());
      double molecularMass = number(masses, species);
      if (flow < 0.0 || molecularMass <= 0.0) {
        throw new IllegalStateException("Species flows and molecular data must be physical");
      }
      mass += flow * molecularMass;
      JsonObject counts = atoms.getAsJsonObject(species);
      for (Map.Entry<String, JsonElement> atom : counts.entrySet()) {
        double count = number(counts, atom.getKey());
        if (count < 0.0) {
          throw new IllegalStateException("Atom counts must be nonnegative");
        }
        elements.put(atom.getKey(), elements.getOrDefault(atom.getKey(), 0.0) + flow * count);
      }
    }
    return mass;
  }

  /**
   * Check each element, including elements appearing unexpectedly at the outlet.
   *
   * @param inlet inlet elemental molar flows
   * @param outlet outlet elemental molar flows
   * @param tolerance maximum relative discrepancy for each element
   * @param label check label
   */
  private static void verifyElements(Map<String, Double> inlet, Map<String, Double> outlet, double tolerance,
      String label) {
    Map<String, Double> all = new HashMap<>(outlet);
    all.putAll(inlet);
    for (String element : all.keySet()) {
      double initial = inlet.getOrDefault(element, 0.0);
      double finalFlow = outlet.getOrDefault(element, 0.0);
      if (Math.abs(finalFlow - initial) > tolerance * Math.max(initial, 1.0e-20)) {
        throw new IllegalStateException(label + " does not conserve element " + element + " within tolerance");
      }
    }
  }

  /**
   * Parse a required JSON object.
   *
   * @param json JSON text
   * @return parsed object
   */
  private static JsonObject parseObject(String json) {
    if (json == null) {
      throw new IllegalStateException("Backend returned no result");
    }
    return JsonParser.parseString(json).getAsJsonObject();
  }

  /**
   * Read a required nonempty text field.
   *
   * @param object containing object
   * @param key field name
   * @return nonempty field value
   */
  private static String text(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      throw new IllegalStateException("Missing kinetics text field: " + key);
    }
    String value = object.get(key).getAsString();
    if (value.trim().isEmpty()) {
      throw new IllegalStateException("Empty kinetics text field: " + key);
    }
    return value;
  }

  /**
   * Read a finite numeric contract field.
   *
   * @param object containing object
   * @param key field name
   * @return finite field value
   */
  private static double number(JsonObject object, String key) {
    if (object == null || !object.has(key)) {
      throw new IllegalStateException("Missing kinetics contract field: " + key);
    }
    double value = object.get(key).getAsDouble();
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Nonfinite kinetics contract field: " + key);
    }
    return value;
  }

  /**
   * Require a finite positive physical input.
   *
   * @param value input value
   * @param label input label
   */
  private static void requirePositive(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be finite and positive");
    }
  }
}
