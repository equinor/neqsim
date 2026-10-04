---
title: Machine Learning Surrogate Integration
description: Register bounded surrogate models with validated physics fallback and explicit engineering limits.
---

# Machine Learning Surrogate Integration

`SurrogateModelRegistry` coordinates fast surrogate predictions with a caller-supplied
physics fallback. It validates vector shape and numeric values, tracks use outside the
declared training range, and rejects invalid results. It does not define feature names,
units, normalization, physical constraints, or model accuracy: the application owns
those contracts.

## Executable bounded-fallback example

This example uses two inputs in a fixed order:

1. temperature in K;
2. pressure in bara.

The single output is an illustrative positive screening value. The surrogate is used
only inside its inclusive training bounds. An out-of-range request is sent to the
physics callback without running the surrogate.

```java
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.ml.surrogate.SurrogateModelRegistry;
import neqsim.process.ml.surrogate.SurrogateModelRegistry.SurrogateMetadata;
import neqsim.process.ml.surrogate.SurrogateModelRegistry.SurrogateModel;

public final class SurrogateFallbackExample {
  private static final Logger logger = LogManager.getLogger(SurrogateFallbackExample.class);

  private SurrogateFallbackExample() {}

  public static void main(String[] args) {
    SurrogateModelRegistry registry = SurrogateModelRegistry.getInstance();
    registry.clear();
    registry.setEnableFallback(true);

    SurrogateMetadata metadata = new SurrogateMetadata();
    metadata.setModelType("bounded-screening-example");
    metadata.setTrainingDataSource("illustrative-generated-data");
    metadata.setInputBounds(new double[] {250.0, 1.0}, new double[] {400.0, 100.0});

    SurrogateModel surrogate = new SurrogateModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public double[] predict(double[] input) {
        double pressureBara = input[1];
        return new double[] {0.96 * pressureBara};
      }

      @Override
      public int getInputDimension() {
        return 2;
      }

      @Override
      public int getOutputDimension() {
        return 1;
      }
    };

    String modelId = "bounded-pressure-screen";
    registry.register(modelId, surrogate, metadata);

    Function<double[], double[]> physicsFallback = input -> {
      double pressureBara = input[1];
      return new double[] {1.02 * pressureBara};
    };

    double[] surrogateResult =
        registry.predictWithFallback(modelId, new double[] {320.0, 50.0}, physicsFallback);
    double[] fallbackResult =
        registry.predictWithFallback(modelId, new double[] {320.0, 120.0}, physicsFallback);

    assert surrogateResult.length == 1;
    assert Double.isFinite(surrogateResult[0]);
    assert surrogateResult[0] > 0.0;
    assert Math.abs(surrogateResult[0] - 48.0) < 1.0e-12;

    assert fallbackResult.length == 1;
    assert Double.isFinite(fallbackResult[0]);
    assert fallbackResult[0] > 0.0;
    assert Math.abs(fallbackResult[0] - 122.4) < 1.0e-12;

    SurrogateMetadata statistics = registry.getMetadata(modelId).orElseThrow(
        () -> new IllegalStateException("Registered model metadata is missing"));
    assert statistics.getPredictionCount() == 1;
    assert statistics.getFailureCount() == 0;
    assert Math.abs(statistics.getFailureRate()) < 1.0e-12;
    assert Math.abs(statistics.getExtrapolationRate() - 0.5) < 1.0e-12;

    logger.info("Validated surrogate and fallback results for {}", modelId);
    registry.unregister(modelId);
  }
}
```

Run documentation examples with assertions enabled. The repository documentation
contract compiles this exact fence for Java 8 source compatibility and invokes it with
assertions enabled.

## Request and result contract

Declare positive input and output dimensions whenever they are known.
`-1` means unknown; zero and values below `-1` are rejected. If metadata bounds
are present, their dimension must agree with the model input dimension.

`setInputBounds(min, max)` copies nonempty arrays of equal length. Every entry must
be finite and each minimum must be less than or equal to its maximum. Bounds are
inclusive. Leave bounds unset for an unbounded numerical range; infinity is not a
supported bound convention.

Each `predictWithFallback` request behaves as follows:

| Condition | Behavior |
| --- | --- |
| Null, empty, nonfinite, or wrong-length input | Throws `IllegalArgumentException` before either callback; counters are unchanged. |
| Well-formed input inside the bounds | Runs the surrogate and validates its result. |
| Well-formed input outside the bounds | Records extrapolation and runs physics without calling the surrogate. |
| Missing model | Runs physics; no model counters exist. |
| Surrogate throws or returns null, empty, nonfinite, or wrong-length output | Records one surrogate failure, then runs physics. |
| Physics throws or returns an invalid output | Throws `IllegalStateException` and preserves available causes. |
| Fallback is required but disabled or absent | Throws `IllegalStateException`; the registry does not extrapolate the surrogate. |

Both callbacks receive independent copies of the request vector. A failed surrogate
therefore cannot corrupt the caller's input or the physics request.

## Monitoring semantics

For registered models:

- `getPredictionCount()` counts accepted surrogate results only.
- `getFailureCount()` counts surrogate exceptions or rejected surrogate results.
- `getFailureRate()` divides failures by completed surrogate attempts.
- `getExtrapolationRate()` divides out-of-range requests by all well-formed requests
  to the registered model.
- `getLastUsed()` is updated after an accepted surrogate prediction.

Record feature names, order, units, normalization, training-data revision, model
revision, physics-fallback revision, and acceptance thresholds outside the numeric
vectors. The registry cannot infer them.

## Persistence boundary

`saveModel(modelId, path)` serializes the registered model and metadata;
`loadModel(modelId, path)` validates the restored schema before registration.
Only load trusted files. Java serialization is not a portable exchange format and
must not be used with untrusted input. A loaded model still requires the same
external feature, unit, provenance, and physical-validation contract.

## Engineering boundary

Finite values, matching dimensions, training-range membership, and a successful
fallback establish software-contract validity only. They do not prove:

- thermodynamic consistency or phase stability;
- mass or energy conservation;
- prediction accuracy or uncertainty;
- suitability for extrapolation, control, optimization, or safety decisions;
- equipment limits, operating envelopes, or regulatory compliance.

Apply independent physical checks to both surrogate and physics results. Keep the
physics route directly testable, monitor drift and fallback rates, version the
surrogate with its data and feature contract, and require accountable engineering
review before operational use.

## Related documentation

- [AI validation framework](../../integration/ai_validation_framework)
- [Digital-twin integration](../digital-twin-integration)
- [Batch studies and optimization](../optimization/)
