package com.gudu.xsd.modules.ai.gateway.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 模型网关后台接口出入参（contracts.md §6）。key 只以掩码出现。 */
public final class GatewayDtos {

    private GatewayDtos() {
    }

    public record PriceView(BigDecimal inputCacheHitFen, BigDecimal inputFen, BigDecimal outputFen) {
    }

    public record PeakHoursView(String timezone, boolean weekdaysOnly, List<String> ranges, BigDecimal offPeakRatio) {
    }

    public record ModelView(String model, List<String> capabilities, PriceView price, PeakHoursView peakHours,
                            boolean supportsThinking) {
    }

    public record HealthView(boolean ok, Integer latencyMs, LocalDateTime checkedAt) {
    }

    public record ProviderView(Long providerId, String name, String type, String baseUrl, String apiKeyMasked,
                               String keySource, boolean enabled, List<ModelView> models, HealthView health) {
    }

    public record TargetView(Long providerId, String model) {
    }

    public record RouteView(String capability, TargetView primary, List<TargetView> fallbacks, int timeoutMs,
                            int maxRetries, String thinking, LocalDateTime updatedAt, String updatedBy) {
    }

    public record GatewayView(List<ProviderView> providers, List<RouteView> routes) {
    }

    /** POST/PUT 供应商；apiKey 留空 = 不修改（PUT）。type 缺省 OPENAI_COMPATIBLE。 */
    public record ProviderSaveCommand(String name, String type, String baseUrl, String apiKey, Boolean enabled,
                                      List<ModelView> models) {
    }

    public record RouteSaveCommand(TargetView primary, List<TargetView> fallbacks, Integer timeoutMs,
                                   Integer maxRetries, String thinking) {
    }

    public record ProviderTestCommand(String model, String capability) {
    }

    public record ProviderTestResult(boolean ok, long latencyMs, String error, String sample) {
    }

    public record UsageRow(String capability, String provider, String model, long calls, double failRate,
                           long p95Ms, BigDecimal costFen) {
    }

    public record UsageView(String range, List<UsageRow> byRoute) {
    }
}
