package com.gudu.xsd.modules.backoffice.application;

import com.gudu.xsd.modules.backoffice.application.dto.BackofficeViews;
import com.gudu.xsd.modules.backoffice.domain.model.AccountRow;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import com.gudu.xsd.modules.backoffice.domain.model.FamilyBrief;
import com.gudu.xsd.modules.backoffice.domain.repository.AccountDirectoryQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 账号列表带所属家庭简要信息：批量查询一次、角色保留、无家庭为空列表、空页不查家庭。 */
class BackofficeAccountFamiliesTest {

    final AccountDirectoryQuery dir = mock(AccountDirectoryQuery.class);
    final BackofficeAppService svc = new BackofficeAppService(dir, null, null, null, null, null, null, null, null);

    static AccountRow row(long id, int fam) {
        return new AccountRow(id, "n" + id, null, null, "ACTIVE", "USER", fam, null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 批量查询一次_返回家庭名称与角色_无家庭为空列表() {
        AccountSearch s = new AccountSearch(null, null, null, null, false);
        when(dir.search(any(), anyInt(), anyInt())).thenReturn(List.of(row(1, 2), row(2, 0), row(3, 1)));
        when(dir.count(s)).thenReturn(3L);
        when(dir.familiesOf(any())).thenReturn(Map.of(
                1L, List.of(new FamilyBrief(10L, "我们家", "OWNER", "ACTIVE"), new FamilyBrief(11L, "爸妈家", "MEMBER", "ACTIVE")),
                3L, List.of(new FamilyBrief(12L, "合租", "ADMIN", "ACTIVE"))));

        BackofficeViews.Page<BackofficeViews.AccountItem> p = svc.accounts(s, 1, 20);

        assertThat(p.records()).hasSize(3);
        assertThat(p.records().get(0).families()).extracting(FamilyBrief::name, FamilyBrief::role)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("我们家", "OWNER"),
                        org.assertj.core.groups.Tuple.tuple("爸妈家", "MEMBER"));
        assertThat(p.records().get(1).families()).isEmpty();
        assertThat(p.records().get(2).families().get(0).role()).isEqualTo("ADMIN");
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(dir, times(1)).familiesOf(ids.capture());
        assertThat(ids.getValue()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 空页不查家庭() {
        AccountSearch s = new AccountSearch(null, null, null, null, false);
        when(dir.search(any(), anyInt(), anyInt())).thenReturn(List.of());
        assertThat(svc.accounts(s, 1, 20).records()).isEmpty();
        verify(dir, never()).familiesOf(any());
    }
}
