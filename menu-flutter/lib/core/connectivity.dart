import 'package:flutter/foundation.dart';

/// 全局联网状态：ApiClient 拦截器在连接类错误时置 false、任一请求拿到响应置 true。
/// 不引入 connectivity_plus（避免新增原生依赖），以「最近一次请求结果」为准。
class Connectivity {
  Connectivity._();
  static final ValueNotifier<bool> online = ValueNotifier<bool>(true);
}
