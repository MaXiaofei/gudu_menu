package com.gudu.xsd.modules.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentSessionPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AgentSessionMapper extends BaseMapper<AgentSessionPO> {

    /** 保存期限清理：含已逻辑删除的会话（BaseMapper 会自动加 deleted=0，故手写）。agent_session 为全局表。 */
    @Select("SELECT id FROM agent_session WHERE updated_at < #{cutoff} ORDER BY updated_at LIMIT #{limit}")
    List<String> selectIdsUpdatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    /** 注销：这些档案的全部会话 id，含已逻辑删除的。 */
    @Select("<script>SELECT id FROM agent_session WHERE member_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<String> selectIdsByMembers(@Param("ids") Collection<Long> memberIds);

    /** 账号合并：含已逻辑删除的会话。 */
    @org.apache.ibatis.annotations.Update("UPDATE agent_session SET member_id = #{to} WHERE member_id = #{from}")
    int reassignMember(@Param("from") Long from, @Param("to") Long to);

    /** 物理删除（BaseMapper.delete 在 @TableLogic 实体上是逻辑删除）。 */
    @Delete("<script>DELETE FROM agent_session WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int hardDeleteByIds(@Param("ids") Collection<String> ids);
}
