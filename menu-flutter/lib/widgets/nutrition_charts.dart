import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 营养雷达图（营养报告）：实际摄入（主色填充）vs 建议量（虚线 100% 圈）。
///
/// [ratios] 每轴 实际/建议；超过 [overLimit] 的轴标签标红。半径按 1.5 倍封顶，避免超标轴撑爆画布。
class NutritionRadar extends StatelessWidget {
  final List<String> labels;
  final List<double> ratios;
  final List<bool> over;
  final double size;
  const NutritionRadar({super.key, required this.labels, required this.ratios, required this.over, this.size = 220});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return SizedBox(
      height: size,
      child: CustomPaint(
        size: Size.infinite,
        painter: _RadarPainter(
          labels: labels,
          ratios: ratios,
          over: over,
          fill: t.primary,
          grid: t.border,
          text: t.textStyles.xs.copyWith(color: t.body),
          overText: t.textStyles.xs.copyWith(color: AppTokens.errorText),
        ),
      ),
    );
  }
}

class _RadarPainter extends CustomPainter {
  final List<String> labels;
  final List<double> ratios;
  final List<bool> over;
  final Color fill;
  final Color grid;
  final TextStyle text;
  final TextStyle overText;

  _RadarPainter({
    required this.labels,
    required this.ratios,
    required this.over,
    required this.fill,
    required this.grid,
    required this.text,
    required this.overText,
  });

  static const _cap = 1.5;

  Offset _pt(Offset c, double r, int i, int n) {
    final a = -math.pi / 2 + 2 * math.pi * i / n;
    return Offset(c.dx + r * math.cos(a), c.dy + r * math.sin(a));
  }

  @override
  void paint(Canvas canvas, Size size) {
    final n = labels.length;
    if (n < 3) return;
    final c = size.center(Offset.zero);
    final rMax = math.min(size.width, size.height) / 2 - 30;
    final r100 = rMax / _cap;
    final gridPaint = Paint()
      ..color = grid
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    // 网格：50% / 100%(虚线) / 150%
    for (final k in [0.5, 1.0, 1.5]) {
      final path = Path();
      for (var i = 0; i <= n; i++) {
        final p = _pt(c, r100 * k, i % n, n);
        i == 0 ? path.moveTo(p.dx, p.dy) : path.lineTo(p.dx, p.dy);
      }
      if (k == 1.0) {
        _dashed(canvas, path, gridPaint..strokeWidth = 1.4);
        gridPaint.strokeWidth = 1;
      } else {
        canvas.drawPath(path, gridPaint);
      }
    }
    for (var i = 0; i < n; i++) {
      canvas.drawLine(c, _pt(c, rMax, i, n), gridPaint);
    }
    // 实际摄入
    final shape = Path();
    for (var i = 0; i <= n; i++) {
      final v = ratios[i % n].clamp(0.0, _cap);
      final p = _pt(c, r100 * v, i % n, n);
      i == 0 ? shape.moveTo(p.dx, p.dy) : shape.lineTo(p.dx, p.dy);
    }
    canvas.drawPath(shape, Paint()..color = fill.withAlpha(70));
    canvas.drawPath(
        shape,
        Paint()
          ..color = fill
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2);
    for (var i = 0; i < n; i++) {
      final p = _pt(c, r100 * ratios[i].clamp(0.0, _cap), i, n);
      canvas.drawCircle(p, 3.5, Paint()..color = over[i] ? AppTokens.error : fill);
    }
    // 标签
    for (var i = 0; i < n; i++) {
      final tp = TextPainter(
        text: TextSpan(
          text: '${labels[i]}\n${(ratios[i] * 100).round()}%',
          style: over[i] ? overText : text,
        ),
        textAlign: TextAlign.center,
        textDirection: TextDirection.ltr,
      )..layout();
      final p = _pt(c, rMax + 16, i, n);
      tp.paint(canvas, Offset(p.dx - tp.width / 2, p.dy - tp.height / 2));
    }
  }

  void _dashed(Canvas canvas, Path path, Paint paint) {
    for (final m in path.computeMetrics()) {
      var d = 0.0;
      while (d < m.length) {
        canvas.drawPath(m.extractPath(d, math.min(d + 5, m.length)), paint);
        d += 9;
      }
    }
  }

  @override
  bool shouldRepaint(covariant _RadarPainter old) => old.ratios != ratios || old.fill != fill || old.labels != labels;
}

/// 趋势折线（7 日钠摄入）：阈值虚线 + 超阈值点标红 + 日期刻度。
class TrendLineChart extends StatelessWidget {
  final List<double> values;
  final List<String> xLabels;
  final double limit;
  final String unit;
  final double height;
  const TrendLineChart({
    super.key,
    required this.values,
    required this.xLabels,
    required this.limit,
    this.unit = '',
    this.height = 150,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return SizedBox(
      height: height,
      child: CustomPaint(
        size: Size.infinite,
        painter: _TrendPainter(
          values: values,
          xLabels: xLabels,
          limit: limit,
          unit: unit,
          line: t.primaryDeep,
          grid: t.border,
          text: t.textStyles.meta.copyWith(color: t.body, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

class _TrendPainter extends CustomPainter {
  final List<double> values;
  final List<String> xLabels;
  final double limit;
  final String unit;
  final Color line;
  final Color grid;
  final TextStyle text;
  _TrendPainter({
    required this.values,
    required this.xLabels,
    required this.limit,
    required this.unit,
    required this.line,
    required this.grid,
    required this.text,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (values.isEmpty) return;
    const left = 8.0, right = 8.0, top = 18.0, bottom = 20.0;
    final w = size.width - left - right;
    final h = size.height - top - bottom;
    final maxV = [...values, limit].reduce(math.max) * 1.15;
    double y(double v) => top + h * (1 - (maxV == 0 ? 0 : v / maxV));
    double x(int i) => left + (values.length == 1 ? w / 2 : w * i / (values.length - 1));

    // 阈值虚线 + 标签
    if (limit > 0) {
      final ly = y(limit);
      final p = Paint()
        ..color = AppTokens.error.withAlpha(160)
        ..strokeWidth = 1.2;
      for (var d = left; d < left + w; d += 8) {
        canvas.drawLine(Offset(d, ly), Offset(math.min(d + 4, left + w), ly), p);
      }
      final tp = TextPainter(
        text: TextSpan(text: '建议 ≤${_fmt(limit)}$unit', style: text.copyWith(color: AppTokens.errorText)),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(left + w - tp.width, ly - tp.height - 2));
    }
    // 折线 + 面积
    final path = Path();
    final area = Path();
    for (var i = 0; i < values.length; i++) {
      final p = Offset(x(i), y(values[i]));
      if (i == 0) {
        path.moveTo(p.dx, p.dy);
        area.moveTo(p.dx, top + h);
        area.lineTo(p.dx, p.dy);
      } else {
        path.lineTo(p.dx, p.dy);
        area.lineTo(p.dx, p.dy);
      }
    }
    area
      ..lineTo(x(values.length - 1), top + h)
      ..close();
    canvas.drawPath(area, Paint()..color = line.withAlpha(28));
    canvas.drawPath(
        path,
        Paint()
          ..color = line
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2.2
          ..strokeJoin = StrokeJoin.round);
    for (var i = 0; i < values.length; i++) {
      final hot = limit > 0 && values[i] > limit;
      final p = Offset(x(i), y(values[i]));
      canvas.drawCircle(p, 4, Paint()..color = hot ? AppTokens.error : line);
      canvas.drawCircle(p, 2, Paint()..color = AppTokens.onPhoto);
      if (hot) {
        final tp = TextPainter(text: TextSpan(text: _fmt(values[i]), style: text.copyWith(color: AppTokens.errorText)), textDirection: TextDirection.ltr)
          ..layout();
        tp.paint(canvas, Offset(p.dx - tp.width / 2, p.dy - tp.height - 4));
      }
      if (i < xLabels.length) {
        final tp = TextPainter(text: TextSpan(text: xLabels[i], style: text), textDirection: TextDirection.ltr)..layout();
        tp.paint(canvas, Offset(p.dx - tp.width / 2, top + h + 5));
      }
    }
    canvas.drawLine(Offset(left, top + h), Offset(left + w, top + h), Paint()..color = grid);
  }

  String _fmt(double v) => v == v.roundToDouble() ? '${v.toInt()}' : v.toStringAsFixed(1);

  @override
  bool shouldRepaint(covariant _TrendPainter old) => old.values != values || old.limit != limit || old.line != line;
}
