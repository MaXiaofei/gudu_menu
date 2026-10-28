import 'package:dio/dio.dart';
import 'constants.dart';
import 'api_response.dart';
import 'community_status.dart';
import 'feature_switch.dart';
import 'connectivity.dart';

/// dio 单例：对应小程序 menu-mini/src/utils/request.ts。
///
/// 关键契约（探索确认，必须遵守）：
/// - baseURL 带 /gudu 前缀（后端 context-path），测试环境走 http://49.232.3.201:9090/gudu
/// - 每次请求塞 `Authorization: <裸 token>`（无 Bearer 前缀）
/// - 统一解包 {code,msg,data}：code==0 返回 data；code==401 清 token+跳登录；其它 toast+抛 ApiError
class ApiClient {
  ApiClient._();
  static final ApiClient instance = ApiClient._();

  late final Dio dio;

  /// 当前 token（由 AuthStore 在 login/启动时设置；401 时清空）。
  String? token;

  /// 401 未登录回调（go_router 跳登录页）。
  void Function()? onUnauthorized;

  /// 业务错误/网络错误提示回调（app 注入 SnackBar）。
  void Function(String message)? onErrorToast;

  /// 最近一次失败请求的报错编号（X-Trace-Id 前 8 位，xxxx-xxxx），意见反馈默认附带。
  static String? lastErrorId;

  static void _rememberError(String? traceId) {
    if (traceId == null) return;
    final hex = traceId.replaceAll(RegExp(r'[^0-9a-fA-F]'), '').toLowerCase();
    lastErrorId = hex.length >= 8 ? '${hex.substring(0, 4)}-${hex.substring(4, 8)}' : traceId;
  }

  void init({void Function()? onUnauthorized, void Function(String)? onErrorToast}) {
    this.onUnauthorized = onUnauthorized;
    this.onErrorToast = onErrorToast;
    dio = Dio(BaseOptions(
      baseUrl: AppConstants.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 15),
      headers: {'Content-Type': 'application/json'},
    ));

    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        if (token != null && token!.isNotEmpty) {
          options.headers['Authorization'] = token; // 裸 token，无 Bearer
        }
        handler.next(options);
      },
      onResponse: (response, handler) {
        Connectivity.online.value = true;
        // 报错编号：后端 TraceIdFilter 写响应头 X-Trace-Id，业务失败时随 DioException 带出
        final traceId = response.headers.value('x-trace-id');
        if (traceId != null) response.requestOptions.extra['traceId'] = traceId;
        final body = response.data;
        if (body is Map<String, dynamic> && body.containsKey('code')) {
          final api = ApiResponse.fromJson(body);
          CommunityStatus.note(response.requestOptions.path, api.code);
          if (api.code == CommunityStatus.suspendedCode) {
            // 厨房圈紧急关停（A.12）：页面由 CommunityPausedGate 显示暂停提示，不弹报错
            handler.reject(DioException(
              requestOptions: response.requestOptions,
              message: CommunityStatus.message,
            ));
            return;
          }
          if (FeatureSwitch.noteCode(api.code)) {
            // 咕嘟助手 / 问咕嘟紧急关停（5032 / 5033）：切到暂停状态，不弹报错
            handler.reject(DioException(
              requestOptions: response.requestOptions,
              message: api.msg.isEmpty ? FeatureSwitch.agentMessage : api.msg,
            ));
            return;
          }
          if (api.code == 401) {
            token = null;
            // 读实例字段（动态），而非 init 闭包捕获的局部参数——
            // 这样 app 在 init 之后赋值的 onUnauthorized 才会生效（401→跳登录）。
            this.onUnauthorized?.call();
            handler.reject(DioException(
              requestOptions: response.requestOptions,
              message: '未登录',
            ));
            return;
          }
          if (!api.ok) {
            _rememberError(traceId);
            this.onErrorToast?.call(api.msg.isEmpty ? '请求失败' : api.msg);
            handler.reject(DioException(
              requestOptions: response.requestOptions,
              message: api.msg,
            ));
            return;
          }
          // 成功：把 data 塞回 response.data，调用方直接拿解包后的数据
          response.data = api.data;
        }
        handler.next(response);
      },
      onError: (e, handler) {
        final traceId = e.response?.headers.value('x-trace-id');
        if (traceId != null) e.requestOptions.extra['traceId'] = traceId;
        _rememberError(traceId);
        if (e.response != null) {
          Connectivity.online.value = true;
        } else if (e.type == DioExceptionType.connectionError ||
            e.type == DioExceptionType.connectionTimeout) {
          Connectivity.online.value = false;
        }
        // 网络层错误（拦截器主动 reject 的带 message，不重复提示）
        final msg = e.message ?? '';
        if (msg.isEmpty &&
            (e.type == DioExceptionType.connectionTimeout ||
                e.type == DioExceptionType.sendTimeout ||
                e.type == DioExceptionType.receiveTimeout ||
                e.type == DioExceptionType.connectionError)) {
          this.onErrorToast?.call('网络连接失败，请检查后端是否可达');
        }
        handler.next(e);
      },
    ));
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) async {
    final r = await dio.get(path, queryParameters: query);
    return r.data;
  }

  Future<dynamic> post(String path,
      {dynamic body, Map<String, dynamic>? query}) async {
    final r = await dio.post(path, data: body, queryParameters: query);
    return r.data;
  }

  Future<dynamic> put(String path,
      {dynamic body, Map<String, dynamic>? query}) async {
    final r = await dio.put(path, data: body, queryParameters: query);
    return r.data;
  }

  Future<dynamic> delete(String path, {Map<String, dynamic>? query}) async {
    final r = await dio.delete(path, queryParameters: query);
    return r.data;
  }
}
