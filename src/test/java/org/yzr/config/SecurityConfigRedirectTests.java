package org.yzr.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 登录/注册/退出后的跳转只接受站内路径：纯函数 */
public class SecurityConfigRedirectTests {

    @Test
    public void keepsSitePath() {
        assertEquals("/apps/abc", SecurityConfig.safeRedirect("/apps/abc"));
    }

    @Test
    public void fallsBackForExternalOrMissing() {
        assertEquals("/apps", SecurityConfig.safeRedirect(null));
        assertEquals("/apps", SecurityConfig.safeRedirect("http://evil.com"));
        assertEquals("/apps", SecurityConfig.safeRedirect("//evil.com"));
        assertEquals("/apps", SecurityConfig.safeRedirect("/\\evil.com"));
        assertEquals("/apps", SecurityConfig.safeRedirect("/apps\r\nSet-Cookie: x"));
    }
}
