package com.gudu.xsd.modules.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.shopping.ShoppingList;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShoppingListMapper extends BaseMapper<ShoppingList> {

    /** 本家庭当前最大序号（含逻辑删除行，避免序号复用；显式带 family_id）。 */
    @Select("SELECT MAX(seq) FROM shopping_list WHERE family_id = #{familyId}")
    Integer selectMaxSeq(@Param("familyId") Long familyId);
}
