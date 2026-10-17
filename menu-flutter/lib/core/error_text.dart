import 'package:dio/dio.dart';

/// 异常 → 可展示文案。
///
/// ApiClient 拦截器 reject 的业务错误把后端 msg 放在 [DioException.message]；
/// 其他异常（解析失败、平台异常）回落 [fallback]，避免把堆栈抛给用户。
String errorText(Object e, [String fallback = '操作失败，请稍后重试']) {
  if (e is DioException) {
    final m = e.message;
    if (m != null && m.isNotEmpty && !m.startsWith('DioException')) return m;
    return fallback;
  }
  return fallback;
}

/// 报错编号（原型 States：「报错编号 7f3a-91c2 · 反馈时附上它」）。
/// 取后端 X-Trace-Id 前 8 位，格式 xxxx-xxxx；拿不到返回 null。
String? errorIdOf(Object? e) {
  if (e is! DioException) return null;
  final raw = (e.requestOptions.extra['traceId'] ?? e.response?.headers.value('x-trace-id'))?.toString();
  if (raw == null) return null;
  final hex = raw.replaceAll(RegExp(r'[^0-9a-fA-F]'), '').toLowerCase();
  if (hex.length < 8) return raw.isEmpty ? null : raw;
  return '${hex.substring(0, 4)}-${hex.substring(4, 8)}';
}
