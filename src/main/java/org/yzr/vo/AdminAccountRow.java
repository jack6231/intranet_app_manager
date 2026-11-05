package org.yzr.vo;

/** 管理页「账号」列表的一行 */
public class AdminAccountRow {

    private final String id;
    private final String username;
    private final boolean admin;
    private final String createTime;
    private final int operationCount;
    private final String lastOperationTime;

    public AdminAccountRow(String id, String username, boolean admin, String createTime,
                           int operationCount, String lastOperationTime) {
        this.id = id;
        this.username = username;
        this.admin = admin;
        this.createTime = createTime;
        this.operationCount = operationCount;
        this.lastOperationTime = lastOperationTime;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public String getCreateTime() {
        return createTime;
    }

    public int getOperationCount() {
        return operationCount;
    }

    public String getLastOperationTime() {
        return lastOperationTime;
    }
}
