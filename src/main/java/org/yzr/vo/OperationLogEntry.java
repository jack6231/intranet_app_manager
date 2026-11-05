package org.yzr.vo;

/**
 * operation.log 的一行（管理页展示用，只读）。
 * 1.0.14 之前的行没有账号列，account 为 "-"。
 */
public class OperationLogEntry {

    public static final String NO_ACCOUNT = "-";

    private final String time;
    private final String action;
    private final String target;
    private final String targetId;
    private final String result;
    private final String ip;
    private final String account;

    public OperationLogEntry(String time, String action, String target, String targetId,
                             String result, String ip, String account) {
        this.time = time;
        this.action = action;
        this.target = target;
        this.targetId = targetId;
        this.result = result;
        this.ip = ip;
        this.account = account;
    }

    public String getTime() {
        return time;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getResult() {
        return result;
    }

    public String getIp() {
        return ip;
    }

    public String getAccount() {
        return account;
    }
}
