package org.yzr.service;

import org.junit.Test;
import org.yzr.model.Package;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** findByCommit 的 tpE2EBuild 过滤：纯函数，不起 Spring 上下文、不连库 */
public class PackageServiceE2EBuildFilterTests {

    private static Package pkg(String id, String tpE2EBuild) {
        Package p = new Package();
        p.setId(id);
        p.setTpE2EBuild(tpE2EBuild);
        return p;
    }

    @Test
    public void emptyFilterKeepsOriginalList() {
        List<Package> in = Arrays.asList(pkg("a", "1"), pkg("b", null));
        assertSame(in, PackageService.filterByE2EBuild(in, null));
        assertSame(in, PackageService.filterByE2EBuild(in, ""));
        assertSame(in, PackageService.filterByE2EBuild(in, "  "));
    }

    @Test
    public void filterKeepsOnlyMatchingE2EBuilds() {
        List<Package> in = Arrays.asList(pkg("release", null), pkg("e2e", "1"), pkg("legacy", ""), pkg("e2e2", "1"));
        List<Package> out = PackageService.filterByE2EBuild(in, "1");
        assertEquals(2, out.size());
        assertEquals("e2e", out.get(0).getId());
        assertEquals("e2e2", out.get(1).getId());
    }

    @Test
    public void filterTrimsRequestedValue() {
        List<Package> out = PackageService.filterByE2EBuild(Collections.singletonList(pkg("e2e", "1")), " 1 ");
        assertEquals(1, out.size());
    }

    @Test
    public void nullListStaysNull() {
        assertNull(PackageService.filterByE2EBuild(null, "1"));
    }
}
