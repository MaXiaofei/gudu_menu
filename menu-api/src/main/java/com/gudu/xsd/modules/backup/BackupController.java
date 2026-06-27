package com.gudu.xsd.modules.backup;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
@Tag(name = "备份")
public class BackupController {

    private final BackupService svc;

    @GetMapping("/export")
    public R<Map<String, Object>> export() {
        return R.ok(svc.exportAll());
    }

    @PostMapping("/import")
    public R<Map<String, Object>> imp(@RequestBody Map<String, Object> data) {
        return R.ok(svc.importAll(data));
    }
}
