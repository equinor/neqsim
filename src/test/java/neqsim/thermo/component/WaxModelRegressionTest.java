package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseWax;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/** Regression coverage for real alternative wax components, issue 3914. */
class WaxModelRegressionTest {
  private SystemInterface createFluid(String model) {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("methane", 6.78);
    system.addTBPfraction("C19", 10.13, 0.170, 0.7814);
    system.addPlusFraction("C20", 10.62, 0.381, 0.850871882888);
    system.getCharacterization().characterisePlusFraction();
    system.getWaxModel().addTBPWax();
    system.createDatabase(true);
    system.setMixingRule(2);
    system.setWaxModelType(model);
    system.addSolidComplexPhase("wax");
    system.setMultiphaseWaxCheck(true);
    system.setMultiPhaseCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
    system.init(0);
    system.init(1);
    return system;
  }

  @ParameterizedTest
  @CsvSource({"Pedersen, 275.0", "Won, 275.0", "Wilson, 275.0", "Coutinho, 275.0", "Pedersen, 261.0", "Won, 261.0",
      "Wilson, 261.0", "Coutinho, 261.0"})
  void characterizedFluidConservesEachComponent(String model, double temperature) {
    SystemInterface system = createFluid(model);
    system.setTemperature(temperature);
    system.setPressure(5.0);
    double[] inventory = new double[system.getNumberOfComponents()];
    double availableWax = 0.0;
    for (int i = 0; i < inventory.length; i++) {
      ComponentInterface component = system.getPhase(0).getComponent(i);
      inventory[i] = component.getNumberOfmoles();
      if (component.isWaxFormer()) {
        availableWax += inventory[i];
      }
    }
    new ThermodynamicOperations(system).TPflash();
    assertTrue(system.hasPhaseType("wax"), model + " must predict wax in the cold test fluid");
    PhaseInterface wax = system.getPhase("wax");
    String componentClass = "Won".equals(model) ? "ComponentWonWax"
        : "Wilson".equals(model) ? "ComponentWaxWilson"
            : "Coutinho".equals(model) ? "ComponentCoutinhoWax" : "ComponentWax";
    assertEquals(componentClass, wax.getComponent(0).getClass().getSimpleName());
    assertTrue(wax.getNumberOfMolesInPhase() <= availableWax + 1e-7,
        model + " wax cannot exceed the available wax-former inventory");
    assertTrue(wax.getComponent("methane").getx() < 1e-12);
    for (int i = 0; i < inventory.length; i++) {
      double recovered = 0.0;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        ComponentInterface component = system.getPhase(p).getComponent(i);
        recovered += component.getNumberOfMolesInPhase();
        assertTrue(Double.isFinite(component.getFugacityCoefficient()) && component.getFugacityCoefficient() > 0.0,
            model + " must return finite positive coefficients");
      }
      assertEquals(inventory[i], recovered, 1e-6 * Math.max(1.0, inventory[i]), model + " component balance " + i);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Won", "Wilson", "Coutinho"})
  void changingPopulatedWaxModelFailsExplicitly(String model) {
    SystemInterface system = createFluid("Pedersen");
    assertThrows(IllegalStateException.class, () -> system.setWaxModelType(model));
    PhaseWax wax = (PhaseWax) system.getPhase(5);
    assertEquals("Pedersen", wax.getWaxComponentModel());
    assertEquals(ComponentWax.class, wax.getComponent(0).getClass());
    assertThrows(IllegalStateException.class, () -> wax.setWaxComponentModel(model));
    system.setWaxModelType("pedersen");
  }

  @Test
  void morganKobayashiIndependentBenchmarkAndDomain() {
    // Published independent Chemicals MK example (J/mol).
    assertEquals(38728.00667307733, WaxModelCorrelations.vaporizationEnthalpy(553.15, 751.35, 0.302), 0.1);
    assertThrows(IllegalArgumentException.class, () -> WaxModelCorrelations.vaporizationEnthalpy(800.0, 751.35, 0.302));
    assertThrows(IllegalArgumentException.class,
        () -> WaxModelCorrelations.vaporizationEnthalpy(Double.NaN, 751.35, 0.302));
  }

  private double activity(ComponentInterface component, PhaseInterface phase) {
    if (component instanceof ComponentWonWax) {
      return ((ComponentWonWax) component).getWonActivityCoefficient(phase);
    }
    if (component instanceof ComponentWaxWilson) {
      return ((ComponentWaxWilson) component).getWilsonActivityCoefficient(phase);
    }
    if (component instanceof ComponentCoutinhoWax) {
      return Math.exp(((ComponentCoutinhoWax) component).calcLnGammaUNIQUAC(phase));
    }
    return 1.0;
  }

  @ParameterizedTest
  @ValueSource(strings = {"Pedersen", "Won", "Wilson", "Coutinho"})
  void pureWaxLimitExcludesMethaneAndHasCorrectPressureUnits(String model) {
    SystemInterface system = createFluid(model);
    PhaseInterface wax = system.getPhase(5);
    wax.setTemperature(275.0);
    wax.setPressure(50.0);
    int selected = -1;
    for (int i = 0; i < wax.getNumberOfComponents(); i++) {
      wax.getComponent(i).setx(1e-100);
      if (selected < 0 && wax.getComponent(i).isWaxFormer()) {
        selected = i;
      }
    }
    assertTrue(selected >= 0);
    ComponentSolid component = (ComponentSolid) wax.getComponent(selected);
    component.setx(1.0);
    assertEquals(1.0, activity(component, wax), 1e-10, "Pure solid activity must be unity");
    component.setx(0.8);
    wax.getComponent("methane").setx(0.2);
    assertEquals(1.0, activity(component, wax), 1e-10,
        "Excluded supercritical methane must not enter solid activity correlations");
    double actual = component.fugcoef2(wax);
    double t = wax.getTemperature();
    double tf = component.getTriplePointTemperature();
    double mw = 1000.0 * component.getMolarMass();
    double deltaCp = (0.3033 * mw - 4.635e-4 * mw * t) * 4.184;
    double exponent = -component.getHeatOfFusion() / (component.R * t) * (1.0 - t / tf)
        + deltaCp / component.R * (tf / t - 1.0 - Math.log(tf / t));
    double volumeSI = component.refPhase.getMolarVolume() * 1e-5;
    exponent += -0.1 * volumeSI * (50.0 - 1.0) * 1e5 / (component.R * t);
    double expected = component.refPhase.getComponent(0).getFugacityCoefficient() * Math.exp(exponent);
    assertEquals(expected, actual, Math.abs(expected) * 1e-10);
    component.setHeatOfFusion(Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> component.fugcoef2(wax));
    assertTrue(Double.isFinite(((ComponentSolid) wax.getComponent("methane")).fugcoef2(wax)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Won", "Wilson", "Coutinho"})
  void infiniteDilutionCoefficientRemainsFinite(String model) {
    SystemInterface system = createFluid(model);
    PhaseInterface wax = system.getPhase(5);
    wax.setTemperature(275.0);
    int selected = -1;
    for (int i = 0; i < wax.getNumberOfComponents(); i++) {
      if (wax.getComponent(i).isWaxFormer()) {
        selected = i;
        break;
      }
    }
    assertTrue(selected >= 0);
    ComponentSolid component = (ComponentSolid) wax.getComponent(selected);
    // Exercise the exact zero limit; Component.setx(0) currently preserves its previous value.
    component.x = 0.0;
    double coefficient = component.fugcoef2(wax);
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0);
  }

  @Test
  void invalidModelNameDoesNotChangeConfiguration() {
    SystemInterface system = createFluid("Pedersen");
    assertThrows(IllegalArgumentException.class, () -> system.setWaxModelType("typo"));
    assertThrows(IllegalArgumentException.class, () -> system.setWaxModelType(null));
    assertEquals("Pedersen", ((PhaseWax) system.getPhase(5)).getWaxComponentModel());
  }
}
