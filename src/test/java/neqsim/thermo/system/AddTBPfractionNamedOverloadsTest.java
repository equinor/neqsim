package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.TbpClosure;

/**
 * Verifies the named <code>addTBPfraction_*</code> overloads.
 *
 * <p>
 * The point of the named family is that the method name states which properties the caller supplied. These tests
 * therefore assert that each overload actually stores what it was given and derives only what it was supposed to
 * derive.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class AddTBPfractionNamedOverloadsTest {
  /** Tolerance on a property that is stored verbatim rather than correlated. */
  private static final double EXACT_TOLERANCE = 1.0e-9;

  private SystemInterface newFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    return fluid;
  }

  private int lastComponentIndex(SystemInterface fluid) {
    return fluid.getPhase(0).getNumberOfComponents() - 1;
  }

  @Test
  void testMwSgMatchesTheClassicOverload() {
    SystemInterface named = newFluid();
    named.addTBPfraction_Mw_Sg("C7", 1.0, 0.092, 0.73);

    SystemInterface classic = newFluid();
    classic.addTBPfraction("C7", 1.0, 0.092, 0.73);

    int i = lastComponentIndex(named);
    assertEquals(classic.getPhase(0).getComponent(i).getTC(), named.getPhase(0).getComponent(i).getTC(),
        EXACT_TOLERANCE);
    assertEquals(classic.getPhase(0).getComponent(i).getPC(), named.getPhase(0).getComponent(i).getPC(),
        EXACT_TOLERANCE);
  }

  @Test
  void testMwTbStoresTheSuppliedBoilingPointAndMolarMass() {
    SystemInterface fluid = newFluid();
    double molarMass = 0.100;
    double boilingPoint = 372.0;
    fluid.addTBPfraction_Mw_Tb("C7", 1.0, molarMass, boilingPoint);

    int i = lastComponentIndex(fluid);
    assertEquals(molarMass, fluid.getPhase(0).getComponent(i).getMolarMass(), 1.0e-6);
    assertEquals(boilingPoint, fluid.getPhase(0).getComponent(i).getNormalBoilingPoint(), 0.5);
  }

  @Test
  void testMwTbAgreesWithTheDeprecatedNumericOverload() {
    SystemInterface named = newFluid();
    named.addTBPfraction_Mw_Tb("C7", 1.0, 0.100, 372.0);

    SystemInterface legacy = newFluid();
    legacy.addTBPfraction2("C7", 1.0, 0.100, 372.0);

    int i = lastComponentIndex(named);
    assertEquals(legacy.getPhase(0).getComponent(i).getTC(), named.getPhase(0).getComponent(i).getTC(), 1.0e-6);
    assertEquals(legacy.getPhase(0).getComponent(i).getPC(), named.getPhase(0).getComponent(i).getPC(), 1.0e-6);
  }

  @Test
  void testMwTbRejectsAClosureThatCannotBeInvertedForDensity() {
    SystemInterface fluid = newFluid();
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> fluid.addTBPfraction_Mw_Tb("C7", 1.0, 0.100, 372.0, TbpClosure.RIAZI_DAUBERT_1987));
    assertTrue(thrown.getCause().getMessage().contains("RIAZI_DAUBERT_1980"));
  }

  @Test
  void testSgTbStoresTheSuppliedDensityAndBoilingPoint() {
    SystemInterface fluid = newFluid();
    double density = 0.734;
    double boilingPoint = 447.3;
    fluid.addTBPfraction_Sg_Tb("C10", 1.0, density, boilingPoint);

    int i = lastComponentIndex(fluid);
    assertEquals(boilingPoint, fluid.getPhase(0).getComponent(i).getNormalBoilingPoint(), 0.5);
    // The default RD-1987 closure should land close to the real nC10 molar mass of 142.3 g/mol.
    assertEquals(0.1423, fluid.getPhase(0).getComponent(i).getMolarMass(), 0.006);
  }

  @Test
  void testSgTbWithTbpModelClosureMatchesTheDeprecatedNumericOverload() {
    SystemInterface named = newFluid();
    named.addTBPfraction_Sg_Tb("C10", 1.0, 0.734, 447.3, TbpClosure.TBP_MODEL);

    SystemInterface legacy = newFluid();
    legacy.addTBPfraction3("C10", 1.0, 0.734, 447.3);

    int i = lastComponentIndex(named);
    assertEquals(legacy.getPhase(0).getComponent(i).getMolarMass(), named.getPhase(0).getComponent(i).getMolarMass(),
        1.0e-6);
  }

  @Test
  void testTbKwDerivesDensityExactlyFromTheWatsonDefinition() {
    SystemInterface fluid = newFluid();
    double boilingPoint = 447.3;
    double watsonK = 12.0;
    fluid.addTBPfraction_Tb_Kw("C10", 1.0, boilingPoint, watsonK);

    // SG = (1.8*Tb)^(1/3) / Kw, no correlation involved.
    double expectedDensity = Math.pow(1.8 * boilingPoint, 1.0 / 3.0) / watsonK;
    int i = lastComponentIndex(fluid);
    assertEquals(boilingPoint, fluid.getPhase(0).getComponent(i).getNormalBoilingPoint(), 0.5);
    assertEquals(expectedDensity, fluid.getPhase(0).getComponent(i).getNormalLiquidDensity(), 1.0e-6);
  }

  @Test
  void testTbPnaOfAPureParaffinMatchesTheParaffinWatsonFactor() {
    SystemInterface viaPna = newFluid();
    viaPna.addTBPfraction_Tb_Pna("C10", 1.0, 447.3, 1.0, 0.0, 0.0);

    SystemInterface viaKw = newFluid();
    viaKw.addTBPfraction_Tb_Kw("C10", 1.0, 447.3, viaPna.calculateWatsonKFromPna(1.0, 0.0, 0.0));

    int i = lastComponentIndex(viaPna);
    assertEquals(viaKw.getPhase(0).getComponent(i).getMolarMass(), viaPna.getPhase(0).getComponent(i).getMolarMass(),
        EXACT_TOLERANCE);
  }

  @Test
  void testTbPnaGivesAHeavierAndDenserFractionAsAromaticContentRises() {
    SystemInterface paraffinic = newFluid();
    paraffinic.addTBPfraction_Tb_Pna("cut", 1.0, 450.0, 0.90, 0.10, 0.0);

    SystemInterface aromatic = newFluid();
    aromatic.addTBPfraction_Tb_Pna("cut", 1.0, 450.0, 0.20, 0.20, 0.60);

    int i = lastComponentIndex(paraffinic);
    double paraffinicDensity = paraffinic.getPhase(0).getComponent(i).getNormalLiquidDensity();
    double aromaticDensity = aromatic.getPhase(0).getComponent(i).getNormalLiquidDensity();
    assertTrue(aromaticDensity > paraffinicDensity, "an aromatic cut at the same boiling point must be denser, got "
        + aromaticDensity + " vs " + paraffinicDensity);
  }

  @Test
  void testWatsonKFromPnaBlendsLinearlyAndValidatesTheSum() {
    SystemInterface fluid = newFluid();
    assertEquals(0.6 * 12.8 + 0.25 * 11.0 + 0.15 * 10.1, fluid.calculateWatsonKFromPna(0.60, 0.25, 0.15), 1.0e-9);
    assertEquals(11.5, fluid.calculateWatsonKFromPna(0.5, 0.0, 0.5, 13.0, 11.0, 10.0), 1.0e-9);

    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> fluid.calculateWatsonKFromPna(0.5, 0.25, 0.15));
    assertTrue(thrown.getCause().getMessage().contains("sum to 1"));

    assertThrows(RuntimeException.class, () -> fluid.calculateWatsonKFromPna(1.2, -0.2, 0.0));
  }

  @Test
  void testMwSgTbStoresAllThreeWithoutCorrelatingAnything() {
    SystemInterface fluid = newFluid();
    double molarMass = 0.150;
    double density = 0.800;
    double boilingPoint = 500.0;
    fluid.addTBPfraction_Mw_Sg_Tb("C11", 1.0, molarMass, density, boilingPoint);

    int i = lastComponentIndex(fluid);
    assertEquals(molarMass, fluid.getPhase(0).getComponent(i).getMolarMass(), 1.0e-6);
    assertEquals(density, fluid.getPhase(0).getComponent(i).getNormalLiquidDensity(), 1.0e-6);
    assertEquals(boilingPoint, fluid.getPhase(0).getComponent(i).getNormalBoilingPoint(), 0.5);
  }

  @Test
  void testMwSgCritStoresTheSuppliedCriticalProperties() {
    SystemInterface fluid = newFluid();
    double criticalTemperature = 560.0;
    double criticalPressure = 30.0;
    double acentricFactor = 0.49;
    fluid.addTBPfraction_Mw_Sg_Crit("C7", 1.0, 0.092, 0.73, criticalTemperature, criticalPressure, acentricFactor);

    int i = lastComponentIndex(fluid);
    assertEquals(criticalTemperature, fluid.getPhase(0).getComponent(i).getTC(), 1.0e-6);
    assertEquals(criticalPressure, fluid.getPhase(0).getComponent(i).getPC(), 1.0e-6);
    assertEquals(acentricFactor, fluid.getPhase(0).getComponent(i).getAcentricFactor(), 1.0e-6);
  }

  @Test
  void testBoilingPointPinDoesNotLeakIntoTheNextFraction() {
    // The pin lives on the shared TBP model. A missing finally block would silently give every
    // later fraction the same boiling point.
    SystemInterface pinned = newFluid();
    pinned.addTBPfraction_Mw_Sg_Tb("C7", 1.0, 0.092, 0.73, 480.0);
    pinned.addTBPfraction_Mw_Sg("C10", 1.0, 0.142, 0.78);

    SystemInterface clean = newFluid();
    clean.addTBPfraction_Mw_Sg("C10", 1.0, 0.142, 0.78);

    int pinnedIndex = lastComponentIndex(pinned);
    int cleanIndex = lastComponentIndex(clean);
    assertEquals(clean.getPhase(0).getComponent(cleanIndex).getNormalBoilingPoint(),
        pinned.getPhase(0).getComponent(pinnedIndex).getNormalBoilingPoint(), 1.0e-6);
  }

  @Test
  void testNonPositiveArgumentsAreRejected() {
    SystemInterface fluid = newFluid();
    assertThrows(RuntimeException.class, () -> fluid.addTBPfraction_Mw_Tb("C7", 1.0, -0.1, 372.0));
    assertThrows(RuntimeException.class, () -> fluid.addTBPfraction_Mw_Tb("C7", 1.0, 0.1, 0.0));
    assertThrows(RuntimeException.class, () -> fluid.addTBPfraction_Sg_Tb("C7", 1.0, 0.0, 372.0));
    assertThrows(RuntimeException.class, () -> fluid.addTBPfraction_Tb_Kw("C7", 1.0, 372.0, -1.0));
    assertThrows(RuntimeException.class, () -> fluid.addTBPfraction_Mw_Sg_Tb("C7", 1.0, 0.1, 0.7, -1.0));
  }

  @Test
  void testAWildlyWrongUnitIsRejectedRatherThanCharacterized() {
    SystemInterface fluid = newFluid();
    // Molar mass given in g/mol instead of kg/mol produces a non-physical specific gravity.
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> fluid.addTBPfraction_Mw_Tb("C7", 1.0, 100.0, 372.0));
    assertTrue(thrown.getCause().getMessage().contains("kg/mol"),
        "the message should point at the likely unit mistake, got: " + thrown.getCause().getMessage());
  }
}
