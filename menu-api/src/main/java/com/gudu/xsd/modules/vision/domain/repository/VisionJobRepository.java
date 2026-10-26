package com.gudu.xsd.modules.vision.domain.repository;

import com.gudu.xsd.modules.vision.domain.model.VisionJob;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VisionJobRepository {

    void save(VisionJob job);

    Optional<VisionJob> findById(Long id);

    /** 原图仍在、创建早于 createdBefore 的任务（到期清图）。 */
    List<VisionJob> findWithImageCreatedBefore(LocalDateTime createdBefore, int limit);

    /** 删除创建早于 createdBefore 的任务（结果到期），返回删除条数。 */
    int deleteCreatedBefore(LocalDateTime createdBefore);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
