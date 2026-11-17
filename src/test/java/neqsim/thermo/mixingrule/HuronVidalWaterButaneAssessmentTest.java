package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks staged PR-HV water/n-butane flashes against independent numerical endpoints. Experimental errors and model
 * phase labels are reported without an accuracy threshold.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalWaterButaneAssessmentTest extends neqsim.NeqSimTest {
  /** The two observed branches, traced from fresh and retained, reversed-component systems. */
  @Test
  void stagedTupleReproducesIndependentEquilibriaAcrossPhaseHistory() throws Exception {
    Path path = Paths.get("target", "hv-water-nbutane-native.csv");
    Files.createDirectories(path.getParent());
    try (
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/neqsim/thermo/mixingrule/hv-water-nbutane/assessment.csv"),
            StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
      output.println(reader.readLine() + ",native_rich_label,native_rich_water,native_aqueous_butane,"
          + "native_rich_Z,native_aqueous_Z,max_balance,max_normalization,max_logf");
      SystemInterface retained = null;
      String line;
      int rows = 0;
      while ((line = reader.readLine()) != null) {
        String[] row = line.split(",", -1);
        double t = Double.parseDouble(row[6]);
        double p = Double.parseDouble(row[7]);
        SystemInterface fresh = fluid(t, p, false);
        if (retained == null) {
          retained = fluid(t, p, true);
        }
        retained.setTemperature(t);
        retained.setPressure(p);
        PhaseInterface freshRich = null;
        PhaseInterface freshAqueous = null;
        double[] maxResiduals = new double[3];
        for (SystemInterface state : new SystemInterface[] {fresh, retained}) {
          ThermodynamicOperations operations = new ThermodynamicOperations(state);
          operations.TPflash();
          if (rows == 0 && state == retained) {
            operations.TPflash();
          }
          state.init(3);
          assertEquals(2, state.getNumberOfPhases(), line);
          assertTrue(state.hasPhaseType(PhaseType.AQUEOUS), line);
          PhaseInterface aqueous = state.getPhase(PhaseType.AQUEOUS);
          PhaseInterface rich = state.getPhase(0) == aqueous ? state.getPhase(1) : state.getPhase(0);
          assertTrue(aqueous.getComponent("water").getx() > 0.9 && rich.getComponent("n-butane").getx() > 0.8);
          double[] endpoints = {rich.getComponent("water").getx(), aqueous.getComponent("n-butane").getx(), rich.getZ(),
              aqueous.getZ()};
          for (int index = 0; index < endpoints.length; index++) {
            double expected = Double.parseDouble(row[9 + index]);
            assertEquals(expected, endpoints[index], Math.max(1e-13, expected * 5e-8), line);
          }
          double[] residuals = closure(state);
          checkFugacityIdentities(state);
          for (int index = 0; index < residuals.length; index++) {
            maxResiduals[index] = Math.max(maxResiduals[index], residuals[index]);
          }
          if (state == fresh) {
            freshRich = rich;
            freshAqueous = aqueous;
          } else {
            assertEquals(freshRich.getType(), rich.getType(), line);
          }
        }
        output.printf(Locale.ROOT, "%s,%s,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g%n", line, freshRich.getType(),
            freshRich.getComponent("water").getx(), freshAqueous.getComponent("n-butane").getx(), freshRich.getZ(),
            freshAqueous.getZ(), maxResiduals[0], maxResiduals[1], maxResiduals[2]);
        rows++;
      }
      assertEquals(14, rows);
      assertTrue(!output.checkError());
    }
  }

  /**
   * Creates a synthetic equimolar feed with only the water/n-butane pair assigned the staged PR-HV tuple.
   *
   * @param t temperature in kelvin
   * @param p absolute pressure in bara
   * @param reversed whether n-butane is added first
   * @return an independently configured test fluid
   */
  private static SystemInterface fluid(double t, double p, boolean reversed) {
    SystemInterface fluid = new SystemPrEos(t, p);
    fluid.addComponent(reversed ? "n-butane" : "water", 0.5);
    fluid.addComponent(reversed ? "water" : "n-butane", 0.5);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    int water = reversed ? 1 : 0;
    int butane = 1 - water;
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
      rule.setClassicOrHV(water, butane, "HV");
      rule.setClassicOrHV(butane, water, "HV");
      rule.setHValphaParameter(water, butane, 0.06);
      rule.setHValphaParameter(butane, water, 0.06);
      rule.setHVDijParameter(water, butane, 4968.0);
      rule.setHVDijParameter(butane, water, 1567.0);
      rule.setHVDijTParameter(water, butane, -19.6);
      rule.setHVDijTParameter(butane, water, 5.7);
    }
    return fluid;
  }

  /**
   * Verifies closure for both phases and components of the synthetic binary feed.
   *
   * @param fluid the flashed two-phase fluid
   * @return maximum balance, normalization and log-fugacity residuals
   */
  private static double[] closure(SystemInterface fluid) {
    PhaseInterface first = fluid.getPhase(0);
    PhaseInterface second = fluid.getPhase(1);
    double beta = fluid.getBeta(0);
    double otherBeta = fluid.getBeta(1);
    assertTrue(beta > 0 && beta < 1 && otherBeta > 0 && otherBeta < 1);
    double normalization = Math.abs(1 - beta - otherBeta);
    for (PhaseInterface phase : new PhaseInterface[] {first, second}) {
      normalization = Math.max(normalization,
          Math.abs(1 - phase.getComponent(0).getx() - phase.getComponent(1).getx()));
    }
    double balance = 0;
    double logFugacity = 0;
    for (int component = 0; component < 2; component++) {
      double x = first.getComponent(component).getx();
      double y = second.getComponent(component).getx();
      balance = Math.max(balance, Math.abs(0.5 - beta * x - otherBeta * y));
      logFugacity = Math.max(logFugacity,
          Math.abs(Math.log(x / y) + first.getComponent(component).getLogFugacityCoefficient()
              - second.getComponent(component).getLogFugacityCoefficient()));
    }
    assertTrue(balance <= 1e-10, "Component balance " + balance);
    assertTrue(normalization <= 1e-12, "Normalization " + normalization);
    assertTrue(logFugacity <= 1e-8, "Log-fugacity residual " + logFugacity);
    return new double[] {balance, normalization, logFugacity};
  }

  /**
   * Checks the native thermodynamic identities and finite differences on an independent snapshot.
   *
   * @param fluid the initialized two-phase fluid
   */
  private static void checkFugacityIdentities(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(SerializationUtils.clone(fluid));
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients(), "Residual Gibbs energy");
    assertTrue(checker.checkFugacityCoefficientsDn(), "Composition Gibbs–Duhem");
    assertTrue(checker.checkFugacityCoefficientsDn2(), "Composition Hessian symmetry");
    assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure identity");
    assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature identity");
    checker.setMaxError(1e-6);
    assertTrue(checker.checkNumerically(), "Finite differences");
  }
}
