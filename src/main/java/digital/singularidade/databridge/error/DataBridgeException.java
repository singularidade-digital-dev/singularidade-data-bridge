package digital.singularidade.databridge.error;

public final class DataBridgeException extends RuntimeException {

    private final ErrorCodes code;
    private final String hint;

    public DataBridgeException(ErrorCodes code, String message, String hint) {
        super(message);
        this.code = code;
        this.hint = hint;
    }

    public DataBridgeException(ErrorCodes code, String message, String hint, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.hint = hint;
    }

    public ErrorCodes code() { return code; }
    public String hint() { return hint; }
}
