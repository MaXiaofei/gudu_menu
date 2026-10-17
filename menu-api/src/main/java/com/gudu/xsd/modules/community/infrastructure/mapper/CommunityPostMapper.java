package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityPostPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CommunityPostMapper extends BaseMapper<CommunityPostPO> {

    /** 计数回刷：在现值上累加（GREATEST 防负数）。 */
    @Update("UPDATE community_post SET like_count = GREATEST(like_count + #{likes}, 0), "
            + "collect_count = GREATEST(collect_count + #{collects}, 0), "
            + "comment_count = GREATEST(comment_count + #{comments}, 0), "
            + "copy_count = GREATEST(copy_count + #{copies}, 0) WHERE id = #{id}")
    int addCounts(@Param("id") Long id, @Param("likes") long likes, @Param("collects") long collects,
                  @Param("comments") long comments, @Param("copies") long copies);
}
