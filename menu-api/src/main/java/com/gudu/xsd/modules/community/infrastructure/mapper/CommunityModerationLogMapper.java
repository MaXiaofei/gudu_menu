package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityModerationLogPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface CommunityModerationLogMapper extends BaseMapper<CommunityModerationLogPO> {

    @Select("SELECT AVG(TIMESTAMPDIFF(SECOND, p.create_time, l.create_time)) / 60.0 FROM community_moderation_log l "
            + "JOIN community_post p ON p.id = l.post_id WHERE l.create_time >= #{since}")
    Double avgHandleMinutesSince(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM community_moderation_log l JOIN community_post p ON p.id = l.post_id "
            + "WHERE p.author_id = #{authorId} AND l.action = 'BAN_7D'")
    long banCount(@Param("authorId") Long authorId);
}
