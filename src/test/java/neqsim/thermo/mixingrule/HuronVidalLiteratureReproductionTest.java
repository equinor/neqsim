package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reproduces the published SRK-HV correlation for MEG and n-heptane without changing library defaults.
 *
 * <p>
 * The interaction parameters are from Folas, Modeling of Complex Mixtures Containing Hydrogen Bonding Molecules, Ph.D.
 * thesis (2006), Table 6.2. Table 6.6 reports mean absolute relative deviations of 6.7 percent for heptane in MEG and
 * 21.2 percent for MEG in heptane. Measurements and the compatible MEG pure-component configuration are from Derawi,
 * Modeling of Phase Equilibria Containing Associating Fluids (2002), Tables 3.3, 4.1 and 5.5. The parameters use
 * volume-weighted modified NRTL, rather than ordinary low-pressure NRTL. With MEG as component 1, D12 = (g12 - g22)/R =
 * 430 K, D21 = (g21 - g11)/R = 2595 K and alpha = 0.13. The temperature-independent offsets are zero. MEG has Tc = 720
 * K, Pc = 82 bar and the dimensionless Mathias-Copeman coefficients (1.1121, 0.9679, -1.4200).
 * </p>
 *
 * <p>
 * Primary sources: <a href=
 * "https://backend.orbit.dtu.dk/ws/portalfiles/portal/123926886/CDocuments_and_SettingsalbDesktopPh.D._Georgios_K._Folas.pdf.pdf">Folas
 * thesis</a> and <a href="https://backend.orbit.dtu.dk/ws/portalfiles/portal/5653725/PhD_Derawi.pdf">Derawi thesis</a>.
 * The published correlation error exceeds the reported measurement uncertainties; reproducing this correlation does not
 * establish accuracy within experimental uncertainty or justify transferring the coefficients to PR or other
 * pure-component models.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalLiteratureReproductionTest extends neqsim.NeqSimTest {
  /** Temperature in kelvin and measured mass fractions, after dividing the source's percent values by 100. */
  private static final double[][] MEASUREMENTS = {{315.95, 0.000198, 0.001478}, {322.75, 0.000287, 0.001532},
      {329.75, 0.000399, 0.001760}, {336.55, 0.000553, 0.001903}, {341.15, 0.000677, 0.002024},
      {346.95, 0.000867, 0.002119}, {351.85, 0.001066, 0.002238}};

  /**
   * Reproduces both published aggregate deviations while checking numerical equilibrium at each measured state.
   */
  @Test
  void reproducesPublishedMegHeptaneCorrelationWithCompatiblePureComponentParameters() {
    double megDeviation = 0.0;
    double heptaneDeviation = 0.0;
    for (double[] measurement : MEASUREMENTS) {
      SystemInterface fluid = literatureFluid(measurement[0]);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(3);
      assertEquilibrium(fluid);
      double megInHeptane = massFraction(fluid.getPhase(PhaseType.OIL), 0);
      double heptaneInMeg = massFraction(fluid.getPhase(PhaseType.AQUEOUS), 1);
      megDeviation += Math.abs(megInHeptane / measurement[1] - 1.0) * 100.0;
      heptaneDeviation += Math.abs(heptaneInMeg / measurement[2] - 1.0) * 100.0;
    }
    assertEquals(6.7, heptaneDeviation / MEASUREMENTS.length, 0.15,
        "Published mean absolute relative deviation for heptane in MEG, in percentage points");
    assertEquals(21.2, megDeviation / MEASUREMENTS.length, 0.15,
        "Published mean absolute relative deviation for MEG in heptane, in percentage points");
  }

  /**
   * Creates an explicit test-local literature configuration, including the pure properties used in the fit.
   *
   * @param temperature measured equilibrium temperature in kelvin
   * @return configured equal-mole feed at 1 bara
   */
  private static SystemInterface literatureFluid(double temperature) {
    SystemInterface fluid = new SystemSrkEos(temperature, 1.0);
    fluid.addComponent("MEG", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    for (int phaseIndex = 0; phaseIndex < fluid.getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      HVMixingRulesInterface rule = (HVMixingRulesInterface) phase.getMixingRule();
      rule.setClassicOrHV(0, 1, "HV");
      rule.setClassicOrHV(1, 0, "HV");
      rule.setHValphaParameter(0, 1, 0.13);
      rule.setHVDijParameter(0, 1, 430.0);
      rule.setHVDijParameter(1, 0, 2595.0);
      rule.setHVDijTParameter(0, 1, 0.0);
      rule.setHVDijTParameter(1, 0, 0.0);
      ComponentEosInterface meg = (ComponentEosInterface) phase.getComponent("MEG");
      meg.setTC(720.0);
      meg.setPC(82.0);
      meg.setAttractiveTerm(4);
      meg.getAttractiveTerm().setParameters(0, 1.1121);
      meg.getAttractiveTerm().setParameters(1, 0.9679);
      meg.getAttractiveTerm().setParameters(2, -1.4200);
    }
    return fluid;
  }

  /**
   * Converts a phase mole fraction to the mass-fraction basis of the experimental table.
   *
   * @param phase equilibrium phase
   * @param component component index
   * @return component mass fraction
   */
  private static double massFraction(PhaseInterface phase, int component) {
    return phase.getComponent(component).getx() * phase.getComponent(component).getMolarMass() / phase.getMolarMass();
  }

  /**
   * Checks the two-liquid topology, normalized fractions, component balance and interphase fugacity equality.
   *
   * @param fluid equilibrated literature fluid
   */
  private static void assertEquilibrium(SystemInterface fluid) {
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction > 0.0 && fraction < 1.0);
        compositionSum += fraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      double reference = logFugacity(fluid.getPhase(0), component);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
        assertEquals(reference, logFugacity(fluid.getPhase(phase), component), 1.0e-8);
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1.0e-10);
    }
  }

  /**
   * Returns log fugacity without the common pressure factor.
   *
   * @param phase equilibrium phase
   * @param component component index
   * @return log mole fraction plus log fugacity coefficient
   */
  private static double logFugacity(PhaseInterface phase, int component) {
    double coefficient = phase.getComponent(component).getFugacityCoefficient();
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0);
    return Math.log(phase.getComponent(component).getx()) + Math.log(coefficient);
  }
}
