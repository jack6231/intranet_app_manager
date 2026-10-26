package org.yzr.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 在打包机上执行一段 bash 脚本：ssh user@host bash -s，脚本走 stdin。
 *
 * 脚本走 stdin 而不是拼在命令行里，路径/密码里的引号就不用再过一层 ssh 的转义。
 * 打包机自己（235）也走 ssh 回环：平台进程由 LaunchDaemon 拉起，和 Jenkins agent 不在
 * 同一种会话里，直接本地跑 xcodebuild 访问 login 钥匙串不可靠；SSH 会话与 agent 一致。
 */
public class RemoteShell {

    public static class Result {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;

        Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        public boolean ok() {
            return !timedOut && exitCode == 0;
        }
    }

    private RemoteShell() {
    }

    /** 单引号转义：abc'd → 'abc'\''d'，供生成脚本时安全嵌入任意字符串 */
    public static String quote(String s) {
        return "'" + (s == null ? "" : s.replace("'", "'\\''")) + "'";
    }

    /**
     * @param timeoutSeconds 超时后强杀 ssh 进程，结果 timedOut=true
     */
    public static Result run(String user, String host, String script, long timeoutSeconds) {
        List<String> cmd = Arrays.asList("ssh",
                "-o", "BatchMode=yes",
                "-o", "ConnectTimeout=5",
                "-o", "ServerAliveInterval=30",
                user + "@" + host, "bash", "-s");
        Process process = null;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(script.getBytes(StandardCharsets.UTF_8));
            }
            StreamCollector collector = new StreamCollector(process.getInputStream());
            collector.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            collector.join(5000);
            String output = collector.text();
            return finished
                    ? new Result(process.exitValue(), output, false)
                    : new Result(-1, output + "\n[platform] 超时 " + timeoutSeconds + "s，已终止", true);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(-1, "[platform] 执行失败: " + e, false);
        }
    }

    /** 后台读取输出，避免管道写满导致子进程阻塞 */
    private static class StreamCollector extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamCollector(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            int n;
            try {
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        buffer.write(chunk, 0, n);
                    }
                }
            } catch (Exception ignored) {
                // 进程被强杀时流会被关闭，已读到的部分照常返回
            }
        }

        String text() {
            synchronized (buffer) {
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
