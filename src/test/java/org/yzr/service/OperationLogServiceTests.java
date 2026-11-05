package org.yzr.service;

import org.junit.Test;

import org.yzr.vo.OperationLogEntry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    @Test
    public void parsesNewLineWithAccount() {
        OperationLogEntry e = OperationLogService.parseLine(
                "2026-11-05 16:57:08\t删除包\tcom.x 1.0 (Build 1)\tid1\t成功\t10.0.0.1\th\t-\tUA\t张三");
        assertEquals("删除包", e.getAction());
        assertEquals("张三", e.getAccount());
        assertEquals("10.0.0.1", e.getIp());
    }

    @Test
    public void oldLineWithoutAccountColumnIsAnonymous() {
        OperationLogEntry e = OperationLogService.parseLine("t\t定版\tobj\tid\t成功\tip\th\t-\tUA");
        assertEquals(OperationLogEntry.NO_ACCOUNT, e.getAccount());
    }

    @Test
    public void incompleteLineIsSkipped() {
        assertNull(OperationLogService.parseLine("2026-11-05\t删除包\t半行"));
        assertNull(OperationLogService.parseLine(""));
    }
}
