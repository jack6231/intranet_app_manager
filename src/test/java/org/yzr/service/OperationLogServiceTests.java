package org.yzr.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 操作日志行格式：纯函数 */
public class OperationLogServiceTests {

    @Test
    public void formatLineUsesTabsAndDashForEmpty() {
        assertEquals("a\t-\t-\tb c\n", OperationLogService.formatLine("a", null, "", "b\tc"));
    }

    @Test
    public void formatLineFlattensNewlines() {
        assertEquals("x y\n", OperationLogService.formatLine("x\r\ny"));
    }
}
