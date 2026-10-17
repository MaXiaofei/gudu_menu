import 'dart:async';

import '../core/api_client.dart';
import '../models/showcase_types.dart';

/// 拍照识别（contracts.md §3）：冰箱 / 小票 / 菜谱截图。

enum VisionMode { fridge, receipt, recipe }

extension VisionModeX on VisionMode {
  String get code => switch (this) { VisionMode.fridge => 'FRIDGE', VisionMode.receipt => 'RECEIPT', VisionMode.recipe => 'RECIPE' };
  String get label => switch (this) { VisionMode.fridge => '冰箱', VisionMode.receipt => '小票', VisionMode.recipe => '菜谱截图' };
  String get hint => switch (this) {
        VisionMode.fridge => '对准冰箱隔层，一次拍一层',
        VisionMode.receipt => '把小票摊平，拍全商品明细',
        VisionMode.recipe => '截图或拍下菜谱页面，用料和步骤都要入镜',
      };
}

class VisionItem {
  final String name;
  final String rawName;
  final int? ingredientId;
  final String? iconKey;
  final double amount;
  final String unit;
  final double confidence;

  /// AUTO（≥0.8 默认勾选）/ CONFIRM（0.5–0.8 待确认）
  final String level;
  final List<String> alternatives;
  final DateTime? suggestedBestBefore;

  const VisionItem({
    required this.name,
    this.rawName = '',
    this.ingredientId,
    this.iconKey,
    this.amount = 1,
    this.unit = '个',
    this.confidence = 1,
    this.level = 'AUTO',
    this.alternatives = const [],
    this.suggestedBestBefore,
  });

  factory VisionItem.fromJson(Map<String, dynamic> j) {
    final conf = Json.d(j['confidence'], 1);
    return VisionItem(
      name: Json.s(j['name']),
      rawName: Json.s(j['rawName']),
      ingredientId: Json.iOrNull(j['ingredientId']),
      iconKey: Json.sOrNull(j['iconKey']),
      amount: Json.d(j['amount'], 1),
      unit: Json.s(j['unit'], '个'),
      confidence: conf,
      level: Json.s(j['level'], levelOf(conf)),
      alternatives: Json.strs(j['alternatives']),
      suggestedBestBefore: Json.date(j['suggestedBestBefore']),
    );
  }

  bool get needsConfirm => level == 'CONFIRM';

  /// 识别名与对齐后的字典名不同时提示「西红柿 → 番茄」。
  bool get renamed => rawName.isNotEmpty && rawName != name;

  /// 置信度分级规则（与后端一致）：≥0.8 AUTO，0.5–0.8 CONFIRM，<0.5 不展示。
  static String levelOf(double c) => c >= 0.8 ? 'AUTO' : 'CONFIRM';
  static bool visible(double c) => c >= 0.5;
}

class VisionJob {
  final int jobId;
  final String mode;

  /// PENDING / DONE / FAILED
  final String status;
  final String? error;
  final DateTime? imageExpiresAt;
  final List<VisionItem> items;
  final ({String title, List<IngredientLine> ingredients, List<String> steps})? recipe;

  const VisionJob({
    required this.jobId,
    required this.mode,
    this.status = 'PENDING',
    this.error,
    this.imageExpiresAt,
    this.items = const [],
    this.recipe,
  });

  factory VisionJob.fromJson(Map<String, dynamic> j) {
    final r = j['recipe'];
    return VisionJob(
      jobId: Json.i(j['jobId']),
      mode: Json.s(j['mode'], 'FRIDGE'),
      status: Json.s(j['status'], 'PENDING'),
      error: Json.sOrNull(j['error']),
      imageExpiresAt: Json.date(j['imageExpiresAt']),
      // 置信度 <0.5 的不展示（服务端也会过滤，这里兜底）
      items: Json.maps(j['items']).map(VisionItem.fromJson).where((e) => VisionItem.visible(e.confidence)).toList(),
      recipe: r is Map
          ? (
              title: Json.s(r['title']),
              ingredients: Json.maps(r['ingredients']).map(IngredientLine.fromJson).toList(),
              steps: Json.strs(r['steps']),
            )
          : null,
    );
  }

  bool get done => status == 'DONE';
  bool get failed => status == 'FAILED';
  int get confirmCount => items.where((e) => e.needsConfirm).length;
}

class VisionService {
  VisionService._();

  static Future<VisionJob> create({required VisionMode mode, String? imageUrl, int? fileId}) async {
    final d = Json.map(await ApiClient.instance.post('/vision/jobs', body: {
      'mode': mode.code,
      if (imageUrl != null) 'imageUrl': imageUrl,
      if (fileId != null) 'fileId': fileId,
    }));
    return VisionJob(jobId: Json.i(d['jobId']), mode: mode.code, status: Json.s(d['status'], 'PENDING'));
  }

  static Future<VisionJob> get(int jobId) async =>
      VisionJob.fromJson(Json.map(await ApiClient.instance.get('/vision/jobs/$jobId')));

  /// 轮询直到 DONE / FAILED（间隔 [interval]，最多 [maxTries] 次）。
  static Future<VisionJob> waitDone(int jobId,
      {Duration interval = const Duration(milliseconds: 800), int maxTries = 40}) async {
    for (var i = 0; i < maxTries; i++) {
      final job = await get(jobId);
      if (job.done || job.failed) return job;
      await Future<void>.delayed(interval);
    }
    throw TimeoutException('识别超时');
  }

  static Future<int> apply(int jobId, List<Map<String, dynamic>> items) async =>
      Json.i(Json.map(await ApiClient.instance.post('/vision/jobs/$jobId/apply', body: {'items': items}))['added']);
}
