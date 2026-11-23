package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Regression tests for the Pedersen wax split of TBP fractions and for building the wax phase of
 * characterized pseudo components.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PedersenWaxSplitTest extends neqsim.NeqSimTest {
  /**
   * Builds a light oil with one C20 TBP fraction.
   *
   * @param density normal liquid density of the C20 fraction in g/cm3
   * @return uncharacterized fluid
   */
  private static SystemInterface oil(double density) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("n-heptane", 0.3);
    fluid.addTBPfraction("C20", 0.6, 0.28, density);
    return fluid;
  }

  /**
   * Adds the wax phase, optionally after writing the pseudo components to the temporary database.
   *
   * @param fluid characterized fluid
   * @param createDatabase whether to call {@code createDatabase(true)} with temporary tables
   */
  private static void addWaxPhase(SystemInterface fluid, boolean createDatabase) {
    if (createDatabase) {
      NeqSimDataBase.setCreateTemporaryTables(true);
      fluid.createDatabase(true);
    }
    fluid.setMixingRule(2);
    fluid.addSolidComplexPhase("wax");
    fluid.setMultiphaseWaxCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
  }

  /** A fraction not denser than the n-paraffin of its molar mass is entirely wax forming, not NaN. */
  @Test
  void paraffinicFractionIsEntirelyWaxFormingInsteadOfNaN() {
    // Pedersen n-paraffin density at M = 280 g/mol is 0.3915 + 0.0675 ln(280) = 0.772 g/cm3.
    SystemInterface fluid = oil(0.76);
    fluid.getWaxModel().addTBPWax();
    double wax = fluid.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles();
    double nonWax = fluid.getPhase(0).getComponent("C20_PC").getNumberOfmoles();
    assertEquals(0.6, wax, 1e-12);
    assertEquals(0.0, nonWax, 1e-12);
  }

  /** The split is bounded to [0, 1] of the fraction and conserves its moles. */
  @Test
  void splitIsBoundedAndConservesMoles() {
    for (double density : new double[] {0.70, 0.76, 0.80, 0.85, 0.95, 1.05}) {
      SystemInterface fluid = oil(density);
      fluid.getWaxModel().addTBPWax();
      double wax = fluid.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles();
      double nonWax = fluid.getPhase(0).getComponent("C20_PC").getNumberOfmoles();
      assertTrue(Double.isFinite(wax) && wax >= 0.0 && wax <= 0.6, "density " + density);
      assertEquals(0.6, wax + nonWax, 1e-12, "density " + density);
    }
  }

  /** Repeated calls, as during parameter tuning, must not move more and more of a fraction into wax. */
  @Test
  void repeatedAddTbpWaxIsIdempotent() {
    SystemInterface fluid = oil(0.85);
    fluid.getWaxModel().addTBPWax();
    double first = fluid.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles();
    fluid.getWaxModel().addTBPWax();
    fluid.getWaxModel().addTBPWax();
    assertEquals(first, fluid.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles(), 1e-12);
    assertEquals(0.6 - first, fluid.getPhase(0).getComponent("C20_PC").getNumberOfmoles(), 1e-12);

    // Changing a parameter re-splits from the total of the pair, with or without removeWax().
    fluid.getWaxModel().setWaxParameter(0, 1.2);
    fluid.getWaxModel().addTBPWax();
    double retuned = fluid.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles();
    SystemInterface reference = oil(0.85);
    reference.getWaxModel().setWaxParameter(0, 1.2);
    reference.getWaxModel().addTBPWax();
    assertEquals(reference.getPhase(0).getComponent("waxC20_PC").getNumberOfmoles(), retuned, 1e-12);
  }

  /** The wax phase of a pseudo component must carry its characterization without a temporary database. */
  @Test
  void waxPhaseCopiesPseudoComponentCharacterization() {
    SystemInterface fluid = oil(0.85);
    fluid.getWaxModel().addTBPWax();
    addWaxPhase(fluid, false);
    ComponentInterface fluidComponent = fluid.getPhase(0).getComponent("waxC20_PC");
    ComponentInterface solidComponent = fluid.getPhases()[5].getComponent(fluidComponent.getComponentNumber());
    assertTrue(solidComponent.isWaxFormer());
    assertEquals(fluidComponent.getMolarMass(), solidComponent.getMolarMass(), 1e-12);
    assertEquals(fluidComponent.getTC(), solidComponent.getTC(), 1e-9);
    assertEquals(fluidComponent.getPC(), solidComponent.getPC(), 1e-9);
    assertEquals(fluidComponent.getAcentricFactor(), solidComponent.getAcentricFactor(), 1e-12);
    assertEquals(fluidComponent.getTriplePointTemperature(), solidComponent.getTriplePointTemperature(), 1e-9);
    assertEquals(fluidComponent.getHeatOfFusion(), solidComponent.getHeatOfFusion(), 1e-9);
  }

  /** WAT must not depend on whether createDatabase(true) was called before adding the wax phase. */
  @Test
  void watIsIndependentOfTemporaryDatabase() {
    double[] wat = new double[2];
    for (int k = 0; k < 2; k++) {
      SystemInterface fluid = oil(0.85);
      fluid.getWaxModel().addTBPWax();
      addWaxPhase(fluid, k == 1);
      new ThermodynamicOperations(fluid).calcWAT();
      wat[k] = fluid.getTemperature("C");
    }
    // Before the fix the run without a database returned the n-heptane freezing onset near -112 C.
    assertEquals(wat[1], wat[0], 1e-3);
    assertTrue(wat[0] > 0.0 && wat[0] < 40.0, "WAT " + wat[0] + " C");
  }
}
