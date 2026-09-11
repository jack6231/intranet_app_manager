package org.yzr.utils.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从安装包里提取构建它的 git commit。
 *
 * 键名统一为 {@link #KEY}，三种格式的来源不同但键名相同：
 *   - IPA：Info.plist 的 GIT_COMMIT_HASH（Xcode build phase 写入，已有）
 *   - APK：AndroidManifest 的 <meta-data android:name="GIT_COMMIT_HASH" .../>
 *   - AAB：先经 bundletool 转成 universal.apk，再走 APK 同一条路
 *
 * 这个类只负责「从哪读、怎么读」——APKParser / AABParser / IPAParser 都调它，
 * 提取规则只存在这一处，不允许各自复制一份（两个 Android parser 本来就有大量重复代码，
 * 再复制提取逻辑必然漂移）。
 */
public final class CommitExtractor {

    /** 三种格式共用的键名 */
    public static final String KEY = "GIT_COMMIT_HASH";

    private static final Pattern META_TAG =
            Pattern.compile("<meta-data\\b[^>]*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_VALUE =
            Pattern.compile("android:value\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private CommitExtractor() {
    }

    /**
     * 从 APK 的 AndroidManifest XML 文本里取 commit。
     *
     * @param manifestXml net.dongliu.apk.parser.ApkFile#getManifestXml() 的返回值
     * @return commit 字符串；没有、为空、或只是资源引用时返回 null
     */
    public static String fromApkManifest(String manifestXml) {
        if (manifestXml == null || manifestXml.isEmpty()) {
            return null;
        }
        Matcher tag = META_TAG.matcher(manifestXml);
        while (tag.find()) {
            String one = tag.group();
            if (!one.contains(KEY)) {
                continue;
            }
            Matcher value = META_VALUE.matcher(one);
            if (value.find()) {
                return normalize(value.group(1));
            }
        }
        return null;
    }

    /**
     * 清洗取到的值。
     *
     * 刻意把 {@code @0x7f13084f} 这类**资源引用**判为无效：manifest 里写
     * {@code android:value="@string/xxx"} 时，编译后 manifest 只剩资源 ID，
     * 拿到的不是 commit 本身。注入时必须用字面量（manifestPlaceholders），不能走资源。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || v.startsWith("@") || "unknown".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v)) {
            return null;
        }
        return v;
    }
}
