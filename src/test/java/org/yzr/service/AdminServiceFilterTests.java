package org.yzr.service;

import org.junit.Test;
import org.yzr.vo.OperationLogEntry;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** 管理页操作记录筛选：纯函数 */
public class AdminServiceFilterTests {

    private final List<OperationLogEntry> logs = Arrays.asList(
            new OperationLogEntry("t3", "删除包", "a", "1", "成功", "ip", "张三"),
            new OperationLogEntry("t2", "上传 App Store", "b", "2", "已发起", "ip", "李四"),
            new OperationLogEntry("t1", "定版", "c", "3", "成功", "ip", "-"));

    @Test
    public void emptyFilterKeepsAll() {
        assertEquals(3, AdminService.filterLogs(logs, null, "").size());
    }

    @Test
    public void filtersByAccountAndAction() {
        assertEquals("t3", AdminService.filterLogs(logs, "张三", null).get(0).getTime());
        assertEquals(0, AdminService.filterLogs(logs, "张三", "定版").size());
        assertEquals("t1", AdminService.filterLogs(logs, "-", null).get(0).getTime());
    }
}
