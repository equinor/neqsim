package neqsim.thermo.characterization;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class PlusFractionModel implements Serializable {
    private static final long serialVersionUID = 1000;
    private String name = "";
    private SystemInterface system = null;
    double MPlus = 0.0, zPlus = 0.0, densPlus = 0.0;
    int firstPlusFractionNumber = 1;
    int lastPlusFractionNumber = 80;
    int plusComponentNumber = 0;
    private double maxPlusMolarMass = 1000.0;
    double[] z, M, dens, TC, PC, acs;

    public PlusFractionModel(SystemInterface system) {
        this.system = system;
    }

    class PedersenPlusModel implements PlusFractionModelInterface, Cloneable {

        double[] coefs = { 4.4660105006, -1.1266303727, 0.80, 0.0408709562 };
        double[] SRKcoefs = { 4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785 };
        double[] PRcoefs = { 4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785 };
        double[] plusCoefs = { 0.7774204804, -0.05721666929 };
        public double[] PVTsimMolarMass = { 86.1780014038086, 96, 107, 121, 134, 147, 161, 175, 190, 206, 222, 237, 251,
                263, 275, 291, 305, 318, 331, 345, 359, 374, 388, 402, 416, 430, 444, 458, 472, 486, 500, 514, 528, 542,
                556, 570, 584, 598, 612, 626, 640, 654, 668, 682, 696, 710, 724, 738, 752, 766, 780, 794, 808, 822, 836,
                850, 864, 878, 892, 906, 920, 934, 948, 962, 976, 990, 1004, 1018, 1032, 1046, 1060, 1074, 1088, 1102,
                1116, 1130, 1144, 1158, 1172, 1186, 1200, 1214, 1228, 1242, 1256, 1270, 1284, 1298, 1312, 1326, 1340,
                1354, 1368, 1382, 1396, 1410, 1424, 1438, 1452, 1466, 1480, 1494, 1508, 1522, 1536, 1550, 1564, 1578,
                1592, 1606, 1620, 1634, 1648, 1662, 1676, 1690, 1704, 1718, 1732, 1746, 1760, 1774, 1788, 1802, 1816,
                1830, 1844, 1858, 1872, 1886, 1900, 1914, 1928, 1942, 1956, 1970, 1984, 1998, 2012, 2026, 2040, 2054,
                2068, 2082, 2096, 2110, 2124, 2138, 2152, 2166, 2180, 2194, 2208, 2222, 2236, 2250, 2264, 2278, 2292,
                2306, 2320, 2334, 2348, 2362, 2376, 2390, 2404, 2418, 2432, 2446, 2460, 2474, 2488, 2502, 2516, 2530,
                2544, 2558, 2572, 2586, 2600, 2614, 2628, 2642, 2656, 2670, 2684, 2698, 2712, 2726, 2740, 2754, 2768,
                2782, 2796 };
        public double[] PVTsimDensities = { 0.663999915122986, 0.737999975681305, 0.764999985694885, 0.781000018119812,
                0.791999995708466, 0.796000003814697, 0.810000002384186, 0.824999988079071, 0.836000025272369,
                0.842000007629395, 0.848999977111816, 0.845000028610229, 0.84799998998642, 0.857999980449677,
                0.862999975681305, 0.867999970912933, 0.873000025749207, 0.876999974250793, 0.880999982357025,
                0.884999990463257, 0.888999998569489, 0.89300000667572, 0.897000014781952, 0.899999976158142,
                0.902999997138977, 0.907000005245209, 0.910000026226044, 0.912999987602234, 0.916000008583069,
                0.919000029563904, 0.921999990940094, 0.924000024795532, 0.926999986171722, 0.930000007152557,
                0.931999981403351, 0.935000002384186, 0.936999976634979, 0.939000010490417, 0.941999971866608,
                0.944000005722046, 0.945999979972839, 0.948000013828278, 0.949999988079071, 0.952000021934509,
                0.953999996185303, 0.955999970436096, 0.958000004291534, 0.959999978542328, 0.962000012397766,
                0.96399998664856, 0.966000020503998, 0.967000007629395, 0.968999981880188, 0.971000015735626,
                0.972000002861023, 0.973999977111816, 0.976000010967255, 0.976999998092651, 0.978999972343445,
                0.980000019073486, 0.98199999332428, 0.982999980449677, 0.985000014305115, 0.986000001430511,
                0.987999975681305, 0.989000022411346, 0.99099999666214, 0.991999983787537, 0.992999970912933,
                0.995000004768372, 0.995999991893768, 0.996999979019165, 0.999000012874603, 1, 1.00100004673004 };
        boolean firsttime = true;
        int numberOfPlusPseudocomponents = 0;

        public PedersenPlusModel() {
            name = "Standard Pedersen";
            lastPlusFractionNumber = 80;
            maxPlusMolarMass = 0.605;
        }

        /**
         * @return the name
         */
        @Override
		public String getName() {
            return name;
        }

        @Override
		public double getMPlus() {
            return MPlus;
        }

        @Override
		public double getZPlus() {
            return zPlus;
        }

        /**
         * @return the maxPlusMolarMass
         */
        @Override
		public double getMaxPlusMolarMass() {
            return maxPlusMolarMass;
        }

        @Override
		public double getNumberOfPlusPseudocomponents() {
            return numberOfPlusPseudocomponents;
        }

        @Override
		public double[] getZ() {
            return z;
        }

        @Override
		public double[] getM() {
            return M;
        }

        @Override
		public double[] getDens() {
            return dens;
        }

        @Override
		public double getDensPlus() {
            return densPlus;
        }

        @Override
		public int getFirstPlusFractionNumber() {
            return firstPlusFractionNumber;
        }

        @Override
		public int getFirstTBPFractionNumber() {
            int firstTBPNumber = 0;
            double M = 1e10, Mmin = 1e10;
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).isIsTBPfraction()) {
                    M = system.getPhase(0).getComponent(i).getMolarMass();

                    if (M < Mmin) {
                        Mmin = M;
                        if (system.getPhase(0).getComponent(i).getComponentName().substring(3, 4).equals("_")) {
                            firstTBPNumber = new Integer(
                                    system.getPhase(0).getComponent(i).getComponentName().substring(1, 3));
                        } else {
                            firstTBPNumber = new Integer(
                                    system.getPhase(0).getComponent(i).getComponentName().substring(1, 2));
                        }
                    }
                }
            }
            // System.out.println("TBP min " + firstTBPNumber);
            return firstTBPNumber;
        }

        @Override
		public int getPlusComponentNumber() {
            return plusComponentNumber;
        }

        @Override
		public int getLastPlusFractionNumber() {
            return lastPlusFractionNumber;
        }

        @Override
		public boolean hasPlusFraction() {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                    MPlus = system.getPhase(0).getComponent(i).getMolarMass();
                    zPlus = system.getPhase(0).getComponent(i).getz();
                    densPlus = system.getPhase(0).getComponent(i).getNormalLiquidDensity();
                    plusComponentNumber = i;
                    return true;
                }
            }
            return false;
        }

        @Override
		public void characterizePlusFraction(TBPModelInterface TBPModel) {
            system.init(0);
            Integer firstPlusNumber = new Integer(0);
            if (system.getPhase(0).getComponent(plusComponentNumber).getComponentName().substring(3, 4).equals("_")) {
                firstPlusNumber = new Integer(
                        system.getPhase(0).getComponent(plusComponentNumber).getComponentName().substring(1, 3));
            } else {
                firstPlusNumber = new Integer(
                        system.getPhase(0).getComponent(plusComponentNumber).getComponentName().substring(1, 2));
            }
            if (firstPlusFractionNumber < firstPlusNumber.intValue()) {
                firstPlusFractionNumber = firstPlusNumber.intValue();
            }

            numberOfPlusPseudocomponents = lastPlusFractionNumber - firstPlusFractionNumber + 1;

            // System.out.println("first plus fraction number " + firstPlusFractionNumber);
            coefs[0] = 0.1;
            coefs[1] = Math.log(zPlus) / getFirstPlusFractionNumber();
            PedersenPlusModelSolver solver3 = new PedersenPlusModelSolver(system, this);
            solver3.solve();

            z = new double[lastPlusFractionNumber];
            M = new double[lastPlusFractionNumber];
            dens = new double[lastPlusFractionNumber];
            TC = new double[lastPlusFractionNumber];
            PC = new double[lastPlusFractionNumber];
            acs = new double[lastPlusFractionNumber];

            for (int i = firstPlusFractionNumber; i < lastPlusFractionNumber; i++) {
                z[i] = Math.exp(getCoef(0) + getCoef(1) * i);
                M[i] = PVTsimMolarMass[i - 6] / 1000.0;
                dens[i] = getCoef(2) + getCoef(3) * Math.log(i);

                // System.out.println("z,m,dens " + z[i] + " " + M[i] + " " + dens[i]);
            }
        }

        public double[] getCoefs() {
            return this.coefs;
        }

        public double getCoef(int i) {
            return this.coefs[i];
        }

        public void setCoefs(double[] coefs) {
            System.arraycopy(coefs, 0, this.coefs, 0, coefs.length);

            if (firsttime) {
                if (coefs.length == 3) {
                    double Dtot = 0.0;
                    for (int i = firstPlusFractionNumber; i < lastPlusFractionNumber; i++) {
                        Dtot += (densPlus - this.getCoef(2)) / Math.log(i);// (this.getCoef(2)+this.getCoef(3)*Math.log(i)-this.getCoef(2))/Math.log(i);
                    }
                    double lengthPlus = lastPlusFractionNumber - firstPlusFractionNumber;
                    // System.out.println("length plus " + lengthPlus);
                    Dtot /= lengthPlus;
                    // System.out.println("D " + Dtot);
                    this.coefs[3] = Dtot;
                }
                firsttime = false;
            }

            double zSum = 0.0, mSum = 0.0, densSum = 0.0;
            int iter = 0;
            do {
                iter++;
                zSum = 0.0;
                mSum = 0.0;
                densSum = 0.0;
                for (int i = firstPlusFractionNumber; i < lastPlusFractionNumber; i++) {
                    double ztemp = Math.exp(this.getCoef(0) + this.getCoef(1) * (i));
                    double M = PVTsimMolarMass[i - 6] / 1000.0;
                    double dens = this.getCoef(2) + this.getCoef(3) * Math.log(i);
                    zSum += ztemp;
                    mSum += ztemp * M;
                    densSum += (ztemp * M / dens);
                }
                densSum = mSum / densSum;

                this.coefs[3] += 1.0 * (densPlus - densSum) / densSum * this.coefs[3];
                // System.out.println("coef " + this.coefs[3]);
            } while (Math.abs(densPlus - densSum) > 1e-6 && iter < 1000);
        }

        public void setCoefs(double coef, int i) {
            this.coefs[i] = coef;
        }
    }

    private class PedersenHeavyOilPlusModel extends PedersenPlusModel {

        public PedersenHeavyOilPlusModel() {
            lastPlusFractionNumber = 200;
            maxPlusMolarMass = 2.10;
            name = "Pedersen Heavy Oil";
        }
    }

    public PlusFractionModelInterface getModel(String name) {

        if (name.equals("heavyOil")) {
            return new PedersenHeavyOilPlusModel();
        } else {
            return new PedersenPlusModel();
        }
    }
}
