package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.SarirAtmosphericAssay;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies caloric secant acceleration without adding equilibrium flash evaluations.
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
class PHflashCaloricSecantTest extends neqsim.NeqSimTest {
  /** Explicit assay density assumptions retained from the existing public-assay regression. */
  private static final double[] SPECIFIC_GRAVITY = {0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000};
  /** Explicit assay molar masses in kg/mol retained from the existing public-assay regression. */
  private static final double[] MOLAR_MASS = {0.092957679997, 0.105352037330, 0.117746394663, 0.136337930662,
      0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324, 0.303661754657,
      0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649, 0.743661439976};

  /** Counts the actual inner TP flash calls rather than iterations of a local scalar update. */
  private static final class CountedTPflash extends TPflash {
    private static final long serialVersionUID = 1000L;
    private int calls;

    /**
     * Create a counted equilibrium operation.
     *
     * @param fluid material system owned by the PH flash
     */
    private CountedTPflash(SystemInterface fluid) {
      super(fluid);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
      calls++;
      super.run();
    }
  }

  /**
   * Create an equilibrated assay/steam target from explicitly supplied public regression assumptions.
   *
   * @return target state at 535.15 K and 2.3 bara with ten mole percent water
   */
  private static SystemInterface targetState() {
    SystemSrkEos fluid = new SystemSrkEos(SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15,
        SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0);
    SarirAtmosphericAssay.create(fluid, SPECIFIC_GRAVITY, MOLAR_MASS).apply();
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), "kg/hr");
    fluid.addComponent("water", fluid.getTotalNumberOfMoles() * 0.10 / 0.90);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTemperature(535.15);
    fluid.setPressure(2.3);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    return fluid;
  }

  /**
   * Nearby starts retain the same pressure-valid equilibrium and inventory with fewer full TP evaluations.
   */
  @Test
  void assaySteamCaloricSolveReusesCompletedEquilibriumResiduals() {
    SystemInterface target = targetState();
    double targetEnthalpy = target.getEnthalpy();
    double totalMoles = target.getTotalNumberOfMoles();
    double[] composition = target.getMolarComposition();
    for (double initialTemperature : new double[] {520.15, 550.15}) {
      SystemInterface fluid = target.clone();
      fluid.setTemperature(initialTemperature);
      PHflash flash = new PHflash(fluid, targetEnthalpy, 0);
      CountedTPflash inner = new CountedTPflash(fluid);
      flash.tpFlash = inner;

      flash.run();
      fluid.init(2);

      assertTrue(inner.calls <= 6, "The original Cp-only iteration needed nine inner TP flashes for this case");
      assertEquals(535.15, fluid.getTemperature(), 1.0e-6);
      assertEquals(targetEnthalpy, fluid.getEnthalpy(), Math.abs(targetEnthalpy) * 1.0e-8);
      assertEquals(2.3, fluid.getPressure(), 0.0);
      assertEquals(totalMoles, fluid.getTotalNumberOfMoles(), 0.0);
      assertEquals(target.getNumberOfPhases(), fluid.getNumberOfPhases());
      for (int component = 0; component < composition.length; component++) {
        assertEquals(composition[component], fluid.getMolarComposition()[component], 0.0);
        double recovered = 0.0;
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
        }
        assertEquals(composition[component], recovered, 1.0e-10);
      }
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertEquals(fluid.getPressure(), ((PhaseEos) fluid.getPhase(phase)).calcPressure(), 1.0e-7);
        assertEquals(target.getBeta(phase), fluid.getBeta(phase), 1.0e-8);
      }
    }
    assertEquals(535.15, target.getTemperature(), 0.0, "The source target remains unchanged");
    assertEquals(targetEnthalpy, target.getEnthalpy(), 0.0);
    assertEquals(totalMoles, target.getTotalNumberOfMoles(), 0.0);
  }
}
