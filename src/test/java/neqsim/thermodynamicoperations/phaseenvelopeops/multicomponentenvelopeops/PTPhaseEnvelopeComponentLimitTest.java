package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Regression tests for the number of components the Michelsen phase envelope accepts.
 *
 * <p>
 * The cricondentherm and cricondenbar composition arrays were declared with a fixed length of 100 while the loops that
 * fill them are bounded by the component count, so any fluid with more than 100 components failed with
 * {@code ArrayIndexOutOfBoundsException: Index 100 out of bounds for length 100}. Reported against a 101-component
 * characterised fluid.
 * </p>
 *
 * <p>
 * Both ways of building a large fluid are covered, because they fail differently. A fluid of named database components
 * traces an envelope, reaches the cricondentherm update and throws. A fluid padded with TBP fractions does not trace an
 * envelope at all in this solver, so it never reaches that code and passes even when the bug is present; for that route
 * only the array length reveals the defect. Testing just one of the two would miss half the problem.
 * </p>
 *
 * @author Pablo Dupuy
 * @version 1.0
 */
public class PTPhaseEnvelopeComponentLimitTest {
  /** Backbone component names, chosen so the mixture actually traces an envelope. */
  private static final String[] BACKBONE_NAMES = new String[] {"methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "n-hexane"};

  /** Backbone mole fractions, summing to 0.94; the remainder is spread over the filler components. */
  private static final double[] BACKBONE_FRACTIONS = new double[] {0.780, 0.080, 0.040, 0.010, 0.015, 0.006, 0.005,
      0.004};

  /** Component count used for the over-the-limit cases. */
  private static final int ABOVE_LIMIT = 110;

  /** The length the composition arrays used to be fixed at. */
  private static final int OLD_FIXED_LENGTH = 100;

  /** Names of the four composition arrays exposed through {@code get(String)}. */
  private static final String[] COMPOSITION_KEYS = new String[] {"cricondenthermX", "cricondenthermY", "cricondenbarX",
      "cricondenbarY"};

  /**
   * Read hydrocarbon names from the component database, lightest first.
   *
   * <p>
   * Ordering by molar mass keeps the selection deterministic and keeps the fluid light enough that the continuation
   * actually traces an envelope.
   * </p>
   *
   * @param count maximum number of names to return; fewer are returned if the database holds fewer
   * @return hydrocarbon names, excluding those already used as backbone components
   */
  private List<String> lightHydrocarbonNames(int count) {
    List<String> names = new ArrayList<String>();
    List<String> backbone = Arrays.asList(BACKBONE_NAMES);
    try (NeqSimDataBase database = new NeqSimDataBase()) {
      ResultSet rs = database.getResultSet("SELECT NAME FROM COMP WHERE COMPTYPE='HC' ORDER BY MOLARMASS, NAME");
      while (rs.next() && names.size() < count) {
        String name = rs.getString(1);
        if (!backbone.contains(name)) {
          names.add(name);
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException("could not read component names from the database", ex);
    }
    return names;
  }

  /**
   * Add the backbone components to a fluid.
   *
   * @param fluid system to add the components to
   * @return the mole fraction left over for the filler components
   */
  private double addBackbone(SystemInterface fluid) {
    double used = 0.0;
    for (int i = 0; i < BACKBONE_NAMES.length; i++) {
      fluid.addComponent(BACKBONE_NAMES[i], BACKBONE_FRACTIONS[i]);
      used += BACKBONE_FRACTIONS[i];
    }
    return 1.0 - used;
  }

  /**
   * Build a light gas padded with named database components.
   *
   * @param numberOfComponents total component count to reach
   * @return a system built only with {@code addComponent}
   */
  private SystemInterface buildFromNamedComponents(int numberOfComponents) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 15.0, 50.0);
    double spare = addBackbone(fluid);
    List<String> fillers = lightHydrocarbonNames(numberOfComponents - BACKBONE_NAMES.length);
    assertTrue(fillers.size() > 0, "the component database must provide filler hydrocarbons");
    double each = spare / fillers.size();
    for (int i = 0; i < fillers.size(); i++) {
      fluid.addComponent(fillers.get(i), each);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build a light gas padded with TBP fractions.
   *
   * @param numberOfComponents total component count to reach
   * @return a system whose filler components come from {@code addTBPfraction}
   */
  private SystemInterface buildFromTbpFractions(int numberOfComponents) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 15.0, 50.0);
    double spare = addBackbone(fluid);
    int fillers = numberOfComponents - BACKBONE_NAMES.length;
    double each = spare / fillers;
    for (int i = 0; i < fillers; i++) {
      // Molar mass and density are stepped so the fractions stay distinguishable.
      fluid.addTBPfraction("C" + (7 + i), each, 100.0 + 2.0 * i, 0.75 + 0.0005 * i);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Assert that the four composition arrays carry one entry per component.
   *
   * @param ops operations object on which the envelope has already been calculated
   * @param numberOfComponents expected array length
   * @param label description used in the assertion messages
   */
  private void assertCompositionArrayLengths(ThermodynamicOperations ops, int numberOfComponents, String label) {
    for (int i = 0; i < COMPOSITION_KEYS.length; i++) {
      String key = COMPOSITION_KEYS[i];
      double[] values = ops.get(key);
      assertNotNull(values, label + ": " + key + " must be available");
      assertEquals(numberOfComponents, values.length, label + ": " + key + " must have one entry per component");
    }
  }

  /**
   * Named-component fluid above the old limit. This traces an envelope, so it reaches the cricondentherm update, and is
   * the case that threw for the reporting user.
   */
  @Test
  void testNamedComponentFluidAboveLimit() {
    SystemInterface fluid = buildFromNamedComponents(ABOVE_LIMIT);
    int numberOfComponents = fluid.getPhase(0).getNumberOfComponents();
    assertTrue(numberOfComponents > OLD_FIXED_LENGTH,
        "fluid must exceed the old fixed array length, was " + numberOfComponents);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope(),
        "named-component fluid above 100 components must not overflow the criconden arrays");
    assertCompositionArrayLengths(ops, numberOfComponents, "named components");
  }

  /**
   * TBP-fraction fluid above the old limit. It does not trace an envelope here, so the array length is what exposes the
   * defect.
   */
  @Test
  void testTbpFractionFluidAboveLimit() {
    SystemInterface fluid = buildFromTbpFractions(ABOVE_LIMIT);
    int numberOfComponents = fluid.getPhase(0).getNumberOfComponents();
    assertTrue(numberOfComponents > OLD_FIXED_LENGTH,
        "fluid must exceed the old fixed array length, was " + numberOfComponents);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope(),
        "TBP-fraction fluid above 100 components must not overflow the criconden arrays");
    assertCompositionArrayLengths(ops, numberOfComponents, "TBP fractions");
  }

  /**
   * A fluid at the old boundary must keep working, so the fix does not disturb existing behaviour.
   */
  @Test
  void testEnvelopeStillWorksAtOneHundredComponents() {
    SystemInterface fluid = buildFromNamedComponents(OLD_FIXED_LENGTH);
    assertEquals(OLD_FIXED_LENGTH, fluid.getPhase(0).getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope(), "phase envelope must still work at exactly 100 components");
    assertNotNull(ops.get("dewT"));
    assertCompositionArrayLengths(ops, OLD_FIXED_LENGTH, "boundary");
  }
}
