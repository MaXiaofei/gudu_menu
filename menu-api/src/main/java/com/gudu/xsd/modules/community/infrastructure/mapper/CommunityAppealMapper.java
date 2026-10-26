package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityRelationPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** community_appeal 显式 SQL（复用关系行投影：userId = author_id，reason = text，targetId = handled）。 */
public interface CommunityAppealMapper extends BaseMapper<CommunityRelationPO> {

    @Insert("INSERT IGNORE INTO community_appeal(post_id, author_id, text, create_time) "
            + "VALUES (#{postId}, #{authorId}, #{text}, #{at})")
    int insertIgnore(@Param("postId") Long postId, @Param("authorId") Long authorId, @Param("text") String text,
                     @Param("at") LocalDateTime at);

    @Select("<script>SELECT post_id AS postId, author_id AS userId, text AS reason, handled AS targetId, "
            + "create_time AS createTime FROM community_appeal WHERE post_id IN "
            + "<foreach collection='postIds' item='p' open='(' separator=',' close=')'>#{p}</foreach></script>")
    List<CommunityRelationPO> findByPosts(@Param("postIds") List<Long> postIds);

    @Update("UPDATE community_appeal SET handled = 1, handled_time = #{at} WHERE post_id = #{postId} AND handled = 0")
    int markHandled(@Param("postId") Long postId, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM community_appeal WHERE handled = 0")
    long countPending();

    @Update("UPDATE community_appeal SET author_id = #{to} WHERE author_id = #{from}")
    int reassignAuthor(@Param("from") Long from, @Param("to") Long to);
}
