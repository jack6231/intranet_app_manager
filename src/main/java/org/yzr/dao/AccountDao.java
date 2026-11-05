package org.yzr.dao;

import org.springframework.data.repository.CrudRepository;
import org.yzr.model.Account;

public interface AccountDao extends CrudRepository<Account, String> {

    Account findByUsername(String username);
}
