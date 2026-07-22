package neqsim.process.safety.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.safety.reaction.RunawayReactionAnalyzer.RunawayVerdict;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link RunawayReactionAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class RunawayReactionAnalyzerTest {

  /**
   * A high adiabatic rise that pushes MTSR above the maximum-allowable temperature is flagged as a runaway risk.
   */
  @Test
  void mtsrExceedsMaxAllowableIsRunawayRisk() {
    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(80.0, "C")
        .setReactionEnthalpy(-80.0, "kJ/mol").setLimitingReactantMoles(1000.0).setTotalMass(1000.0, "kg")
        .setSpecificHeat(2.0, "kJ/kgK").setMaxAllowableTemperature(110.0, "C").analyze();

    assertEquals(40.0, result.getAdiabaticTemperatureRiseK(), 1e-6);
    assertTrue(result.isMarginExceeded());
    assertEquals(RunawayVerdict.RUNAWAY_RISK, result.getVerdict());
    assertTrue(result.isTwoPhaseReliefScreeningRequired());
  }

  /**
   * A modest rise that keeps MTSR comfortably below the maximum-allowable temperature is acceptable.
   */
  @Test
  void comfortableMarginIsAcceptable() {
    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(50.0, "C")
        .setReactionEnthalpy(-20.0, "kJ/mol").setLimitingReactantMoles(500.0).setTotalMass(2000.0, "kg")
        .setSpecificHeat(2.5, "kJ/kgK").setMaxAllowableTemperature(200.0, "C").analyze();

    assertEquals(2.0, result.getAdiabaticTemperatureRiseK(), 1e-6);
    assertFalse(result.isMarginExceeded());
    assertEquals(RunawayVerdict.ACCEPTABLE_MARGIN, result.getVerdict());
    assertFalse(result.isTwoPhaseReliefScreeningRequired());
  }

  /**
   * With no maximum-allowable temperature supplied the verdict falls back to NO_RATING for a low-severity rise and the
   * margin serialises as NaN.
   */
  @Test
  void noRatingSerialisesWithNaNMargin() {
    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(40.0, "C")
        .setReactionEnthalpy(-10.0, "kJ/mol").setLimitingReactantMoles(100.0).setTotalMass(1000.0, "kg")
        .setSpecificHeat(2.0, "kJ/kgK").analyze();

    assertFalse(result.isMaxAllowableProvided());
    assertEquals(RunawayVerdict.NO_RATING, result.getVerdict());
    assertTrue(Double.isNaN(result.getTemperatureMarginK()));
    assertTrue(result.toJson().contains("NaN"));
  }

  /**
   * A large adiabatic rise without a rating is still flagged as a runaway risk on severity alone.
   */
  @Test
  void highSeverityWithoutRatingIsRunawayRisk() {
    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(60.0, "C")
        .setReactionEnthalpy(-200.0, "kJ/mol").setLimitingReactantMoles(2000.0).setTotalMass(1000.0, "kg")
        .setSpecificHeat(2.0, "kJ/kgK").setGassySystem(true).analyze();

    assertTrue(result.getAdiabaticTemperatureRiseK() >= 200.0);
    assertEquals(RunawayVerdict.RUNAWAY_RISK, result.getVerdict());
    assertTrue(result.isTwoPhaseReliefScreeningRequired());
  }

  /**
   * Supplying an activation energy and an initial heat-release rate yields a finite adiabatic time-to-maximum-rate.
   */
  @Test
  void timeToMaxRateComputedFromKinetics() {
    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(100.0, "C")
        .setReactionEnthalpy(-60.0, "kJ/mol").setLimitingReactantMoles(800.0).setTotalMass(1000.0, "kg")
        .setSpecificHeat(2.0, "kJ/kgK").setActivationEnergy(100.0, "kJ/mol").setInitialHeatReleaseRate(2.0, "W/kg")
        .analyze();

    assertTrue(result.isTimeToMaxRateProvided());
    assertTrue(result.getTimeToMaxRateS() > 0.0);
  }

  /**
   * A fluid can supply the specific heat when it is not given explicitly.
   */
  @Test
  void fluidDerivesSpecificHeat() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methanol", 1.0);
    fluid.setMixingRule("classic");

    RunawayReactionResult result = new RunawayReactionAnalyzer().setProcessTemperature(60.0, "C")
        .setReactionEnthalpy(-30.0, "kJ/mol").setLimitingReactantMoles(200.0).setTotalMass(500.0, "kg").setFluid(fluid)
        .analyze();

    assertTrue(result.getSpecificHeatJPerKgK() > 0.0);
    assertTrue(result.getAdiabaticTemperatureRiseK() > 0.0);
  }

  /**
   * A maximum-allowable temperature at or below the process temperature is rejected.
   */
  @Test
  void rejectsMaxAllowableBelowProcessTemperature() {
    final RunawayReactionAnalyzer analyzer = new RunawayReactionAnalyzer().setProcessTemperature(120.0, "C")
        .setReactionEnthalpy(-50.0, "kJ/mol").setLimitingReactantMoles(100.0).setTotalMass(1000.0, "kg")
        .setSpecificHeat(2.0, "kJ/kgK").setMaxAllowableTemperature(100.0, "C");

    assertThrows(IllegalStateException.class, new Executable() {
      @Override
      public void execute() {
        analyzer.analyze();
      }
    });
  }
}
