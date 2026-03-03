package cn.hellocsc.handler;

import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.exception.ShareIdExhaustedException;
import cn.hellocsc.exception.ShareNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShareNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleShareNotFound(ShareNotFoundException ex) {
        log.warn("分享未找到: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.warn("文件上传大小超出限制");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "文件大小超过500MB限制");
    }

    @ExceptionHandler(ShareIdExhaustedException.class)
    public ResponseEntity<Map<String, Object>> handleShareIdExhausted(ShareIdExhaustedException ex) {
        log.warn("分享码资源耗尽: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_CODE_EXHAUSTED", ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("安全验证失败: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBusinessException(RuntimeException ex) {
        log.error("业务错误: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorCode);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
