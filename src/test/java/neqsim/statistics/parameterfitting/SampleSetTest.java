package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * Unit tests for {@link neqsim.statistics.parameterfitting.SampleSet}.
 *
 * @author asmf
 * @version 1.0
 */
class SampleSetTest {
  /**
   * Linear test function so that calculated values are analytically known.
   */
  private static class LinearFunction extends LevenbergMarquardtFunction {
    /**
     * Constructor for LinearFunction.
     *
     * @param initialParams an array of type double
     */
    LinearFunction(double[] initialParams) {
      setInitialGuess(initialParams);
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0] + params[1];
    }
  }

  /**
   * Builds a sample with an attached function and per-variable standard deviations.
   *
   * @param sampleValue a double
   * @param dependentValues an array of type double
   * @param standardDeviations an array of type double
   * @return a {@link neqsim.statistics.parameterfitting.SampleValue} object
   */
  private static SampleValue createSample(double sampleValue, double[] dependentValues, double[] standardDeviations) {
    SampleValue sample = new SampleValue(sampleValue, 1.0, dependentValues.clone(), standardDeviations.clone());
    sample.setFunction(new LinearFunction(new double[] {2.0, 1.0}));
    return sample;
  }

  /**
   * Verifies that a default constructed set is empty.
   */
  @Test
  void defaultConstructorCreatesEmptySet() {
    assertEquals(0, new SampleSet().getLength());
  }

  /**
   * Verifies that the array constructor preserves sample order.
   */
  @Test
  void arrayConstructorPreservesOrder() {
    SampleValue first = createSample(4.0, new double[] {1.0}, new double[] {0.1});
    SampleValue second = createSample(9.0, new double[] {2.0}, new double[] {0.2});

    SampleSet set = new SampleSet(new SampleValue[] {first, second});

    assertEquals(2, set.getLength());
    assertSame(first, set.getSample(0));
    assertSame(second, set.getSample(1));
  }

  /**
   * Verifies that the list constructor copies entries so later list edits are not visible.
   */
  @Test
  void arrayListConstructorCopiesEntries() {
    ArrayList<SampleValue> source = new ArrayList<SampleValue>();
    source.add(createSample(4.0, new double[] {1.0}, new double[] {0.1}));

    SampleSet set = new SampleSet(source);
    source.add(createSample(9.0, new double[] {2.0}, new double[] {0.2}));

    assertEquals(1, set.getLength());
  }

  /**
   * Verifies that add appends a sample to the end of the set.
   */
  @Test
  void addAppendsSample() {
    SampleSet set = new SampleSet();
    SampleValue sample = createSample(4.0, new double[] {1.0}, new double[] {0.1});

    set.add(sample);

    assertEquals(1, set.getLength());
    assertSame(sample, set.getSample(0));
  }

  /**
   * Verifies that addSampleSet appends every sample of the other set by reference.
   */
  @Test
  void addSampleSetAppendsAllSamples() {
    SampleValue first = createSample(4.0, new double[] {1.0}, new double[] {0.1});
    SampleValue second = createSample(9.0, new double[] {2.0}, new double[] {0.2});
    SampleSet target = new SampleSet(new SampleValue[] {first});
    SampleSet source = new SampleSet(new SampleValue[] {second});

    target.addSampleSet(source);

    assertEquals(2, target.getLength());
    assertEquals(1, source.getLength());
    assertSame(second, target.getSample(1));
  }

  /**
   * Verifies that clone deep copies the samples so the copies can be mutated independently.
   */
  @Test
  void cloneCreatesIndependentSamples() {
    SampleSet original = new SampleSet(new SampleValue[] {createSample(4.0, new double[] {1.0}, new double[] {0.1})});

    SampleSet copy = original.clone();
    copy.getSample(0).setDependentValue(0, 42.0);

    assertEquals(1, copy.getLength());
    assertNotSame(original.getSample(0), copy.getSample(0));
    assertEquals(1.0, original.getSample(0).getDependentValue(0), 1.0e-12);
    assertEquals(42.0, copy.getSample(0).getDependentValue(0), 1.0e-12);
  }

  /**
   * Verifies that resampling with zero standard deviation reproduces the dependent values exactly.
   */
  @Test
  void createNewNormalDistributedSetWithZeroDeviationKeepsValues() {
    SampleSet original = new SampleSet(
        new SampleValue[] {createSample(4.0, new double[] {1.0, 5.0}, new double[] {0.0, 0.0})});

    SampleSet resampled = original.createNewNormalDistributedSet();

    assertEquals(1, resampled.getLength());
    assertEquals(1.0, resampled.getSample(0).getDependentValue(0), 1.0e-12);
    assertEquals(5.0, resampled.getSample(0).getDependentValue(1), 1.0e-12);
  }

  /**
   * Verifies that resampling returns a detached set and leaves the original values untouched.
   */
  @Test
  void createNewNormalDistributedSetLeavesOriginalUntouched() {
    SampleSet original = new SampleSet(new SampleValue[] {createSample(4.0, new double[] {1.0}, new double[] {0.5})});

    SampleSet resampled = original.createNewNormalDistributedSet();

    assertNotSame(original.getSample(0), resampled.getSample(0));
    assertEquals(1.0, original.getSample(0).getDependentValue(0), 1.0e-12);
    assertEquals(original.getLength(), resampled.getLength());
  }
}
