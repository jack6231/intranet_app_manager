package org.yzr.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** asc.nodes 配置解析：纯函数 */
public class AscUploadServiceNodesTests {

    @Test
    public void parsesNodeHostPairs() {
        Map<String, String> m = AscUploadService.parseNodes(" Jenkins-01=10.2.54.234 , Jenkins-02=10.2.54.235,bad,=x");
        assertEquals(2, m.size());
        assertEquals("10.2.54.234", m.get("Jenkins-01"));
        assertEquals("10.2.54.235", m.get("Jenkins-02"));
    }

    @Test
    public void emptyConfigGivesEmptyMap() {
        assertTrue(AscUploadService.parseNodes("").isEmpty());
        assertTrue(AscUploadService.parseNodes(null).isEmpty());
    }
}
