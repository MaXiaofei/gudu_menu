import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import 'package:menu_flutter/core/api_client.dart';
import 'mock_http.dart';

/// 同时支持 JSON 与 SSE 的内存适配器：responder 返回 [SseBody] 时按 text/event-stream 下发，
/// 返回 [HttpError] 时给对应状态码，其余按 `{code,msg,data}` JSON 编码。
class SseBody {
  final String text;
  const SseBody(this.text);
}

class HttpError {
  final int status;
  const HttpError(this.status);
}

/// 组装一条 SSE 事件。
String sse(String event, Map<String, dynamic> data) => 'event: $event\ndata: ${jsonEncode(data)}\n\n';

class ShowcaseAdapter implements HttpClientAdapter {
  final dynamic Function(RequestOptions o) responder;
  final List<RequestOptions> requests = [];
  ShowcaseAdapter(this.responder);

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    final r = responder(options);
    if (r is SseBody) {
      return ResponseBody(Stream.value(Uint8List.fromList(utf8.encode(r.text))), 200,
          headers: {Headers.contentTypeHeader: ['text/event-stream']});
    }
    if (r is HttpError) {
      return ResponseBody.fromString('{"error":"x"}', r.status, headers: {Headers.contentTypeHeader: ['application/json']});
    }
    return ResponseBody.fromString(jsonEncode(r), 200, headers: {Headers.contentTypeHeader: ['application/json']});
  }

  @override
  void close({bool force = false}) {}
}

/// 安装 [ShowcaseAdapter]（复用 installMock 完成 ApiClient 初始化）。
ShowcaseAdapter installShowcase(dynamic Function(RequestOptions o) responder) {
  installMock((_) => okResponse(null));
  final a = ShowcaseAdapter(responder);
  ApiClient.instance.dio.httpClientAdapter = a;
  return a;
}
