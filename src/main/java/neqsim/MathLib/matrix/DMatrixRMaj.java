package neqsim.MathLib.matrix;

public class DMatrixRMaj {

    public double[] matrix;
    int numRows;
    int numCols;

    public DMatrixRMaj( int numRows, int numCols ) {

        matrix = new double[numRows*numCols];
    }

    public DMatrixRMaj( double[][] input ) {

        this.numRows = input.length;
        this.numCols = input[0].length;

        double[] inputArray = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                inputArray[row*this.numCols+col] = input[row][col];

        this.matrix = inputArray;

    }

    public DMatrixRMaj( double[] input, int numRows, int numCols ) {

        matrix = input;
        this.numRows = numRows;
        this.numCols = numCols;
    }

    public double calculateMatrixNorm(DMatrixRMaj mat) {
        double sum = 0.0;

        for (int i = 0; i < mat.matrix.length; i++) {
                sum += Math.pow(mat.matrix[i], 2);
        }

        return Math.sqrt(sum);
    }

    public DMatrixRMaj transpose() {

        double[] matrixTransposed = new double[this.numCols*this.numRows];

        for (int row = 0; row < this.numRows; row++)
            for (int col = 0; col < this.numCols; col++) {
                matrixTransposed[col*this.numRows+row] = matrix[row*this.numCols+col];
                //this.resultMatT[j][i] = matrix[i][j];
            }

        return new DMatrixRMaj(matrixTransposed,this.numCols,this.numRows);
    }

    public DMatrixRMaj mult(DMatrixRMaj mat2) {

        int numRowsMat1 = this.numRows;
        int numColsMat1 = this.numCols;
        int numRowsMat2 = mat2.numRows;
        int numColsMat2 = mat2.numCols;

        double[] result = new double[numRowsMat1*numColsMat2];

        for (int rowsMat1 = 0; rowsMat1 < numRowsMat1; rowsMat1++) {
            for (int colsMat2 = 0; colsMat2 < numColsMat2; colsMat2++) {
                for (int colsMat1 = 0; colsMat1 < numColsMat1; colsMat1++) {
                    //result[w_i1][v_i2] += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                    result[rowsMat1*numColsMat2+colsMat2] += this.matrix[rowsMat1*numColsMat1+colsMat1] * mat2.matrix[colsMat1*numColsMat2+colsMat2];
                }
            }
        }

        return new DMatrixRMaj(result, numRowsMat1, numColsMat2);
    }

    public void set( int row, int col, double value ) {

        matrix[row*this.numCols+col] = value;
    }

    public void unsafe_set( int row, int col, double value ) {

        matrix[row*this.numCols+col] = value;
    }

    public double get( int row, int col ) {

        return matrix[row*this.numCols+col];
    }

    public double unsafe_get( int row, int col ) {

        return matrix[row*this.numCols+col];
    }


}