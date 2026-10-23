---
title: "Data Smoothing with DataSmoother"
description: "Source-backed Java example for Savitzky-Golay smoothing with NeqSim DataSmoother, including window constraints and boundary behavior."
---

`DataSmoother` applies the current NeqSim Savitzky-Golay convolution to an
equally spaced array. Use it for exploratory smoothing when the sampling grid is
uniform and the chosen window is small relative to the signal features. Preserve
the original measurements and their uncertainty: smoothing does not replace
outlier assessment, uncertainty propagation, or an engineering data-quality
review.

## Current API

The public constructor is:

`DataSmoother(double[] values, int leftPoints, int rightPoints, int derivativeOrder, int polynomialOrder)`

The supported workflow is to construct the object with the input array, call
`runSmoothing()`, and read the result with `getSmoothedNumbers()`. The class has
no public no-argument constructor and no `setInputNumbers` method.

For the current implementation:

- the array length must be at least `leftPoints + rightPoints + 1`;
- `leftPoints` and `rightPoints` must be non-negative;
- `derivativeOrder` must not exceed `polynomialOrder`;
- `leftPoints + rightPoints` must be at least `polynomialOrder`;
- the first `leftPoints` and last `rightPoints` result values remain unchanged;
- `getSmoothedNumbers()` returns the object's mutable result array rather than a defensive copy.

The implementation currently reports invalid parameter combinations rather than
throwing a validation exception. Validate configuration before construction and
do not treat a returned array from an invalid configuration as qualified output.

## Executable Java 8 example

`DataSmootherTest#documentationExampleCompilesAndRuns` extracts this sole Java
fence, compiles it with Java 8 source settings, enables assertions, and executes
`main`. The numerical reference values are also protected by the existing direct
unit test.

```java
import neqsim.statistics.dataanalysis.datasmoothing.DataSmoother;

public final class DataSmootherGuideExample {
  private DataSmootherGuideExample() {}

  public static void main(String[] args) {
    double[] measurements = {
      10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 15.5, 15.0, 19.0,
      14.0, 14.0, 13.0, 12.0, 12.0, 11.0, 10.0, 9.0, 8.0
    };

    int leftPoints = 3;
    int rightPoints = 3;
    int derivativeOrder = 0;
    int polynomialOrder = 4;
    DataSmoother smoother =
        new DataSmoother(
            measurements, leftPoints, rightPoints, derivativeOrder, polynomialOrder);

    smoother.runSmoothing();
    double[] smoothed = smoother.getSmoothedNumbers();

    assert smoothed.length == measurements.length;
    assert Math.abs(smoothed[0] - measurements[0]) < 1.0e-12;
    assert Math.abs(smoothed[3] - 12.567099567099579) < 1.0e-12;
    assert Math.abs(smoothed[8] - 16.66233766233768) < 1.0e-12;
    assert Math.abs(smoothed[17] - measurements[17]) < 1.0e-12;
  }
}
```

## Interpretation and limits

The seven-point window in the example comprises three points on each side plus
the center point. Polynomial order four controls the local least-squares basis;
derivative order zero requests smoothing. The assertions protect two interior
reference values and the current unchanged-edge behavior.

Do not interpret a visually smoother curve as evidence that the measurement is
more accurate. Review window and polynomial sensitivity, compare with the raw
data, retain units and sampling interval, and validate important trends against
independent physical knowledge. Derivative modes require an explicit sampling
interval scale and separate qualification; they are outside this example.

## Source and related documentation

- [`DataSmoother` source](../../src/main/java/neqsim/statistics/dataanalysis/datasmoothing/DataSmoother.java)
- [`DataSmootherTest` regression](../../src/test/java/neqsim/statistics/dataanalysis/datasmoothing/DataSmootherTest.java)
- [Statistics package overview](README.md)
- [Parameter fitting](parameter_fitting.md)
- [Monte Carlo simulation](monte_carlo_simulation.md)
