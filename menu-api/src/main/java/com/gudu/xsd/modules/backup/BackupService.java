package com.gudu.xsd.modules.backup;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量数据备份/恢复：遍历核心表导出为 JSON，反向按表覆盖导入。
 * MVP 单人自用，导入采用「清表 + 重灌」的覆盖语义。
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private final JdbcTemplate jdbcTemplate;

    /** 按依赖顺序排列：被引用的字典/主表先导入。 */
    private static final List<String> TABLES = List.of(
            "sys_dict", "nutrition_metric", "user", "member",
            "ingredient", "ingredient_nutrition",
            "dish", "dish_step", "dish_dict", "dish_ingredient",
            "menu", "menu_dish",
            "favorite", "cooking_record", "dish_history");

    public Map<String, Object> exportAll() {
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String t : TABLES) {
            tables.put(t, jdbcTemplate.queryForList("SELECT * FROM " + t));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tables", tables);
        result.put("tableCount", TABLES.size());
        return result;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importAll(Map<String, Object> data) {
        // 容错：前端若传完整 R 响应 {code,msg,data:{tables}}，解包到 data 层
        Object inner = data.get("data");
        if (inner instanceof Map) {
            data = (Map<String, Object>) inner;
        }
        Map<String, List<Map<String, Object>>> tables =
                (Map<String, List<Map<String, Object>>>) data.get("tables");

        Map<String, Object> counts = new LinkedHashMap<>();
        for (String t : TABLES) {
            jdbcTemplate.update("DELETE FROM " + t);
            List<Map<String, Object>> rows = tables.get(t);
            if (rows == null || rows.isEmpty()) {
                counts.put(t, 0);
                continue;
            }
            for (Map<String, Object> row : rows) {
                List<String> cols = new ArrayList<>(row.keySet());
                String placeholders = String.join(",", cols.stream().map(c -> "?").toList());
                String sql = "INSERT INTO " + t + " (" + String.join(",", cols)
                        + ") VALUES (" + placeholders + ")";
                jdbcTemplate.update(sql, cols.stream().map(row::get).toArray());
            }
            counts.put(t, rows.size());
        }
        return counts;
    }
}
