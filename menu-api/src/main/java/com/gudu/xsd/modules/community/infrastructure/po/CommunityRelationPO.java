package com.gudu.xsd.modules.community.infrastructure.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关系类小表（reaction / follow / copy / report / ban）的通用行投影：
 * 这些表只经 Mapper 上的显式 SQL（INSERT IGNORE 幂等写）读写，不走 MyBatis-Plus 通用 CRUD，
 * 因此不标 @TableName，各 Mapper 以本类型作 BaseMapper 泛型参数。
 */
@Data
public class CommunityRelationPO {

    private Long id;
    private Long postId;
    private Long userId;
    private Long targetId;
    private Long dishId;
    private String type;
    private String reason;
    private LocalDateTime createTime;
}
