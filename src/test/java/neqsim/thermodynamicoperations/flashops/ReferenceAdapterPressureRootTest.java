package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.netlib.util.doubleW;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermo.system.SystemVegaEos;
import neqsim.thermo.util.Vega.Vega;
import neqsim.thermo.util.gerg.GERG2008;
import neqsim.thermo.util.leachman.Leachman;

/**
 * Actual reference-EOS pressure must close in the phase volume coordinate used by the flash gate.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ReferenceAdapterPressureRootTest {
  /**
   * Creates a single reference phase with an explicit extensive inventory and requested root.
   *
   * @param model reference model index
   * @param component pure component
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param amount total moles
   * @param root requested density root
   * @return initialized fluid
   */
  private static SystemInterface fluid(int model, String component, double temperature, double pressure, double amount,
      PhaseType root) {
    SystemInterface result = model == 0 ? new SystemGERG2008Eos(temperature, pressure)
        : model == 1 ? new SystemLeachmanEos(temperature, pressure, component, false)
            : new SystemVegaEos(temperature, pressure);
    if (result.getNumberOfComponents() == 0) {
      result.addComponent(component, 1.0);
    }
    result.setTotalNumberOfMoles(amount);
    result.init(0);
    result.setNumberOfPhases(1);
    result.setBeta(0, 1.0);
    result.setPhaseType(0, root);
    result.init(3);
    return result;
  }

  /**
   * Evaluates the native equation independently of cached phase derivatives and gas constant.
   *
   * @param model reference model index
   * @param component pure component
   * @param temperature temperature in K
   * @param molarVolume internal molar volume
   * @return native pressure in bar
   */
  private static double nativePressure(int model, String component, double temperature, double molarVolume) {
    double density = 100.0 / molarVolume;
    doubleW pressure = new doubleW(0.0);
    doubleW compressibility = new doubleW(0.0);
    if (model == 0) {
      GERG2008 reference = new GERG2008();
      reference.SetupGERG();
      double[] composition = new double[23];
      composition[1] = 1.0;
      reference.PressureGERG(temperature, density, composition, pressure, compressibility);
    } else if (model == 1) {
      Leachman reference = new Leachman();
      reference
          .SetupLeachman(component.startsWith("para") ? "para" : component.startsWith("ortho") ? "ortho" : "normal");
      reference.PressureLeachman(temperature, density, pressure, compressibility);
    } else {
      Vega reference = new Vega();
      reference.SetupVega();
      reference.PressureVega(temperature, density, pressure, compressibility);
    }
    return pressure.val / 100.0;
  }

  /**
   * Checks native closure and the analytic pressure derivative against an independent central difference.
   *
   * @param model reference model index
   * @param component pure component
   * @param system initialized test fluid
   */
  private static void assertPressureAndSlope(int model, String component, SystemInterface system) {
    PhaseInterface phase = system.getPhase(0);
    PhaseEosInterface eos = (PhaseEosInterface) phase;
    double volume = phase.getMolarVolume();
    double amount = phase.getNumberOfMolesInPhase();
    double actual = nativePressure(model, component, phase.getTemperature(), volume);
    double slope = eos.calcPressuredV();
    assertEquals(system.getPressure(), actual, Math.max(1e-7, 1e-8 * system.getPressure()));
    assertEquals(actual, eos.calcPressure(), Math.max(1e-10, 1e-11 * Math.abs(actual)));
    double step = volume * 1e-5;
    double plus = nativePressure(model, component, phase.getTemperature(), volume + step);
    double minus = nativePressure(model, component, phase.getTemperature(), volume - step);
    double finiteDifference = (plus - minus) / (2.0 * step * amount);
    assertTrue(slope < 0.0);
    assertEquals(finiteDifference, slope, Math.max(1e-12, Math.abs(slope) * 2e-7),
        "Derivative must use bar per total internal volume, independent of inventory scale");
    FluidEquilibriumChecks.validatePressureRoot(phase);
    phase.setMolarVolume(volume * 1.01);
    assertEquals(nativePressure(model, component, phase.getTemperature(), phase.getMolarVolume()), eos.calcPressure(),
        Math.max(1e-10, 1e-11 * Math.abs(actual)),
        "Pressure must be recomputed at the stored volume, not from cached root Helmholtz derivatives");
  }

  /** Verifies gas roots, all hydrogen variants and extensive inventory scaling. */
  @Test
  void nativePressureAndTotalVolumeDerivativeCloseAcrossScaleAndHydrogenTypes() {
    String[][] components = {{"methane"}, {"hydrogen", "para-hydrogen", "ortho-hydrogen"}, {"helium"}};
    for (int model = 0; model < components.length; model++) {
      for (String component : components[model]) {
        for (double amount : new double[] {1e-6, 1.0, 1e6}) {
          for (double pressure : new double[] {1.0, 100.0}) {
            assertPressureAndSlope(model, component, fluid(model, component, 300.0, pressure, amount, PhaseType.GAS));
          }
        }
      }
    }
  }

  /** Verifies dense reference roots without changing the native equation parameters. */
  @Test
  void denseReferenceRootsUseTheSamePressureAndDerivativeContract() {
    assertPressureAndSlope(0, "methane", fluid(0, "methane", 120.0, 10.0, 1.0, PhaseType.LIQUID));
    assertPressureAndSlope(1, "hydrogen", fluid(1, "hydrogen", 20.0, 1.0, 1.0, PhaseType.LIQUID));
    assertPressureAndSlope(2, "helium", fluid(2, "helium", 4.0, 1.0, 1.0, PhaseType.LIQUID));
  }

  /** A corrupted stored volume must be rejected independently of the total amount. */
  @Test
  void pressureGateRejectsCorruptedReferenceVolumesAtEveryInventoryScale() {
    String[] components = {"methane", "hydrogen", "helium"};
    for (int model = 0; model < components.length; model++) {
      for (double amount : new double[] {1e-6, 1.0, 1e6}) {
        PhaseInterface phase = fluid(model, components[model], 300.0, 100.0, amount, PhaseType.GAS).getPhase(0);
        phase.setMolarVolume(phase.getMolarVolume() * 1.1);
        assertThrows(IllegalStateException.class, () -> FluidEquilibriumChecks.validatePressureRoot(phase));
      }
    }
  }
}
