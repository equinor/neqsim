package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousRootTransitionCandidateTest {
  @Test
  void acceptsOilAqueousCandidateTopologyAcrossSeparatorPressures() {
    for (double pressureBara : new double[] { 2.10, 1.62, 1.20, 0.74 }) {
      SystemInterface candidate = createOilAqueousCandidate(pressureBara);
      TPflash flash = new TPflash(candidate.clone(), false);

      assertTrue(candidate.hasPhaseType(PhaseType.OIL));
      assertTrue(candidate.hasPhaseType(PhaseType.AQUEOUS));
      assertTrue(flash.hasExactlyOneAqueousAndOneCubicPhase(candidate),
          "OIL+AQUEOUS candidate was rejected at " + pressureBara + " bara");
    }
  }

  @Test
  void acceptsGasAqueousCandidateButRejectsOtherPhaseTopologies() {
    SystemInterface gasAqueous = createSrkCandidate(260.0, 100.0);
    TPflash flash = new TPflash(gasAqueous.clone(), false);

    assertTrue(flash.hasExactlyOneAqueousAndOneCubicPhase(gasAqueous));

    SystemInterface threePhase = createSrkCandidate(255.0, 90.0);
    assertFalse(flash.hasExactlyOneAqueousAndOneCubicPhase(threePhase));

    SystemInterface dryGasOil = createDryGasOilCandidate();
    assertTrue(dryGasOil.hasPhaseType(PhaseType.GAS));
    assertTrue(dryGasOil.hasPhaseType(PhaseType.OIL));
    assertFalse(flash.hasExactlyOneAqueousAndOneCubicPhase(dryGasOil));
  }

  private SystemInterface createDryGasOilCandidate() {
    SystemInterface system = new SystemPrEos(220.0, 100.0);
    system.addComponent("methane", 0.72);
    system.addComponent("ethane", 0.08);
    system.addComponent("propane", 0.05);
    system.addComponent("n-heptane", 0.10);
    system.addComponent("nC10", 0.05);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private SystemInterface createOilAqueousCandidate(double pressureBara) {
    SystemInterface feed = new SystemPrEos1978(273.15 + 30.0, pressureBara);
    addSeparatorFeed(feed);
    feed.setMixingRule("classic");
    feed.setMultiPhaseCheck(true);
    new ThermodynamicOperations(feed).TPflash();

    SystemInterface candidate = feed.phaseToSystem(feed.getPhaseNumberOfPhase(PhaseType.OIL),
        feed.getPhaseNumberOfPhase(PhaseType.AQUEOUS));
    candidate.setMultiPhaseCheck(true);
    candidate.setTemperature(273.15 + 30.0);
    candidate.setPressure(pressureBara, "bara");
    new ThermodynamicOperations(candidate).TPflash();
    candidate.init(3);
    return candidate;
  }

  private SystemInterface createSrkCandidate(double temperatureK, double pressureBara) {
    SystemInterface system = new SystemSrkEos(temperatureK, pressureBara);
    system.addComponent("CO2", 0.543865141103918);
    system.addComponent("methane", 0.2937712952303271);
    system.addComponent("ethane", 0.07010605470616459);
    system.addComponent("water", 0.09225750895959021);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void addSeparatorFeed(SystemInterface system) {
    system.addComponent("methane", 0.0034);
    system.addComponent("ethane", 0.0038);
    system.addComponent("propane", 0.0406);
    system.addComponent("i-butane", 0.0339);
    system.addComponent("n-butane", 0.1114);
    system.addComponent("i-pentane", 0.0536);
    system.addComponent("n-pentane", 0.0733);
    system.addComponent("n-hexane", 0.0681);
    system.addComponent("n-heptane", 0.0637);
    system.addComponent("n-octane", 0.0365);
    system.addComponent("nC10", 0.0172);
    system.addComponent("water", 0.4945);
  }
}
