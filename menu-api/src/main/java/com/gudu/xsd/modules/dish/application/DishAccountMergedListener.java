package com.gudu.xsd.modules.dish.application;

import com.gudu.xsd.modules.dish.DishPersonalDataService;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 账号合并：菜谱创建人（dish.created_by，V89）随账号改指目标账号，合并后「导出我的数据」仍能归属本人。 */
@Component
@RequiredArgsConstructor
public class DishAccountMergedListener {

    private final DishPersonalDataService personalData;

    @EventListener
    public void on(AccountMerged e) {
        personalData.reassignCreator(e.sourceAccountId(), e.targetAccountId());
    }
}
