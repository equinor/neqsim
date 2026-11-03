package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import java.io.PrintWriter;
import java.util.Locale;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Repeated-batch timing of inherited mixing and clone paths, excluding EOS initialization.
 *
 * <p>
 * Writes raw CSV batches to the path supplied as the first argument. This is a diagnostic benchmark, not a wall-time
 * assertion in the unit suite or a physical validation of diffusion coefficients.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LiquidMixingBenchmark {
  /** Prevent dead-code elimination of observed results. */
  private static volatile double consumed;

  /**
   * Positive asymmetric limiting coefficients with unchanged work in both builds.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class Fixed extends Diffusivity {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /**
     * Construct the controlled limiting-coefficient model.
     *
     * @param p owned property context
     */
    Fixed(PhysicalProperties p) {
      super(p);
    }

    /** {@inheritDoc} */
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
      return i == j ? 0.0 : 1e-9 * (1 + i + 2 * j);
    }
  }

  /**
   * Write warmed raw timing batches.
   *
   * @param args output file
   * @throws Exception if output fails
   */
  public static void main(String[] args) throws Exception {
    try (PrintWriter o = new PrintWriter(args[0])) {
      o.println("components,operation,batch,iterations,ns_per_call,checksum");
      String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
      for (int n : new int[] {2, 6}) {
        SystemSrkEos f = new SystemSrkEos(300, 10);
        for (int i = 0; i < n; i++)
          f.addComponent(names[i], 1.0 / n);
        f.setMixingRule("classic");
        f.init(0);
        f.init(3);
        f.initProperties();
        Fixed model = new Fixed(f.getPhase(0).getPhysicalProperties());
        for (int k = 0; k < 30000; k++)
          consumed = model.calcDiffusionCoefficients(0, 0)[0][1];
        for (String op : new String[] {"mixing", "clone"}) {
          for (int warm = 0; warm < 30000; warm++) {
            if (op.equals("mixing"))
              consumed = model.calcDiffusionCoefficients(0, 0)[0][1];
            else
              consumed = model.clone().getMaxwellStefanBinaryDiffusionCoefficient(0, 1);
          }
          for (int batch = 0; batch < 7; batch++) {
            int iterations = 100000;
            double sum = 0;
            long start = System.nanoTime();
            for (int k = 0; k < iterations; k++) {
              if (op.equals("mixing"))
                sum += model.calcDiffusionCoefficients(0, 0)[0][1];
              else
                sum += model.clone().getMaxwellStefanBinaryDiffusionCoefficient(0, 1);
            }
            long elapsed = System.nanoTime() - start;
            consumed = sum;
            o.printf(Locale.US, "%d,%s,%d,%d,%.12g,%.16g%n", n, op, batch, iterations, (double) elapsed / iterations,
                sum);
          }
        }
      }
    }
  }
}
