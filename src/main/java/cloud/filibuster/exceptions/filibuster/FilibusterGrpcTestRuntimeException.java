package cloud.filibuster.exceptions.filibuster;

public class FilibusterGrpcTestRuntimeException extends FilibusterRuntimeException {
    public FilibusterGrpcTestRuntimeException(String error, String recommendation) {
        super(error + "\n\t * " + recommendation);
    }

    public FilibusterGrpcTestRuntimeException(String error, String recommendation, RuntimeException re) {
        super(error + "\n\t * " + recommendation + "\n" + re.getClass().getName() + ": " + re.getMessage());
    }
}
