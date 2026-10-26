package org.yzr.controller;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yzr.service.AscUploadService;
import org.yzr.service.OperationLogService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 上传 App Store Connect 的接口（列表页调用）
 */
@Controller
public class AscController {

    @Resource
    private AscUploadService ascUploadService;
    @Resource
    private OperationLogService operationLogService;

    /**
     * 某 App 下 iOS 包的上传状态 + archive 是否可用
     * 返回 { success, states: { 包id: { status, available, reason, buildVersion, message, uploadTime } } }
     */
    @RequestMapping("/asc/state/{appId}")
    @ResponseBody
    public Map<String, Object> state(@PathVariable("appId") String appId) {
        Map<String, Object> map = new HashMap<>();
        try {
            map.put("states", ascUploadService.states(appId));
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
            map.put("message", e.getMessage());
            e.printStackTrace();
        }
        return map;
    }

    /** 发起上传（异步），立即返回；前端轮询 state 看结果 */
    @RequestMapping("/asc/upload/{id}")
    @ResponseBody
    public Map<String, Object> upload(@PathVariable("id") String id, HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        String target = operationLogService.describePackage(id);
        try {
            ascUploadService.startUpload(id);
            map.put("success", true);
        } catch (IllegalStateException e) {
            map.put("success", false);
            map.put("message", e.getMessage());
        } catch (Exception e) {
            map.put("success", false);
            map.put("message", "发起上传失败: " + e.getMessage());
            e.printStackTrace();
        }
        operationLogService.record(request, OperationLogService.ASC_UPLOAD, id, target,
                Boolean.TRUE.equals(map.get("success")) ? "已发起" : "未发起: " + map.get("message"));
        return map;
    }

    /** 最近一次上传的完整日志（纯文本） */
    @RequestMapping("/asc/log/{id}")
    public void log(@PathVariable("id") String id, HttpServletResponse response) {
        try {
            File f = ascUploadService.logFile(id);
            response.setContentType("text/plain;charset=UTF-8");
            String text = f == null ? "暂无上传日志" : FileUtils.readFileToString(f, StandardCharsets.UTF_8);
            response.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
