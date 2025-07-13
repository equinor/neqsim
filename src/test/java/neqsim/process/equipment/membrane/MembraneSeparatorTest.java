package neqsim.process.equipment.membrane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Unit test for MembraneSeparator. */
class MembraneSeparatorTest extends neqsim.NeqSimTest {
  ProcessSystem processOps;
  MembraneSeparator membrane;
  StreamInterface inlet;

  @BeforeEach
  public void setUp() {
    SystemSrkEos system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("methane", 90.0);
    system.addComponent("CO2", 10.0);
    system.setMixingRule(2);

    inlet = new Stream("feed", system);
    inlet.setFlowRate(1.0, "kg/sec");

    membrane = new MembraneSeparator("membrane", inlet);
    membrane.setPermeateFraction("CO2", 0.5);
    membrane.setDefaultPermeateFraction(0.1);

    processOps = new ProcessSystem();
    processOps.add(inlet);
    processOps.add(membrane);
    processOps.add(membrane.getPermeateStream());
    processOps.add(membrane.getRetentateStream());
  }

  @Test
  void massBalance() {
    processOps.run();
    double in = inlet.getMolarRate();
    double out = membrane.getPermeateStream().getMolarRate()
        + membrane.getRetentateStream().getMolarRate();
    assertEquals(in, out, 1e-6);
  }

  @Test
  void compositionChange() {
    processOps.run();
    double xCO2Feed = inlet.getFluid().getPhase(0).getComponent("CO2").getz();
    double xCO2Perm = membrane.getPermeateStream().getFluid().getPhase(0).getComponent("CO2").getz();
    double xCO2Ret = membrane.getRetentateStream().getFluid().getPhase(0).getComponent("CO2").getz();
    // Permeate should have higher CO2 fraction
    assertTrue(xCO2Perm > xCO2Feed);
    // Retentate should have lower CO2 fraction
    assertTrue(xCO2Ret < xCO2Feed);
  }

  @Test
  void permeabilityModel() {
    MembraneSeparator mem2 = new MembraneSeparator("perm", inlet);
    mem2.clearPermeateFractions();
    mem2.setMembraneArea(10.0);
    mem2.setPermeability("CO2", 5e-6);
    mem2.setPermeability("methane", 1e-6);

    ProcessSystem proc = new ProcessSystem();
    proc.add(inlet);
    proc.add(mem2);
    proc.add(mem2.getPermeateStream());
    proc.add(mem2.getRetentateStream());
    proc.run();

    double in = inlet.getMolarRate();
    double out = mem2.getPermeateStream().getMolarRate() + mem2.getRetentateStream().getMolarRate();
    assertEquals(in, out, 1e-6);
    double xCO2Perm = mem2.getPermeateStream().getFluid().getPhase(0).getComponent("CO2").getx();
    double xCO2Feed = inlet.getFluid().getPhase(0).getComponent("CO2").getx();
    assertTrue(xCO2Perm > xCO2Feed);
  }
}
