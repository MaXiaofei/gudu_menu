package com.gudu.xsd.modules.backoffice.domain.repository;

import com.gudu.xsd.modules.backoffice.domain.model.AccountRow;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;

import java.util.List;

/** 账号目录只读查询（平台级，跨家庭；只读 account / account_ban / family_membership / family 元数据）。 */
public interface AccountDirectoryQuery {

    List<AccountRow> search(AccountSearch search, int offset, int limit);

    long count(AccountSearch search);
}
