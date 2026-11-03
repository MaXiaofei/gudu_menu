package com.gudu.xsd.modules.backoffice.domain.repository;

import com.gudu.xsd.modules.backoffice.domain.model.AccountRow;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;

import com.gudu.xsd.modules.backoffice.domain.model.FamilyBrief;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 账号目录只读查询（平台级，跨家庭；只读 account / account_ban / family_membership / family 元数据）。 */
public interface AccountDirectoryQuery {

    List<AccountRow> search(AccountSearch search, int offset, int limit);

    long count(AccountSearch search);

    /** 批量取账号所属（未解散）家庭的简要元数据，一次查询；无家庭的账号不在结果里。 */
    Map<Long, List<FamilyBrief>> familiesOf(Collection<Long> accountIds);
}
