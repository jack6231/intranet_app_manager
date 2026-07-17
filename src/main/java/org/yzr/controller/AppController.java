package org.yzr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yzr.service.AppService;
import org.yzr.utils.PathManager;
import org.yzr.vo.AppViewModel;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AppController {

    @Resource
    private AppService appService;
    @Resource
    private PathManager pathManager;

    @GetMapping("/apps")
    public String apps(HttpServletRequest request) {
        String scheme = request.getScheme();
        Boolean isHttps = "https".equals(scheme);
        try{
            List<AppViewModel> apps = this.appService.findAll(request);
            request.setAttribute("apps", apps);
            request.setAttribute("baseURL", this.pathManager.getBaseURL(isHttps));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "index";
    }

    @GetMapping("/apps/{appID}")
    public String getAppById(@PathVariable("appID") String appID, HttpServletRequest request) {
        AppViewModel appViewModel = this.appService.getById(appID, request);
        request.setAttribute("package", appViewModel);
        request.setAttribute("apps", appViewModel.getPackageList());
        return "list";
    }

    @RequestMapping("/packageList/{appID}")
    @ResponseBody
    public Map<String, Object> getAppPackageList(@PathVariable("appID") String appID, HttpServletRequest request) {
        AppViewModel appViewModel = this.appService.getById(appID, request);
        Map<String, Object> map = new HashMap<>();
        try {
            map.put("packages", appViewModel.getPackageList());
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
        }
        return map;
    }

    @RequestMapping("/app/delete/{id}")
    @ResponseBody
    public Map<String, Object> deleteById(@PathVariable("id") String id) {
        Map<String, Object> map = new HashMap<>();
        try {
            this.appService.deleteById(id);
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
        }
        return map;
    }

    /**
     * 清理指定 App 的过期非定版包(3-30 天)
     * @param appID App ID
     * @param days 清理多少天以前的包
     */
    @RequestMapping("/app/clean/{appID}/{days}")
    @ResponseBody
    public Map<String, Object> clean(@PathVariable("appID") String appID, @PathVariable("days") int days) {
        Map<String, Object> map = new HashMap<>();
        try {
            int count = this.appService.cleanExpiredPackages(appID, days);
            map.put("count", count);
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
            e.printStackTrace();
        }
        return map;
    }

}
