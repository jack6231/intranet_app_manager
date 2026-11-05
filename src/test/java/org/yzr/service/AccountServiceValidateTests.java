package org.yzr.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** 注册参数校验：纯函数 */
public class AccountServiceValidateTests {

    @Test
    public void acceptsChineseNameAndEmailLikeName() {
        assertNull(AccountService.validate("张三", "123456", "123456"));
        assertNull(AccountService.validate("tom.lee@tp", "123456", "123456"));
    }

    @Test
    public void rejectsBadUsername() {
        assertEquals("用户名长度需在 2~32 个字符之间", AccountService.validate("a", "123456", "123456"));
        assertEquals("用户名只能包含中文、字母、数字和 . _ @ -", AccountService.validate("a b", "123456", "123456"));
    }

    @Test
    public void rejectsShortOrMismatchedPassword() {
        assertEquals("密码长度需在 6~64 位之间", AccountService.validate("tom", "12345", "12345"));
        assertEquals("两次输入的密码不一致", AccountService.validate("tom", "123456", "1234567"));
    }
}
