package neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.NeqSimTest;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Equation and lifecycle checks for the selectable Filippov model's pressure correction.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FilippovConductivityMethodTest extends NeqSimTest {
  /**
   * Creates a synthetic phase with known constants, isolating the correction from database fits and EOS errors.
   *
   * @param tr reduced temperature
   * @param pr reduced pressure
   * @return model with a constant 0.1 W/(m K) base conductivity, Tc=500 K and Pc=40 bara
   */
  private FilippovConductivityMethod synthetic(double tr, double pr) {
    SystemInterface fluid = new SystemSrkEos(500 * tr, 40 * pr);
    fluid.addComponent("n-hexane", 1.0);
    fluid.init(0);
    ComponentInterface component = fluid.getPhase(0).getComponent(0);
    component.setTC(500);
    component.setPC(40);
    component.setLiquidConductivityParameter(0.1, 0);
    component.setLiquidConductivityParameter(0, 1);
    component.setLiquidConductivityParameter(0, 2);
    PhysicalProperties properties = fluid.getPhase(0).getPhysicalProperties();
    properties.setConductivityModel("Filippov");
    return (FilippovConductivityMethod) properties.getConductivityModel();
  }

  /**
   * Checks frozen independent evaluations of the CHEMPAK section 8.6.5 expression, not experimental accuracy.
   *
   * @param tr reduced temperature
   * @param pr reduced pressure
   * @param factor independent dimensionless equation value
   */
  @ParameterizedTest
  @CsvSource({"0.5, 1, 1", "0.5, 50, 1.301066653091826", "0.5, 200, 1.817163198065763", "0.6, 5, 1.048075621075822",
      "0.7, 10, 1.130606599229482", "0.7, 100, 1.775347912869548", "0.8, 1, 1.001328776498109",
      "0.8, 50, 1.570267830251932", "0.9, 10, 1.186960091117727", "0.9, 200, 2.826078929373058"})
  void publishedDerivedExpression(double tr, double pr, double factor) {
    FilippovConductivityMethod model = synthetic(tr, pr);
    assertEquals(0.1 * factor, model.calcConductivity(), 1e-14);
    model.setUsePressureCorrection(false);
    assertEquals(0.1, model.calcConductivity(), 1e-15);
  }

  /** Checks the retained floor and implementation cutoffs without claiming a physical validity envelope. */
  @Test
  void floorAndCutoffs() {
    assertEquals(0.1, synthetic(0.7, 0.009).calcConductivity(), 1e-15);
    assertEquals(0.1, synthetic(0.5, 0.01).calcConductivity(), 1e-15);
    assertEquals(0.1, synthetic(0.95, 100).calcConductivity(), 1e-15);
    assertTrue(synthetic(Math.nextDown(0.95), 100).calcConductivity() > 0.2);
  }

  /** Demonstrates that clones must own their mutable pure-conductivity work array. */
  @Test
  void clonedWorkArrayIsIndependent() {
    FilippovConductivityMethod original = synthetic(0.7, 10);
    original.calcConductivity();
    FilippovConductivityMethod copy = original.clone();
    assertNotSame(original.pureComponentConductivity, copy.pureComponentConductivity);
    copy.pureComponentConductivity[0] = 12;
    assertEquals(0.1, original.pureComponentConductivity[0], 1e-15);
  }

  /** Verifies reusing a model on a different component count resizes its work storage. */
  @Test
  void phaseRebindingResizesStorage() {
    FilippovConductivityMethod reused = synthetic(0.7, 10);
    PhysicalProperties destination = liquid(1, false).getPhase(0).getPhysicalProperties();
    double expected = destination.getConductivityModel().calcConductivity();
    reused.setPhase(destination);
    assertEquals(expected, reused.calcConductivity(), 1e-14);
    assertEquals(2, reused.pureComponentConductivity.length);
  }

  /** Checks missing critical-data bypass separately from nonfinite state propagation. */
  @Test
  void unavailableCriticalDataIsNotACorrection() {
    SystemInterface fluid = liquid(1, false);
    FilippovConductivityMethod model = (FilippovConductivityMethod) fluid.getPhase(0).getPhysicalProperties()
        .getConductivityModel();
    model.setUsePressureCorrection(false);
    double uncorrected = model.calcConductivity();
    model.setUsePressureCorrection(true);
    for (double missing : new double[] {0, -1}) {
      for (int i = 0; i < 2; i++) {
        fluid.getPhase(0).getComponent(i).setTC(missing);
      }
      assertEquals(uncorrected, model.calcConductivity(), 1e-15);
    }
    for (double nonfinite : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      for (int i = 0; i < 2; i++) {
        fluid.getPhase(0).getComponent(i).setTC(nonfinite);
      }
      assertTrue(Double.isNaN(model.calcConductivity()));
    }
    for (int i = 0; i < 2; i++) {
      fluid.getPhase(0).getComponent(i).setTC(500);
      fluid.getPhase(0).getComponent(i).setPC(Double.NaN);
    }
    assertTrue(Double.isNaN(model.calcConductivity()));
    assertTrue(Double.isNaN(synthetic(Double.NaN, 10).calcConductivity()));
    assertTrue(Double.isNaN(synthetic(0.7, Double.NaN).calcConductivity()));
  }

  /** Checks the exact unity-floor shortcut and its neighbors at the highest retained reduced temperature. */
  @Test
  void lowPressureShortcutPreservesTheFloor() {
    for (double tr : new double[] {0.01, 0.5, 0.8, Math.nextDown(0.95)}) {
      for (double pr : new double[] {0.01, Math.nextDown(0.5), 0.5, Math.nextUp(0.5)}) {
        assertEquals(0.1, synthetic(tr, pr).calcConductivity(), 1e-15);
      }
    }
  }

  /** Verifies the three existing model-set routes on an aqueous phase without asserting mixture accuracy. */
  @Test
  void specializedSetsUseTheSameCorrection() {
    for (PhysicalPropertyModel set : new PhysicalPropertyModel[] {PhysicalPropertyModel.GLYCOL,
        PhysicalPropertyModel.AMINE, PhysicalPropertyModel.CO2WATER}) {
      SystemInterface fluid = new SystemSrkEos(350, 400);
      fluid.addComponent("water", 1);
      fluid.init(0);
      fluid.getPhase(0).setType(PhaseType.AQUEOUS);
      ComponentInterface component = fluid.getPhase(0).getComponent(0);
      component.setTC(500);
      component.setPC(40);
      component.setLiquidConductivityParameter(0.1, 0);
      component.setLiquidConductivityParameter(0, 1);
      component.setLiquidConductivityParameter(0, 2);
      fluid.setPhysicalPropertyModel(set);
      assertTrue(
          fluid.getPhase(0).getPhysicalProperties().getConductivityModel() instanceof FilippovConductivityMethod);
      assertEquals(0.1130606599229482,
          fluid.getPhase(0).getPhysicalProperties().getConductivityModel().calcConductivity(), 1e-13);
    }
  }

  /** Checks that changing composition reevaluates both the base mixing and pseudo-critical properties. */
  @Test
  void compositionChangeDoesNotReuseOldValues() {
    SystemInterface changed = liquid(1, false);
    double initial = evaluate(changed);
    changed.setMolarComposition(new double[] {0.25, 0.75});
    new ThermodynamicOperations(changed).TPflash();
    changed.initProperties();
    double result = evaluate(changed);
    assertTrue(Math.abs(result - initial) > 1e-5);
    SystemInterface fresh = liquid(1, false);
    fresh.setMolarComposition(new double[] {0.25, 0.75});
    new ThermodynamicOperations(fresh).TPflash();
    fresh.initProperties();
    assertEquals(result, evaluate(fresh), 1e-14);
    changed.setMolarComposition(new double[] {0.75, 0.25});
    new ThermodynamicOperations(changed).TPflash();
    changed.initProperties();
    assertEquals(initial, evaluate(changed), 1e-14);
  }

  /** Checks monotonic pressure response at fixed reduced temperatures away from the retained cutoff. */
  @Test
  void pressureTrend() {
    for (double tr : new double[] {0.5, 0.7, 0.9}) {
      double previous = 0;
      for (double pr : new double[] {0.01, 1, 5, 10, 50, 100, 200}) {
        double value = synthetic(tr, pr).calcConductivity();
        assertTrue(value >= previous);
        previous = value;
      }
    }
  }

  /** Executes the guide example through the physical-property facade, including the disable switch. */
  @Test
  void guideExampleAndPressureUnits() {
    SystemInterface fluid = new SystemSrkEos(350, 100);
    fluid.addComponent("n-hexane", 1);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    fluid.getPhase(0).getPhysicalProperties().setConductivityModel("Filippov");
    fluid.getPhase(0).initPhysicalProperties();
    double corrected = fluid.getPhase(0).getThermalConductivity("W/mK");
    FilippovConductivityMethod model = (FilippovConductivityMethod) fluid.getPhase(0).getPhysicalProperties()
        .getConductivityModel();
    model.setUsePressureCorrection(false);
    fluid.getPhase(0).initPhysicalProperties();
    double uncorrected = fluid.getPhase(0).getThermalConductivity("W/mK");
    assertTrue(corrected > uncorrected);
    model.setUsePressureCorrection(true);
    fluid.setPressure(10000, "kPa");
    fluid.getPhase(0).initPhysicalProperties();
    assertEquals(corrected, fluid.getPhase(0).getThermalConductivity("W/mK"), 1e-14);
  }

  /**
   * Creates and flashes a physical binary liquid without altering its critical or transport parameters.
   *
   * @param amount total moles
   * @param reverse whether to reverse insertion order
   * @return initialized liquid at 350 K and 100 bara
   */
  private SystemInterface liquid(double amount, boolean reverse) {
    SystemInterface fluid = new SystemSrkEos(350, 100);
    fluid.addComponent(reverse ? "n-heptane" : "n-hexane", amount * (reverse ? 0.25 : 0.75));
    fluid.addComponent(reverse ? "n-hexane" : "n-heptane", amount * (reverse ? 0.75 : 0.25));
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    fluid.getPhase(0).getPhysicalProperties().setConductivityModel("Filippov");
    return fluid;
  }

  /**
   * Evaluates the configured model directly, without hiding failures behind the physical-property facade.
   *
   * @param fluid configured fluid
   * @return conductivity in W/(m K)
   */
  private double evaluate(SystemInterface fluid) {
    return fluid.getPhase(0).getPhysicalProperties().getConductivityModel().calcConductivity();
  }

  /** Checks amount/order independence and repeated state changes through the existing phase route. */
  @Test
  void physicalRouteAndLifecycle() {
    SystemInterface original = liquid(1, false);
    double reference = evaluate(original);
    assertTrue(reference > 0.05 && reference < 0.3);
    assertEquals(reference, evaluate(liquid(0.001, false)), 1e-12);
    assertEquals(reference, evaluate(liquid(1000, true)), 1e-12);
    assertEquals(reference, evaluate(original), 1e-15);
    SystemInterface copy = original.clone();
    copy.setPressure(2000, "kPa");
    copy.setTemperature(370);
    copy.initProperties();
    double changed = evaluate(copy);
    assertTrue(Math.abs(changed - reference) > 1e-4);
    assertEquals(reference, evaluate(original), 1e-15);
    assertEquals(changed, evaluate(copy), 1e-15);
    copy.setPressure(100, "bara");
    copy.setTemperature(350);
    copy.initProperties();
    assertEquals(reference, evaluate(copy), 1e-12);
  }
}
