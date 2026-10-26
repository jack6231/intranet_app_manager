package org.yzr.utils.ipa;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** 上传 App Store 的脚本生成与输出解析：纯函数，不起 Spring 上下文 */
public class AscScriptsTests {

    @Test
    public void parseProbeReadsExistsAndCommit() {
        Map<String, AscScripts.Probe> m = AscScripts.parseProbe(
                "a\t1\t5693dc1bc7\nb\t0\t\nc\t1\t\nnoise line\n");
        assertTrue(m.get("a").exists);
        assertEquals("5693dc1bc7", m.get("a").commit);
        assertFalse(m.get("b").exists);
        assertTrue(m.get("c").exists);
        assertEquals("", m.get("c").commit);
        assertEquals(3, m.size());
    }

    @Test
    public void sameBuildUsesBidirectionalPrefix() {
        assertTrue(AscScripts.sameBuild("5693dc1bc7", "5693dc1bc7aaaa"));
        assertTrue(AscScripts.sameBuild("5693dc1bc7aaaa", "5693dc1bc7"));
        assertFalse(AscScripts.sameBuild("5693dc1bc7", "1111111111"));
        // 任一边没有 commit 只能信路径
        assertTrue(AscScripts.sameBuild(null, "5693dc1bc7"));
        assertTrue(AscScripts.sameBuild("5693dc1bc7", ""));
    }

    @Test
    public void probeScriptQuotesPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("id1", "/a/it's here.xcarchive");
        String s = AscScripts.probeScript(paths);
        assertTrue(s.contains("probe 'id1' '/a/it'\\''s here.xcarchive'"));
    }

    @Test
    public void uploadScriptEnablesBuildNumberManagement() {
        String s = AscScripts.uploadScript("/x/A-1.xcarchive", "HM2XFJ76LK", "pw");
        assertTrue(s.contains("<key>manageAppVersionAndBuildNumber</key>\n\t<true/>"));
        assertTrue(s.contains("<string>upload</string>"));
        assertTrue(s.contains("<string>app-store-connect</string>"));
        assertTrue(s.contains("security unlock-keychain -p 'pw'"));
        assertTrue(s.contains("-allowProvisioningUpdates"));
    }

    @Test
    public void uploadScriptSkipsUnlockWithoutPassword() {
        assertFalse(AscScripts.uploadScript("/x", "", "").contains("unlock-keychain"));
    }

    @Test
    public void parseBuildNumberFromScriptOutput() {
        String out = "Upload succeeded.\n** EXPORT SUCCEEDED **\n[asc] buildNumber=71701\n[asc] xcodebuild exit=0";
        assertEquals("71701", AscScripts.parseBuildNumber(out));
        assertNull(AscScripts.parseBuildNumber("no build number"));
    }

    @Test
    public void failureSummaryPrefersErrorLines() {
        String out = "line1\nerror: Failed to authenticate\nfoo\n[asc] host=x\n[asc] xcodebuild exit=70";
        assertEquals("error: Failed to authenticate", AscScripts.failureSummary(out, 1000));
        assertEquals("[asc] archive 不存在: /x", AscScripts.failureSummary("[asc] archive 不存在: /x", 1000));
    }
}
