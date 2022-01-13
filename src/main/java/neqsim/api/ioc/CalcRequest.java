package neqsim.api.ioc;

import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlRootElement;
import com.google.gson.Gson;
import neqsim.api.ioc.exceptions.NeqSimFlashModeException;
import neqsim.api.ioc.exceptions.NeqSimFnException;
import neqsim.api.ioc.exceptions.NeqSimFractionsException;
import neqsim.api.ioc.exceptions.NeqSimSpException;
import neqsim.thermo.system.SystemInterface;



/**
 *
 * @author jo.lyshoel
 */
@XmlRootElement
public class CalcRequest {

    private static final double fractionsDelta = 0.01;
    private static final double onlineFractionsDelta = 5;

    protected SystemInterface fluid;

    public List<Double> Sp1;
    public List<Double> Sp2;
    public int FlashMode;
    public int fn;
    public String db;
    public List<String> components;
    public List<Double> fractions;
    public List<List<Double>> onlineFractions;

    public CalcRequest(List<Double> spec1, List<Double> spec2, int mode, SystemInterface fluid) {
        this.Sp1 = spec1;
        this.Sp2 = spec2;

        this.FlashMode = mode;
        this.fluid = fluid;
    }

    public CalcRequest(List<Double> spec1, List<Double> spec2, int mode, List<String> components,
            List<List<Double>> fractions) {
        this.Sp1 = spec1;
        this.Sp2 = spec2;

        this.FlashMode = mode;
        this.components = components;
        this.onlineFractions = fractions;
    }

    public boolean isEmpty() {
        if (Sp1 == null || Sp1.isEmpty())
            return true;

        if (Sp2 == null || Sp2.isEmpty())
            return true;

        return false;
    }

    public String toJson() throws Exception {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void validate() throws NeqSimFlashModeException, NeqSimFnException, NeqSimSpException,
            NeqSimFractionsException {
        if (FlashMode < 1 || FlashMode > 3)
            throw new NeqSimFlashModeException("FlashMode must be between 1 and 3");

        if (fn < 1 || fn > 9)
            throw new NeqSimFnException("fn must be between 1 and 9");

        if (Sp1 == null || Sp2 == null)
            throw new NeqSimSpException("Sp1 and Sp2 must be set");

        if (Sp1.size() != Sp2.size())
            throw new NeqSimSpException("Sp1 and Sp2 must be of equal length");

        if (fractions != null) {
            double sum = getFractionsSum(fractionsDelta);
            if (sum != 1 && sum != 100)
                throw new NeqSimFractionsException(
                        "Sum of fractions must be equal to 1 or 100, currently "
                                + getFractionsSum(0.0001));

            if (components != null && components.size() != fractions.size()) {
                throw new NeqSimFractionsException(
                        "Count of fractions and count of components must be equal");
            }
        }

        if (onlineFractions != null) {
            if (components != null && components.size() != onlineFractions.size()) {
                throw new NeqSimFractionsException(
                        "Count of onlineFractions and count of components must be equal");
            }

            for (List<Double> l : onlineFractions) {
                if (l.size() != Sp1.size()) {
                    throw new NeqSimFractionsException(
                            "Count of each online fractions must be equal to length of datapoints");
                }
            }
        }
    }

    public void validateOnlineFractions(int idx) throws NeqSimFractionsException {
        if (onlineFractions != null) {
            double sum = getOnlineFractionsSum(idx, onlineFractionsDelta);
            if (sum != 1 && sum != 100)
                throw new NeqSimFractionsException(
                        "Sum of fractions must be equal to 1 or 100, currently (" + idx + ") "
                                + getOnlineFractionsSum(idx, 0.0001));
        }
    }

    /**
     * Will return true if the fraction is set to be set to static
     * 
     * @return
     */
    public boolean isStaticFractions() {
        return fractions != null;
    }

    /**
     * Will return true if the fraction is set to be set to static
     * 
     * @return
     */
    public boolean isOnlineFractions() {
        if (isStaticFractions())
            return false;

        return onlineFractions != null;
    }

    /**
     * Return sum for the static fractions
     * 
     * @param delta
     * @return
     */
    public double getFractionsSum(double delta) {
        if (fractions != null) {
            double sum = 0.0;
            for (Double f : fractions) {
                sum += f;
            }

            if (sum >= (100.0 - delta) && sum <= (100.0 + delta))
                return 100;

            if (sum >= 1.0 - (delta / 100.0) && sum <= 1.0 + (delta / 100.0))
                return 1;

            return sum;
        }
        return 0;
    }

    public double getOnlineFractionsSum(int idx, double delta) {
        if (onlineFractions != null) {
            double sum = 0.0;

            for (int i = 0; i < onlineFractions.size(); i++) {
                sum += (double) (onlineFractions.get(i).get(idx));
            }

            if (sum > 100.0 - delta && sum < 100.0 + delta)
                return 100;

            if (sum > 1.0 - (delta / 100.0) && sum < 1.0 + (delta / 100.0))
                return 1;

            return sum;
        }
        return 0;
    }

    public double[] getFractionsAsArray() {
        double factor = getFractionsSum(fractionsDelta);
        if (fractions != null && !fractions.isEmpty()) {
            double[] o = new double[fractions.size()];
            for (int i = 0; i < fractions.size(); i++) {
                o[i] = (double) (fractions.get(i) / factor);
            }
            return o;
        }

        return new double[0];
    }

    public double[] getOnlineFractionsAsArray(int idx) {
        double factor = getOnlineFractionsSum(idx, onlineFractionsDelta);
        if (onlineFractions != null) {
            double[] o = new double[onlineFractions.size()];

            for (int i = 0; i < onlineFractions.size(); i++) {
                o[i] = (double) (onlineFractions.get(i).get(idx) / factor);
            }
            return o;
        }

        return new double[0];
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.Sp1);
        hash = 89 * hash + Objects.hashCode(this.Sp2);
        hash = 89 * hash + this.FlashMode;
        hash = 89 * hash + this.fn;
        hash = 89 * hash + Objects.hashCode(this.fractions);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CalcRequest other = (CalcRequest) obj;
        if (this.FlashMode != other.FlashMode) {
            return false;
        }
        if (this.fn != other.fn) {
            return false;
        }
        if (!Objects.equals(this.Sp1, other.Sp1)) {
            return false;
        }
        if (!Objects.equals(this.Sp2, other.Sp2)) {
            return false;
        }
        if (!Objects.equals(this.fractions, other.fractions)) {
            return false;
        }
        return true;
    }

}
