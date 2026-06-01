package neqsim.process.equipment.pipeline.twophasepipe.numerics;

/**
 * Conservative state limiters for transient two-fluid numerics.
 */
public final class ConservativeStateLimiter {
  private static final double EPS = 1e-12;

  private ConservativeStateLimiter() {}

  /**
   * Enforce non-negative gas, oil, and water masses while preserving total mass whenever the
   * incoming cell state has a positive total inventory.
   *
   * <p>
   * Momentum is scaled with the phase mass correction so phase velocities are preserved. If the
   * total mass is non-positive, positivity and conservation cannot both be satisfied; the method
   * then falls back to the previous finite state when available.
   * </p>
   *
   * @param state conservative cell state [gasMass, oilMass, waterMass, gasMom, oilMom, waterMom,
   *        energy]
   * @param fallback previous finite conservative state, or {@code null}
   */
  public static void enforceThreePhaseMassPositivity(double[] state, double[] fallback) {
    if (state == null || state.length == 0) {
      return;
    }

    if (hasNonFinite(state)) {
      restoreFiniteFallback(state, fallback);
    }

    int massCount = Math.min(3, state.length);
    double totalMass = 0.0;
    double positiveMass = 0.0;
    boolean hasNegativeMass = false;

    for (int i = 0; i < massCount; i++) {
      double mass = state[i];
      if (!Double.isFinite(mass)) {
        restoreFiniteFallback(state, fallback);
        return;
      }
      totalMass += mass;
      if (mass < 0.0) {
        hasNegativeMass = true;
      } else {
        positiveMass += mass;
      }
    }

    if (!hasNegativeMass) {
      return;
    }

    if (totalMass > EPS && positiveMass > EPS) {
      redistributePositiveMass(state, massCount, totalMass, positiveMass);
      return;
    }

    if (fallbackHasPositiveMass(fallback, massCount)) {
      restoreFiniteFallback(state, fallback);
    } else {
      zeroMassAndMomentum(state, massCount);
    }
  }

  private static boolean hasNonFinite(double[] state) {
    for (double value : state) {
      if (!Double.isFinite(value)) {
        return true;
      }
    }
    return false;
  }

  private static void restoreFiniteFallback(double[] state, double[] fallback) {
    if (fallback != null) {
      int n = Math.min(state.length, fallback.length);
      boolean fallbackFinite = true;
      for (int i = 0; i < n; i++) {
        if (!Double.isFinite(fallback[i])) {
          fallbackFinite = false;
          break;
        }
      }
      if (fallbackFinite) {
        System.arraycopy(fallback, 0, state, 0, n);
        for (int i = n; i < state.length; i++) {
          if (!Double.isFinite(state[i])) {
            state[i] = 0.0;
          }
        }
        return;
      }
    }

    for (int i = 0; i < state.length; i++) {
      if (!Double.isFinite(state[i])) {
        state[i] = 0.0;
      }
    }
  }

  private static void redistributePositiveMass(double[] state, int massCount, double totalMass,
      double positiveMass) {
    double massScale = totalMass / positiveMass;

    for (int i = 0; i < massCount; i++) {
      double oldMass = state[i];
      double newMass = oldMass > 0.0 ? oldMass * massScale : 0.0;
      int momentumIndex = i + 3;

      if (momentumIndex < state.length) {
        if (oldMass > EPS && Double.isFinite(state[momentumIndex])) {
          state[momentumIndex] *= newMass / oldMass;
        } else {
          state[momentumIndex] = 0.0;
        }
      }

      state[i] = newMass;
    }
  }

  private static boolean fallbackHasPositiveMass(double[] fallback, int massCount) {
    if (fallback == null || fallback.length < massCount) {
      return false;
    }

    double totalMass = 0.0;
    for (int i = 0; i < massCount; i++) {
      if (!Double.isFinite(fallback[i]) || fallback[i] < 0.0) {
        return false;
      }
      totalMass += fallback[i];
    }
    return totalMass > EPS;
  }

  private static void zeroMassAndMomentum(double[] state, int massCount) {
    for (int i = 0; i < massCount; i++) {
      state[i] = 0.0;
      int momentumIndex = i + 3;
      if (momentumIndex < state.length) {
        state[momentumIndex] = 0.0;
      }
    }
  }
}
