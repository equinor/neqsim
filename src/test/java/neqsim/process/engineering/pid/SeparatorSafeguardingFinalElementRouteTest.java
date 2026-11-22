package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.pid.rules.SeparatorSafeguardingPidRule;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks that proposed separator trips act on the appropriate inventory path.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class SeparatorSafeguardingFinalElementRouteTest {
  /**
   * Inlet isolation must not be represented as the low-level gas-blowby final element.
   *
   * @param threePhase whether the real source has gas, oil and water outlets
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lowLowLevelDoesNotUseInletIsolation(boolean threePhase) {
    List<PidElement> elements = proposals(threePhase);
    PidElement low = purpose(elements, "LEVEL-LL-TRIP");
    PidElement inlet = purpose(elements, "INLET-ISOLATION");
    assertFalse(low.getConnectedElementIds().contains(inlet.getId()));
    assertTrue(inlet.getRequirementIds().stream().noneMatch(id -> id.endsWith("LEVEL-LL-TRIP")));
  }

  /**
   * A missing controlled liquid-withdrawal route remains an explicit unresolved proposal.
   *
   * @param threePhase whether the real source has gas, oil and water outlets
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unresolvedLiquidFinalElementRetainsHold(boolean threePhase) {
    PidElement low = purpose(proposals(threePhase), "LEVEL-LL-TRIP");
    assertTrue(low.getConnectedElementIds().isEmpty());
    assertEquals("LIQUID_OUTLET_ISOLATION_OR_CONTROL", low.getAttributes().get("requiredFinalElementRoute"));
    assertEquals("PROJECT_INPUT_REQUIRED", low.getAttributes().get("finalElementStatus"));
    assertEquals("HOLD", low.getAttributes().get("finalElementRouteReview"));
    assertEquals("NOT_ESTABLISHED", low.getAttributes().get("finalElementSafetyCredit"));
  }

  /**
   * The high-level carry-over proposal retains its inlet-isolation connection.
   *
   * @param threePhase whether the real source has gas, oil and water outlets
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void highHighLevelRetainsInletIsolation(boolean threePhase) {
    List<PidElement> elements = proposals(threePhase);
    PidElement high = purpose(elements, "LEVEL-HH-TRIP");
    PidElement inlet = purpose(elements, "INLET-ISOLATION");
    assertTrue(high.getConnectedElementIds().contains(inlet.getId()));
    assertTrue(inlet.getRequirementIds().stream().anyMatch(id -> id.endsWith("LEVEL-HH-TRIP")));
  }

  /**
   * Generates the safeguarding rule against real separator requirements.
   *
   * @param threePhase whether to construct an aqueous three-phase separator
   * @return native proposal elements
   */
  private static List<PidElement> proposals(boolean threePhase) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", .85);
    fluid.addComponent("n-heptane", .15);
    if (threePhase) {
      fluid.addComponent("water", .1);
      fluid.setMultiPhaseCheck(true);
    }
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(2000.0, "kg/hr");
    Separator separator = threePhase ? new ThreePhaseSeparator("V-101", feed) : new Separator("V-101", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();
    assertEquals(threePhase ? 3 : 2, separator.getThermoSystem().getNumberOfPhases());
    assertEquals(threePhase, separator.getThermoSystem().hasPhaseType("aqueous"),
        "The three-phase source must actually retain a water phase");
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Separator route", process, "2.0.1")
        .registerProposedInstruments(false).build();
    return new SeparatorSafeguardingPidRule()
        .propose(new PidDesignContext(project, new PidDesignBasis("SEPARATOR-ROUTE", "20")), separator);
  }

  /**
   * Finds one deterministic rule purpose without depending on sequence allocation.
   *
   * @param elements proposed safeguarding elements
   * @param purpose stable proposal purpose
   * @return matching proposal
   */
  private static PidElement purpose(List<PidElement> elements, String purpose) {
    return elements.stream().filter(element -> purpose.equals(element.getAttributes().get("proposalPurpose")))
        .findFirst().orElseThrow(() -> new AssertionError("Missing purpose " + purpose));
  }
}
