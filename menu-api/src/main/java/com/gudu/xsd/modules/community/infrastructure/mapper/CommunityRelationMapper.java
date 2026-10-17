package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityRelationPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 关系类小表（reaction / follow / copy / report / ban）的显式 SQL。
 * 写一律 INSERT IGNORE / 条件 DELETE，返回影响行数供调用方判断「是否真的变了」（幂等计数的依据）。
 */
public interface CommunityRelationMapper extends BaseMapper<CommunityRelationPO> {

    // ---------------- 赞 / 收藏 ----------------

    @Insert("INSERT IGNORE INTO community_reaction(post_id, user_id, type) VALUES (#{postId}, #{userId}, #{type})")
    int insertReaction(@Param("postId") Long postId, @Param("userId") Long userId, @Param("type") String type);

    @Delete("DELETE FROM community_reaction WHERE post_id = #{postId} AND user_id = #{userId} AND type = #{type}")
    int deleteReaction(@Param("postId") Long postId, @Param("userId") Long userId, @Param("type") String type);

    @Select("<script>SELECT post_id FROM community_reaction WHERE user_id = #{userId} AND type = #{type} "
            + "AND post_id IN <foreach collection='postIds' item='p' open='(' separator=',' close=')'>#{p}</foreach>"
            + "</script>")
    List<Long> reactedPostIds(@Param("userId") Long userId, @Param("type") String type,
                              @Param("postIds") List<Long> postIds);

    // ---------------- 关注 ----------------

    @Insert("INSERT IGNORE INTO community_follow(follower_id, followee_id) VALUES (#{follower}, #{followee})")
    int insertFollow(@Param("follower") Long follower, @Param("followee") Long followee);

    @Delete("DELETE FROM community_follow WHERE follower_id = #{follower} AND followee_id = #{followee}")
    int deleteFollow(@Param("follower") Long follower, @Param("followee") Long followee);

    @Select("SELECT COUNT(*) FROM community_follow WHERE follower_id = #{follower} AND followee_id = #{followee}")
    long countFollow(@Param("follower") Long follower, @Param("followee") Long followee);

    @Select("SELECT followee_id FROM community_follow WHERE follower_id = #{follower}")
    List<Long> followees(@Param("follower") Long follower);

    @Select("SELECT follower_id FROM community_follow WHERE followee_id = #{followee}")
    List<Long> followers(@Param("followee") Long followee);

    @Select("SELECT COUNT(*) FROM community_follow WHERE followee_id = #{followee}")
    long followerCount(@Param("followee") Long followee);

    @Select("SELECT f.followee_id FROM community_follow f WHERE f.follower_id = #{follower} "
            + "AND (SELECT COUNT(*) FROM community_follow g WHERE g.followee_id = f.followee_id) > #{threshold}")
    List<Long> bigFollowees(@Param("follower") Long follower, @Param("threshold") long threshold);

    // ---------------- 照着做 ----------------

    @Select("SELECT dish_id FROM community_copy WHERE post_id = #{postId} AND user_id = #{userId} LIMIT 1")
    Long copiedDishId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO community_copy(post_id, user_id, dish_id) VALUES (#{postId}, #{userId}, #{dishId})")
    int insertCopy(@Param("postId") Long postId, @Param("userId") Long userId, @Param("dishId") Long dishId);

    @Select("SELECT user_id FROM community_copy WHERE post_id = #{postId} ORDER BY create_time DESC, id DESC "
            + "LIMIT #{limit}")
    List<Long> recentCopiers(@Param("postId") Long postId, @Param("limit") int limit);

    // ---------------- 举报 ----------------

    @Insert("INSERT IGNORE INTO community_report(post_id, reporter_id, reason) "
            + "VALUES (#{postId}, #{reporterId}, #{reason})")
    int insertReport(@Param("postId") Long postId, @Param("reporterId") Long reporterId,
                     @Param("reason") String reason);

    @Select("SELECT COUNT(*) FROM community_report WHERE post_id = #{postId}")
    long reportCount(@Param("postId") Long postId);

    @Select("<script>SELECT post_id AS postId, COUNT(*) AS targetId FROM community_report WHERE post_id IN "
            + "<foreach collection='postIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "GROUP BY post_id</script>")
    List<CommunityRelationPO> reportCounts(@Param("postIds") List<Long> postIds);

    @Select("<script>SELECT post_id AS postId, reporter_id AS userId, reason, create_time AS createTime "
            + "FROM community_report WHERE post_id IN "
            + "<foreach collection='postIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "ORDER BY create_time DESC, id DESC</script>")
    List<CommunityRelationPO> reports(@Param("postIds") List<Long> postIds);

    @Select("SELECT COUNT(*) FROM community_report WHERE create_time >= #{since}")
    long reportsSince(@Param("since") LocalDateTime since);

    // ---------------- 封禁 ----------------

    @Insert("INSERT INTO community_ban(user_id, until, reason) VALUES (#{userId}, #{until}, #{reason}) "
            + "ON DUPLICATE KEY UPDATE until = GREATEST(until, VALUES(until)), reason = VALUES(reason)")
    int upsertBan(@Param("userId") Long userId, @Param("until") LocalDateTime until, @Param("reason") String reason);

    @Select("SELECT until FROM community_ban WHERE user_id = #{userId}")
    LocalDateTime bannedUntil(@Param("userId") Long userId);
}
