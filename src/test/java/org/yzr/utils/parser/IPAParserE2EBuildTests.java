package org.yzr.utils.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Info.plist TPE2EBuild 的清洗：Release 包展开为空串、历史包缺键，都要落成 null */
public class IPAParserE2EBuildTests {

    @Test
    public void missingOrBlankBecomesNull() {
        assertNull(IPAParser.normalizeE2EBuild(null));
        assertNull(IPAParser.normalizeE2EBuild(""));
        assertNull(IPAParser.normalizeE2EBuild("   "));
    }

    @Test
    public void valueIsTrimmed() {
        assertEquals("1", IPAParser.normalizeE2EBuild("1"));
        assertEquals("1", IPAParser.normalizeE2EBuild(" 1\n"));
    }
}
