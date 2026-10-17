package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityTopicCheckinPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface CommunityTopicCheckinMapper extends BaseMapper<CommunityTopicCheckinPO> {

    @Insert("INSERT IGNORE INTO community_topic_checkin(topic_id, user_id, checkin_date, post_id) "
            + "VALUES (#{topicId}, #{userId}, #{date}, #{postId})")
    int insertIgnore(@Param("topicId") Long topicId, @Param("userId") Long userId, @Param("date") LocalDate date,
                     @Param("postId") Long postId);

    @Select("SELECT COUNT(DISTINCT user_id) FROM community_topic_checkin WHERE topic_id = #{topicId}")
    long participantCount(@Param("topicId") Long topicId);
}
