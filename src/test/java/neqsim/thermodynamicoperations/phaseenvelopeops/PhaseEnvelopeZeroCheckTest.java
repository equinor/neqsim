package neqsim.thermodynamicoperations.phaseenvelopeops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhaseEnvelopeZeroCheckTest {
  @Test
  public void testGERGPhaseEnvelope() {
    SystemInterface gerg_fluid = new SystemGERG2008Eos();
    gerg_fluid.addComponent("methane", 1.0);
    gerg_fluid.addComponent("ethane", 0.1);
    gerg_fluid.addComponent("propane", 0.001);
    gerg_fluid.addComponent("n-butane", 0.001);
    gerg_fluid.addComponent("n-pentane", 0.0001);
    gerg_fluid.setPressure(50.1, "bara");
    gerg_fluid.setTemperature(300.0, "K");
    ThermodynamicOperations ops = new ThermodynamicOperations(gerg_fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");
    assertTrue(cricondenbar[0] > 0.0 && cricondenbar[1] > 0.0, "cricondenbar should be calculated");
    assertTrue(cricondentherm[0] > 0.0 && cricondentherm[1] > 0.0,
        "cricondentherm should be calculated");
    assertTrue(Arrays.stream(dewT).noneMatch(t -> t == 0.0), "dewT should not contain zeros");
    assertTrue(Arrays.stream(dewP).noneMatch(p -> p == 0.0), "dewP should not contain zeros");
  }

  @Test
  public void testSRKPhaseEnvelope() {
    SystemInterface srk_fluid = new SystemSrkEos(300.0, 50.1);
    srk_fluid.addComponent("methane", 1.0);
    srk_fluid.addComponent("ethane", 0.1);
    srk_fluid.addComponent("propane", 0.001);
    srk_fluid.addComponent("n-butane", 0.001);
    srk_fluid.addComponent("n-pentane", 0.0001);
    ThermodynamicOperations ops = new ThermodynamicOperations(srk_fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");
    assertTrue(cricondenbar[0] > 0.0 && cricondenbar[1] > 0.0, "cricondenbar should be calculated");
    assertTrue(cricondentherm[0] > 0.0 && cricondentherm[1] > 0.0,
        "cricondentherm should be calculated");
    assertTrue(Arrays.stream(dewT).noneMatch(t -> t == 0.0), "dewT should not contain zeros");
    assertTrue(Arrays.stream(dewP).noneMatch(p -> p == 0.0), "dewP should not contain zeros");
  }
}
