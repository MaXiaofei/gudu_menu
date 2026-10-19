/** 保存图片到相册（处理授权拒绝：引导去设置页打开权限）。 */

/** 错误是否为相册授权被拒（首次拒绝 / 之前拒绝过）。 */
export function isAuthDenied(errMsg?: string): boolean {
  return !!errMsg && /auth|deny|denied|authorize|permission/i.test(errMsg)
}

/** 用户主动取消（不提示）。 */
export function isCancelled(errMsg?: string): boolean {
  return !!errMsg && /cancel/i.test(errMsg)
}

export function saveToAlbum(filePath: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.saveImageToPhotosAlbum({
      filePath,
      success: () => {
        uni.showToast({ title: '已保存到相册', icon: 'none' })
        resolve(true)
      },
      fail: (e: any) => {
        const msg = e?.errMsg || ''
        if (isAuthDenied(msg)) {
          uni.showModal({
            title: '需要相册权限',
            content: '保存图片需要「添加到相册」权限，去设置里打开后再试一次',
            confirmText: '去设置',
            success: ({ confirm }) => {
              if (confirm) uni.openSetting({})
            },
          })
        } else if (!isCancelled(msg)) {
          uni.showToast({ title: '保存失败，请稍后再试', icon: 'none' })
        }
        resolve(false)
      },
    })
  })
}
