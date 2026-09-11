package org.yzr.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.yzr.model.Package;

import java.util.List;

public interface PackageDao extends CrudRepository <Package, String > {

    @Query("select p from Package p where p.app.id = :appId")
    List<Package> findByAppId(@Param("appId") String appId);

    /**
     * 按 git commit 找包（限定 bundleID），最新的在前。
     *
     * commit 做**双向前缀匹配**，因为两端存的长度不一定相同：
     *   - iOS 的 Info.plist 里是 10 位短 hash（实测 192505ceaa），而调用方手里往往是
     *     git ls-remote 给的 40 位全 SHA → 命中靠「存的短 hash 是查询值的前缀」
     *   - 若某端注入的是全 SHA 而调用方只有短 hash → 命中靠反方向
     * 两个方向都留着，避免因为长度约定变化就查不到。
     */
    @Query("select p from Package p where p.gitCommit is not null and p.gitCommit <> '' "
            + "and p.bundleID = :bundleID "
            + "and (p.gitCommit = :commit "
            + "     or :commit like concat(p.gitCommit, '%') "
            + "     or p.gitCommit like concat(:commit, '%')) "
            + "order by p.createTime desc")
    List<Package> findByBundleIDAndCommit(@Param("bundleID") String bundleID,
                                          @Param("commit") String commit);

    /** 同上，但不限 bundleID（跨应用查同一 commit 的包） */
    @Query("select p from Package p where p.gitCommit is not null and p.gitCommit <> '' "
            + "and (p.gitCommit = :commit "
            + "     or :commit like concat(p.gitCommit, '%') "
            + "     or p.gitCommit like concat(:commit, '%')) "
            + "order by p.createTime desc")
    List<Package> findByCommit(@Param("commit") String commit);
}
