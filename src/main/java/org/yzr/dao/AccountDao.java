package org.yzr.dao;

import org.springframework.data.repository.CrudRepository;
import org.yzr.model.Account;

import java.util.List;

public interface AccountDao extends CrudRepository<Account, String> {

    Account findByUsername(String username);

    List<Account> findAllByOrderByCreateTimeAsc();
}
