package org.yzr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.yzr.dao.AccountDao;
import org.yzr.model.Account;

import javax.annotation.Resource;

/**
 * 启动时确保管理员账号存在。
 *
 * 用户名 app.admin.username（默认 Admin），初始密码 app.admin.password —— 密码只放在线上外置
 * config/application.properties，不进仓库。账号不存在时按初始密码创建；已存在时只确保它是管理员，不改密码。
 * 注册接口保留了这个用户名，别人注册不到同名账号。
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminAccountInitializer.class);

    @Resource
    private AccountDao accountDao;

    @Value("${app.admin.username:Admin}")
    private String username;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            LOG.warn("app.admin.username 为空，不创建管理员账号");
            return;
        }
        Account account = accountDao.findByUsername(name);
        if (account == null) {
            if (password == null || password.isEmpty()) {
                LOG.warn("管理员账号 {} 不存在且未配置 app.admin.password，管理页暂时无人可进", name);
                return;
            }
            account = new Account();
            account.setUsername(name);
            account.setPassword(AccountService.PASSWORD_ENCODER.encode(password));
            account.setCreateTime(System.currentTimeMillis());
            account.setAdmin(true);
            accountDao.save(account);
            LOG.info("已创建管理员账号 {}", name);
        } else if (!account.isAdmin()) {
            account.setAdmin(true);
            accountDao.save(account);
            LOG.info("账号 {} 已设为管理员", name);
        }
    }
}
