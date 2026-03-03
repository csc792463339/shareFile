package cn.hellocsc.exception;

public class ShareIdExhaustedException extends RuntimeException {
    public ShareIdExhaustedException(String message) {
        super(message);
    }
}
