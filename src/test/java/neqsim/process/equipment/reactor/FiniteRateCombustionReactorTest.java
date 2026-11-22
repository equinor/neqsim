package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.util.combustion.CombustionKineticsBackend;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Boundary acceptance tests; these do not qualify a chemical mechanism or industrial furnace.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FiniteRateCombustionReactorTest {
  /** Verify an exact identity state can be projected onto a nonreactive EOS stream. */
  @Test
  void preservesAcceptedExactState() {
    FiniteRateCombustionReactor reactor = reactor(request -> identity(request).toString());
    reactor.run(UUID.randomUUID());
    assertTrue(reactor.isOutletProjectionValid());
    assertEquals(1.0, reactor.getSpeciesMolarFlow("CH4"), 1.0e-10);
    assertEquals(0.0, reactor.getUnmappedMassFraction(), 1.0e-10);
    assertEquals(1.0, reactor.getOutletStream().getThermoSystem().getFlowRate("mole/sec"), 1.0e-10);
  }

  /** Unsupported pollutant species must not be exposed as calculated zero emissions. */
  @Test
  void distinguishesUnsupportedPollutantsFromZeroEmissions() {
    FiniteRateCombustionReactor reactor = reactor(request -> identity(request).toString());
    reactor.run(UUID.randomUUID());
    assertTrue(reactor.isSpeciesAvailable("CH4"));
    assertFalse(reactor.isSpeciesAvailable("NO"));
    assertThrows(IllegalArgumentException.class, () -> reactor.getSpeciesMolarFlow("NO"));
  }

  /** Reject energy closure failures and make the previous outlet unavailable. */
  @Test
  void rejectsBadEnergyAndStaleOutlet() {
    FiniteRateCombustionReactor reactor = reactor(request -> identity(request).toString());
    reactor.run(UUID.randomUUID());
    reactor.setKineticsBackend(request -> {
      JsonObject result = identity(request);
      result.addProperty("energyBalanceRelativeResidual", 0.02);
      return result.toString();
    });
    assertThrows(IllegalStateException.class, () -> reactor.run(UUID.randomUUID()));
    assertFalse(reactor.isOutletProjectionValid());
    assertThrows(IllegalStateException.class, () -> reactor.getOutletStream());
  }

  /** Reject aliases that conserve the backend's own data but misidentify the EOS molecule. */
  @Test
  void verifiesAliasesAgainstIndependentElementData() {
    FiniteRateCombustionReactor reactor = reactor(request -> {
      JsonObject result = identity(request);
      JsonObject counts = result.getAsJsonObject("speciesAtomCounts").getAsJsonObject("CH4");
      counts.addProperty("C", 2.0);
      counts.addProperty("H", 6.0);
      return result.toString();
    });
    assertThrows(IllegalStateException.class, () -> reactor.run(UUID.randomUUID()));
    assertFalse(reactor.isOutletProjectionValid());
  }

  /** Prevent two component aliases from duplicating the same exact species in the EOS projection. */
  @Test
  void rejectsDuplicateSpeciesAliases() {
    FiniteRateCombustionReactor reactor = reactor(request -> {
      JsonObject result = identity(request);
      result.getAsJsonObject("componentToSpecies").addProperty("ethane", "CH4");
      return result.toString();
    });
    assertThrows(IllegalStateException.class, () -> reactor.run(UUID.randomUUID()));
  }

  /** Reject unprovenanced solver results before they become flowsheet streams. */
  @Test
  void requiresMechanismProvenance() {
    FiniteRateCombustionReactor reactor = reactor(request -> {
      JsonObject result = identity(request);
      result.getAsJsonObject("provenance").remove("resolvedMechanismSha256");
      return result.toString();
    });
    assertThrows(IllegalStateException.class, () -> reactor.run(UUID.randomUUID()));
  }

  /** Reject chemical-equilibrium systems that could overwrite the kinetic species during an outlet flash. */
  @Test
  void rejectsChemicalEquilibriumInlet() {
    FiniteRateCombustionReactor reactor = reactor(request -> identity(request).toString());
    reactor.getInletStream().getThermoSystem().isChemicalSystem(true);
    assertFalse(reactor.validateSetup().isValid());
    assertThrows(IllegalStateException.class, () -> reactor.run(UUID.randomUUID()));
  }

  /**
   * Construct the boundary with a known pure-component EOS inlet.
   *
   * @param backend contract implementation used by the test
   * @return configured reactor
   */
  private static FiniteRateCombustionReactor reactor(CombustionKineticsBackend backend) {
    SystemSrkEos fluid = new SystemSrkEos(400.0, 1.01325);
    fluid.addComponent("methane", 1.0, "mole/sec");
    fluid.setMixingRule(2);
    fluid.createDatabase(true);
    fluid.init(0);
    FiniteRateCombustionReactor reactor = new FiniteRateCombustionReactor("kinetic boundary",
        new Stream("feed", fluid));
    reactor.setResidenceTime(0.1);
    reactor.setKineticsBackend(backend);
    return reactor;
  }

  /**
   * Return a conserved identity state with explicit independent-validation fields.
   *
   * @param requestJson request from the Java boundary
   * @return synthetic contract state; no combustion claim
   */
  private static JsonObject identity(String requestJson) {
    JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", 1);
    result.addProperty("converged", true);
    result.addProperty("reactorModel", request.get("reactorModel").getAsString());
    result.add("temperatureK", request.get("temperatureK"));
    result.add("pressurePa", request.get("pressurePa"));
    JsonObject flows = new JsonObject();
    flows.addProperty("CH4", request.getAsJsonObject("componentMolarFlows").get("methane").getAsDouble());
    result.add("speciesMolarFlows", flows);
    JsonObject masses = new JsonObject();
    masses.addProperty("CH4", 0.016043);
    result.add("speciesMolecularMassesKgPerMol", masses);
    JsonObject counts = new JsonObject();
    counts.addProperty("C", 1.0);
    counts.addProperty("H", 4.0);
    JsonObject atoms = new JsonObject();
    atoms.add("CH4", counts);
    result.add("speciesAtomCounts", atoms);
    JsonObject mapping = new JsonObject();
    mapping.addProperty("methane", "CH4");
    result.add("componentToSpecies", mapping);
    result.addProperty("heatTransferToSurroundingsW", 0.0);
    result.addProperty("energyBalanceRelativeResidual", 0.0);
    result.addProperty("mechanismInletEnthalpyJPerKg", 0.0);
    result.addProperty("mechanismOutletEnthalpyJPerKg", 0.0);
    result.addProperty("energyReference", "Synthetic identity boundary test");
    JsonObject provenance = new JsonObject();
    provenance.addProperty("solver", "Synthetic identity");
    provenance.addProperty("solverVersion", "1");
    provenance.addProperty("mechanism", "Identity contract fixture");
    provenance.addProperty("resolvedMechanismSha256",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    provenance.addProperty("speciesCount", 1);
    provenance.addProperty("reactionCount", 1);
    result.add("provenance", provenance);
    return result;
  }
}
