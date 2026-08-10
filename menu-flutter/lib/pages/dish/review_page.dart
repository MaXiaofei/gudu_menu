import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../review/review_form.dart';

/// 写点评页（单菜评价，V43 重构：复用公共 ReviewForm）。
class ReviewPage extends StatefulWidget {
  final int dishId;

  const ReviewPage({super.key, required this.dishId});

  @override
  State<ReviewPage> createState() => _ReviewPageState();
}

class _ReviewPageState extends State<ReviewPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('写评价')),
      body: ReviewForm(
        dishId: widget.dishId,
        title: '给这道菜打个分',
        onSuccess: () {
          if (mounted) context.pop();
        },
      ),
    );
  }
}
