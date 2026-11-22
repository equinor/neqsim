package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Optional read-only native export of every original tail node and every prefix certificate.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Grid65 {
  /** Prevents construction of this optional evidence driver. */
  private Grid65() {
  }

  /**
   * Reads a private numerical array without modifying it.
   *
   * @param type field owner
   * @param name field name
   * @return numerical array for export only
   * @throws ReflectiveOperationException if the declared grid is unavailable
   */
  private static double[][] grid(Class<?> type, String name) throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return (double[][]) field.get(null);
  }

  /**
   * Exports original or candidate grids using the same driver and decimal round-trip format.
   *
   * @param args compressed CSV output and parent or candidate mode
   * @throws Exception if the source contract or output fails
   */
  public static void main(String[] args) throws Exception {
    boolean candidate = "candidate".equals(args[1]);
    double[][] full = candidate
        ? grid(Class.forName(RadialInfiniteInfluence.class.getName() + "$FullTail"), "QUADRATURE")
        : grid(RadialInfiniteInfluence.class, "TAIL_QUADRATURE");
    double[][] prefix = candidate ? grid(RadialInfiniteInfluence.class, "TAIL_PREFIX") : full;
    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(args[0])),
        StandardCharsets.UTF_8)) {
      writer.write("index,frequency,weight,suffix,prefixFrequency,prefixWeight,prefixBound\n");
      for (int i = 0; i < full[0].length; i++) {
        writer.write(i + "," + full[0][i] + "," + full[1][i] + "," + full[2][i]);
        if (i < prefix[0].length) {
          writer.write("," + prefix[0][i] + "," + prefix[1][i] + "," + prefix[2][i]);
        } else {
          writer.write(",,,");
        }
        writer.write("\n");
      }
    }
  }
}
