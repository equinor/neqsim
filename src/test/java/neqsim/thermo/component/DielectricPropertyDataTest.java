package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.h2.tools.Csv;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Qualifies the repaired dielectric records against published reference values and their stated domains.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DielectricPropertyDataTest {
  /** Components whose temperature-only reference correlations have a qualified interval. */
  private static final String[] BOUNDED_NAMES = {"nitrogen", "oxygen", "n-pentane", "n-hexane", "acetone", "MEG",
      "MEGPVTsim18", "MEGPVTsim19", "1-propanol", "i-propanol", "ammonia"};

  /** Corresponding inclusive source intervals in kelvin. */
  private static final double[][] TEMPERATURE_RANGES = {{63.15, 78.15}, {55.15, 90.15}, {223.15, 303.15},
      {263.15, 323.15}, {278.15, 313.15}, {278.15, 323.15}, {278.15, 323.15}, {278.15, 323.15}, {278.15, 323.15},
      {283.15, 323.15}, {278.15, 308.15}};

  /** Records lacking a qualified temperature-only correlation or an unambiguous chemical identity. */
  private static final String[] UNAVAILABLE_NAMES = {"n-butane", "nbutanePVTsim", "i-pentane", "i-butane", "glycerol",
      "PG", "ethylene", "propene", "cis-butene", "trans-butene", "iso-butene"};

  /** Original malformed coefficients, evaluated in kelvin by the component API. */
  private static final double[] MALFORMED_COEFFICIENTS = {-19.2905, 29814.5, -0.019678, 0.013189, 0.031144};

  /** Checks the NBS Circular 514 reference values without extrapolating cryogenic liquids to room temperature. */
  @Test
  void nbsLiquidReferenceValuesAreSpeciesSpecific() {
    assertPermittivity("nitrogen", 70.15, 1.4511, 1.0e-10);
    assertPermittivity("oxygen", 80.15, 1.507, 1.0e-10);
    assertPermittivity("n-pentane", 293.15, 1.844, 1.0e-10);
    assertPermittivity("n-hexane", 293.15, 1.890, 1.0e-10);
    // The 25 degC ammonia observation was excluded from the three-point polynomial fit.
    assertPermittivity("ammonia", 298.15, 16.9, 0.04);
  }

  /** Checks NPL MAT23 observations excluded from the fitted temperature grid. */
  @Test
  void polarCorrelationsReproduceHeldOutNplValues() {
    assertPermittivity("acetone", 283.15, 22.21, 0.04);
    assertPermittivity("acetone", 293.15, 21.13, 0.04);
    assertPermittivity("acetone", 303.15, 20.10, 0.04);
    assertPermittivity("acetone", 308.15, 19.60, 0.04);
    assertPermittivity("1-propanol", 283.15, 22.61, 0.04);
    assertPermittivity("1-propanol", 293.15, 21.15, 0.04);
    assertPermittivity("1-propanol", 303.15, 19.75, 0.04);
    assertPermittivity("1-propanol", 313.15, 18.40, 0.04);
    assertPermittivity("1-propanol", 318.15, 17.76, 0.04);
    assertPermittivity("MEG", 283.15, 44.31, 0.06);
    assertPermittivity("MEG", 293.15, 41.89, 0.06);
    assertPermittivity("MEG", 303.15, 39.66, 0.06);
    assertPermittivity("MEG", 313.15, 37.63, 0.06);
    assertPermittivity("MEG", 318.15, 36.63, 0.06);
    assertPermittivity("i-propanol", 288.15, 20.89, 0.11);
    assertPermittivity("i-propanol", 298.15, 19.30, 0.10);
    assertPermittivity("i-propanol", 308.15, 17.65, 0.07);
    assertPermittivity("i-propanol", 318.15, 16.21, 0.06);
  }

  /** Checks both temperature derivatives by differencing property values inside each source interval. */
  @Test
  void analyticDerivativesMatchCentralDifferencesWithinSourceRanges() {
    double step = 0.05;
    for (int i = 0; i < BOUNDED_NAMES.length; i++) {
      ComponentInterface component = component(BOUNDED_NAMES[i]);
      double lower = TEMPERATURE_RANGES[i][0];
      double upper = TEMPERATURE_RANGES[i][1];
      for (double fraction : new double[] {0.25, 0.5, 0.75}) {
        double temperature = lower + fraction * (upper - lower);
        double before = component.getDielectricConstant(temperature - step);
        double center = component.getDielectricConstant(temperature);
        double after = component.getDielectricConstant(temperature + step);
        double firstDifference = (after - before) / (2.0 * step);
        double secondDifference = (after - 2.0 * center + before) / (step * step);
        String location = BOUNDED_NAMES[i] + " at " + temperature + " K";
        assertEquals(firstDifference, component.getDielectricConstantdT(temperature), 1.0e-8, location);
        assertEquals(secondDifference, component.getDielectricConstantdTdT(temperature), 2.0e-8, location);
      }
    }
  }

  /** Checks that values and both derivatives enforce the same measured temperature envelope. */
  @Test
  void everyGetterRejectsExtrapolationAndAcceptsSourceEndpoints() {
    for (int i = 0; i < BOUNDED_NAMES.length; i++) {
      ComponentInterface component = component(BOUNDED_NAMES[i]);
      double lower = TEMPERATURE_RANGES[i][0];
      double upper = TEMPERATURE_RANGES[i][1];
      for (double temperature : new double[] {lower, upper}) {
        assertTrue(component.getDielectricConstant(temperature) > 1.0, BOUNDED_NAMES[i]);
        assertTrue(Double.isFinite(component.getDielectricConstantdT(temperature)), BOUNDED_NAMES[i]);
        assertTrue(Double.isFinite(component.getDielectricConstantdTdT(temperature)), BOUNDED_NAMES[i]);
      }
      for (double temperature : new double[] {lower - 1.0e-6, upper + 1.0e-6, 0.0, -1.0, Double.NaN,
          Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
        assertRejected(component, temperature);
      }
    }
  }

  /** Ensures missing correlations and the ambiguous glycerol record do not supply invented dielectric values. */
  @Test
  void unavailablePropertiesFailExplicitlyInEveryGetter() {
    for (String name : UNAVAILABLE_NAMES) {
      ComponentInterface component = component(name);
      for (double temperature : new double[] {200.0, 298.15, 400.0}) {
        assertRejected(component, temperature);
      }
    }
  }

  /** Preserves the loaded reference domain when components are cloned or given an application-specific name. */
  @Test
  void renamingAndCloningCannotBypassReferenceQualification() {
    for (String name : UNAVAILABLE_NAMES) {
      ComponentInterface original = component(name);
      ComponentInterface clone = original.clone();
      assertRejected(clone, 298.15);
      clone.setComponentName("custom-" + name);
      assertRejected(clone, 298.15);
      original.setComponentName("renamed-" + name);
      assertRejected(original, 298.15);
      assertRejected(original.clone(), 298.15);
    }
    ComponentInterface nitrogen = component("nitrogen");
    nitrogen.setComponentName("custom-nitrogen");
    assertEquals(1.4511, nitrogen.getDielectricConstant(70.15), 1.0e-10);
    assertRejected(nitrogen, 298.15);
    assertRejected(nitrogen.clone(), 298.15);
  }

  /**
   * Rejects old serialized coefficients instead of attaching the new source qualification to obsolete values.
   *
   * @throws Exception if the legacy state cannot be constructed or serialized
   */
  @Test
  void obsoleteSerializedCoefficientsCannotAcquireNewSourceQualification() throws Exception {
    String[] names = {"acetone", "MEGPVTsim18", "MEG"};
    double[][] obsolete = {MALFORMED_COEFFICIENTS, MALFORMED_COEFFICIENTS, {-18.46, 16744.0, 0.0, 0.0, 0.0}};
    Field referenceName = Component.class.getDeclaredField("dielectricReferenceName");
    referenceName.setAccessible(true);
    for (int i = 0; i < names.length; i++) {
      Component oldComponent = (Component) component(names[i]);
      oldComponent.dielectricParameter = obsolete[i].clone();
      // Java serialization supplies null for the reference-name field absent from older saved components.
      referenceName.set(oldComponent, null);
      ComponentInterface restored = serializationRoundTrip(oldComponent);
      assertRejected(restored, 298.15);
      assertRejected(restored.clone(), 298.15);
      restored.setComponentName("restored-" + names[i]);
      assertRejected(restored, 298.15);
    }
  }

  /** Ensures the alternate MEG records share the canonical property and analytic derivatives. */
  @Test
  void glycolAliasesMatchCanonicalValuesAndDerivatives() {
    ComponentInterface canonical = component("MEG");
    for (String alias : new String[] {"MEGPVTsim18", "MEGPVTsim19"}) {
      ComponentInterface alternate = component(alias);
      for (double temperature : new double[] {278.15, 298.15, 323.15}) {
        assertEquals(canonical.getDielectricConstant(temperature), alternate.getDielectricConstant(temperature), 0.0,
            alias);
        assertEquals(canonical.getDielectricConstantdT(temperature), alternate.getDielectricConstantdT(temperature),
            0.0, alias);
        assertEquals(canonical.getDielectricConstantdTdT(temperature), alternate.getDielectricConstantdTdT(temperature),
            0.0, alias);
      }
    }
  }

  /**
   * Audits the authoritative resource and the historical test database, then checks the extended runtime overlay.
   *
   * @throws Exception if a database resource cannot be read
   */
  @Test
  void databaseModesLoadTheSameRepairsAndHistoricalFixtureHasNoMalformedSignature() throws Exception {
    Map<String, double[]> main = readCoefficients("data/COMP.csv", false);
    Map<String, double[]> historical = readCoefficients("neqsim/thermodynamicoperations/COMP.csv", true);
    for (String[] names : new String[][] {BOUNDED_NAMES, UNAVAILABLE_NAMES}) {
      for (String name : names) {
        assertNotNull(main.get(name), name);
        // The historical resource predates some current components, including ammonia.
        if (historical.containsKey(name)) {
          assertArrayEquals(main.get(name), historical.get(name), 0.0, name);
        }
      }
    }
    for (String name : UNAVAILABLE_NAMES) {
      for (double coefficient : main.get(name)) {
        assertTrue(Double.isNaN(coefficient), name + " must mark every coefficient unavailable");
      }
    }
    boolean temporaryTables = NeqSimDataBase.createTemporaryTables();
    NeqSimDataBase.setCreateTemporaryTables(false);
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        for (String[] names : new String[][] {BOUNDED_NAMES, UNAVAILABLE_NAMES}) {
          for (String name : names) {
            try (NeqSimDataBase database = new NeqSimDataBase();
                ResultSet row = database.getResultSet("SELECT * FROM COMP WHERE NAME='" + name + "'")) {
              assertTrue(row.next(), name);
              double[] loaded = new double[5];
              for (int i = 0; i < loaded.length; i++) {
                loaded[i] = Double.parseDouble(row.getString("DIELECTRICPARAMETER" + (i + 1)));
              }
              assertArrayEquals(main.get(name), loaded, 0.0, name + "; extended=" + extended);
            }
          }
        }
        nbsLiquidReferenceValuesAreSpeciesSpecific();
        polarCorrelationsReproduceHeldOutNplValues();
        analyticDerivativesMatchCentralDifferencesWithinSourceRanges();
        everyGetterRejectsExtrapolationAndAcceptsSourceEndpoints();
        unavailablePropertiesFailExplicitlyInEveryGetter();
        glycolAliasesMatchCanonicalValuesAndDerivatives();
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
      NeqSimDataBase.setCreateTemporaryTables(temporaryTables);
    }
  }

  /** Rejects obsolete raw extended coefficients when callers bypass canonical-property synchronization. */
  @Test
  void directlyImportedLegacyExtendedRecordsCannotAcquireSourceQualification() {
    boolean temporaryTables = NeqSimDataBase.createTemporaryTables();
    NeqSimDataBase.setCreateTemporaryTables(false);
    try {
      NeqSimDataBase.updateTable("COMP", "data/COMP_EXT.csv");
      for (int i = 0; i < BOUNDED_NAMES.length; i++) {
        double temperature = (TEMPERATURE_RANGES[i][0] + TEMPERATURE_RANGES[i][1]) / 2.0;
        assertRejected(component(BOUNDED_NAMES[i]), temperature);
      }
      unavailablePropertiesFailExplicitlyInEveryGetter();
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
      NeqSimDataBase.setCreateTemporaryTables(temporaryTables);
    }
  }

  /** Verifies ordinary cubic-EOS flashes remain usable when no dielectric property is requested. */
  @Test
  void ordinarySrkFlashDoesNotRequireMissingDielectricData() {
    String[] names = {"methane", "nitrogen", "oxygen", "n-butane", "i-butane", "n-pentane", "i-pentane", "n-hexane"};
    double[] feed = {0.70, 0.05, 0.01, 0.07, 0.07, 0.04, 0.03, 0.03};
    SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], feed[i]);
    }
    system.setMixingRule(2);
    new ThermodynamicOperations(system).TPflash();
    double[] inventory = new double[names.length];
    double phaseFractionSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      assertTrue(phase.getBeta() > 0.0);
      phaseFractionSum += phase.getBeta();
      double compositionSum = 0.0;
      for (int i = 0; i < names.length; i++) {
        ComponentInterface component = phase.getComponent(names[i]);
        assertTrue(Double.isFinite(component.getFugacityCoefficient()), names[i]);
        assertTrue(component.getFugacityCoefficient() > 0.0, names[i]);
        assertTrue(component.getx() >= 0.0, names[i]);
        compositionSum += component.getx();
        inventory[i] += component.getNumberOfMolesInPhase();
      }
      assertEquals(1.0, compositionSum, 1.0e-10);
    }
    assertEquals(1.0, phaseFractionSum, 1.0e-10);
    assertArrayEquals(feed, inventory, 1.0e-9);
  }

  /**
   * Constructs a component using the same named-column loader as thermodynamic systems.
   *
   * @param name database component name
   * @return component with its database property records
   */
  private static ComponentInterface component(String name) {
    return new ComponentSrk(name, 1.0, 1.0, 0);
  }

  /**
   * Saves and restores a component without reloading its database coefficients.
   *
   * @param component component to serialize
   * @return deserialized component
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if the serialized component class cannot be resolved
   */
  private static ComponentInterface serializationRoundTrip(ComponentInterface component)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(component);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ComponentInterface) input.readObject();
    }
  }

  /**
   * Checks one independent source observation.
   *
   * @param name database component name
   * @param temperature temperature in kelvin
   * @param expected published relative permittivity
   * @param tolerance absolute permitted representation error
   */
  private static void assertPermittivity(String name, double temperature, double expected, double tolerance) {
    assertEquals(expected, component(name).getDielectricConstant(temperature), tolerance,
        name + " at " + temperature + " K");
  }

  /**
   * Checks that the property and both derivatives reject the same unavailable state.
   *
   * @param component component under test
   * @param temperature unavailable or out-of-range temperature in kelvin
   */
  private static void assertRejected(ComponentInterface component, double temperature) {
    assertThrows(IllegalArgumentException.class, () -> component.getDielectricConstant(temperature));
    assertThrows(IllegalArgumentException.class, () -> component.getDielectricConstantdT(temperature));
    assertThrows(IllegalArgumentException.class, () -> component.getDielectricConstantdTdT(temperature));
  }

  /**
   * Reads dielectric coefficients and rejects the malformed signature for every row, including undeclared aliases.
   *
   * @param resource component CSV classpath resource
   * @param historical whether the resource uses the old headerless 150-column layout
   * @return component names mapped to their five dielectric coefficients
   * @throws Exception if the resource cannot be read
   */
  private static Map<String, double[]> readCoefficients(String resource, boolean historical) throws Exception {
    InputStream input = DielectricPropertyDataTest.class.getClassLoader().getResourceAsStream(resource);
    assertNotNull(input, resource);
    String[] header = null;
    if (historical) {
      header = new String[150];
      for (int i = 0; i < header.length; i++) {
        header[i] = "COLUMN" + i;
      }
      header[1] = "NAME";
      for (int i = 0; i < 5; i++) {
        header[40 + i] = "DIELECTRICPARAMETER" + (i + 1);
      }
    }
    Map<String, double[]> coefficients = new LinkedHashMap<String, double[]>();
    try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        ResultSet rows = new Csv().read(reader, header)) {
      while (rows.next()) {
        String name = rows.getString("NAME").trim();
        double[] values = new double[5];
        boolean malformed = true;
        for (int i = 0; i < values.length; i++) {
          values[i] = Double.parseDouble(rows.getString("DIELECTRICPARAMETER" + (i + 1)).trim());
          malformed &= values[i] == MALFORMED_COEFFICIENTS[i];
        }
        assertFalse(malformed, resource + ": " + name);
        coefficients.put(name, values);
      }
    }
    return coefficients;
  }
}
