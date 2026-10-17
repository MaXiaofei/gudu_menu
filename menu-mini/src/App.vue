<script setup lang="ts">
import { onLaunch, onShow } from "@dcloudio/uni-app";
import { silentLogin } from "@/api/auth";
import { initTheme, applyTabBar } from "@/utils/theme";
import { hasConsent } from "@/utils/consent";
onShow(() => applyTabBar());
onLaunch(async () => {
  initTheme();
  const token = uni.getStorageSync("token");
  const loggedOut = uni.getStorageSync("logged_out");
  if (!token) {
    if (loggedOut || !hasConsent()) {
      // 主动退出过 / 尚未勾选协议：不发起任何登录（含 wx.login），进登录页先同意协议
      uni.reLaunch({ url: "/pages/login/Login" });
      return;
    }
    // 已同意协议的老用户：wx.login 静默登录（无感），失败才进账号登录页
    const ok = await silentLogin();
    if (!ok) {
      uni.reLaunch({ url: "/pages/login/Login" });
    }
  }
});
</script>

<style lang="scss">
/* ============================================================
 * 设计 token：皮肤色板 / 功能色 / 动效来自 tokens.generated.css
 * （由 docs/design/tokens.json 经 scripts/design/gen-tokens.mjs 生成，勿手改），
 * 默认皮肤为注册表第一项，运行时换肤由 utils/theme.ts 覆盖。
 * 组件一律用 var(--xx) 取值。
 * ============================================================ */
@import './styles/tokens.generated';

page {
  --warning-text: #B8860B; /* 黄底上的可读文字档（chips/警示文案用） */

  /* 圆角（AppTokens rSm/rMd/rLg/rXl/rPill） */
  --r-sm: 8px;
  --r-md: 12px;
  --r-lg: 16px;
  --r-xl: 22px;
  --r-pill: 999px;

  /* 间距（AppTokens sp 系列） */
  --sp-4: 4px;
  --sp-6: 6px;
  --sp-8: 8px;
  --sp-10: 10px;
  --sp-12: 12px;
  --sp-16: 16px;
  --sp-20: 20px;
  --sp-24: 24px;

  /* 字号阶梯（tokens.json typography.scale；新代码禁止裸 px 字号） */
  --fs-display: 40px;
  --fs-h1: 32px;
  --fs-h2: 24px;
  --fs-h3: 20px;
  --fs-subtitle: 18px;
  --fs-lg: 16px;
  --fs-md: 14px;
  --fs-sm: 12px;
  --fs-xs: 11px;
  --fs-tiny: 10px;
  --fs-micro: 9px;

  /* 叠加色（--on-primary 随皮肤，见 tokens.generated.css） */
  --mask: rgba(0, 0, 0, 0.4);  /* 弹层遮罩 */

  background: var(--bg);
  color: var(--title);
  font-size: 14px;
  -webkit-font-smoothing: antialiased;
}

/* ---- 原生元素复位 ---- */
button {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  line-height: normal;
}
button::after { border: none; }
input { font-size: 14px; color: var(--title); }

/* ---- 通用按钮（对齐 APP 48px 通栏 CTA） ---- */
.btn-primary {
  background: var(--primary);
  color: #FFFFFF;
  border-radius: var(--r-md);
  padding: 13px 0;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
}
.btn-primary[disabled],
.btn-primary.is-disabled {
  background: var(--border);
  color: rgba(255, 255, 255, 0.85);
}
.btn-ghost {
  background: var(--card);
  color: var(--primary);
  border: 1px solid var(--primary);
  border-radius: var(--r-md);
  padding: 12px 0;
  font-size: 15px;
  font-weight: 700;
  text-align: center;
}

/* ============================================================
 * 以下为登录页（保留的旧代码）依赖的全局类，勿删
 * ============================================================ */
.yh-card {
  background: #FFFFFF;
  border-radius: 18px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.08);
  padding: 16px;
  margin-bottom: 12px;
}
.card {
  background: #FFFFFF;
  border-radius: 18px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.08);
  padding: 16px;
  margin-bottom: 12px;
}
.yh-btn-gradient {
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  color: var(--on-primary);
  border: none;
  border-radius: 14px;
  padding: 14px 0;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(var(--sh), 0.3);
  line-height: 1.4;
}
.yh-btn-gradient::after { border: none; }
.yh-btn-gradient[disabled] {
  background: var(--primary-soft);
  color: rgba(255, 255, 255, 0.85);
  box-shadow: none;
}
.yh-btn-ghost {
  background: var(--card);
  color: var(--primary);
  border: 1px solid var(--primary);
  border-radius: 14px;
  padding: 13px 0;
  font-size: 16px;
  line-height: 1.4;
}
.yh-btn-ghost::after { border: none; }
</style>
