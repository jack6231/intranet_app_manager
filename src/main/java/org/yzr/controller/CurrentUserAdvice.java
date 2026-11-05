package org.yzr.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.yzr.service.AccountService;

import javax.servlet.http.HttpServletRequest;

/**
 * 所有页面共用的登录信息：导航栏显示当前账号或「登录 / 注册」，管理员多一个「管理」入口
 */
@ControllerAdvice
public class CurrentUserAdvice {

    /** 当前登录的用户名，未登录为 null（Spring Security 包装后的 request 对匿名用户返回 null） */
    @ModelAttribute("currentUser")
    public String currentUser(HttpServletRequest request) {
        return request.getRemoteUser();
    }

    /** 当前账号是否管理员：导航栏显示「管理」入口 */
    @ModelAttribute("isAdmin")
    public boolean isAdmin(HttpServletRequest request) {
        return request.isUserInRole(AccountService.ROLE_ADMIN);
    }

    /** 当前页面路径，用作登录/退出后跳回的地址 */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
