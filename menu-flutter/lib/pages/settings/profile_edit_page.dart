import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../core/image_helper.dart';
import '../../services/member_service.dart';
import '../../services/upload_service.dart';
import '../../stores/auth_store.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';

/// 个人资料编辑（G7）：头像 + 昵称，GET/PUT /member/me。
class ProfileEditPage extends StatefulWidget {
  const ProfileEditPage({super.key});

  @override
  State<ProfileEditPage> createState() => _ProfileEditPageState();
}

class _ProfileEditPageState extends State<ProfileEditPage> {
  final _name = TextEditingController();
  MemberProfile? _me;
  String? _error;
  bool _loading = true;
  bool _saving = false;
  bool _uploading = false;
  String? _avatarUrl; // 已上传的新头像（本站 /uploads/ 地址）

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final me = await MemberService.me();
      if (!mounted) return;
      setState(() {
        _me = me;
        _name.text = me.name;
        _avatarUrl = me.avatarUrl;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = errorText(e, '资料加载失败');
        _loading = false;
      });
    }
  }

  void _snack(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  Future<void> _pickAvatar() async {
    try {
      final x = await ImagePicker().pickImage(source: ImageSource.gallery);
      if (x == null || !mounted) return;
      setState(() => _uploading = true);
      final r = await UploadService.uploadOne(File(x.path));
      if (mounted) setState(() => _avatarUrl = r.url);
    } catch (e) {
      if (mounted) _snack('头像上传失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _save() async {
    final name = _name.text.trim();
    if (name.isEmpty || name.length > 20) {
      _snack('昵称需 1–20 个字');
      return;
    }
    setState(() => _saving = true);
    final auth = context.read<AuthStore>();
    try {
      await MemberService.updateMe(
        name: name,
        avatarUrl: _avatarUrl != _me?.avatarUrl ? _avatarUrl : null,
      );
      auth.updateNickname(name);
      if (!mounted) return;
      _snack('已保存');
      Navigator.of(context).pop();
    } catch (e) {
      if (mounted) _snack('保存失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const BackHeader(title: '个人资料'),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? ErrorView(text: _error!, onRetry: _load)
                      : _form(t),
            ),
          ],
        ),
      ),
    );
  }

  Widget _form(AppTokens t) {
    final ts = t.textStyles;
    final url = _avatarUrl;
    final avatar = url != null && url.isNotEmpty
        ? ClipOval(
            child: Image.network(
              ImageHelper.toThumbnail(ImageHelper.toAbsolute(url)),
              width: 80,
              height: 80,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) =>
                  InitialAvatar(name: _name.text, size: 80, radius: AppTokens.rPill),
            ),
          )
        : InitialAvatar(name: _name.text, size: 80, radius: AppTokens.rPill);
    return ListView(
      padding: const EdgeInsets.all(AppTokens.sp16),
      children: [
        Center(
          child: GestureDetector(
            onTap: _uploading ? null : _pickAvatar,
            child: Column(
              children: [
                avatar,
                const SizedBox(height: AppTokens.sp8),
                Text(_uploading ? '上传中…' : '更换头像',
                    style: ts.caption.copyWith(color: t.primary)),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppTokens.sp24),
        Text('昵称', style: ts.sectionLabel),
        const SizedBox(height: AppTokens.sp8),
        TextField(
          key: const Key('profile-name'),
          controller: _name,
          maxLength: 20,
          style: ts.input,
          decoration: const InputDecoration(hintText: '家里人怎么称呼你'),
        ),
        if ((_me?.phone ?? '').isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp8),
          Text('手机号 ${_me!.phone}', style: ts.caption.copyWith(color: t.caption)),
        ],
        const SizedBox(height: AppTokens.sp24),
        ElevatedButton(
          onPressed: _saving || _uploading ? null : _save,
          child: Text(_saving ? '保存中…' : '保存'),
        ),
      ],
    );
  }
}
