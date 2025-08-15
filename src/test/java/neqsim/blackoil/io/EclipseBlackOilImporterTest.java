package neqsim.blackoil.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.SystemBlackOil;

/** JUnit test + runnable demo for EclipseBlackOilImporter using a typical METRIC deck. */
public class EclipseBlackOilImporterTest {

  private static String sampleDeck() {
    return String.join("\n",
        Arrays.asList("UNITS  METRIC", "", "DENSITY", "  800.0   1000.0   1.2  /", "", "PVTO",
            "-- Rs     Pb     Bo      mu_o", "  0.0     250    1.20    1.50",
            "  300     1.15   1.40", "  400     1.12   1.30 /", "  100.0   200    1.35    1.60",
            "  250     1.30   1.50", "  300     1.26   1.40 /", "  200.0   150    1.50    1.80",
            "  200     1.42   1.70", "  250     1.36   1.60 /", "/", "", "PVTG",
            "-- Rv     Pd     Bg       mu_g", "  0.0     120    0.0045   0.012",
            "  150     0.0042 0.011", "  200     0.0040 0.010 /", "/", "", "PVTW",
            "-- P      Bw      mu_w", "  50      1.02    0.5", "  150     1.01    0.6",
            "  250     1.00    0.7 /", "/"));
  }

  @Test
  public void importTypicalDeck() throws IOException {
    Path tmp = Files.createTempFile("MY_FLUID", ".DAT");
    Files.write(tmp, sampleDeck().getBytes(StandardCharsets.UTF_8));

    EclipseBlackOilImporter.Result res = EclipseBlackOilImporter.fromFile(tmp);
    assertNotNull(res);
    assertNotNull(res.pvt);
    assertNotNull(res.system);

    assertEquals(150.0, res.bubblePoint, 1e-9);

    BlackOilPVTTable pvt = res.pvt;
    assertEquals(200.0, pvt.Rs(180.0), 1e-9);

    assertEquals(1.50, pvt.Bo(150.0), 1e-9);
    assertEquals(1.80e-3, pvt.mu_o(150.0), 1e-12);

    assertEquals(0.0042, pvt.Bg(150.0), 1e-9);
    assertEquals(0.011e-3, pvt.mu_g(150.0), 1e-12);

    assertEquals(1.01, pvt.Bw(150.0), 1e-9);
    assertEquals(0.6e-3, pvt.mu_w(150.0), 1e-12);

    SystemBlackOil bo = res.system;
    assertEquals(1.0, bo.getOilStdTotal(), 1e-12);
    assertEquals(200.0, bo.getGasStdTotal(), 1e-9);
    assertEquals(res.bubblePoint, bo.getPressure(), 1e-9);
  }
}
