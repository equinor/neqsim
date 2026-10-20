package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Independent arithmetic regressions for ISO 6976 relative density and both Wobbe indices.
 *
 * <p>
 * The 2016 air factors are from ISO 6976:2016, clause 10.5 (Annex A.3, Table A.4), at 101.325 kPa. Component constants
 * are explicit fixtures from ISO6976constants2016.csv and ISO6976constants.csv; expected results do not use calculated
 * properties or internal fields of either implementation. The legacy fixture preserves its existing 15 degree
 * correction for the 15.55 degree volume key.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ISO6976AirFactorTest extends neqsim.NeqSimTest {
  /** Relative molar amounts from issue 4458, normalized independently below. */
  private static final double[] AMOUNTS = {0.931819, 0.025618, 0.010335, 0.015391};
  /** Component order shared by all fixtures. */
  private static final String[] NAMES = {"methane", "ethane", "nitrogen", "CO2"};
  /** Implemented volume-correction keys in degrees Celsius. */
  private static final double[] VOLUME = {0.0, 15.0, 15.55, 20.0};
  /** Independent combustion keys in degrees Celsius. */
  private static final double[] ENERGY = {0.0, 15.0, 15.55, 20.0, 25.0};
  /** Molar masses in g/mol, indexed by edition (1995, 2016) and component. */
  private static final double[][] MASSES = {{16.043, 30.070, 28.0135, 44.010}, {16.04246, 30.06904, 28.0134, 44.0095}};
  /** Dry air compression factors, indexed by edition and volume key. */
  private static final double[][] AIR = {{0.99941, 0.99958, 0.99958, 0.99963},
      {0.999419, 0.999595, 0.999601, 0.999645}};
  /** Component summation factors, indexed by edition, volume key and component. */
  private static final double[][][] FACTORS = {
      {{0.0490, 0.1000, 0.0224, 0.0819}, {0.0447, 0.0922, 0.0173, 0.0748}, {0.0447, 0.0922, 0.0173, 0.0748},
          {0.0436, 0.0894, 0.0173, 0.0728}},
      {{0.04886, 0.0997, 0.0214, 0.0821}, {0.04452, 0.0919, 0.0170, 0.0752}, {0.04437, 0.0916, 0.0169, 0.0749},
          {0.04317, 0.0895, 0.0156, 0.0730}}};
  /** Methane/ethane superior heats in kJ/mol, indexed by edition and combustion key. */
  private static final double[][][] SUPERIOR = {
      {{892.97, 1564.34}, {891.56, 1562.14}, {891.51, 1562.06}, {891.09, 1561.41}, {890.63, 1560.69}},
      {{892.92, 1564.35}, {891.51, 1562.14}, {891.46, 1562.06}, {891.05, 1561.42}, {890.58, 1560.69}}};
  /** Methane/ethane inferior heats in kJ/mol, identical for these two edition fixtures. */
  private static final double[][] INFERIOR = {{802.82, 1429.12}, {802.69, 1428.84}, {802.69, 1428.83},
      {802.65, 1428.74}, {802.60, 1428.64}};

  /**
   * Enumerates both editions, all implemented volume tables, combustion keys and reference states.
   *
   * @return eighty independent reference-condition cases
   */
  static Stream<Arguments> referenceConditions() {
    List<Arguments> cases = new ArrayList<>();
    for (int edition = 0; edition < 2; edition++) {
      for (int volume = 0; volume < VOLUME.length; volume++) {
        for (int energy = 0; energy < ENERGY.length; energy++) {
          for (String state : new String[] {"ideal", "real"}) {
            cases.add(Arguments.of(edition, volume, energy, state));
          }
        }
      }
    }
    return cases.stream();
  }

  /**
   * Checks the formula chain against explicit constants, including both Wobbe routes and aliases.
   *
   * @param edition zero for 1995, one for 2016
   * @param volume index of the metering reference temperature
   * @param energy index of the independent combustion reference temperature
   * @param state ideal or real reference state
   */
  @ParameterizedTest(name = "edition={0}, volumeIndex={1}, energyIndex={2}, state={3}")
  @MethodSource("referenceConditions")
  void agreesWithIndependentFormulaChain(int edition, int volume, int energy, String state) {
    SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
    double total = 0.0;
    for (int i = 0; i < NAMES.length; i++) {
      gas.addComponent(NAMES[i], AMOUNTS[i]);
      total += AMOUNTS[i];
    }
    gas.setMixingRule("classic");
    gas.init(0);
    double mass = 0.0;
    double factor = 0.0;
    double superiorHeat = 0.0;
    double inferiorHeat = 0.0;
    for (int i = 0; i < NAMES.length; i++) {
      double fraction = AMOUNTS[i] / total;
      mass += fraction * MASSES[edition][i];
      factor += fraction * FACTORS[edition][volume][i];
      if (i < 2) {
        superiorHeat += fraction * SUPERIOR[edition][energy][i];
        inferiorHeat += fraction * INFERIOR[energy][i];
      }
    }
    double z = 1.0 - factor * factor;
    double correction = "real".equals(state) ? z : 1.0;
    double relativeDensity = mass / 28.96546 * ("real".equals(state) ? AIR[edition][volume] / z : 1.0);
    double gasConstant = edition == 1 ? 8.3144621 : 8.314510;
    double molarDensity = 101325.0 / (gasConstant * (VOLUME[volume] + 273.15) * correction);
    double gcv = superiorHeat * molarDensity;
    double lcv = inferiorHeat * molarDensity;
    Standard_ISO6976 standard = edition == 1 ? new Standard_ISO6976_2016(gas, VOLUME[volume], ENERGY[energy], "volume")
        : new Standard_ISO6976(gas, VOLUME[volume], ENERGY[energy], "volume");
    standard.setReferenceState(state);
    standard.calculate();
    assertEquals(mass, standard.getValue("MolarMass"), 1e-10);
    assertEquals(z, standard.getValue("CompressionFactor"), 1e-12);
    assertEquals(relativeDensity, standard.getValue("RelativeDensity"), 1e-12);
    assertEquals(gcv, standard.getValue("GCV"), 1e-8);
    assertEquals(lcv, standard.getValue("LCV"), 1e-8);
    double superiorWobbe = gcv / Math.sqrt(relativeDensity);
    double inferiorWobbe = lcv / Math.sqrt(relativeDensity);
    for (String parameter : new String[] {"SuperiorWobbeIndex", "WI", "WobbeIndex"}) {
      assertEquals(superiorWobbe, standard.getValue(parameter), 1e-8, parameter);
      assertEquals(superiorWobbe / 3600.0, standard.getValue(parameter, "kWh"), 1e-11, parameter);
    }
    assertEquals(inferiorWobbe, standard.getValue("InferiorWobbeIndex"), 1e-8);
    assertEquals(inferiorWobbe / 3600.0, standard.getValue("InferiorWobbeIndex", "kWh"), 1e-11);
    assertEquals(VOLUME[volume], standard.getVolRefT());
    assertEquals(ENERGY[energy], standard.getEnergyRefT());
  }
}
