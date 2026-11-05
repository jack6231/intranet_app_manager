package org.yzr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yzr.dao.AccountDao;
import org.yzr.model.Account;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 账号：注册、删除 + 供 Spring Security 登录时按用户名取账号。
 * 编码器放在这里而不是声明成 Bean，避免 SecurityConfig ⇄ AccountService 的循环依赖。
 */
@Service
public class AccountService implements UserDetailsService {

    public static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    public static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    static final int USERNAME_MIN = 2;
    static final int USERNAME_MAX = 32;
    static final int PASSWORD_MIN = 6;
    static final int PASSWORD_MAX = 64;
    // 允许中文姓名、字母数字和邮箱常见符号；不允许空白，避免署名里出现看不见的差异
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}._@-]+$");

    @Resource
    private AccountDao accountDao;

    // 管理员用户名：注册时保留，不允许别人注册同名（不区分大小写）
    @Value("${app.admin.username:Admin}")
    private String adminUsername;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = username == null ? null : accountDao.findByUsername(username.trim());
        if (account == null) {
            throw new UsernameNotFoundException("账号不存在");
        }
        // 用库里的用户名做署名，登录时输入的大小写不影响记录
        return User.withUsername(account.getUsername())
                .password(account.getPassword())
                .roles(account.isAdmin() ? ROLE_ADMIN : ROLE_USER)
                .build();
    }

    /**
     * 注册。校验不过抛 IllegalArgumentException，消息直接展示给用户。
     * @return 规范化后的用户名
     */
    @Transactional
    public String register(String username, String password, String confirm) {
        String name = username == null ? "" : username.trim();
        String error = validate(name, password, confirm);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (isReserved(name, adminUsername)) {
            throw new IllegalArgumentException("该用户名不可用");
        }
        if (accountDao.findByUsername(name) != null) {
            throw new IllegalArgumentException("用户名已被注册");
        }
        Account account = new Account();
        account.setUsername(name);
        account.setPassword(PASSWORD_ENCODER.encode(password));
        account.setCreateTime(System.currentTimeMillis());
        accountDao.save(account);
        return name;
    }

    /** 所有账号，按注册时间先后 */
    public List<Account> listAccounts() {
        return accountDao.findAllByOrderByCreateTimeAsc();
    }

    /**
     * 删除账号（管理员操作）。管理员账号不能删。账号删除后它已登录的会话在下一次请求时失效（SecurityConfig 的会话校验）。
     * 校验不过抛 IllegalArgumentException，消息直接给前端提示。
     * @return 被删账号的用户名
     */
    @Transactional
    public String deleteAccount(String id) {
        Account account = accountDao.findById(id).orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (account.isAdmin()) {
            throw new IllegalArgumentException("管理员账号不能删除");
        }
        accountDao.delete(account);
        return account.getUsername();
    }

    /** 账号的用户名，用于操作记录；账号不存在时返回 id 本身 */
    public String usernameOf(String id) {
        return accountDao.findById(id).map(Account::getUsername).orElse(id);
    }

    /** 账号是否还存在（已登录会话每次请求校验一次，账号被删后立即失效） */
    public boolean exists(String username) {
        return username != null && accountDao.findByUsername(username) != null;
    }

    /** 用户名是否为保留名（管理员用户名，不区分大小写）。纯函数 */
    static boolean isReserved(String username, String adminUsername) {
        return adminUsername != null && adminUsername.trim().equalsIgnoreCase(username);
    }

    /** 注册参数校验；通过返回 null，否则返回给用户看的错误信息。纯函数 */
    static String validate(String username, String password, String confirm) {
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            return "用户名长度需在 " + USERNAME_MIN + "~" + USERNAME_MAX + " 个字符之间";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "用户名只能包含中文、字母、数字和 . _ @ -";
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            return "密码长度需在 " + PASSWORD_MIN + "~" + PASSWORD_MAX + " 位之间";
        }
        if (!password.equals(confirm)) {
            return "两次输入的密码不一致";
        }
        return null;
    }
}
