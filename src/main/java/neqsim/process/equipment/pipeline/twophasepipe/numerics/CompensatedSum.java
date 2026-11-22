package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/** Numerically stable Neumaier accumulation for long signed transport ledgers. */
final class CompensatedSum implements Serializable {
  private static final long serialVersionUID = 1L;
  private double sum;
  private double correction;

  /** Add one finite ledger contribution. */
  void add(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Ledger contributions must be finite");
    }
    double next = sum + value;
    correction += Math.abs(sum) >= Math.abs(value) ? sum - next + value : value - next + sum;
    sum = next;
  }

  /** @return accumulated value including the lost-low-order correction */
  double value() {
    return sum + correction;
  }
}
