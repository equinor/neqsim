package neqsim.process.equipment.util;

import java.util.UUID;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;

/**
 * Dedicated anti-surge calculator.
 *
 * <p>
 * This is a typed convenience subclass of {@link Calculator} for the compressor anti-surge recycle use case. The
 * generic {@link Calculator} historically selected the anti-surge behaviour from a name prefix
 * ({@code "anti surge calculator"}); relying on a magic name is fragile, so this class triggers the same proven
 * {@link Calculator#runAntiSurgeCalc(java.util.UUID)} logic explicitly, regardless of the unit name.
 * </p>
 *
 * <p>
 * The input variable must be the protected {@link Compressor} and the output variable the discharge {@link Splitter}
 * whose recycle branch (split index 1) is opened to keep the compressor on the surge control line.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class AntiSurgeCalculator extends Calculator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for AntiSurgeCalculator.
   *
   * @param name a {@link java.lang.String} object
   */
  public AntiSurgeCalculator(String name) {
    super(name);
  }

  /**
   * Constructor for AntiSurgeCalculator with explicit wiring.
   *
   * @param name a {@link java.lang.String} object
   * @param compressor the protected {@link neqsim.process.equipment.compressor.Compressor}
   * @param antiSurgeSplitter the discharge {@link neqsim.process.equipment.splitter.Splitter} whose recycle branch
   * (split index 1) is controlled
   */
  public AntiSurgeCalculator(String name, Compressor compressor, Splitter antiSurgeSplitter) {
    super(name);
    addInputVariable(compressor);
    setOutputVariable(antiSurgeSplitter);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    runAntiSurgeCalc(id);
  }
}
