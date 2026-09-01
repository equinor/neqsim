package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for component lookup in {@link NeqSimDataBase} and for the relationship between the standard and extended
 * component databases.
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class NeqSimDataBaseComponentLookupTest {
  /**
   * Read the NAME column of a component database resource.
   *
   * @param resource classpath resource, e.g. {@code data/COMP.csv}
   * @return the component names in file order
   * @throws IOException if the resource cannot be read
   */
  private static List<String> readNames(String resource) throws IOException {
    List<String> names = new ArrayList<String>();
    InputStream in = NeqSimDataBaseComponentLookupTest.class.getClassLoader().getResourceAsStream(resource);
    assertTrue(in != null, resource + " must be on the test classpath");
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
    try {
      reader.readLine(); // header
      String line = reader.readLine();
      while (line != null) {
        int first = line.indexOf(',');
        int second = line.indexOf(',', first + 1);
        if (first > 0 && second > first) {
          names.add(line.substring(first + 1, second));
        }
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
    return names;
  }

  /** A component that exists must be found, one that does not must not. */
  @Test
  public void hasComponentFindsKnownComponents() {
    assertTrue(NeqSimDataBase.hasComponent("methane"));
    assertTrue(NeqSimDataBase.hasComponent("water"));
    assertFalse(NeqSimDataBase.hasComponent("definitely-not-a-component"));
  }

  /**
   * A name containing an apostrophe must not break the lookup.
   *
   * <p>
   * The query used to be assembled by string concatenation, so such a name produced invalid SQL. More than two thousand
   * names in the extended database contain an apostrophe.
   * </p>
   */
  @Test
  public void hasComponentToleratesQuotesInTheName() {
    assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        assertFalse(NeqSimDataBase.hasComponent("4'-hydroxyacetophenone"));
        assertFalse(NeqSimDataBase.hasComponent("n,n'-diphenyl-p-phenylenediamine"));
        assertFalse(NeqSimDataBase.hasComponent("' OR '1'='1"));
        assertFalse(NeqSimDataBase.hasComponent("x'; DROP TABLE comp; --"));
      }
    });
    // the injection attempt must not have damaged the table
    assertTrue(NeqSimDataBase.hasComponent("methane"));
  }

  /** A null name must be handled rather than reaching the database. */
  @Test
  public void hasComponentHandlesNull() {
    assertFalse(NeqSimDataBase.hasComponent(null));
    assertFalse(NeqSimDataBase.hasTempComponent(null));
  }

  /**
   * The extended database must contain every component of the standard database.
   *
   * <p>
   * {@link NeqSimDataBase#useExtendedComponentDatabase(boolean)} replaces the COMP table, so any component present only
   * in the standard file would silently disappear from fluids created after the switch.
   * </p>
   *
   * @throws IOException if a resource cannot be read
   */
  @Test
  public void extendedDatabaseContainsEveryStandardComponent() throws IOException {
    List<String> standard = readNames("data/COMP.csv");
    Set<String> extended = new HashSet<String>(readNames("data/COMP_EXT.csv"));
    List<String> missing = new ArrayList<String>();
    for (int i = 0; i < standard.size(); i++) {
      if (!extended.contains(standard.get(i))) {
        missing.add(standard.get(i));
      }
    }
    assertEquals(0, missing.size(), "components absent from COMP_EXT.csv: " + missing);
  }
}
