package org.yzr.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.yzr.model.Package;

import java.util.List;

public interface PackageDao extends CrudRepository <Package, String > {

    @Query("select p from Package p where p.app.id = :appId")
    List<Package> findByAppId(@Param("appId") String appId);
}
