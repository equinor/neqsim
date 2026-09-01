package neqsim.process.equipment.expander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Guards the {@link Expander#DEFAULT_EXPANDER_CALC_STEPS} default.
 *
 * <p>
 * The polytropic expansion path used a hard-coded 40 pressure steps until the step count was taken from
 * {@code Compressor.getNumberOfCompressorCalcSteps()} and seeded to 5. That is a numerical-resolution change on a
 * public equipment class, so these tests assert that the cheap default still reproduces the 40-step result.
 * </p>
 */
public class ExpanderPolytropicStepsTest extends neqsim.NeqSimTest {

  private static SystemInterface makeRichGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 90.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(50000.0, "kg/hr");
    return fluid;
  }

  private static Expander runExpander(int calcSteps) {
    Stream feed = new Stream("feed", makeRichGas());
    feed.setTemperature(25.0, "C");
    feed.setPressure(90.0, "bara");
    feed.run();

    Expander expander = new Expander("expander", feed);
    expander.setOutletPressure(30.0, "bara");
    expander.setPolytropicEfficiency(0.80);
    expander.setUsePolytropicCalc(true);
    expander.setNumberOfCompressorCalcSteps(calcSteps);
    expander.run();
    return expander;
  }

  @Test
  public void defaultStepCountIsFive() {
    assertEquals(5, Expander.DEFAULT_EXPANDER_CALC_STEPS);
    assertEquals(Expander.DEFAULT_EXPANDER_CALC_STEPS, new Expander("e").getNumberOfCompressorCalcSteps());
    // The constructor taking an inlet stream must chain to Expander(String) so the default applies.
    Stream feed = new Stream("feed", makeRichGas());
    assertEquals(Expander.DEFAULT_EXPANDER_CALC_STEPS, new Expander("e2", feed).getNumberOfCompressorCalcSteps());
  }

  @Test
  public void fiveStepsReproduceTheHistoricalFortyStepResult() {
    Expander coarse = runExpander(Expander.DEFAULT_EXPANDER_CALC_STEPS);
    Expander fine = runExpander(40);

    // Measured on this 90 -> 30 bara rich-gas case: 0.06 K out of a 51 K temperature drop
    // (0.12 % of the drop). Well inside engineering accuracy, but not exactly identical - the
    // tolerance below documents the real discretization error of the cheaper default.
    assertEquals(fine.getOutletStream().getTemperature("C"), coarse.getOutletStream().getTemperature("C"), 0.1,
        "outlet temperature must stay within the documented polytropic step-count tolerance");
    assertEquals(fine.getPower("kW"), coarse.getPower("kW"), 0.005 * Math.abs(fine.getPower("kW")),
        "shaft power must be insensitive to the polytropic step count");
    assertEquals(fine.getOutletStream().getFluid().getEnthalpy("kJ/kg"),
        coarse.getOutletStream().getFluid().getEnthalpy("kJ/kg"),
        0.005 * Math.abs(fine.getOutletStream().getFluid().getEnthalpy("kJ/kg")),
        "outlet enthalpy must be insensitive to the polytropic step count");
  }
}
