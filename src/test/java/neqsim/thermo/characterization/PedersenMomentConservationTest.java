package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Independent mole, molecular-weight moment and liquid-volume closure for plus-fraction splitting.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PedersenMomentConservationTest {
  /**
   * Supplies light, black and heavy oil means, lump counts and distribution models.
   *
   * @return characterization cases
   */
  static Stream<Arguments> cases() {
    Stream.Builder<Arguments> cases = Stream.builder();
    for (String model : new String[] {"Pedersen", "Whitson Gamma Model"}) {
      for (int lumps : new int[] {6, 12, 28}) {
        if ("Pedersen".equals(model)) {
          cases.add(Arguments.of(model, lumps, 0.150, 0.75));
          cases.add(Arguments.of(model, lumps, 0.590, 0.94));
        }
        cases.add(Arguments.of(model, lumps, 0.2400000474587263, 0.9193944763618844));
      }
    }
    return cases.build();
  }

  /**
   * Verifies the supplied extensive moments before and after actual lump construction.
   *
   * @param model distribution model
   * @param lumps requested number of plus lumps
   * @param molarMass supplied plus molar mass in kg/mol
   * @param density supplied plus density in g/cm3
   */
  @ParameterizedTest
  @MethodSource("cases")
  void preservesInputMoments(String model, int lumps, double molarMass, double density) {
    SystemInterface fluid = new SystemPrEos(361.15, 350.0);
    fluid.addComponent("methane", 65.6137);
    fluid.addPlusFraction("C7", 34.3863, molarMass, density);
    fluid.init(0);
    double[] before = moments(fluid);
    fluid.getCharacterization().setPlusFractionModel(model);
    fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(lumps);
    fluid.getCharacterization().characterisePlusFraction();
    fluid.init(0);
    double[] after = moments(fluid);
    assertEquals(before[0], after[0], before[0] * 1.0e-11, "total moles");
    assertEquals(before[1], after[1], before[1] * 1.0e-11, "sum(n_i M_i)");
    PlusFractionModelInterface split = fluid.getCharacterization().getPlusFractionModel();
    double moleSum = 0.0;
    double massSum = 0.0;
    double volumeSum = 0.0;
    for (int carbon = split.getFirstPlusFractionNumber(); carbon < split.getLastPlusFractionNumber(); carbon++) {
      double fraction = split.getZ()[carbon];
      double mass = split.getM()[carbon];
      double cutDensity = split.getDens()[carbon];
      assertTrue(Double.isFinite(fraction) && fraction > 0.0, "positive finite fraction");
      assertTrue(Double.isFinite(mass) && mass > 0.0, "positive finite molar mass");
      assertTrue(Double.isFinite(cutDensity) && cutDensity > 0.0, "positive finite density");
      moleSum += fraction;
      massSum += fraction * mass;
      volumeSum += fraction * mass / cutDensity;
    }
    assertEquals(0.343863, moleSum, 0.343863e-11, "plus mole fraction");
    assertEquals(molarMass, massSum / moleSum, molarMass * 1.0e-11, "plus mean MW");
    // Density uses mass / additive liquid volume, rather than a mole-weighted mean.
    if ("Pedersen".equals(model)) {
      assertEquals(density, massSum / volumeSum, 2.0e-6, "plus density basis");
    }
  }

  /**
   * Independently sums total component moles and their molecular-weight moment.
   *
   * @param fluid system whose phase-0 component records carry overall inventories
   * @return moles and mass in mol and kg
   */
  private static double[] moments(SystemInterface fluid) {
    double moles = 0.0;
    double mass = 0.0;
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double amount = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
      double molarMass = fluid.getPhase(0).getComponent(component).getMolarMass();
      assertTrue(Double.isFinite(amount) && amount > 0.0);
      assertTrue(Double.isFinite(molarMass) && molarMass > 0.0);
      moles += amount;
      mass += amount * molarMass;
    }
    return new double[] {moles, mass};
  }
}
