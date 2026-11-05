package org.yzr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yzr.service.AccountService;
import org.yzr.service.AdminService;
import org.yzr.service.OperationLogService;
import org.yzr.vo.OperationLogEntry;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理页（仅管理员，见 SecurityConfig.ADMIN_ONLY）：账号管理、每个人的操作记录、App Store 上传人。
 */
@Controller
public class AdminController {

    /** 操作记录最多显示的条数（最新的在前） */
    static final int LOG_LIMIT = 500;
    private static final List<String> TABS = Arrays.asList("accounts", "logs", "asc");

    @Resource
    private AccountService accountService;
    @Resource
    private AdminService adminService;
    @Resource
    private OperationLogService operationLogService;

    @GetMapping("/admin")
    public String admin(@RequestParam(value = "tab", defaultValue = "accounts") String tab,
                        @RequestParam(value = "user", required = false) String user,
                        @RequestParam(value = "action", required = false) String action,
                        Model model) throws IOException {
        List<OperationLogEntry> logs = operationLogService.readAll();
        List<OperationLogEntry> matched = AdminService.filterLogs(logs, user, action);

        model.addAttribute("tab", TABS.contains(tab) ? tab : "accounts");
        model.addAttribute("accounts", adminService.accountRows(logs));
        model.addAttribute("logAccounts", adminService.logAccounts(logs));
        model.addAttribute("actions", OperationLogService.ACTIONS);
        model.addAttribute("selectedUser", user);
        model.addAttribute("selectedAction", action);
        model.addAttribute("logs", matched.subList(0, Math.min(LOG_LIMIT, matched.size())));
        model.addAttribute("logTotal", matched.size());
        model.addAttribute("logLimit", LOG_LIMIT);
        model.addAttribute("ascUploads", adminService.ascUploads());
        return "admin";
    }

    /** 删除账号：管理员账号不能删；被删账号已登录的会话下一次请求即失效 */
    @PostMapping("/admin/accounts/{id}/delete")
    @ResponseBody
    public Map<String, Object> deleteAccount(@PathVariable("id") String id, HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        // 删除前先取用户名，删完就查不到了
        String target = accountService.usernameOf(id);
        try {
            accountService.deleteAccount(id);
            map.put("success", true);
        } catch (IllegalArgumentException e) {
            map.put("success", false);
            map.put("message", e.getMessage());
        }
        operationLogService.record(request, OperationLogService.DELETE_ACCOUNT, id, target,
                Boolean.TRUE.equals(map.get("success")) ? "成功" : "失败: " + map.get("message"));
        return map;
    }
}
