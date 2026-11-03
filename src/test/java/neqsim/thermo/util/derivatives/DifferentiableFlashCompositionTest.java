package neqsim.thermo.util.derivatives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent reflash checks for equilibrium composition sensitivities.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DifferentiableFlashCompositionTest {
  /**
   * Builds an interior VLE fixture with a declared EOS and inventory scale.
   *
   * @param model zero for SRK, one for PR, two for associating CPA
   * @param scale total feed moles
   * @return converged two-phase fluid
   */
  private SystemInterface fluid(int model, double scale) {
    SystemInterface result;
    if (model == 2) {
      result = new SystemSrkCPAstatoil(298.15, 50.0);
      result.addComponent("methane", 0.98 * scale);
      result.addComponent("water", 0.02 * scale);
      result.setMixingRule(10);
    } else {
      result = model == 0 ? new SystemSrkEos(250.0, 30.0) : new SystemPrEos(250.0, 30.0);
      result.addComponent("methane", 0.55 * scale);
      result.addComponent("ethane", 0.20 * scale);
      result.addComponent("propane", 0.15 * scale);
      result.addComponent("n-heptane", 0.10 * scale);
      result.setMixingRule(2);
    }
    new ThermodynamicOperations(result).TPflash();
    result.initProperties();
    assertEquals(2, result.getNumberOfPhases());
    return result;
  }

  /**
   * Independently reflashes a normalized feed perturbation (z+h e_j)/(1+h).
   *
   * @param original baseline fluid
   * @param component perturbed species
   * @param step signed dimensionless addition
   * @return new equilibrium without changing the baseline
   */
  private SystemInterface perturb(SystemInterface original, int component, double step) {
    SystemInterface result = original.clone();
    double[] z = original.getMolarComposition();
    for (int i = 0; i < z.length; i++) {
      z[i] = (z[i] + (i == component ? step : 0.0)) / (1.0 + step);
    }
    result.setMolarComposition(z);
    new ThermodynamicOperations(result).TPflash();
    result.initProperties();
    assertEquals(original.getNumberOfPhases(), result.getNumberOfPhases());
    return result;
  }

  /**
   * Reads physical vapor fraction independently of phase-array ordering.
   *
   * @param fluid equilibrium fluid
   * @return vapor mole fraction
   */
  private double beta(SystemInterface fluid) {
    return fluid.getPhase(PhaseType.GAS).getBeta();
  }

  /**
   * Reads a phase composition ratio.
   *
   * @param fluid equilibrium fluid
   * @param component component index
   * @return vapor/liquid K-value
   */
  private double kValue(SystemInterface fluid, int component) {
    int liquid = fluid.getPhase(0).getType() == PhaseType.GAS ? 1 : 0;
    return fluid.getPhase(PhaseType.GAS).getComponent(component).getx()
        / fluid.getPhase(liquid).getComponent(component).getx();
  }

  /**
   * Applies a fixed relative gate with an absolute floor near zero.
   *
   * @param expected independent numerical derivative
   * @param actual returned derivative
   */
  private void close(double expected, double actual) {
    assertEquals(expected, actual, 2.0e-7 + 2.0e-4 * Math.abs(expected));
  }

  /** Verifies every K and beta feed derivative at two perturbation sizes and mole scales. */
  @Test
  void normalizedFeedSensitivitiesMatchIndependentReflashes() {
    for (int model = 0; model < 3; model++) {
      for (double scale : new double[] {1.0, 1000.0}) {
        SystemInterface f = fluid(model, scale);
        FlashGradients g = new DifferentiableFlash(f).computeFlashGradients();
        assertTrue(g.isValid(), g.getErrorMessage());
        // Binary K-values are feed-independent. Larger CPA probes resolve reflash roundoff
        // in this near-zero derivative without relaxing the fixed derivative error gate.
        double probe = model == 2 ? 2.0e-4 : 2.0e-5;
        for (double h : new double[] {probe, probe / 2.0}) {
          for (int j = 0; j < f.getNumberOfComponents(); j++) {
            SystemInterface plus = perturb(f, j, h);
            SystemInterface minus = perturb(f, j, -h);
            close((beta(plus) - beta(minus)) / (2.0 * h), g.getDBetadz(j));
            for (int i = 0; i < f.getNumberOfComponents(); i++) {
              close((kValue(plus, i) - kValue(minus, i)) / (2.0 * h), g.getDKdz(i, j));
            }
          }
        }
      }
    }
  }

  /** Verifies the composition gradient of molecular weight against its exact normalized-addition value. */
  @Test
  void molecularWeightHasNonzeroNormalizedCompositionGradient() {
    SystemInterface f = fluid(0, 1000.0);
    PropertyGradient g = new DifferentiableFlash(f).computePropertyGradient("molarmass");
    for (int j = 0; j < f.getNumberOfComponents(); j++) {
      assertEquals(f.getComponent(j).getMolarMass() - f.getMolarMass(), g.getDerivativeWrtComponent(j), 1.0e-12);
    }
  }

  /** Verifies that the same wrapper observes temperature and composition changes. */
  @Test
  void repeatedWrapperTracksCurrentFluid() {
    SystemInterface f = fluid(0, 1.0);
    DifferentiableFlash wrapper = new DifferentiableFlash(f);
    FlashGradients old = wrapper.computeFlashGradients();
    f.setTemperature(260.0);
    new ThermodynamicOperations(f).TPflash();
    FlashGradients expected = new DifferentiableFlash(f).computeFlashGradients();
    FlashGradients current = wrapper.computeFlashGradients();
    assertTrue(expected.isValid());
    assertEquals(expected.getBeta(), current.getBeta(), 1.0e-12);
    assertTrue(Math.abs(old.getBeta() - current.getBeta()) > 1.0e-3);
    f.setMolarComposition(new double[] {0.5, 0.25, 0.15, 0.1});
    new ThermodynamicOperations(f).TPflash();
    expected = new DifferentiableFlash(f).computeFlashGradients();
    current = wrapper.computeFlashGradients();
    assertTrue(current.isValid(), current.getErrorMessage());
    assertEquals(expected.getDBetadz(0), current.getDBetadz(0), 1.0e-12);
  }

  /** Verifies that property evaluation does not overwrite any phase inventory or fugacity state. */
  @Test
  void propertyDerivativesPreserveCallerStateExactly() {
    SystemInterface f = fluid(0, 10.0);
    double[] before = f.getPhase(0).getMolarComposition();
    double initialBeta = f.getPhase(0).getBeta();
    double initialFugacity = f.getPhase(0).getComponent(0).getLogFugacityCoefficient();
    new DifferentiableFlash(f).computePropertyGradient("density");
    assertEquals(initialBeta, f.getPhase(0).getBeta());
    assertEquals(initialFugacity, f.getPhase(0).getComponent(0).getLogFugacityCoefficient());
    for (int i = 0; i < before.length; i++) {
      assertEquals(before[i], f.getPhase(0).getComponent(i).getx());
    }
  }

  /** Rejects a three-phase state rather than treating its last liquid as the only liquid. */
  @Test
  void threePhaseFlashDoesNotReturnTwoPhaseSensitivities() {
    SystemInterface f = new SystemSrkEos(300.0, 10.0);
    f.addComponent("methane", 0.7);
    f.addComponent("n-heptane", 0.15);
    f.addComponent("water", 0.15);
    f.setMixingRule(2);
    f.setMultiPhaseCheck(true);
    new ThermodynamicOperations(f).TPflash();
    assertEquals(3, f.getNumberOfPhases());
    assertFalse(new DifferentiableFlash(f).computeFlashGradients().isValid());
    assertThrows(IllegalArgumentException.class, () -> new DifferentiableFlash(f).computePropertyGradient("density"));
  }

  /**
   * Reads independently specified molar/intensive observables.
   *
   * @param f initialized equilibrium
   * @param name observable name
   * @return value in the derivative API's documented units
   */
  private double property(SystemInterface f, String name) {
    switch (name) {
    case "density":
      return f.getDensity("kg/m3");
    case "enthalpy":
      return f.getEnthalpy("J/mol");
    case "entropy":
      return f.getEntropy("J/molK");
    case "Cp":
      return f.getCp("J/molK");
    case "compressibility":
      return f.getZ();
    case "molarvolume":
      return f.getVolume("m3") / f.getTotalNumberOfMoles();
    case "gibbsenergy":
      return f.getGibbsEnergy() / f.getTotalNumberOfMoles();
    case "internalenergy":
      return f.getInternalEnergy() / f.getTotalNumberOfMoles();
    case "beta":
      return beta(f);
    default:
      return f.getMolarMass();
    }
  }

  /** Verifies shared local-property gradients against independent full equilibrium calculations. */
  @Test
  void batchPropertyGradientsMatchReflashes() {
    String[] names = {"density", "enthalpy", "entropy", "Cp", "compressibility", "molarvolume", "gibbsenergy",
        "internalenergy", "beta", "molarmass"};
    for (int model = 0; model < 3; model++) {
      SystemInterface f = fluid(model, 10.0);
      PropertyGradient[] gradients = new DifferentiableFlash(f).computePropertyGradients(names);
      assertEquals(names.length, gradients.length);
      for (int i = 0; i < names.length; i++) {
        assertEquals(property(f, names[i]), gradients[i].getValue(),
            1.0e-10 * Math.max(1.0, Math.abs(property(f, names[i]))));
      }
      for (int direction = 0; direction < f.getNumberOfComponents() + 2; direction++) {
        double initialStep = direction == 0 ? 0.01 : direction == 1 ? 0.002 : model == 2 ? 2.0e-4 : 2.0e-5;
        for (double step : new double[] {initialStep, initialStep / 2.0}) {
          SystemInterface plus;
          SystemInterface minus;
          if (direction >= 2) {
            plus = perturb(f, direction - 2, step);
            minus = perturb(f, direction - 2, -step);
          } else {
            plus = f.clone();
            minus = f.clone();
            if (direction == 0) {
              plus.setTemperature(f.getTemperature() + step);
              minus.setTemperature(f.getTemperature() - step);
            } else {
              plus.setPressure(f.getPressure() + step);
              minus.setPressure(f.getPressure() - step);
            }
            new ThermodynamicOperations(plus).TPflash();
            new ThermodynamicOperations(minus).TPflash();
            plus.initProperties();
            minus.initProperties();
            assertEquals(2, plus.getNumberOfPhases());
            assertEquals(2, minus.getNumberOfPhases());
            FlashGradients split = new DifferentiableFlash(f).computeFlashGradients();
            for (int component = 0; component < f.getNumberOfComponents(); component++) {
              close((kValue(plus, component) - kValue(minus, component)) / (2.0 * step),
                  direction == 0 ? split.getDKdT(component) : split.getDKdP(component));
            }
          }
          for (int i = 0; i < names.length; i++) {
            double expected = (property(plus, names[i]) - property(minus, names[i])) / (2.0 * step);
            double actual = direction == 0 ? gradients[i].getDerivativeWrtTemperature()
                : direction == 1 ? gradients[i].getDerivativeWrtPressure()
                    : gradients[i].getDerivativeWrtComponent(direction - 2);
            assertEquals(expected, actual, 2.0e-7 + 2.0e-4 * Math.abs(expected),
                names[i] + ", EOS " + model + ", direction " + direction + ", step " + step);
          }
        }
      }
    }
  }

  /** Verifies SI volume/energy units and intensive invariance to feed scaling. */
  @Test
  void molarPropertyValuesAndGradientsAreInventoryIndependent() {
    String[] names = {"gibbsenergy", "internalenergy", "molarvolume", "enthalpy"};
    PropertyGradient[] unit = new DifferentiableFlash(fluid(0, 1.0)).computePropertyGradients(names);
    PropertyGradient[] scaled = new DifferentiableFlash(fluid(0, 1000.0)).computePropertyGradients(names);
    assertEquals("m3/mol", unit[2].getUnit());
    for (int i = 0; i < names.length; i++) {
      close(unit[i].getValue(), scaled[i].getValue());
      close(unit[i].getDerivativeWrtTemperature(), scaled[i].getDerivativeWrtTemperature());
      close(unit[i].getDerivativeWrtPressure(), scaled[i].getDerivativeWrtPressure());
      for (int j = 0; j < 4; j++) {
        close(unit[i].getDerivativeWrtComponent(j), scaled[i].getDerivativeWrtComponent(j));
      }
    }
  }

  /** Verifies a defined one-sided trace-component derivative in a single gas phase. */
  @Test
  void singlePhaseZeroFeedUsesNormalizedOneSidedDerivative() {
    SystemInterface f = new SystemSrkEos(400.0, 10.0);
    f.addComponent("methane", 1.0);
    f.addComponent("ethane", 0.0);
    f.setMixingRule(2);
    new ThermodynamicOperations(f).TPflash();
    PropertyGradient[] g = new DifferentiableFlash(f)
        .computePropertyGradients(new String[] {"molarmass", "beta", "density"});
    assertEquals(f.getComponent(1).getMolarMass() - f.getMolarMass(), g[0].getDerivativeWrtComponent(1), 1.0e-12);
    assertEquals(1.0, g[1].getValue(), 1.0e-12);
    assertEquals(0.0, g[1].getDerivativeWrtTemperature());
    assertEquals(0.0, g[1].getDerivativeWrtComponent(1));
    f.initProperties();
    double h = 1.0e-4;
    double rho0 = f.getDensity("kg/m3");
    double rho1 = perturb(f, 1, h).getDensity("kg/m3");
    double rho2 = perturb(f, 1, 2.0 * h).getDensity("kg/m3");
    close((-3.0 * rho0 + 4.0 * rho1 - rho2) / (2.0 * h), g[2].getDerivativeWrtComponent(1));
    assertThrows(IllegalArgumentException.class, () -> new DifferentiableFlash(f).computePropertyGradient("entropy"));
    assertFalse(new DifferentiableFlash(f).computeFlashGradients().isValid());
  }

  /** Verifies unsupported composition and non-equilibrium inputs fail explicitly. */
  @Test
  void invalidStateDoesNotProduceValidSensitivities() {
    SystemInterface f = fluid(0, 1.0);
    f.getPhase(0).getComponent(0).setx(0.5);
    DifferentiableFlash wrapper = new DifferentiableFlash(f);
    assertFalse(wrapper.computeFlashGradients().isValid());
    assertThrows(IllegalArgumentException.class, () -> wrapper.computePropertyGradient("density"));
    assertThrows(IllegalArgumentException.class, () -> wrapper.computePropertyGradients(null));
  }

  /** Executes the complete public API example maintained in the differentiable thermodynamics guide. */
  @Test
  void documentationExample() {
    SystemInterface fluid = new SystemSrkEos(250.0, 30.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("propane", 0.2);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();

    DifferentiableFlash derivatives = new DifferentiableFlash(fluid);
    FlashGradients flash = derivatives.computeFlashGradients();
    if (!flash.isValid()) {
      throw new IllegalStateException(flash.getErrorMessage());
    }
    PropertyGradient[] properties = derivatives.computePropertyGradients(new String[] {"density", "enthalpy", "beta"});
    double densityPerBar = properties[0].getDerivativeWrtPressure();
    double enthalpyAlongFeedChange = properties[1].directionalDerivative(0.0, 0.0, new double[] {0.01, -0.01});
    assertTrue(Double.isFinite(densityPerBar));
    assertTrue(Math.abs(enthalpyAlongFeedChange) > 0.1);
  }

  /** Verifies that sharing local states returns the same sensitivities as separate public calls. */
  @Test
  void batchMatchesScalarCalls() {
    for (int model = 0; model < 3; model++) {
      SystemInterface f = fluid(model, 1.0);
      DifferentiableFlash derivative = new DifferentiableFlash(f);
      String[] names = {"density", "enthalpy", "entropy", "Cp", "compressibility", "viscosity", "thermalconductivity",
          "soundspeed", "joulethomson", "gamma", "Cv"};
      PropertyGradient[] batch = derivative.computePropertyGradients(names);
      for (int i = 0; i < names.length; i++) {
        PropertyGradient scalar = derivative.computePropertyGradient(names[i]);
        close(scalar.getValue(), batch[i].getValue());
        double[] expected = scalar.toArray();
        double[] actual = batch[i].toArray();
        for (int j = 0; j < expected.length; j++) {
          close(expected[j], actual[j]);
        }
      }
    }
  }
}
