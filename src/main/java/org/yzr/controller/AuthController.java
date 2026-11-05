package org.yzr.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.yzr.config.SecurityConfig;
import org.yzr.service.AccountService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 登录 / 注册页面。登录表单提交（POST /login）和退出（POST /logout）由 Spring Security 处理，见 SecurityConfig。
 */
@Controller
public class AuthController {

    private static final String VIEW = "auth";

    @Resource
    private AccountService accountService;

    @GetMapping("/login")
    public String login(@RequestParam(value = "next", required = false) String next,
                        HttpServletRequest request, Model model) {
        String error = null;
        if (request.getParameter("error") != null) {
            error = "用户名或密码错误";
        } else if (request.getParameter("expired") != null) {
            error = "页面已过期，请重新登录";
        } else if (request.getParameter("denied") != null) {
            error = "该页面需要管理员账号，请用管理员账号登录";
        }
        return render(model, "login", next, error, null);
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(value = "next", required = false) String next, Model model) {
        return render(model, "register", next, null, null);
    }

    /** 注册成功后直接登录，跳回来源页面 */
    @PostMapping("/register")
    public ModelAndView register(@RequestParam(value = "username", defaultValue = "") String username,
                           @RequestParam(value = "password", defaultValue = "") String password,
                           @RequestParam(value = "confirm", defaultValue = "") String confirm,
                           @RequestParam(value = "next", required = false) String next,
                           HttpServletRequest request, Model model) {
        String error;
        try {
            String name = accountService.register(username, password, confirm);
            signIn(name, request);
            // 不用 "redirect:"：本项目没走 Boot 的 MVC 自动配置，redirect 会把 model（如 currentPath）拼进跳转地址
            RedirectView redirect = new RedirectView(SecurityConfig.safeRedirect(next), true);
            redirect.setExposeModelAttributes(false);
            return new ModelAndView(redirect);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        } catch (DataIntegrityViolationException e) {
            // 两人同时注册同名时由唯一约束兜底
            error = "用户名已被注册";
        }
        return new ModelAndView(render(model, "register", next, error, username));
    }

    private String render(Model model, String mode, String next, String error, String username) {
        model.addAttribute("mode", mode);
        model.addAttribute("next", SecurityConfig.safeRedirect(next));
        model.addAttribute("error", error);
        model.addAttribute("username", username);
        return VIEW;
    }

    /** 把刚注册的账号放进安全上下文，请求结束时由 Spring Security 存进会话 */
    private void signIn(String username, HttpServletRequest request) {
        UserDetails details = accountService.loadUserByUsername(username);
        if (request.getSession(false) != null) {
            // 与登录一致：认证身份变化时换会话 id，防会话固定
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
