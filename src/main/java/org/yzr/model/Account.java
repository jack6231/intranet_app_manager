package org.yzr.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/**
 * 平台账号。任何人都可以注册；登录的作用是给操作署名（谁上传了 App Store、谁删了包）。
 * 管理员账号只由启动时的 AdminAccountInitializer 创建，注册接口建不出管理员。
 */
@Entity
@Table(name = "tb_account")
public class Account {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    // 登录名，也是操作记录里的署名；库的默认排序规则不区分大小写，唯一约束同样不区分
    @Column(unique = true, nullable = false, length = 64)
    private String username;

    // BCrypt 哈希
    @Column(nullable = false, length = 100)
    private String password;

    // 注册时间，毫秒
    private Long createTime;

    // 是否管理员（可进 /admin：删账号、看操作记录、看 App Store 上传人）；包装类型，加列后历史行为 NULL，按 false 处理
    @Column(name = "is_admin")
    private Boolean admin;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
