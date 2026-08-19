/// 全局常量：后端地址、营养指标中英文映射、餐次定义。
/// 与小程序 menu-mini 保持一致。
class AppConstants {
  AppConstants._();

  /// 后端 baseURL（**带 /gudu 前缀**，即后端 context-path）。
  /// 通过 --dart-define=API_BASE_URL=... 切换环境（HTTPS）：
  ///   生产（默认）：https://imxf.cloud/gudu
  ///   预发：        flutter build/run --dart-define=API_BASE_URL=https://staging.imxf.cloud/gudu
  /// 已全站 HTTPS，iOS 无需再放开 ATS（NSAllowsArbitraryLoads）。
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://imxf.cloud/gudu',
  );

  /// SharedPreferences key：登录 token（对应小程序 uni.setStorageSync('token')）。
  static const String tokenKey = 'token';

  /// 营养指标后端字段名 → 中文。
  /// 后端 nutrition_metric.name 是英文（calorie/protein/...），家庭看不懂 → 中文映射，
  /// 兜底返回英文防新增指标无映射。与 Detail.vue 的 METRIC_CN 一致。
  static const Map<String, String> metricCn = {
    'calorie': '热量',
    'protein': '蛋白质',
    'fat': '脂肪',
    'carb': '碳水',
    'sugar': '糖',
    'gi': '升糖指数',
  };

  /// 把英文指标名转中文，无映射时原样返回。
  static String metricNameCn(String name) => metricCn[name] ?? name;
}
