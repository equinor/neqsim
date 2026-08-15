package neqsim.thermodynamicoperations.propertygenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that the keyword-format OLGA property table generator writes a usable table to the file the caller asked for.
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorKeywordFormatTest {
  /**
   * The writer must honour its filename argument, and run() must build the keyword header itself so a caller does not
   * have to know that initCalc() exists.
   *
   * @param tempDir temporary directory supplied by JUnit
   * @throws IOException if the generated table cannot be read back
   */
  @Test
  void testWriteOLGAinpFileUsesGivenFileName(@TempDir Path tempDir) throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");

    OLGApropertyTableGeneratorKeywordFormat generator = new OLGApropertyTableGeneratorKeywordFormat(fluid);
    // Deliberately asymmetric: a square grid hides index errors between the
    // pressure-indexed and temperature-indexed header arrays.
    generator.setPressureRange(10.0, 60.0, 5);
    generator.setTemperatureRange(273.15, 323.15, 3);
    generator.run();

    // Deliberately point at a directory that does not exist yet.
    File target = tempDir.resolve("tables").resolve("olga_table.tab").toFile();
    generator.writeOLGAinpFile(target.getAbsolutePath());

    assertTrue(target.isFile(), "writeOLGAinpFile did not create " + target.getAbsolutePath());
    assertTrue(target.length() > 0L, "generated OLGA table is empty");

    String content = new String(Files.readAllBytes(target.toPath()), Charset.forName("utf-8"));
    assertTrue(content.contains("PVTTABLE LABEL"), "missing PVTTABLE LABEL keyword");
    assertTrue(content.contains("PHASE = TWO"), "missing PHASE keyword");
    assertTrue(content.contains("COLUMNS = (PT,TM,"), "missing COLUMNS keyword");
    assertTrue(content.contains("PVTTABLE POINT = ("), "no PVTTABLE POINT rows written");
    assertTrue(content.contains("methane"), "component list not written to the header");

    // OLGA rejects a table whose saturation curve arrays are not paired.
    assertEquals(countEntries(content, "BUBBLEPRESSURES"), countEntries(content, "BUBBLETEMPERATURES"),
        "BUBBLEPRESSURES and BUBBLETEMPERATURES must have the same length");
  }

  /**
   * Count the comma separated entries of a bracketed keyword list.
   *
   * @param content the generated table
   * @param keyword the keyword whose list is counted
   * @return the number of entries
   */
  private static int countEntries(String content, String keyword) {
    int start = content.indexOf(keyword + " = (");
    assertTrue(start >= 0, "keyword " + keyword + " not found");
    int open = content.indexOf('(', start);
    int close = content.indexOf(')', open);
    return content.substring(open + 1, close).split(",").length;
  }
}
