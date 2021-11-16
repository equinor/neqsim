/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseGEUnifac;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class UMRPRUFunction extends LevenbergMarquardtFunction {
        private static final long serialVersionUID = 1000;

        /**
         * Creates new Test
         */
        public UMRPRUFunction() {}

        @Override
        public double calcValue(double[] dependentValues) {
                thermoOps.TPflash();
                // system.display();
                return system.getPhases()[0].getComponents()[1].getx();
        }

        @Override
        public void setFittingParams(int i, double value) {
                params[i] = value;

                if (i == 0) {
                        PhaseGEUnifac unifacp =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[0])
                                                        .getMixingRule().getGEPhase();
                        unifacp.setAij(0, 2, value);
                        unifacp.setAij(1, 2, value);

                        PhaseGEUnifac unifacp2 =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[1])
                                                        .getMixingRule().getGEPhase();
                        unifacp2.setAij(0, 2, value);
                        unifacp2.setAij(1, 2, value);
                }

                if (i == 1) {
                        PhaseGEUnifac unifacp =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[0])
                                                        .getMixingRule().getGEPhase();
                        unifacp.setBij(0, 2, value);
                        unifacp.setBij(1, 2, value);

                        PhaseGEUnifac unifacp2 =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[1])
                                                        .getMixingRule().getGEPhase();
                        unifacp2.setBij(0, 2, value);
                        unifacp2.setBij(1, 2, value);
                }

                if (i == 2) {
                        PhaseGEUnifac unifacp =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[0])
                                                        .getMixingRule().getGEPhase();
                        double aa = unifacp.getAij(0, 2);
                        unifacp.setAij(2, 0, value);
                        unifacp.setAij(2, 1, value);

                        PhaseGEUnifac unifacp2 =
                                        (PhaseGEUnifac) ((PhaseEosInterface) system.getPhases()[1])
                                                        .getMixingRule().getGEPhase();
                        unifacp2.setAij(2, 0, value);
                        unifacp2.setAij(2, 1, value);
                }
        }
}
