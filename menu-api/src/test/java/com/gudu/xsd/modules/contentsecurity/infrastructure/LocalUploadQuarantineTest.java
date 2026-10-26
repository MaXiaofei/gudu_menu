package com.gudu.xsd.modules.contentsecurity.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalUploadQuarantineTest {

    @TempDir
    Path tmp;

    @Test
    void 原图与缩略图移入隔离目录_原地址不可再访问() throws Exception {
        Path uploads = tmp.resolve("uploads");
        Files.createDirectories(uploads.resolve("original/20261026"));
        Files.createDirectories(uploads.resolve("thumbnail/20261026"));
        Files.writeString(uploads.resolve("original/20261026/a.jpg"), "x");
        Files.writeString(uploads.resolve("thumbnail/20261026/a.jpg"), "t");
        LocalUploadQuarantine q = new LocalUploadQuarantine(uploads.toString(), "");

        assertThat(q.quarantine("/gudu/uploads/original/20261026/a.jpg")).isTrue();
        assertThat(uploads.resolve("original/20261026/a.jpg")).doesNotExist();
        assertThat(uploads.resolve("thumbnail/20261026/a.jpg")).doesNotExist();
        Path qroot = tmp.resolve("uploads-quarantine");
        assertThat(qroot.resolve("original/20261026/a.jpg")).hasContent("x");
        assertThat(qroot.resolve("thumbnail/20261026/a.jpg")).hasContent("t");

        // 再次隔离 / 外链 / 穿越：无操作
        assertThat(q.quarantine("/gudu/uploads/original/20261026/a.jpg")).isFalse();
        assertThat(q.quarantine("https://evil.com/a.jpg")).isFalse();
        assertThat(q.quarantine("/gudu/uploads/../secret")).isFalse();
    }
}
