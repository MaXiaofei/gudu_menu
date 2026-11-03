package com.gudu.xsd.prode2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本机台账：每个由测试自己创建的账号 id 追加记一行（target/prod-e2e-ledger.txt）。
 * 用途：注销用例会把账号昵称匿名化（失去 E2E_PROD_ 前缀），台账让清理仍能认出它；进程崩溃后兜底清理也读它。
 * 台账只记「本测试创建时拿到的 id」，所以不可能包含真实用户。
 */
final class ProdE2ELedger {

    static final Path FILE = Path.of("target", "prod-e2e-ledger.txt");

    private ProdE2ELedger() {
    }

    static synchronized void recordAccount(long accountId) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "account:" + accountId + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("写台账失败（拒绝继续，否则注销后的测试账号可能清不掉）：" + e.getMessage(), e);
        }
    }

    static synchronized Set<Long> accountIds() {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            if (!Files.exists(FILE)) return ids;
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String l : lines) {
                if (l.startsWith("account:")) ids.add(Long.parseLong(l.substring(8).trim()));
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("读台账失败：" + e.getMessage(), e);
        }
        return ids;
    }

    static synchronized void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {
            // 删不掉不影响正确性：下次清理会发现账号已不存在
        }
    }
}
