package cn.hellocsc.controller;

import cn.hellocsc.model.AdminSharesPageResponse;
import cn.hellocsc.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final ShareService shareService;

    @Value("${app.admin.password:csc}")
    private String adminPassword;

    /**
     * 验证管理员口令
     */
    @PostMapping("/auth")
    public Map<String, Object> authenticate(@RequestBody Map<String, String> request) {
        String password = request.get("password");

        if (adminPassword.equals(password)) {
            log.info("管理员登录成功");
            return Map.of("success", true, "message", "认证成功");
        }

        log.warn("管理员登录失败 - 口令错误");
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "口令错误");
    }

    @GetMapping("/shares")
    public AdminSharesPageResponse getSharesPage(
            @RequestHeader("X-Admin-Password") String password,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "createTimeDesc") String sort) {
        validatePassword(password);

        int safeSize = Math.min(Math.max(size, 1), 100);
        AdminSharesPageResponse result = shareService.getAdminSharesPage(page, safeSize, type, sort);
        log.info("管理员分页查询分享 - page={}, size={}, 返回={}/{}",
                result.getPage(), result.getSize(), result.getItems().size(), result.getTotalItems());
        return result;
    }

    @DeleteMapping("/shares/{shareId}")
    public Map<String, Object> deleteShare(
            @PathVariable String shareId,
            @RequestHeader("X-Admin-Password") String password) {

        validatePassword(password);

        shareService.deleteShare(shareId);
        log.info("管理员删除分享 - ID: {}", shareId);

        return Map.of("success", true, "message", "删除成功");
    }

    private void validatePassword(String password) {
        if (!adminPassword.equals(password)) {
            log.warn("管理员操作失败 - 口令验证失败");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "口令验证失败");
        }
    }
}
