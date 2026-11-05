package org.yzr.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

/**
 * 所有页面共用的登录信息：导航栏显示当前账号或「登录 / 注册」，页面按登录态显示删除、上传 App Store 等按钮
 */
@ControllerAdvice
public class CurrentUserAdvice {

    /** 当前登录的用户名，未登录为 null（Spring Security 包装后的 request 对匿名用户返回 null） */
    @ModelAttribute("currentUser")
    public String currentUser(HttpServletRequest request) {
        return request.getRemoteUser();
    }

    /** 当前页面路径，用作登录/退出后跳回的地址 */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
