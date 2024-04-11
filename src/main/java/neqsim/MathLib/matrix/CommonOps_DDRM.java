package neqsim.MathLib.matrix;

public class CommonOps_DDRM {

    public static DMatrixRMaj mult(DMatrixRMaj mat, DMatrixRMaj mat2, DMatrixRMaj matout) {

        int w1 = mat.matrix.length;
        int w2 = mat.matrix[0].length;
        int v1 = mat2.matrix.length;
        int v2 = mat2.matrix[0].length;

        double[][] result = new double[w1][v2];

        for (int w_i1 = 0; w_i1 < w1; w_i1++) {
            for (int v_i2 = 0; v_i2 < v2; v_i2++) {
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    result[w_i1][v_i2] += mat.matrix[w_i1][w_i2] * mat2.matrix[w_i2][v_i2];
                }

/*
                double sum = 0;
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    sum += mat.matrix[w_i1][w_i2] * mat2.matrix[w_i2][v_i2];
                }
                result[w_i1][v_i2] = sum;
 */
            }
        }

        matout = new DMatrixRMaj(result);

        return matout;
    }

    public static DMatrixRMaj mult(SimpleMatrix mat, DMatrixRMaj mat2, DMatrixRMaj matout) {

        //double[][] mat = matInput.matrix;
        //matInput2

        int w1 = mat.matrix.length;
        int w2 = mat.matrix[0].length;
        int v1 = mat2.matrix.length;
        int v2 = mat2.matrix[0].length;

        double[][] result = new double[w1][v2];

        for (int w_i1 = 0; w_i1 < w1; w_i1++) {
            for (int v_i2 = 0; v_i2 < v2; v_i2++) {
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    result[w_i1][v_i2] += mat.matrix[w_i1][w_i2] * mat2.matrix[w_i2][v_i2];
                }
            }
        }

        matout = new DMatrixRMaj(result);

        return matout;

    }

    public static DMatrixRMaj subtract(SimpleMatrix mat, DMatrixRMaj mat2, DMatrixRMaj matout) {

        int numRows = mat.matrix.length;
        int numCols = mat.matrix[0].length;

        double[][] matrixSub = new double[numRows][numCols];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixSub[i][j] = mat.matrix[i][j]-mat2.matrix[i][j];

        matout = new DMatrixRMaj(matrixSub);

        return matout;

    }

}
