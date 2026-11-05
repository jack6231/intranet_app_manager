package org.yzr.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.yzr.service.AccountService;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 已登录的请求每次确认账号还在：管理员删掉账号后，该账号已登录的会话在下一次请求时就失效，
 * 不用等会话过期。「30 天内自动登录」的 cookie 会在下次自动登录时因账号不存在被作废。
 *
 * 不声明成 Bean（否则 Spring Boot 会把它再注册成普通 Servlet Filter），只挂在 Spring Security 过滤链里。
 */
class AccountStillExistsFilter extends OncePerRequestFilter {

    private final AccountService accountService;

    AccountStillExistsFilter(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && !accountService.exists(auth.getName())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
