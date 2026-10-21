package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.physicalproperties.methods.gasphysicalproperties.density.Density;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for density initialization and independent interfacial models on cloned fluids.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class SurfaceTensionStateLifecycleTest {
  /**
   * Creates a hydrocarbon fluid, optionally flashed without calculating transport properties.
   *
   * @param flash whether to establish the two bulk phases
   * @return SRK methane/decane fluid
   */
  private static SystemInterface fluid(boolean flash) {
    SystemInterface fluid = new SystemSrkEos(310.0, 50.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("n-decane", 0.4);
    fluid.setMixingRule(2);
    if (flash) {
      new ThermodynamicOperations(fluid).TPflash();
      assertEquals(2, fluid.getNumberOfPhases());
    }
    return fluid;
  }

  /**
   * Creates a correlation directly so its initialization contract is tested without dispatcher assistance.
   *
   * @param parachor whether to use Parachor instead of Firozabadi-Ramley
   * @param fluid fluid to bind
   * @return correlation
   */
  private static SurfaceTension correlation(boolean parachor, SystemInterface fluid) {
    return parachor ? new ParachorSurfaceTension(fluid) : new FirozabadiRamleyInterfaceTension(fluid);
  }

  /**
   * Creates an unflashed feed that separates into gas, oil and water at 300 K and 10 bara.
   *
   * @return three-component feed
   */
  private static SystemInterface threePhaseFeed() {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.3);
    fluid.addComponent("water", 0.2);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * A flashed EOS state is enough for a correlation, even without initialized transport properties.
   *
   * @param parachor whether to use Parachor
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void correlationInitializesOnlyDensity(boolean parachor) {
    SystemInterface reference = fluid(true);
    reference.initProperties();
    double expected = correlation(parachor, reference).calcSurfaceTension(0, 1);
    SystemInterface fluid = fluid(true);
    for (int phase = 0; phase < 2; phase++) {
      PhysicalProperties properties = fluid.getPhase(phase).getPhysicalProperties();
      properties.density = 0.0;
      properties.viscosity = -17.0;
      properties.conductivity = -19.0;
    }
    assertEquals(expected, correlation(parachor, fluid).calcSurfaceTension(0, 1), 1e-12);
    for (int phase = 0; phase < 2; phase++) {
      PhysicalProperties properties = fluid.getPhase(phase).getPhysicalProperties();
      assertTrue(properties.density > 0.0);
      assertEquals(-17.0, properties.viscosity, 0.0);
      assertEquals(-19.0, properties.conductivity, 0.0);
    }
  }

  /**
   * A finite stale cache is refreshed after a new flash, rather than mistaken for current density.
   *
   * @param parachor whether to use Parachor
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void correlationRefreshesFiniteStaleDensity(boolean parachor) {
    SystemInterface fluid = fluid(true);
    fluid.initProperties();
    fluid.setTemperature(320.0);
    new ThermodynamicOperations(fluid).TPflash();
    SystemInterface reference = fluid(true);
    reference.setTemperature(320.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double expected = correlation(parachor, reference).calcSurfaceTension(0, 1);
    for (int phase = 0; phase < 2; phase++) {
      fluid.getPhase(phase).getPhysicalProperties().density = 1.0;
    }
    assertEquals(expected, correlation(parachor, fluid).calcSurfaceTension(0, 1), 1e-12);
  }

  /**
   * The pure-component contribution API has the same density initialization contract as the mixture API.
   *
   * @param parachor whether to use Parachor
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pureComponentContributionRefreshesDensity(boolean parachor) {
    SystemInterface fluid = fluid(true);
    fluid.initProperties();
    SurfaceTension model = correlation(parachor, fluid);
    double expected = model.calcPureComponentSurfaceTension(0);
    for (int phase = 0; phase < 2; phase++) {
      fluid.getPhase(phase).getPhysicalProperties().density = 0.0;
    }
    assertTrue(expected > 0.0);
    assertEquals(expected, model.calcPureComponentSurfaceTension(0), 1e-12);
  }

  /**
   * Supplies invalid outputs from a configured density model for each correlation.
   *
   * @return correlation selector and invalid density
   */
  private static Stream<Arguments> invalidDensities() {
    return Stream.of(true, false).flatMap(parachor -> Stream.of(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)
        .map(density -> Arguments.of(parachor, density)));
  }

  /**
   * Initializing density must not hide a genuinely failed physical-property model behind an EOS estimate.
   *
   * @param parachor whether to use Parachor
   * @param density invalid density returned by the configured model
   */
  @ParameterizedTest
  @MethodSource("invalidDensities")
  void invalidDensityModelStillFails(boolean parachor, double density) {
    SystemInterface fluid = fluid(true);
    PhysicalProperties properties = fluid.getPhase(0).getPhysicalProperties();
    properties.densityCalc = new FixedDensity(properties, density);
    assertThrows(IllegalStateException.class, () -> correlation(parachor, fluid).calcSurfaceTension(0, 1));
    assertThrows(IllegalStateException.class, () -> correlation(parachor, fluid).calcPureComponentSurfaceTension(0));
  }

  /**
   * A clone made before flashing must use its own phases and preserve every explicit model selection.
   *
   * @param name selected model
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void cloneBindsSelectedModelToItsOwnBulkState(String name) {
    SystemInterface source = fluid(false);
    source.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", name);
    SurfaceTension original = (SurfaceTension) source.getInterphaseProperties().getSurfaceTensionModel(0);
    SystemInterface clone = source.clone();
    assertNotSame(source.getInterphaseProperties(), clone.getInterphaseProperties());
    SurfaceTension copied = (SurfaceTension) clone.getInterphaseProperties().getSurfaceTensionModel(0);
    assertNotSame(original, copied);
    assertEquals(original.getClass(), copied.getClass());
    assertSame(source, original.system);
    assertSame(clone, copied.system);
    new ThermodynamicOperations(clone).TPflash();
    clone.initProperties();
    SystemInterface reference = fluid(true);
    reference.initProperties();
    reference.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", name);
    assertEquals(reference.getInterfacialTension("gas", "oil"), clone.getInterfacialTension("gas", "oil"), 1e-10);
    clone.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Parachor");
    assertSame(original, source.getInterphaseProperties().getSurfaceTensionModel(0));
  }

  /** Custom numerical parameters must survive cloning without sharing mutable configuration. */
  @Test
  void clonePreservesCdftParameters() {
    SystemInterface source = fluid(false);
    source.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
    CDFTSurfaceTension model = (CDFTSurfaceTension) source.getInterphaseProperties().getSurfaceTensionModel(0);
    model.setNGrid(101);
    model.setUsePredictiveMode(false);
    model.setAttractiveRangeFactor(1.5);
    SystemInterface clone = source.clone();
    CDFTSurfaceTension copied = (CDFTSurfaceTension) clone.getInterphaseProperties().getSurfaceTensionModel(0);
    assertEquals(101, copied.getNGrid());
    assertEquals(1.5, copied.getAttractiveRangeFactor(), 0.0);
    assertFalse(copied.isUsePredictiveMode());
    copied.setNGrid(201);
    assertEquals(101, model.getNGrid());
  }

  /**
   * Numbered selections and all three interface slots survive a clone made before flashing.
   *
   * @param selection numbered model set
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5})
  void clonedThreePhaseModelSetsUseTheRequestedFluid(int selection) {
    SystemInterface source = threePhaseFeed();
    source.getInterphaseProperties().setInterfacialTensionModel(selection);
    SystemInterface clone = source.clone();
    new ThermodynamicOperations(clone).TPflash();
    clone.initProperties();
    SystemInterface reference = threePhaseFeed();
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    reference.getInterphaseProperties().setInterfacialTensionModel(selection);
    assertEquals(3, clone.getNumberOfPhases());
    assertEquals(selection, clone.getInterphaseProperties().getInterfacialTensionModel());
    for (String[] pair : new String[][] {{"gas", "oil"}, {"gas", "aqueous"}, {"oil", "aqueous"}}) {
      double expected = reference.getInterfacialTension(pair[0], pair[1]);
      assertTrue(expected > 0.0);
      assertEquals(expected, clone.getInterfacialTension(pair[0], pair[1]), 1e-10);
    }
  }

  /**
   * Completed profiles belong to the calculated fluid and cannot be exposed as a clone's own calculation.
   *
   * @param simple whether to use Simple instead of Linear Gradient Theory
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void cloneRequiresItsOwnCompletedGradientProfile(boolean simple) {
    SystemInterface source = fluid(true);
    source.initProperties();
    source.getInterphaseProperties().setInterfacialTensionModel("gas", "oil",
        simple ? "Simple Gradient Theory" : "Linear Gradient Theory");
    double expected = source.getInterfacialTension("gas", "oil");
    SurfaceTension original = (SurfaceTension) source.getInterphaseProperties().getSurfaceTensionModel(0);
    SystemInterface clone = source.clone();
    SurfaceTension copied = (SurfaceTension) clone.getInterphaseProperties().getSurfaceTensionModel(0);
    if (simple) {
      GTSurfaceTensionSimple originalGt = (GTSurfaceTensionSimple) original;
      GTSurfaceTensionSimple copiedGt = (GTSurfaceTensionSimple) copied;
      assertEquals(200, originalGt.getPressure().length);
      assertThrows(IllegalStateException.class, copiedGt::getPressure);
      assertNotSame(originalGt.influenceParam, copiedGt.influenceParam);
      copiedGt.influenceParam[0] *= 2.0;
      assertEquals(2.0 * originalGt.influenceParam[0], copiedGt.influenceParam[0], 0.0);
    } else {
      assertEquals(200, ((LGTSurfaceTension) original).getPressure().length);
      assertThrows(IllegalStateException.class, ((LGTSurfaceTension) copied)::getPressure);
    }
    assertEquals(expected, clone.getInterfacialTension("gas", "oil"), 1e-10);
    assertEquals(expected, source.getInterfacialTension("gas", "oil"), 1e-10);
  }

  /**
   * Controlled density model used to exercise the physical-property failure contract.
   *
   * @author NeqSim development team
   * @version 1.0
   */
  private static final class FixedDensity extends Density {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Density returned by this model. */
    private final double value;

    /**
     * Creates a density model with a controlled output.
     *
     * @param properties owning physical properties
     * @param value density in kg/m3
     */
    private FixedDensity(PhysicalProperties properties, double value) {
      super(properties);
      this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDensity() {
      return value;
    }
  }
}
