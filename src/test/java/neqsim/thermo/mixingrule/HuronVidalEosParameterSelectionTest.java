package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.h2.tools.Csv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhasePrCPA;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePrEosvolcor;
import neqsim.thermo.phase.PhaseSrkCPA;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * Qualifies independent cubic-EOS Huron-Vidal calibration selection without changing the shared test database.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalEosParameterSelectionTest {
  /** Legacy row columns, including the parameter orientation identities. */
  private static final String LEGACY_COLUMNS = "COMP1,COMP2,HVTYPE,HVALPHA,HVGIJ,HVGJI,HVGIJT,HVGJIT";
  /** Original shared coefficients used only as a selection fixture. */
  private static final String LEGACY_VALUES = "water,methane,HV,0.15,-123.6,4875.0,2.13,-6.54";
  /** Distinct synthetic SRK calibration, not a physical fit. */
  private static final String SRK_VALUES = "HV,0.21,1001.0,2001.0,0.31,0.41";
  /** Distinct synthetic PR calibration, not a physical fit. */
  private static final String PR_VALUES = "HV,0.42,3001.0,4001.0,0.51,0.61";

  /**
   * Every selected coefficient belongs to the requested EOS, including alpha and both directed offsets.
   *
   * @param suffix selected cubic EOS suffix
   * @throws Exception if the synthetic JDBC fixture cannot be read
   */
  @ParameterizedTest
  @ValueSource(strings = {"_SRK", "_PR"})
  void selectsIndependentCompleteEosCalibration(String suffix) throws Exception {
    EosMixingRuleHandler handler = load(fullColumns(), LEGACY_VALUES + "," + SRK_VALUES + "," + PR_VALUES, suffix,
        false);
    if ("_SRK".equals(suffix)) {
      assertParameters(handler, 0, 1, 0.21, 1001.0, 2001.0, 0.31, 0.41);
    } else {
      assertParameters(handler, 0, 1, 0.42, 3001.0, 4001.0, 0.51, 0.61);
    }
  }

  /**
   * Reversing the fluid component order preserves each selected row's directed interaction identities.
   *
   * @param suffix selected cubic EOS suffix
   * @throws Exception if the synthetic JDBC fixture cannot be read
   */
  @ParameterizedTest
  @ValueSource(strings = {"_SRK", "_PR"})
  void preservesDirectionUnderComponentOrderReversal(String suffix) throws Exception {
    EosMixingRuleHandler handler = load(fullColumns(), LEGACY_VALUES + "," + SRK_VALUES + "," + PR_VALUES, suffix,
        true);
    if ("_SRK".equals(suffix)) {
      assertParameters(handler, 1, 0, 0.21, 1001.0, 2001.0, 0.31, 0.41);
    } else {
      assertParameters(handler, 1, 0, 0.42, 3001.0, 4001.0, 0.51, 0.61);
    }
  }

  /**
   * Existing databases without EOS-specific columns retain the exact shared coefficients.
   *
   * @param suffix selected cubic EOS suffix
   * @throws Exception if the synthetic JDBC fixture cannot be read
   */
  @ParameterizedTest
  @ValueSource(strings = {"_SRK", "_PR"})
  void legacyDatabaseSchemaRemainsCompatible(String suffix) throws Exception {
    assertParameters(load(LEGACY_COLUMNS, LEGACY_VALUES, suffix, false), 0, 1, 0.15, -123.6, 4875.0, 2.13, -6.54);
  }

  /**
   * H2 imports omitted trailing CSV fields as SQL null, allowing unqualified legacy rows to remain unchanged.
   *
   * @param suffix selected cubic EOS suffix
   * @throws Exception if the synthetic JDBC fixture cannot be read
   */
  @ParameterizedTest
  @ValueSource(strings = {"_SRK", "_PR"})
  void omittedCsvOverrideValuesRemainNullAndUseLegacyFallback(String suffix) throws Exception {
    try (ResultSet row = new Csv().read(new StringReader(fullColumns() + "\n" + LEGACY_VALUES + "\n"), null)) {
      assertTrue(row.next());
      assertNull(row.getString("HVALPHA" + suffix));
      EosMixingRuleHandler handler = emptyHandler();
      handler.loadHuronVidalParameters(row,
          EosMixingRuleHandler.findHuronVidalOverrideColumns(row.getMetaData(), suffix), 0, 1);
      assertParameters(handler, 0, 1, 0.15, -123.6, 4875.0, 2.13, -6.54);
    }
  }

  /**
   * A partially populated selected fit is an input error rather than a blend of independently fitted parameter sets.
   *
   * @param partialValues incomplete synthetic PR calibration
   */
  @ParameterizedTest
  @ValueSource(strings = {"HV,0.42,3001.0,4001.0,0.51,", ",0.42,3001.0,4001.0,0.51,0.61", "HV,,,,,"})
  void rejectsPartialSelectedCalibration(String partialValues) {
    assertThrows(IllegalArgumentException.class,
        () -> load(fullColumns(), LEGACY_VALUES + "," + SRK_VALUES + "," + partialValues, "_PR", false));
  }

  /**
   * Non-finite or malformed calibration values cannot become plausible classic-pair fallbacks.
   *
   * @param invalidValue malformed synthetic alpha coefficient
   */
  @ParameterizedTest
  @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "not-a-number"})
  void rejectsInvalidSelectedCalibration(String invalidValue) {
    assertThrows(IllegalArgumentException.class, () -> load(fullColumns(),
        LEGACY_VALUES + "," + SRK_VALUES + ",HV," + invalidValue + ",3001.0,4001.0,0.51,0.61", "_PR", false));
  }

  /**
   * A selected family requires all six schema columns and a recognized pair selector.
   */
  @Test
  void rejectsPartialSchemaAndUnknownSelector() {
    assertThrows(IllegalArgumentException.class,
        () -> load(LEGACY_COLUMNS + ",HVALPHA_PR", LEGACY_VALUES + ",0.42", "_PR", false));
    assertThrows(IllegalArgumentException.class, () -> load(fullColumns(),
        LEGACY_VALUES + "," + SRK_VALUES + ",Unknown,0.42,3001,4001,0.51,0.61", "_PR", false));
  }

  /**
   * An incomplete fit for the other EOS cannot affect the selected complete calibration.
   *
   * @throws Exception if the synthetic JDBC fixture cannot be read
   */
  @Test
  void doesNotReadTheOtherEosCalibration() throws Exception {
    EosMixingRuleHandler handler = load(fullColumns(), LEGACY_VALUES + "," + SRK_VALUES + ",HV,,,,,", "_SRK", false);
    assertParameters(handler, 0, 1, 0.21, 1001.0, 2001.0, 0.31, 0.41);
  }

  /**
   * Cubic subclasses select their parent EOS while CPA retains its existing parameter path.
   */
  @Test
  void recognizesDerivedPrPhasesAndExcludesCpa() {
    assertEquals("_SRK", EosMixingRuleHandler.getHuronVidalEosSuffix(new PhaseSrkEos()));
    assertEquals("_PR", EosMixingRuleHandler.getHuronVidalEosSuffix(new PhasePrEos()));
    assertEquals("_PR", EosMixingRuleHandler.getHuronVidalEosSuffix(new PhasePrEosvolcor()));
    assertEquals("", EosMixingRuleHandler.getHuronVidalEosSuffix(new PhaseSrkCPA()));
    assertEquals("", EosMixingRuleHandler.getHuronVidalEosSuffix(new PhasePrCPA()));
  }

  /**
   * The bundled CSV declares both optional families without inventing an EOS-specific numerical calibration.
   *
   * @throws Exception if the bundled resource cannot be read
   */
  @Test
  void bundledHeaderSupportsBothEosFamiliesWithUnchangedLegacyRows() throws Exception {
    InputStream stream = getClass().getClassLoader().getResourceAsStream("data/INTER.csv");
    assertNotNull(stream);
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        ResultSet row = new Csv().read(reader, null)) {
      assertTrue(row.next());
      assertEquals(6, EosMixingRuleHandler.findHuronVidalOverrideColumns(row.getMetaData(), "_SRK").length);
      assertEquals(6, EosMixingRuleHandler.findHuronVidalOverrideColumns(row.getMetaData(), "_PR").length);
      assertNull(row.getString("HVGIJ_SRK"));
      assertNull(row.getString("HVGIJ_PR"));
      assertEquals(-123.6011438, Double.parseDouble(row.getString("HVGIJ")), 0.0);
    }
  }

  /**
   * Builds the complete optional schema used by synthetic calibration fixtures.
   *
   * @return CSV header containing shared, SRK and PR parameter families
   */
  private static String fullColumns() {
    return LEGACY_COLUMNS + ",HVTYPE_SRK,HVALPHA_SRK,HVGIJ_SRK,HVGJI_SRK,HVGIJT_SRK,HVGJIT_SRK"
        + ",HVTYPE_PR,HVALPHA_PR,HVGIJ_PR,HVGJI_PR,HVGIJT_PR,HVGJIT_PR";
  }

  /**
   * Runs the same oriented row loader used by the cubic mixing-rule setup without changing global database state.
   *
   * @param columns CSV schema
   * @param values CSV coefficient row
   * @param suffix selected EOS suffix
   * @param reversed whether the fluid component order is reversed relative to COMP1/COMP2
   * @return handler containing the selected parameter matrices
   * @throws Exception if the synthetic fixture cannot be read
   */
  private static EosMixingRuleHandler load(String columns, String values, String suffix, boolean reversed)
      throws Exception {
    try (ResultSet row = new Csv().read(new StringReader(columns + "\n" + values + "\n"), null)) {
      assertTrue(row.next());
      EosMixingRuleHandler handler = emptyHandler();
      int[] indices = EosMixingRuleHandler.findHuronVidalOverrideColumns(row.getMetaData(), suffix);
      handler.loadHuronVidalParameters(row, indices, reversed ? 1 : 0, reversed ? 0 : 1);
      return handler;
    }
  }

  /**
   * Allocates only the parameter arrays needed for a binary selection fixture.
   *
   * @return empty binary interaction matrices
   */
  private static EosMixingRuleHandler emptyHandler() {
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.HValpha = new double[2][2];
    handler.HVDij = new double[2][2];
    handler.HVDijT = new double[2][2];
    handler.classicOrHV = new String[2][2];
    return handler;
  }

  /**
   * Checks all coefficients and their directed component identities after selection.
   *
   * @param handler selected interaction matrices
   * @param first phase index of COMP1
   * @param second phase index of COMP2
   * @param alpha expected symmetric nonrandomness coefficient
   * @param dij expected COMP1-to-COMP2 coefficient in kelvin
   * @param dji expected COMP2-to-COMP1 coefficient in kelvin
   * @param dijT expected directed dimensionless offset
   * @param djiT expected reverse directed dimensionless offset
   */
  private static void assertParameters(EosMixingRuleHandler handler, int first, int second, double alpha, double dij,
      double dji, double dijT, double djiT) {
    assertEquals("HV", handler.classicOrHV[first][second]);
    assertEquals("HV", handler.classicOrHV[second][first]);
    assertEquals(alpha, handler.HValpha[first][second], 0.0);
    assertEquals(alpha, handler.HValpha[second][first], 0.0);
    assertEquals(dij, handler.HVDij[first][second], 0.0);
    assertEquals(dji, handler.HVDij[second][first], 0.0);
    assertEquals(dijT, handler.HVDijT[first][second], 0.0);
    assertEquals(djiT, handler.HVDijT[second][first], 0.0);
  }
}
