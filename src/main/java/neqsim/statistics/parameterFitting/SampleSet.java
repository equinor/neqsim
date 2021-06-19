/*
 * DataSet.java
 *
 * Created on 28. januar 2001, 13:17
 */

package neqsim.statistics.parameterFitting;

import java.util.*;

/**
 * @author  Even Solbraa
 * @version
 */
public class SampleSet implements Cloneable {

    private static final long serialVersionUID = 1000;

    private ArrayList samples = new ArrayList(1);

    /** Creates new DataSet */
    public SampleSet() {
    }

    public SampleSet(SampleValue[] samplesIn) {
        samples.addAll(Arrays.asList(samplesIn));
    }

    public SampleSet(ArrayList samplesIn) {
        for (int i = 0; i < samplesIn.size(); i++) {
            samples.add(samplesIn.get(i));
        }
    }

    @Override
    public Object clone() {
        SampleSet clonedSet = null;
        try {
            clonedSet = (SampleSet) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        clonedSet.samples = (ArrayList) samples.clone();
        for (int i = 0; i < samples.size(); i++) {
            clonedSet.samples.set(i, ((SampleValue) samples.get(i)).clone());
        }

        return clonedSet;
    }

    public void add(SampleValue sampleIn) {
        samples.add(sampleIn);
    }

    public void addSampleSet(SampleSet sampleSet) {
        for (int i = 0; i < sampleSet.getLength(); i++) {
            samples.add(sampleSet.getSample(i));
        }
    }

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

    public int getLength() {
        return samples.size();
    }

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
