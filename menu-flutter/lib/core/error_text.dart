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
