package com.gudu.xsd.modules.aiguard.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** AI 额度配置存储（键值，只存运营改过的值）+ 修改日志。 */
public interface AiGuardConfigRepository {

    /** 一次修改：oldValue 为 null 表示之前用默认值。 */
    record Change(String key, String oldValue, String newValue) {
    }

    /** 一条修改日志。 */
    record ChangeLog(String key, String oldValue, String newValue, Long operatorId, String operatorName,
                     LocalDateTime at) {
    }

    /** 全部已存的键值。 */
    Map<String, String> loadAll();

    /** 同一事务内写入新值并追加日志。 */
    void saveAll(List<Change> changes, Long operatorId, String operatorName, LocalDateTime at);

    /** 最近的修改日志，新的在前。 */
    List<ChangeLog> recentLogs(int limit);
}
