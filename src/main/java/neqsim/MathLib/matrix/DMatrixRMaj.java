package neqsim.MathLib.matrix;

public class DMatrixRMaj {

    public double[][] matrix;
    int numRows;
    int numCols;

    public DMatrixRMaj( int numRows, int numCols ) {

        matrix = new double[numRows][numCols];
    }

    public DMatrixRMaj( double[][] input ) {

        matrix = input;
    }

    public double calculateMatrixNorm(DMatrixRMaj mat) {
        double sum = 0.0;

        for (int i = 0; i < mat.matrix.length; i++) {
            for (int j = 0; j < mat.matrix[i].length; j++) {
                sum += Math.pow(mat.matrix[i][j], 2);
            }
        }

        return Math.sqrt(sum);
    }

    public DMatrixRMaj transpose() {

        double[][] matrixTransposed = new double[numCols][numRows];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixTransposed[i][j] = matrix[j][i];

        return new DMatrixRMaj(matrixTransposed);
    }

    public DMatrixRMaj mult(DMatrixRMaj mat) {

        DMatrixRMaj matT = mat.transpose();
        int w1 = this.matrix.length;
        int w2 = this.matrix[0].length;
        int v1 = mat.matrix.length;
        int v2 = mat.matrix[0].length;

        double[][] result = new double[w1][v2];

        for (int w_i1 = 0; w_i1 < w1; w_i1++) {
            for (int v_i2 = 0; v_i2 < v2; v_i2++) {
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    //result[w_i1][v_i2] += this.matrix[w_i1][w_i2] * mat.matrix[w_i2][v_i2];
                    result[w_i1][v_i2] += this.matrix[w_i1][w_i2] * matT.matrix[v_i2][w_i2];
                }
            }
        }

        return new DMatrixRMaj(result);
    }

    public void set( int row, int col, double value ) {

        matrix[row][col] = value;
    }

    public void unsafe_set( int row, int col, double value ) {

        matrix[row][col] = value;
    }

    public double get( int row, int col ) {

        return matrix[row][col];
    }

    public double unsafe_get( int row, int col ) {

        return matrix[row][col];
    }


}