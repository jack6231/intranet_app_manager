package org.yzr;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.Resource;

@SpringBootApplication
@EnableScheduling   // 启用定时任务（org.yzr.task.CleanupScheduler：定时清理过期安装包）
//@EnableJpaRepositories("org.yzr.dao") // JPA扫描该包路径下的Repositorie
//@EntityScan("org.yzr.model") // 扫描Entity实体类
public class Application {
    @Resource
    private Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * HTTPS（server.port）之外再开一个 HTTP 端口（server.http.port），两个端口都直接提供服务、互不跳转。
     *
     * 1.0.14 起不再给 HTTP 端口标 secure=true（原先配合 CONFIDENTIAL 约束使用，目的是 HTTP 不被重定向到 HTTPS）：
     * Tomcat 会给 secure 连接上的会话 cookie 加 Secure 属性，浏览器拒收 http 页面下发的 Secure cookie，
     * 登录态在 HTTP 端口上存不住。去掉约束后 HTTP 同样不跳转，行为不变。
     */
    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        tomcat.addAdditionalTomcatConnectors(httpConnector());
        return tomcat;
    }

    @Bean
    public Connector httpConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        int httpPort = Integer.parseInt(environment.getProperty("server.http.port"));
        connector.setScheme("http");
        connector.setPort(httpPort);
        return connector;
    }

}
