package myau.auth;

public class AuthError extends Exception {
    private final String code;
    private final String requestId;
    private final int status;

    public AuthError(String code, String message, String requestId, int status) {
        super(message);
        this.code = code;
        this.requestId = requestId;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }

    public int getStatus() {
        return status;
    }
}
