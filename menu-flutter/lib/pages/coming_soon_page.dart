import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 占位页：P0 未实现的页面先指向这里，P1/P2 逐步替换为真实页面。
class ComingSoonPage extends StatelessWidget {
  final String title;
  const ComingSoonPage({super.key, required this.title});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        appBar: AppBar(title: Text(title)),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.construction, size: 64, color: t.primary),
              const SizedBox(height: AppTokens.sp16),
              Text('$title（开发中）',
                  style: TextStyle(fontSize: 16, color: t.caption)),
              const SizedBox(height: AppTokens.sp8),
              Text('该页面将在后续版本上线',
                  style: TextStyle(fontSize: 12, color: t.body)),
            ],
          ),
        ),
      );
  }
}
