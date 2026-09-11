package org.yzr.controller;


import com.alibaba.fastjson.JSON;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yzr.model.App;
import org.yzr.model.Package;
import org.yzr.service.AppService;
import org.yzr.service.PackageService;
import org.yzr.utils.PathManager;
import org.yzr.utils.QRCodeUtil;
import org.yzr.utils.parser.CommitExtractor;
import org.yzr.utils.ipa.PlistGenerator;
import org.yzr.utils.webhook.WebHookClient;
import org.yzr.vo.AppViewModel;
import org.yzr.vo.PackageViewModel;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PackageController {
    @Resource
    private AppService appService;
    @Resource
    private PackageService packageService;
    @Resource
    private PathManager pathManager;

    /**
     * 按 git commit 查安装包（给 CI 用）
     *
     * CI 拿到目标 commit 后先问这个接口有没有现成的包，命中就直接下载安装，省掉一次编译。
     *
     * GET /p/byCommit?commit=<sha>&bundleID=<可选>
     *   commit   必填。可以是短 hash 也可以是全 SHA —— 服务端做双向前缀匹配
     *            （iOS 的 Info.plist 里存的是 10 位短 hash，而调用方通常拿的是 40 位全 SHA）
     *   bundleID 选填。不传则跨应用查同一 commit
     *
     * 可选 e2eBuild=1：只要 TP_E2E 构建的包（Info.plist TPE2EBuild=1），供 E2E 运行器按 commit + 构建类型双字段匹配。
     * 返回 { success, count, packages: [...] }，最新的包在前；每个元素含 downloadURL / gitCommit / tpE2EBuild。
     * 没命中时 success=true、count=0（不是错误，调用方据此走编译流程）。
     */
    @RequestMapping("/p/byCommit")
    @ResponseBody
    public Map<String, Object> findByCommit(@RequestParam(value = "commit", required = false) String commit,
                                            @RequestParam(value = "bundleID", required = false) String bundleID,
                                            @RequestParam(value = "e2eBuild", required = false) String e2eBuild,
                                            HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        if (commit == null || commit.trim().isEmpty()) {
            map.put("success", false);
            map.put("message", "commit 不能为空");
            return map;
        }
        try {
            List<PackageViewModel> packages =
                    this.packageService.findByCommit(bundleID, commit.trim(), e2eBuild, request);
            map.put("success", true);
            map.put("count", packages.size());
            map.put("packages", packages);
        } catch (Exception e) {
            map.put("success", false);
            map.put("message", e.getMessage());
        }
        return map;
    }

    /**
     * 预览页
     * @param code
     * @param request
     * @return
     */
    @GetMapping("/s/{code}")
    public String get(@PathVariable("code") String code, HttpServletRequest request) {
        String scheme = request.getScheme();
        Boolean isHttps = "https".equals(scheme);
        String id = request.getParameter("id");
        AppViewModel viewModel = this.appService.findByCode(code, id, request);
        request.setAttribute("app", viewModel);
        request.setAttribute("ca_path", this.pathManager.getCAPath(isHttps));
        request.setAttribute("basePath", this.pathManager.getBaseURL(isHttps));
        return "install";
    }

    /**
     * 设备列表
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/devices/{id}")
    public String devices(@PathVariable("id") String id, HttpServletRequest request) {
        PackageViewModel viewModel= this.packageService.findById(id, request);
        request.setAttribute("app", viewModel);
        return "devices";
    }

    /**
     * 安装教程
     * @param platform
     * @param request
     * @return
     */
    @GetMapping("/guide/{platform}")
    public String guide(@PathVariable("platform") String platform, HttpServletRequest request) {
        request.setAttribute("platform", platform);
        return "guide";
    }

    /**
     * 上传包
     * @param file
     * @param request
     * @return
     */
    @RequestMapping("/app/upload")
    @ResponseBody
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        String scheme = request.getScheme();
        Boolean isHttps = "https".equals(scheme);
        try {
            String filePath = transfer(file);
            Package aPackage = this.packageService.buildPackage(filePath);
            Map<String , String> extra = new HashMap<>();
            String jobName = request.getParameter("jobName");
            String buildNumber = request.getParameter("buildNumber");
            if (StringUtils.hasLength(jobName)) {
                extra.put("jobName", jobName);
            }
            if (StringUtils.hasLength(buildNumber)) {
                extra.put("buildNumber", buildNumber);
            }
            if (!extra.isEmpty()) {
                aPackage.setExtra(JSON.toJSONString(extra));
            }
            // commit 来源：**包内解析优先，参数兜底**。
            //   iOS 的 ipa 里有 GIT_COMMIT_HASH，解析即得，不需要也不该传参数
            //     —— 从包里读的值不可能和包对不上，传参数则有传错的可能。
            //   Android 的 apk/aab 目前**没有**这个字段（工程还没加 manifestPlaceholders
            //     注入 <meta-data android:name="GIT_COMMIT_HASH">），只能由调用方传。
            // 一旦 Android 工程补上 manifest 注入，这里会自动走解析路径，参数自然失效。
            if (aPackage.getGitCommit() == null) {
                String commitParam = request.getParameter("gitCommit");
                if (StringUtils.hasLength(commitParam)) {
                    aPackage.setGitCommit(CommitExtractor.normalize(commitParam));
                }
            }
            App app = this.appService.getByPackage(aPackage);
            app.getPackageList().add(aPackage);
            app.setCurrentPackage(aPackage);
            aPackage.setApp(app);
            app = this.appService.save(app);
            // URL
            String codeURL = this.pathManager.getBaseURL(isHttps) + "p/code/" + app.getCurrentPackage().getId();
            // 发送WebHook消息
            WebHookClient.sendMessage(app, pathManager);
            map.put("code", codeURL);
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
            e.printStackTrace();
        }
        return map;
    }

    /**
     * 下载文件源文件(ipa 或 apk)
     * @param id
     * @param response
     */
    @RequestMapping("/p/{id}")
    public void download(@PathVariable("id") String id, HttpServletResponse response) {
        try {
            Package aPackage = this.packageService.get(id);
            String path = PathManager.getFullPath(aPackage) + aPackage.getFileName();
            File file = new File(path);
            if(file.exists()){ //判断文件父目录是否存在
                response.setContentType("application/force-download");
                // 文件名称转换
                String fileName = aPackage.getName() + "_" + aPackage.getVersion();
                String ext =  "." + FilenameUtils.getExtension(aPackage.getFileName());
                String appName = new String(fileName.getBytes("UTF-8"), "iso-8859-1");
                String contentType = "application/octet-stream";
                // 设置响应头部
                response.reset();
                response.setContentType(contentType);
                response.setHeader("Content-Disposition", "attachment;fileName=" + appName + ext);
                response.setHeader("Content-Length", String.valueOf(file.length()));
                // 读取文件并写入响应
                byte[] buffer = new byte[4096];
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis);
                     OutputStream os = response.getOutputStream()) {
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取 manifest
     * @param id
     * @param response
     */
    @RequestMapping("/m/{id}")
    public void getManifest(@PathVariable("id") String id, HttpServletRequest request, HttpServletResponse response) {
        try {
            PackageViewModel viewModel = this.packageService.findById(id, request);
            if (viewModel != null && viewModel.isiOS()) {
                response.setContentType("application/force-download");
                response.setHeader("Content-Disposition", "attachment;fileName=manifest.plist");
                Writer writer = new OutputStreamWriter(response.getOutputStream());
                PlistGenerator.generate(viewModel, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取包二维码
     * @param id
     * @param response
     */
    @RequestMapping("/p/code/{id}")
    public void getQrCode(@PathVariable("id") String id, HttpServletRequest request, HttpServletResponse response) {
        try {
            PackageViewModel viewModel = this.packageService.findById(id, request);
            if (viewModel != null) {
                response.setContentType("image/png");
                QRCodeUtil.encode(viewModel.getPreviewURL()).withSize(250, 250).writeTo(response.getOutputStream());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除包
     * @param id
     * @return
     */
    @RequestMapping("/p/delete/{id}")
    @ResponseBody
    public Map<String, Object> deleteById(@PathVariable("id") String id) {
        Map<String, Object> map = new HashMap<>();
        try {
            this.packageService.deleteById(id);
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
        }
        return map;
    }

    @RequestMapping("/p/update/{id}/{isRelease}")
    @ResponseBody
    public Map<String, Object> deleteById(@PathVariable("id") String id, @PathVariable("isRelease") Boolean isRelease) {
        Map<String, Object> map = new HashMap<>();
        try {
            this.packageService.updateById(id, isRelease);
            map.put("success", true);
        } catch (Exception e) {
            map.put("success", false);
        }
        return map;
    }

    /**
     * 转存文件
     * @param srcFile
     * @return
     */
    private String transfer(MultipartFile srcFile) {
        try {
            // 获取文件后缀
            String fileName = srcFile.getOriginalFilename();
            String ext = FilenameUtils.getExtension(fileName);
            // 生成文件名
            String newFileName = UUID.randomUUID().toString() + "." + ext;
            // 转存到 tmp
            String destPath = FileUtils.getTempDirectoryPath() + File.separator + newFileName;
            destPath = destPath.replaceAll("//", "/");
            srcFile.transferTo(new File(destPath));
            return destPath;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
