/*
 * SampleSet.java
 *
 * Created on 28. januar 2001, 13:17
 */

package neqsim.statistics.parameterFitting;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <p>SampleSet class.</p>
 *
 * @author  Even Solbraa
 * @version $Id: $Id
 */
public class SampleSet implements Cloneable {

    private static final long serialVersionUID = 1000;

    private ArrayList<SampleValue> samples = new ArrayList<SampleValue>(1);

    /**
     * Creates new DataSet
     */
    public SampleSet() {
    }

    /**
     * <p>Constructor for SampleSet.</p>
     *
     * @param samplesIn an array of {@link neqsim.statistics.parameterFitting.SampleValue} objects
     */
    public SampleSet(SampleValue[] samplesIn) {
        samples.addAll(Arrays.asList(samplesIn));
    }

    /**
     * <p>Constructor for SampleSet.</p>
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
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        clonedSet.samples = (ArrayList<SampleValue>) samples.clone();
        for (int i = 0; i < samples.size(); i++) {
            clonedSet.samples.set(i, (SampleValue) samples.get(i).clone());
        }

        return clonedSet;
    }

    /**
     * <p>add.</p>
     *
     * @param sampleIn a {@link neqsim.statistics.parameterFitting.SampleValue} object
     */
    public void add(SampleValue sampleIn) {
        samples.add(sampleIn);
    }

    /**
     * <p>addSampleSet.</p>
     *
     * @param sampleSet a {@link neqsim.statistics.parameterFitting.SampleSet} object
     */
    public void addSampleSet(SampleSet sampleSet) {
        for (int i = 0; i < sampleSet.getLength(); i++) {
            samples.add(sampleSet.getSample(i));
        }
    }

    /**
     * <p>getSample.</p>
     *
     * @param i a int
     * @return a {@link neqsim.statistics.parameterFitting.SampleValue} object
     */
    public SampleValue getSample(int i) {
        return (SampleValue) this.samples.get(i);
    }

    // public SampleValue[] getSamples() {
    // SampleValue[] samplesOut = new SampleValue[samples.size()];
    // for(int i=0;i<samples.size();i++){
    // samplesOut[i] = (SampleValue) this.samples.get(i);
    // }
    // return samplesOut;
    // }

    /**
     * <p>getLength.</p>
     *
     * @return a int
     */
    public int getLength() {
        return samples.size();
    }

    /**
     * <p>createNewNormalDistributedSet.</p>
     *
     * @return a {@link neqsim.statistics.parameterFitting.SampleSet} object
     */
    public SampleSet createNewNormalDistributedSet() {
        SampleSet newSet = (SampleSet) this.clone();

        for (int i = 0; i < samples.size(); i++) {
            for (int j = 0; j < newSet.getSample(i).getDependentValues().length; j++) {
                System.out.println("old Var: " + newSet.getSample(i).getDependentValue(j));
                double newVar = cern.jet.random.Normal.staticNextDouble(newSet.getSample(i).getDependentValue(j),
                        newSet.getSample(i).getStandardDeviation(j));
                newVar = cern.jet.random.Normal.staticNextDouble(newSet.getSample(i).getDependentValue(j),
                        newSet.getSample(i).getStandardDeviation(j));
                newSet.getSample(i).setDependentValue(j, newVar);
                System.out.println("new var: " + newVar);
            }
        }
        return newSet;
    }
}
