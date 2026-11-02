package com.gudu.xsd.modules.family.domain.repository;

/**
 * 成员档案的跨表引用（账号合并软删除用）：
 * <ul>
 *   <li>{@link #reassign}：把「按档案 id 归属」的个人数据由重复档案 from 改指目标档案 to，之后重复档案才能安全软删除；</li>
 *   <li>{@link #familyHasBusinessData}：家庭里是否有任何业务数据（菜谱 / 食集 / 余量 / 采购单 / 做菜记录等），
 *       用于判断合并后遗留的空家庭能否软解散。</li>
 * </ul>
 * 实现为平台级操作（跨家庭、不经租户插件），只按档案 id / 家庭 id 精确匹配。
 */
public interface ProfileReferences {

    /** 改指，返回改动行数（幂等：重复执行返回 0）。 */
    int reassign(Long fromProfileId, Long toProfileId);

    /** 家庭是否有业务数据（已软删除的不算）。 */
    boolean familyHasBusinessData(Long familyId);
}
