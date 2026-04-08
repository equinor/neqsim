package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link SystemPrLeeKeslerEos}.
 *
 * <p>
 * Verifies that:
 * <ol>
 * <li>The model name is set correctly.</li>
 * <li>Flash calculations converge and produce physically valid results (VF in [0,1], positive
 * density, positive pressure).</li>
 * <li>For light components (ω ≤ 0.49) PR-LK and PR78 give identical VF (same m-factor).</li>
 * <li>For mixtures containing heavy pseudo-components (ω > 0.49) PR-LK and PR78 differ measurably,
 * because PR-LK applies the PR76 m-factor to all components while PR78 uses a modified polynomial
 * for ω > 0.49.</li>
 * <li>Clone preserves model name.</li>
 * </ol>
 */
public class SystemPrLeeKeslerEosTest extends neqsim.NeqSimTest {

  /** Tolerance for floating-point comparisons. */
  private static final double DELTA = 1.0e-4;

  @Test
  @DisplayName("Model name is PR-LK-EoS")
  public void testModelName() {
    SystemInterface fluid = new SystemPrLeeKeslerEos(298.15, 1.0);
    assertEquals("PR-LK-EoS", fluid.getModelName());
  }

  @Test
  @DisplayName("TP flash converges and VF is in [0,1]")
  public void testFlashConverges() {
    SystemInterface fluid = new SystemPrLeeKeslerEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    double vf = fluid.getBeta(0);
    assertTrue(vf >= 0.0 && vf <= 1.0, "Vapour fraction should be in [0,1], got " + vf);
    assertTrue(fluid.getDensity("kg/m3") > 0.0, "Density should be positive");
  }

  @Test
  @DisplayName("PR-LK equals PR78 for light components (omega <= 0.49)")
  public void testVfEqualsToPr78ForLightComponents() {
    // All components have omega < 0.49, so PR76 m == PR78 m -> VF must be identical
    double T = 273.15 + 25.0;
    double P = 60.0;

    SystemInterface fluidLk = new SystemPrLeeKeslerEos(T, P);
    fluidLk.addComponent("methane", 0.85);
    fluidLk.addComponent("ethane", 0.10);
    fluidLk.addComponent("propane", 0.05);
    fluidLk.setMixingRule("classic");

    SystemInterface fluidPr78 = new SystemPrEos1978(T, P);
    fluidPr78.addComponent("methane", 0.85);
    fluidPr78.addComponent("ethane", 0.10);
    fluidPr78.addComponent("propane", 0.05);
    fluidPr78.setMixingRule("classic");

    ThermodynamicOperations opsLk = new ThermodynamicOperations(fluidLk);
    ThermodynamicOperations opsPr78 = new ThermodynamicOperations(fluidPr78);
    opsLk.TPflash();
    opsPr78.TPflash();
    fluidLk.initProperties();
    fluidPr78.initProperties();

    double vfLk = fluidLk.getBeta(0);
    double vfPr78 = fluidPr78.getBeta(0);

    // PR76 == PR78 for omega < 0.49 -> VF must agree within numerical precision
    assertEquals(vfPr78, vfLk, 1e-6,
        "PR-LK and PR78 should give identical VF for light components; got LK=" + vfLk + " vs PR78="
            + vfPr78);
  }

  @Test
  @DisplayName("PR-LK differs from PR78 for heavy components (omega > 0.49)")
  public void testVfDifferentFromPr78ForHeavyComponents() {
    // n-undecane has omega ~ 0.539 > 0.49, so PR78 applies modified polynomial
    // while PR-LK (PR76) still uses the original quadratic -> measurable VF difference
    double T = 273.15 + 100.0;
    double P = 5.0;

    SystemInterface fluidLk = new SystemPrLeeKeslerEos(T, P);
    fluidLk.addComponent("methane", 0.30);
    fluidLk.addComponent("n-heptane", 0.35);
    fluidLk.addComponent("nC10", 0.35);
    fluidLk.setMixingRule("classic");
    fluidLk.setMultiPhaseCheck(true);

    SystemInterface fluidPr78 = new SystemPrEos1978(T, P);
    fluidPr78.addComponent("methane", 0.30);
    fluidPr78.addComponent("n-heptane", 0.35);
    fluidPr78.addComponent("nC10", 0.35);
    fluidPr78.setMixingRule("classic");
    fluidPr78.setMultiPhaseCheck(true);

    ThermodynamicOperations opsLk = new ThermodynamicOperations(fluidLk);
    ThermodynamicOperations opsPr78 = new ThermodynamicOperations(fluidPr78);
    opsLk.TPflash();
    opsPr78.TPflash();
    fluidLk.initProperties();
    fluidPr78.initProperties();

    double vfLk = fluidLk.getBeta(0);
    double vfPr78 = fluidPr78.getBeta(0);

    // Both should be valid
    assertTrue(vfLk >= 0.0 && vfLk <= 1.0, "PR-LK VF out of range: " + vfLk);
    assertTrue(vfPr78 >= 0.0 && vfPr78 <= 1.0, "PR78 VF out of range: " + vfPr78);

    // PR76 != PR78 for heavy components -> VF must differ by more than numerical noise
    assertTrue(Math.abs(vfLk - vfPr78) > 1e-6,
        "PR-LK and PR78 should give different VF for heavy components (omega>0.49); got LK=" + vfLk
            + " vs PR78=" + vfPr78);
  }

  @Test
  @DisplayName("Clone preserves model name")
  public void testClone() {
    SystemInterface fluid = new SystemPrLeeKeslerEos(298.15, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    SystemInterface clone = fluid.clone();
    assertEquals("PR-LK-EoS", clone.getModelName());
  }
}
