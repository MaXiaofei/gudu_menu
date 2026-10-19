part of '../create_page.dart';

/// 写菜谱页拆分（≤500 行）：State 方法以扩展形式放在 part 文件，私有成员同库可见。
extension _CreateFormSteps on _CreateDishPageState {
  Widget _buildStepCard(int index, _StepData step) {
    final t = AppTokens.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        side: BorderSide(color: t.border),
      ),
      color: t.card,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 头部：步骤编号 + 删除
            Row(
              children: [
                Container(
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    color: t.primary,
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    '${index + 1}',
                    style: t.textStyles.sm.copyWith(
                      color: t.card,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '步骤 ${index + 1}',
                  style: t.textStyles.md.copyWith(
                    fontWeight: FontWeight.w600,
                    color: t.title,
                  ),
                ),
                const Spacer(),
                InkWell(
                  onTap: () => _removeStep(index),
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                  hoverColor: t.primary.withValues(alpha: 0.08),
                  child: Icon(Icons.close, size: 20, color: t.caption),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // 描述
            TextField(
              controller: step.textCtrl,
              maxLines: 3,
              style: t.textStyles.md,
              decoration: InputDecoration(
                filled: true,
                fillColor: t.bg,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                  borderSide: BorderSide(color: t.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                  borderSide: BorderSide(color: t.border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                  borderSide:
                      BorderSide(color: t.primary, width: 1.5),
                ),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              ),
            ),
            const SizedBox(height: 8),
            // 步骤图
            _buildStepImage(step),
          ],
        ),
      ),
    );
  }

  Widget _buildStepImage(_StepData step) {
    final t = AppTokens.of(context);
    if (step.imageFile != null) {
      return Stack(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(AppTokens.rSm),
            child: Image.file(
              step.imageFile!,
              height: 100,
              width: double.infinity,
              fit: BoxFit.cover,
            ),
          ),
          Positioned(
            top: 4,
            right: 4,
            child: InkWell(
              onTap: () => _onRemoveStepImage(step),
              borderRadius: BorderRadius.circular(AppTokens.rPill),
              hoverColor: t.primary.withValues(alpha: 0.08),
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                ),
                child: Icon(Icons.close, size: 16, color: t.card),
              ),
            ),
          ),
        ],
      );
    }
    return InkWell(
      onTap: () => _onPickStepImage(step),
      borderRadius: BorderRadius.circular(AppTokens.rSm),
      hoverColor: t.primary.withValues(alpha: 0.08),
      child: Container(
        height: 44,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          border: Border.all(
            color: t.border,
            style: BorderStyle.solid,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_photo_alternate_outlined,
                size: 18, color: t.caption),
            const SizedBox(width: 8),
            Text(
              '添加图片（可选）',
              style: t.textStyles.sm.copyWith(color: t.caption),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddStepButton() {
    final t = AppTokens.of(context);
    return SizedBox(
      height: 44,
      child: OutlinedButton.icon(
        onPressed: _addStep,
        icon: const Icon(Icons.add, size: 18),
        label: const Text('添加步骤'),
        style: OutlinedButton.styleFrom(
          foregroundColor: t.primary,
          side: BorderSide(
            color: t.primary.withAlpha(100),
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
        ),
      ),
    );
  }


  Widget _buildUrlImport() {
    final t = AppTokens.of(context);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          const SizedBox(height: 24),
          // 说明卡片
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: t.highlight,
              borderRadius: BorderRadius.circular(AppTokens.rLg),
              border: Border.all(color: t.border),
            ),
            child: Column(
              children: [
                Icon(Icons.download_for_offline_outlined,
                    size: 48, color: t.primary.withAlpha(200)),
                const SizedBox(height: 12),
                Text(
                  '从其他 App 导入菜谱',
                  style: t.textStyles.subtitle,
                ),
                const SizedBox(height: 8),
                Text(
                  '粘贴下厨房、美食杰、豆果的菜谱链接，\n自动解析菜名、步骤和图片',
                  textAlign: TextAlign.center,
                  style: t.textStyles.sm.copyWith(color: t.caption),
                ),
                const SizedBox(height: 16),
                // 支持的平台标签
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  alignment: WrapAlignment.center,
                  children: [
                    _platformChip('下厨房'),
                    _platformChip('美食杰'),
                    _platformChip('豆果美食'),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          // URL 输入
          TextField(
            controller: _urlCtrl,
            style: t.textStyles.md,
            keyboardType: TextInputType.url,
            decoration: InputDecoration(
              hintText: '粘贴菜谱链接…',
              prefixIcon: const Icon(Icons.link),
              filled: true,
              fillColor: t.bg,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                borderSide: BorderSide(color: t.border),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                borderSide: BorderSide(color: t.border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                borderSide:
                    BorderSide(color: t.primary, width: 1.5),
              ),
              suffixIcon: _urlCtrl.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: () {
                        _urlCtrl.clear();
                        _refresh(() {});
                      },
                    )
                  : null,
            ),
            onChanged: (_) => _refresh(() {}),
          ),
          const SizedBox(height: 16),
          // 导入按钮
          SizedBox(
            height: 48,
            child: ElevatedButton.icon(
              onPressed: _importing ? null : _onImportUrl,
              icon: _importing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.cloud_download_outlined),
              label: Text(
                  _importing ? '正在解析菜谱…' : '开始导入',
                  style: t.textStyles.lg.copyWith(color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _platformChip(String name) {
    final t = AppTokens.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rPill),
        border: Border.all(color: t.border),
      ),
      child: Text(
        name,
        style: t.textStyles.sm.copyWith(color: t.accent),
      ),
    );
  }
}
