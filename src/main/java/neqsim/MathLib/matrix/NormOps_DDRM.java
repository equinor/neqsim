package neqsim.MathLib.matrix;


public class NormOps_DDRM {

    public static double normF( DMatrixRMaj matrix) {

        return matrix.calculateMatrixNorm(matrix);
    }
}
