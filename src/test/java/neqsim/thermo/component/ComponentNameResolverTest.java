package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ComponentNameResolver}.
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class ComponentNameResolverTest {
  /** Names as spelled in data/COMP.csv. */
  private static List<String> databaseNames;

  /**
   * Read the component names straight from the CSV resource, so the test fails if the resolver tables drift away from
   * the database rather than from a copy of it.
   *
   * @throws IOException if the resource cannot be read
   */
  @BeforeAll
  public static void loadDatabaseNames() throws IOException {
    databaseNames = new ArrayList<String>();
    InputStream in = ComponentNameResolverTest.class.getClassLoader().getResourceAsStream("data/COMP.csv");
    assertNotNull(in, "data/COMP.csv must be on the test classpath");
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
    try {
      reader.readLine(); // header
      String line = reader.readLine();
      while (line != null) {
        int first = line.indexOf(',');
        int second = line.indexOf(',', first + 1);
        if (first > 0 && second > first) {
          databaseNames.add(line.substring(first + 1, second));
        }
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
    assertTrue(databaseNames.size() > 200, "expected the full component list");
  }

  /** Every database name must resolve to itself, otherwise a lookup would break. */
  @Test
  public void databaseNamesResolveToThemselves() {
    for (int i = 0; i < databaseNames.size(); i++) {
      String name = databaseNames.get(i);
      assertEquals(name, ComponentNameResolver.resolve(name), "database name changed by the resolver: " + name);
    }
  }

  /** Database names must also resolve when the caller uses a different letter case. */
  @Test
  public void databaseNamesResolveCaseInsensitively() {
    for (int i = 0; i < databaseNames.size(); i++) {
      String name = databaseNames.get(i);
      assertEquals(name, ComponentNameResolver.resolve(name.toUpperCase()), "upper-case form not resolved: " + name);
      assertEquals(name, ComponentNameResolver.resolve(name.toLowerCase()), "lower-case form not resolved: " + name);
    }
  }

  /** Every synonym must point at a component that actually exists. */
  @Test
  public void everySynonymTargetExistsInTheDatabase() {
    Set<String> known = new HashSet<String>(databaseNames);
    Map<String, String> synonyms = ComponentNameResolver.getSynonyms();
    for (Map.Entry<String, String> entry : synonyms.entrySet()) {
      assertTrue(known.contains(entry.getValue()),
          "synonym '" + entry.getKey() + "' points at unknown component '" + entry.getValue() + "'");
    }
    assertTrue(synonyms.size() > 100, "expected a substantial synonym table");
  }

  /** The reservoir shorthand handled before this class existed must keep working. */
  @Test
  public void legacyReservoirShorthandStillResolves() {
    assertEquals("water", ComponentNameResolver.resolve("H2O"));
    assertEquals("nitrogen", ComponentNameResolver.resolve("N2"));
    assertEquals("methane", ComponentNameResolver.resolve("C1"));
    assertEquals("ethane", ComponentNameResolver.resolve("C2"));
    assertEquals("propane", ComponentNameResolver.resolve("C3"));
    assertEquals("i-butane", ComponentNameResolver.resolve("iC4"));
    assertEquals("n-butane", ComponentNameResolver.resolve("nC4"));
    assertEquals("i-pentane", ComponentNameResolver.resolve("iC5"));
    assertEquals("n-pentane", ComponentNameResolver.resolve("nC5"));
    assertEquals("n-hexane", ComponentNameResolver.resolve("C6"));
    assertEquals("n-heptane", ComponentNameResolver.resolve("nC7"));
    assertEquals("n-octane", ComponentNameResolver.resolve("nC8"));
    assertEquals("n-nonane", ComponentNameResolver.resolve("nC9"));
    assertEquals("oxygen", ComponentNameResolver.resolve("O2"));
    assertEquals("helium", ComponentNameResolver.resolve("He"));
    assertEquals("hydrogen", ComponentNameResolver.resolve("H2"));
    assertEquals("argon", ComponentNameResolver.resolve("Ar"));
    assertEquals("H2S", ComponentNameResolver.resolve("H2S"));
  }

  /** The same shorthand must also work through the original entry point. */
  @Test
  public void componentInterfaceAliasDelegatesToResolver() {
    assertEquals("methane", ComponentInterface.getComponentNameFromAlias("C1"));
    assertEquals("n-heptane", ComponentInterface.getComponentNameFromAlias("nC7"));
    assertEquals("2-m-C5", ComponentInterface.getComponentNameFromAlias("2-methylpentane"));
  }

  /** Systematic names must map onto the in-house shorthand used by the database. */
  @Test
  public void systematicNamesResolveToDatabaseShorthand() {
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("2,2,4-trimethylpentane"));
    assertEquals("2-m-C5", ComponentNameResolver.resolve("2-methylpentane"));
    assertEquals("3-M-C7", ComponentNameResolver.resolve("3-methylheptane"));
    assertEquals("M-cy-C5", ComponentNameResolver.resolve("methylcyclopentane"));
    assertEquals("23-dim-C4", ComponentNameResolver.resolve("2,3-dimethylbutane"));
    assertEquals("nC10-Benzene", ComponentNameResolver.resolve("decylbenzene"));
  }

  /** Trivial names in common laboratory use must resolve. */
  @Test
  public void trivialNamesResolve() {
    assertEquals("i-pentane", ComponentNameResolver.resolve("isopentane"));
    assertEquals("i-pentane", ComponentNameResolver.resolve("2-methylbutane"));
    assertEquals("i-butane", ComponentNameResolver.resolve("isobutane"));
    assertEquals("22-dim-C3", ComponentNameResolver.resolve("neopentane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("isooctane"));
    assertEquals("c-hexane", ComponentNameResolver.resolve("cyclohexane"));
    assertEquals("toluene", ComponentNameResolver.resolve("methylbenzene"));
    assertEquals("o-Xylene", ComponentNameResolver.resolve("1,2-dimethylbenzene"));
    assertEquals("CO2", ComponentNameResolver.resolve("carbon dioxide"));
    assertEquals("MEG", ComponentNameResolver.resolve("ethylene glycol"));
  }

  /** The shorthand uses '.' where a systematic name uses ',' between locants. */
  @Test
  public void locantSeparatorIsInterchangeable() {
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1,2,3-TM-Benzene"));
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1.2.3-TM-Benzene"));
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1,2,3-trimethylbenzene"));
  }

  /** Inverted CAS index names, as printed by chromatography software, must resolve. */
  @Test
  public void invertedCasIndexNamesResolve() {
    assertEquals("1.2.4-TMcyC6", ComponentNameResolver.resolve("Cyclohexane, 1,2,4-trimethyl-"));
    assertEquals("2-m-C5", ComponentNameResolver.resolve("Pentane, 2-methyl-"));
  }

  /**
   * A name that does not identify one component must be passed through untouched.
   *
   * <p>
   * The database holds both cis and trans partners for these skeletons, so resolving the stereochemically unspecified
   * parent would silently pick one of the two.
   * </p>
   */
  @Test
  public void stereochemicallyAmbiguousNamesAreNotResolved() {
    assertEquals("1,3-dimethylcyclopentane", ComponentNameResolver.resolve("1,3-dimethylcyclopentane"));
    assertEquals("1,2-dimethylcyclohexane", ComponentNameResolver.resolve("1,2-dimethylcyclohexane"));
    assertEquals("2-butene", ComponentNameResolver.resolve("2-butene"));
    assertFalse(ComponentNameResolver.isKnownName("1,3-dimethylcyclopentane"));
  }

  /** Stereochemistry that is given must be honoured. */
  @Test
  public void explicitStereochemistryResolves() {
    assertEquals("cis-butene", ComponentNameResolver.resolve("cis-2-butene"));
    assertEquals("trans-butene", ComponentNameResolver.resolve("trans-2-butene"));
    assertEquals("cis-13-DM-cy-C6", ComponentNameResolver.resolve("cis-1,3-dimethylcyclohexane"));
  }

  /** Unknown names and null must be returned untouched. */
  @Test
  public void unknownNamesArePassedThrough() {
    assertEquals("not-a-component", ComponentNameResolver.resolve("not-a-component"));
    assertEquals("", ComponentNameResolver.resolve(""));
    assertNull(ComponentNameResolver.resolve(null));
    assertFalse(ComponentNameResolver.isKnownName("not-a-component"));
    assertFalse(ComponentNameResolver.isKnownName(null));
  }

  /** Whitespace and underscores must not defeat the lookup. */
  @Test
  public void separatorsAndWhitespaceAreNormalised() {
    assertEquals("n-heptane", ComponentNameResolver.resolve("  n-heptane  "));
    assertEquals("CO2", ComponentNameResolver.resolve("carbon   dioxide"));
    assertEquals("methane", ComponentNameResolver.resolve("METHANE"));
  }

  /** The examples given in docs/thermo/component_list.md must behave as documented. */
  @Test
  public void documentedExamplesBehaveAsDescribed() {
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("224-TM-C5"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("2,2,4-trimethylpentane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("isooctane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("ISOOCTANE"));
    assertEquals("n-heptane", ComponentNameResolver.resolve("N-HEPTANE"));
    assertEquals("nC10", ComponentNameResolver.resolve("n-decane"));
    assertEquals("n-water", ComponentNameResolver.resolve("n-water"));
    assertEquals("cis-13-DM-cy-C6", ComponentNameResolver.resolve("cis-1,3-dimethylcyclohexane"));
    assertTrue(ComponentNameResolver.isKnownName("isooctane"));
    assertFalse(ComponentNameResolver.getSynonyms().isEmpty());
    assertFalse(ComponentNameResolver.getCanonicalNames().isEmpty());
  }

  /**
   * The straight-chain 'n-' prefix must be optional.
   *
   * <p>
   * The database stores C4 to C9 as {@code n-butane} to {@code n-nonane} but C10 upwards as {@code nC10}, so without
   * this the naming cliff at C10 made {@code n-decane} fail while {@code n-nonane} worked.
   * </p>
   */
  @Test
  public void straightChainPrefixIsOptional() {
    assertEquals("nC10", ComponentNameResolver.resolve("n-decane"));
    assertEquals("nC10", ComponentNameResolver.resolve("decane"));
    assertEquals("nC11", ComponentNameResolver.resolve("n-undecane"));
    assertEquals("nC12", ComponentNameResolver.resolve("n-dodecane"));
    assertEquals("nC15", ComponentNameResolver.resolve("n-pentadecane"));
    assertEquals("nC20", ComponentNameResolver.resolve("n-icosane"));
    // names that already carry the prefix in the database are untouched
    assertEquals("n-butane", ComponentNameResolver.resolve("n-butane"));
    assertEquals("n-nonane", ComponentNameResolver.resolve("n-nonane"));
    assertEquals("nC5-Benzene", ComponentNameResolver.resolve("n-pentylbenzene"));
    assertTrue(ComponentNameResolver.isKnownName("n-decane"));
    assertEquals("n-water", ComponentNameResolver.resolve("n-water"));
    assertEquals("n-acetone", ComponentNameResolver.resolve("n-acetone"));
    assertEquals("n-isobutane", ComponentNameResolver.resolve("n-isobutane"));
    assertEquals("n-n-decane", ComponentNameResolver.resolve("n-n-decane"));
    assertFalse(ComponentNameResolver.isKnownName("n-water"));
    assertFalse(ComponentNameResolver.isKnownName("n-acetone"));
    assertFalse(ComponentNameResolver.isKnownName("n-isobutane"));
    assertFalse(ComponentNameResolver.isKnownName("n-n-decane"));
  }

  /** A fluid must accept a systematic name and build the corresponding component. */
  @Test
  public void fluidAcceptsSystematicName() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("2,2,4-trimethylpentane", 0.1);
    fluid.addComponent("isopentane", 0.1);
    fluid.setMixingRule("classic");
    assertEquals(3, fluid.getNumberOfComponents());
    assertEquals("224-TM-C5", fluid.getPhase(0).getComponent(1).getComponentName());
    assertEquals("i-pentane", fluid.getPhase(0).getComponent(2).getComponentName());
  }
}
