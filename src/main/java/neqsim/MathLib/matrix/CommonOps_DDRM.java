package neqsim.MathLib.matrix;

public class CommonOps_DDRM {

    public static DMatrixRMaj transpose(DMatrixRMaj mat) {

        double[] matrixTransposed = new double[mat.numCols*mat.numRows];

        for (int row = 0; row < mat.numRows; row++)
            for (int col = 0; col < mat.numCols; col++) {
                matrixTransposed[col*mat.numRows+row] = mat.matrix[row*mat.numCols+col];
                //this.resultMatT[j][i] = matrix[i][j];
            }

        return new DMatrixRMaj(matrixTransposed, mat.numCols, mat.numRows);
    }
    public static DMatrixRMaj mult(DMatrixRMaj mat, DMatrixRMaj mat2, DMatrixRMaj matout) {


        int numRowsMat1 = mat.numRows;
        int numColsMat1 = mat.numCols;
        int numRowsMat2 = mat2.numRows;
        int numColsMat2 = mat2.numCols;

        double[] result = new double[numRowsMat1*numColsMat2];

        for (int rowsMat1 = 0; rowsMat1 < numRowsMat1; rowsMat1++) {
            for (int colsMat2 = 0; colsMat2 < numColsMat2; colsMat2++) {
                for (int colsMat1 = 0; colsMat1 < numColsMat1; colsMat1++) {
                    //result[w_i1][v_i2] += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                    result[rowsMat1*numColsMat2+colsMat2] += mat.matrix[rowsMat1*numColsMat1+colsMat1] * mat2.matrix[colsMat1*numColsMat2+colsMat2];
                }
            }
        }

        matout = new DMatrixRMaj(result,numRowsMat1,numColsMat2);

        return matout;
    }

    public static DMatrixRMaj mult(SimpleMatrix mat, DMatrixRMaj mat2, DMatrixRMaj matout) {

        int numRowsMat1 = mat.numRows;
        int numColsMat1 = mat.numCols;
        int numRowsMat2 = mat2.numRows;
        int numColsMat2 = mat2.numCols;

        double[] result = new double[numRowsMat1*numColsMat2];

        for (int rowsMat1 = 0; rowsMat1 < numRowsMat1; rowsMat1++) {
            for (int colsMat2 = 0; colsMat2 < numColsMat2; colsMat2++) {
                for (int colsMat1 = 0; colsMat1 < numColsMat1; colsMat1++) {
                    //result[w_i1][v_i2] += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                    result[rowsMat1*numColsMat2+colsMat2] += mat.matrix[rowsMat1*numColsMat1+colsMat1] * mat2.matrix[colsMat1*numColsMat2+colsMat2];
                }
            }
        }

        matout = new DMatrixRMaj(result,numRowsMat1,numColsMat2);

        return matout;

    }

    public static DMatrixRMaj subtract(SimpleMatrix mat, DMatrixRMaj mat2, DMatrixRMaj matout) {

        double[] matrixSub = new double[mat.numRows*mat.numCols];

        for (int row = 0; row < mat.numRows; row++)
            for (int col = 0; col < mat.numCols; col++)
                matrixSub[row*mat.numCols+col] = mat.matrix[row*mat.numCols+col]-mat2.matrix[row*mat.numCols+col];

        matout = new DMatrixRMaj(matrixSub, mat.numRows, mat.numCols);

        return matout;

    }

}
