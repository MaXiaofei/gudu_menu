import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../models/member.dart';
import '../../stores/member_store.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';

/// 家庭成员列表页（我的 tab 进入，最小闭环）。
///
/// 首字头像 + 姓名 + 副信息（特殊人群/角色标签）+「当前」标记；
/// 点成员行即切换当前就餐成员（POST /member/current，点评/营养统计跟随）。
/// 档案编辑在 admin 端（App 端只读展示）。
class MemberListPage extends StatefulWidget {
  const MemberListPage({super.key});

  @override
  State<MemberListPage> createState() => _MemberListPageState();
}

class _MemberListPageState extends State<MemberListPage> {
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    await context.read<MemberStore>().load();
    if (mounted) setState(() => _loaded = true);
  }

  Future<void> _switchTo(Member mem) async {
    try {
      await context.read<MemberStore>().switchTo(mem.id);
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('已切换为 ${mem.name}')));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('切换失败')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final store = context.watch<MemberStore>();
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(children: [
          // §13.1 管理列表页：只有返回箭头（无大标题）
          const BackHeader(),
          Expanded(
            child: !_loaded
                ? const LoadingView()
                : RefreshIndicator(
                    color: t.primary,
                    onRefresh: _refresh,
                    child: store.members.isEmpty
                        ? ListView(children: const [
                            SizedBox(height: 120),
                            EmptyView(text: '暂无成员，请先在后台添加'),
                          ])
                        : ListView.builder(
                            padding:
                                const EdgeInsets.symmetric(horizontal: 16),
                            itemCount: store.members.length,
                            itemBuilder: (_, i) =>
                                _buildRow(t, store, store.members[i]),
                          ),
                  ),
          ),
        ]),
      ),
    );
  }

  Widget _buildRow(AppTokens t, MemberStore store, Member mem) {
    final isCurrent = mem.id == store.currentId;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: () => _switchTo(mem),
        child: Row(children: [
          InitialAvatar(name: mem.name, size: 40),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(mem.name,
                    style: t.textStyles.md.copyWith(
                        fontWeight: FontWeight.w800, color: t.title)),
                if (mem.subtitle.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(mem.subtitle,
                      style: t.textStyles.xs.copyWith(color: t.caption),
                      overflow: TextOverflow.ellipsis),
                ],
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (isCurrent)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppTokens.success,
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text('当前',
                  style: t.textStyles.xs.copyWith(
                      color: Colors.white, fontWeight: FontWeight.w800)),
            )
          else
            Text('切换',
                style: t.textStyles.sm.copyWith(
                    color: t.primaryDeep, fontWeight: FontWeight.w800)),
        ]),
      ),
    );
  }
}
