<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { silentLogin } from "@/api/auth";
onLaunch(async () => {
  const token = uni.getStorageSync("token");
  const loggedOut = uni.getStorageSync("logged_out");
  if (!token) {
    if (loggedOut) {
      // 主动退出过的用户：不自动静默登录，进登录页自己选
      uni.reLaunch({ url: "/pages/login/Login" });
      return;
    }
    // 新用户零门槛：wx.login 静默登录（无感），失败才进账号登录页
    const ok = await silentLogin();
    if (!ok) {
      uni.reLaunch({ url: "/pages/login/Login" });
    }
  }
});
</script>

<style lang="scss">
/* ============================================================
 * 设计 token（对齐 menu-flutter AppTokens.cream，DESIGN.md 权威）
 * 页面统一奶油底 #FDFAF4；组件一律用 var(--xx) 取值
 * ============================================================ */
page {
  /* 色板 */
  --primary: #E89150;
  --primary-deep: #D17A3C;
  --primary-soft: #F6D9BE;
  --secondary: #FBF0DD;
  --accent: #B8762E;
  --bg: #FDFAF4;
  --card: #FFFFFF;
  --border: #F0E6D6;
  --title: #4A382A;
  --body: #6E5C49;
  --caption: #9C8C7A;
  --highlight: #FFF7EC;
  --success: #4FAE6E;
  --warning: #E5A938;
  --warning-text: #B8860B; /* 黄底上的可读文字档（chips/警示文案用） */
  --error: #DB5A4E;
  --info: #4FA0D0;

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
  background: linear-gradient(135deg, #E89150, #D17A3C);
  color: #FFFFFF;
  border: none;
  border-radius: 14px;
  padding: 14px 0;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(232, 145, 80, 0.3);
  line-height: 1.4;
}
.yh-btn-gradient::after { border: none; }
.yh-btn-gradient[disabled] {
  background: linear-gradient(135deg, #F6D9BE, #F6D9BE);
  color: rgba(255, 255, 255, 0.85);
  box-shadow: none;
}
.yh-btn-ghost {
  background: #FFFFFF;
  color: #E89150;
  border: 1px solid #E89150;
  border-radius: 14px;
  padding: 13px 0;
  font-size: 16px;
  line-height: 1.4;
}
.yh-btn-ghost::after { border: none; }
</style>
