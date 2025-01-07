/*
 * SampleSet.java
 *
 * Created on 28. januar 2001, 13:17
 */

package neqsim.statistics.parameterfitting;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * SampleSet class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SampleSet implements Cloneable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SampleSet.class);
  private ArrayList<SampleValue> samples = new ArrayList<SampleValue>(1);

  /**
   * <p>
   * Constructor for SampleSet.
   * </p>
   */
  public SampleSet() {}

  /**
   * <p>
   * Constructor for SampleSet.
   * </p>
   *
   * @param samplesIn an array of {@link neqsim.statistics.parameterfitting.SampleValue} objects
   */
  public SampleSet(SampleValue[] samplesIn) {
    samples.addAll(Arrays.asList(samplesIn));
  }

  /**
   * <p>
   * Constructor for SampleSet.
   * </p>
   *
   * @param samplesIn a {@link java.util.ArrayList} object
   */
  public SampleSet(ArrayList<SampleValue> samplesIn) {
    for (int i = 0; i < samplesIn.size(); i++) {
      samples.add(samplesIn.get(i));
    }
  }

  /** {@inheritDoc} */
  @Override
  public SampleSet clone() {
    SampleSet clonedSet = null;
    try {
      clonedSet = (SampleSet) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    clonedSet.samples = new ArrayList<SampleValue>(samples);
    for (int i = 0; i < samples.size(); i++) {
      clonedSet.samples.set(i, samples.get(i).clone());
    }

    return clonedSet;
  }

  /**
   * <p>
   * add.
   * </p>
   *
   * @param sampleIn a {@link neqsim.statistics.parameterfitting.SampleValue} object
   */
  public void add(SampleValue sampleIn) {
    samples.add(sampleIn);
  }

  /**
   * <p>
   * addSampleSet.
   * </p>
   *
   * @param sampleSet a {@link neqsim.statistics.parameterfitting.SampleSet} object
   */
  public void addSampleSet(SampleSet sampleSet) {
    for (int i = 0; i < sampleSet.getLength(); i++) {
      samples.add(sampleSet.getSample(i));
    }
  }

  /**
   * <p>
   * getSample.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.statistics.parameterfitting.SampleValue} object
   */
  public SampleValue getSample(int i) {
    return this.samples.get(i);
  }

  // public SampleValue[] getSamples() {
  // SampleValue[] samplesOut = new SampleValue[samples.size()];
  // for(int i=0;i<samples.size();i++){
  // samplesOut[i] = (SampleValue) this.samples.get(i);
  // }
  // return samplesOut;
  // }

  /**
   * <p>
   * getLength.
   * </p>
   *
   * @return a int
   */
  public int getLength() {
    return samples.size();
  }

  /**
   * <p>
   * createNewNormalDistributedSet.
   * </p>
   *
   * @return a {@link neqsim.statistics.parameterfitting.SampleSet} object
   */
  public SampleSet createNewNormalDistributedSet() {
    SampleSet newSet = this.clone();

    for (int i = 0; i < samples.size(); i++) {
      for (int j = 0; j < newSet.getSample(i).getDependentValues().length; j++) {
        System.out.println("old Var: " + newSet.getSample(i).getDependentValue(j));
        double newVar = cern.jet.random.Normal.staticNextDouble(
            newSet.getSample(i).getDependentValue(j), newSet.getSample(i).getStandardDeviation(j));
        newVar = cern.jet.random.Normal.staticNextDouble(newSet.getSample(i).getDependentValue(j),
            newSet.getSample(i).getStandardDeviation(j));
        newSet.getSample(i).setDependentValue(j, newVar);
        System.out.println("new var: " + newVar);
      }
    }
    return newSet;
  }
}
