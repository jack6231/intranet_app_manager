package org.yzr.config;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.yzr.service.AccountService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 登录鉴权：默认全部放行，只有下面几个操作必须登录。
 *
 * 浏览、下载、安装、扫码，以及打包机/E2E 调用的 /app/upload、/p/byCommit 都不需要登录，行为与之前一致。
 * 需要登录的接口只收 POST，并校验 CSRF token（页面通过 meta 标签取 token，见 static/js/auth.js）；
 * CSRF 只对这几个接口和登录/注册/退出生效，其余接口不受影响。
 *
 * 登录有两个入口：登录页的表单提交（成功/失败都重定向），和页面上的登录弹窗（ajax，返回 JSON）。
 * 登录成功后 Spring Security 会换一个新的 CSRF token，弹窗登录时把新 token 带回去，页面不用刷新就能接着操作。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    /** 必须登录的操作：删除包 / 删除应用 / 清理过期包 / 上传 App Store */
    static final String[] LOGIN_REQUIRED = {"/p/delete/**", "/app/delete/**", "/app/clean/**", "/asc/upload/**"};

    /** 登录、注册、退出的表单提交同样校验 CSRF */
    private static final String[] AUTH_FORMS = {"/login", "/register", "/logout"};

    static final String DEFAULT_REDIRECT = "/apps";

    @Resource
    private AccountService accountService;

    // 记住登录的签名密钥。线上写在外置 config/application.properties；为空时每次启动随机生成，重启后需重新登录
    @Value("${app.security.remember-me-key:}")
    private String rememberMeKey;

    @Value("${app.security.remember-me-days:30}")
    private int rememberMeDays;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(accountService).passwordEncoder(AccountService.PASSWORD_ENCODER);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers(LOGIN_REQUIRED).authenticated()
                .anyRequest().permitAll()
            .and().formLogin()
                .loginPage("/login")
                .successHandler((req, res, auth) -> {
                    if (isAjax(req)) {
                        writeJson(res, HttpServletResponse.SC_OK, loginSuccessBody(req, auth.getName()));
                    } else {
                        res.sendRedirect(safeRedirect(req.getParameter("next")));
                    }
                })
                .failureHandler((req, res, e) -> {
                    if (isAjax(req)) {
                        // 200 + success=false：由弹窗自己提示，不触发页面的全局错误处理
                        writeJson(res, HttpServletResponse.SC_OK, failureBody("用户名或密码错误"));
                    } else {
                        res.sendRedirect("/login?error&next=" + encode(safeRedirect(req.getParameter("next"))));
                    }
                })
                .permitAll()
            .and().logout()
                .logoutUrl("/logout")
                .logoutSuccessHandler((req, res, auth) -> res.sendRedirect(safeRedirect(req.getParameter("next"))))
            .and().rememberMe()
                .key(resolveRememberMeKey())
                .tokenValiditySeconds(rememberMeDays * 24 * 3600)
                .userDetailsService(accountService)
            .and().csrf()
                .requireCsrfProtectionMatcher(csrfMatcher())
            .and().exceptionHandling()
                // 受保护接口都是页面 ajax 调用：未登录返回 JSON 401，由 auth.js 统一跳登录页
                .authenticationEntryPoint((req, res, e) ->
                        writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, failureBody("请先登录")))
                // 走到这里只有 CSRF 校验失败（会话过期后页面上的 token 失效）
                .accessDeniedHandler((req, res, e) -> {
                    if (isAjax(req)) {
                        writeJson(res, HttpServletResponse.SC_FORBIDDEN, failureBody("页面已过期，请刷新后重试"));
                    } else {
                        res.sendRedirect("/login?expired");
                    }
                })
            .and().headers()
                // 平台同时以 http/https 提供服务，不能让浏览器记住「只走 https」
                .httpStrictTransportSecurity().disable();
    }

    private static RequestMatcher csrfMatcher() {
        List<RequestMatcher> matchers = new ArrayList<>();
        for (String path : LOGIN_REQUIRED) {
            matchers.add(new AntPathRequestMatcher(path, "POST"));
        }
        for (String path : AUTH_FORMS) {
            matchers.add(new AntPathRequestMatcher(path, "POST"));
        }
        return new OrRequestMatcher(matchers);
    }

    private String resolveRememberMeKey() {
        if (rememberMeKey != null && !rememberMeKey.trim().isEmpty()) {
            return rememberMeKey.trim();
        }
        LOG.warn("app.security.remember-me-key 未配置，使用随机密钥：平台重启后所有人需要重新登录");
        return UUID.randomUUID().toString();
    }

    /**
     * 登录/注册/退出后的跳转地址：只接受站内路径，防止被拼成跳到外站的链接。纯函数
     */
    public static String safeRedirect(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")
                || next.indexOf('\r') >= 0 || next.indexOf('\n') >= 0) {
            return DEFAULT_REDIRECT;
        }
        return next;
    }

    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return DEFAULT_REDIRECT;
        }
    }

    /** 弹窗登录成功：返回用户名和登录后新换的 CSRF token（读取 token 时才会存进会话） */
    private static Map<String, Object> loginSuccessBody(HttpServletRequest request, String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("username", username);
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            body.put("csrfHeader", token.getHeaderName());
            body.put("csrfParameter", token.getParameterName());
            body.put("csrfToken", token.getToken());
        }
        return body;
    }

    private static Map<String, Object> failureBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private static void writeJson(HttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8));
    }
}
