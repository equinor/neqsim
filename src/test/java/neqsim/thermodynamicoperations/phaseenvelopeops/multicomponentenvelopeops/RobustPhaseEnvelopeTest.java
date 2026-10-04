package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link RobustPhaseEnvelope}.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class RobustPhaseEnvelopeTest {
  /**
   * Builds a rich natural gas whose envelope continuation is known to terminate early.
   *
   * @return rich gas fluid
   */
  private SystemInterface richGas() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("nitrogen", 0.0040);
    fluid.addComponent("CO2", 0.0090);
    fluid.addComponent("methane", 0.8992);
    fluid.addComponent("ethane", 0.0409);
    fluid.addComponent("propane", 0.0236);
    fluid.addComponent("i-butane", 0.0049);
    fluid.addComponent("n-butane", 0.0077);
    fluid.addComponent("i-pentane", 0.0025);
    fluid.addComponent("n-pentane", 0.0027);
    fluid.addComponent("n-hexane", 0.0024);
    fluid.addComponent("n-heptane", 0.0019);
    fluid.addComponent("n-octane", 0.0012);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** A pure component has no two-phase area in the PT plane, so the search must report invalid. */
  @Test
  void pureComponentHasNoTwoPhaseArea() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    RobustPhaseEnvelope envelope = new RobustPhaseEnvelope(fluid).setTemperatureRange(150.0, 250.0)
        .setPressureRange(1.0, 100.0).setResolution(12, 12).setRefinementIterations(8);
    envelope.calculate();

    assertFalse(envelope.isValid(), "a pure component has zero PT area and cannot have a cricondenbar");
  }

  /** The search must find a physically sensible cricondenbar for a rich gas. */
  @Test
  void richGasCricondenbarIsPhysical() {
    RobustPhaseEnvelope envelope = new RobustPhaseEnvelope(richGas()).setTemperatureRange(170.0, 380.0)
        .setPressureRange(2.0, 200.0).setResolution(40, 40).setRefinementIterations(16);
    envelope.calculate();

    assertTrue(envelope.isValid(), "rich gas must have a two-phase region");
    double cricondenbar = envelope.getCricondenbarPressure();
    double cricondentherm = envelope.getCricondenthermTemperature();

    assertTrue(cricondenbar > 90.0 && cricondenbar < 180.0,
        "cricondenbar out of physical range: " + cricondenbar + " bara");
    assertTrue(cricondentherm > 280.0 && cricondentherm < 340.0,
        "cricondentherm out of physical range: " + cricondentherm + " K");
    assertTrue(envelope.getDewPressures().length > 5, "too few boundary points");
    assertTrue(envelope.getCricondenbarTemperature() < cricondentherm,
        "cricondenbar must occur at a lower temperature than the cricondentherm");
  }

  /**
   * The state at the cricondenbar must be on the boundary: two-phase just below and single-phase just above.
   */
  @Test
  void cricondenbarLiesOnTheTwoPhaseBoundary() {
    SystemInterface fluid = richGas();
    RobustPhaseEnvelope envelope = new RobustPhaseEnvelope(fluid).setTemperatureRange(170.0, 380.0)
        .setPressureRange(2.0, 200.0).setResolution(40, 40).setRefinementIterations(16);
    envelope.calculate();

    double temperature = envelope.getCricondenbarTemperature();
    double pressure = envelope.getCricondenbarPressure();

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    fluid.setTemperature(temperature);
    fluid.setPressure(pressure * 0.97);
    ops.TPflash();
    assertEquals(2, fluid.getNumberOfPhases(), "just below the cricondenbar must be two-phase");

    fluid.setPressure(pressure * 1.05);
    ops.TPflash();
    assertEquals(1, fluid.getNumberOfPhases(), "above the cricondenbar must be single-phase");
  }

  /**
   * Regression guard for the defect that motivated this class: {@link PTphaseEnvelope} continuation truncates for this
   * fluid and silently reports the endpoint of a partial trace as the cricondenbar. The truncation must be detectable.
   */
  @Test
  void truncatedContinuationIsDetected() {
    SystemInterface fluid = richGas();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope(true, 1.0);
    double continuationCricondenbar = ops.get("cricondenbar")[1];

    RobustPhaseEnvelope envelope = new RobustPhaseEnvelope(richGas()).setTemperatureRange(170.0, 380.0)
        .setPressureRange(2.0, 200.0).setResolution(40, 40).setRefinementIterations(16);
    envelope.calculate();

    assertTrue(envelope.isValid());
    if (continuationCricondenbar < envelope.getCricondenbarPressure() * 0.95) {
      assertTrue(envelope.continuationLooksTruncated(continuationCricondenbar, 0.05),
          "a continuation value well below the robust value must be flagged as truncated");
    }
    assertFalse(envelope.continuationLooksTruncated(envelope.getCricondenbarPressure() * 0.99, 0.05),
        "a continuation value matching the robust value must not be flagged");
  }

  /** The JSON summary must carry the headline numbers. */
  @Test
  void jsonSummaryCarriesResults() {
    RobustPhaseEnvelope envelope = new RobustPhaseEnvelope(richGas()).setTemperatureRange(200.0, 340.0)
        .setPressureRange(5.0, 180.0).setResolution(15, 15).setRefinementIterations(8);
    envelope.calculate();

    String json = envelope.toJson();
    assertTrue(json.contains("cricondenbarPressureBara"));
    assertTrue(json.contains("cricondenthermTemperatureK"));
    assertTrue(json.contains("\"valid\":true"));
  }
}
