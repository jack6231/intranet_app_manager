package org.yzr.utils.ipa;

import org.yzr.utils.RemoteShell;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传 App Store Connect 用到的远程脚本生成与输出解析。纯函数，便于单测。
 */
public class AscScripts {

    private static final Pattern BUILD_NUMBER =
            Pattern.compile("<key>buildNumber</key>\\s*<string>([^<]+)</string>");

    private AscScripts() {
    }

    /** 探测结果：archive 是否存在，以及 archive 内 App 的 GIT_COMMIT_HASH（没有则为空串） */
    public static class Probe {
        public final boolean exists;
        public final String commit;

        public Probe(boolean exists, String commit) {
            this.exists = exists;
            this.commit = commit;
        }
    }

    /**
     * 生成批量探测脚本：每个 key 输出一行「key \t 0|1 \t commit」。
     * @param paths key（包 id）→ archive 绝对路径
     */
    public static String probeScript(Map<String, String> paths) {
        StringBuilder sb = new StringBuilder();
        sb.append("probe() {\n")
          .append("  if [ -d \"$2\" ]; then\n")
          .append("    app=$(ls -d \"$2\"/Products/Applications/*.app 2>/dev/null | head -1)\n")
          .append("    c=$(/usr/libexec/PlistBuddy -c 'Print :GIT_COMMIT_HASH' \"$app/Info.plist\" 2>/dev/null)\n")
          .append("    printf '%s\\t1\\t%s\\n' \"$1\" \"$c\"\n")
          .append("  else\n")
          .append("    printf '%s\\t0\\t\\n' \"$1\"\n")
          .append("  fi\n")
          .append("}\n");
        for (Map.Entry<String, String> e : paths.entrySet()) {
            sb.append("probe ").append(RemoteShell.quote(e.getKey()))
              .append(' ').append(RemoteShell.quote(e.getValue())).append('\n');
        }
        return sb.toString();
    }

    public static Map<String, Probe> parseProbe(String output) {
        Map<String, Probe> result = new HashMap<>();
        if (output == null) {
            return result;
        }
        for (String line : output.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length >= 2 && ("0".equals(parts[1]) || "1".equals(parts[1]))) {
                String commit = parts.length >= 3 ? parts[2].trim() : "";
                result.put(parts[0], new Probe("1".equals(parts[1]), commit));
            }
        }
        return result;
    }

    /**
     * archive 是否就是这个包：两边都有 commit 时必须双向前缀匹配（ipa 与 archive 的 hash 长度可能不同）；
     * 任一边没有 commit 时只能信路径（archive 名带构建号，路径本身就区分了构建）。
     */
    public static boolean sameBuild(String packageCommit, String archiveCommit) {
        if (packageCommit == null || packageCommit.isEmpty() || archiveCommit == null || archiveCommit.isEmpty()) {
            return true;
        }
        return packageCommit.startsWith(archiveCommit) || archiveCommit.startsWith(packageCommit);
    }

    /**
     * 生成上传脚本：解锁钥匙串 → 写临时 ExportOptions → xcodebuild -exportArchive（destination=upload）
     * → 打印 DistributionSummary.plist 供取实际 build 号。认证用打包机 Xcode 里已登录的 Apple ID。
     */
    public static String uploadScript(String archivePath, String teamId, String keychainPassword) {
        StringBuilder sb = new StringBuilder();
        sb.append("ARCHIVE=").append(RemoteShell.quote(archivePath)).append('\n')
          .append("if [ ! -d \"$ARCHIVE\" ]; then echo \"[asc] archive 不存在: $ARCHIVE\"; exit 3; fi\n");
        if (keychainPassword != null && !keychainPassword.isEmpty()) {
            sb.append("security unlock-keychain -p ").append(RemoteShell.quote(keychainPassword))
              .append(" ~/Library/Keychains/login.keychain || { echo '[asc] 解锁钥匙串失败'; exit 4; }\n");
        }
        sb.append("TMP=$(mktemp -d /tmp/asc-upload.XXXXXX)\n")
          .append("trap 'rm -rf \"$TMP\"' EXIT\n")
          .append("cat > \"$TMP/ExportOptions.plist\" <<'PLIST'\n")
          .append(exportOptions(teamId))
          .append("PLIST\n")
          .append("echo \"[asc] host=$(hostname) archive=$ARCHIVE\"\n")
          .append("xcodebuild -exportArchive -archivePath \"$ARCHIVE\" -exportPath \"$TMP/out\"")
          .append(" -exportOptionsPlist \"$TMP/ExportOptions.plist\" -allowProvisioningUpdates\n")
          .append("rc=$?\n")
          .append("if [ -f \"$TMP/out/DistributionSummary.plist\" ]; then\n")
          .append("  echo '[asc] DistributionSummary:'\n")
          .append("  plutil -convert xml1 -o - \"$TMP/out/DistributionSummary.plist\"\n")
          .append("fi\n")
          .append("echo \"[asc] xcodebuild exit=$rc\"\n")
          .append("exit $rc\n");
        return sb.toString();
    }

    static String exportOptions(String teamId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
          .append("<plist version=\"1.0\">\n<dict>\n")
          .append("\t<key>destination</key>\n\t<string>upload</string>\n")
          .append("\t<key>method</key>\n\t<string>app-store-connect</string>\n")
          .append("\t<key>signingStyle</key>\n\t<string>automatic</string>\n")
          // 显式开启：上传时由 Xcode 查 App Store Connect 已有 build 号并自动递增，不依赖默认值
          .append("\t<key>manageAppVersionAndBuildNumber</key>\n\t<true/>\n")
          .append("\t<key>uploadSymbols</key>\n\t<true/>\n")
          .append("\t<key>stripSwiftSymbols</key>\n\t<true/>\n");
        if (teamId != null && !teamId.isEmpty()) {
            sb.append("\t<key>teamID</key>\n\t<string>").append(teamId).append("</string>\n");
        }
        sb.append("</dict>\n</plist>\n");
        return sb.toString();
    }

    /** 从上传输出里的 DistributionSummary 取实际 build 号；取不到返回 null */
    public static String parseBuildNumber(String output) {
        if (output == null) {
            return null;
        }
        Matcher m = BUILD_NUMBER.matcher(output);
        return m.find() ? m.group(1).trim() : null;
    }

    /** 失败摘要：取输出里最后几条 error 行，没有则取末尾几行；截断到 maxLen */
    public static String failureSummary(String output, int maxLen) {
        if (output == null || output.isEmpty()) {
            return "无输出";
        }
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        int picked = 0;
        for (int i = lines.length - 1; i >= 0 && picked < 3; i--) {
            String l = lines[i].trim();
            if (l.toLowerCase().contains("error") || isOwnFailureLine(l)) {
                sb.insert(0, l + "\n");
                picked++;
            }
        }
        if (picked == 0) {
            for (int i = Math.max(0, lines.length - 3); i < lines.length; i++) {
                sb.append(lines[i].trim()).append('\n');
            }
        }
        String s = sb.toString().trim();
        return s.length() > maxLen ? s.substring(s.length() - maxLen) : s;
    }

    /** 本脚本/平台自己打的失败提示行（排除过程信息行） */
    private static boolean isOwnFailureLine(String l) {
        if (l.startsWith("[platform]")) {
            return true;
        }
        return l.startsWith("[asc]") && !l.contains("host=") && !l.contains("exit=") && !l.contains("DistributionSummary");
    }
}
