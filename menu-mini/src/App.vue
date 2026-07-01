<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  const token = uni.getStorageSync("token");
  if (!token) {
    uni.reLaunch({ url: "/pages/login/Login" });
  }
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style lang="scss">
@import "uview-plus/index.scss";
/* ============ 温暖咕嘟·卡片流（Flutter 风格移植） ============ */
/* 全局奶油白底 #FDFAF4 + 卡片白底大圆角 18 + 柔和阴影 + 暖橙渐变按钮 */
page {
  background: #FDFAF4;
  color: #4A382A;
  font-size: 14px;
  -webkit-font-smoothing: antialiased;
}

/* 通用卡片：白底 + 大圆角 + 柔和阴影 */
.yh-card {
  background: #FFFFFF;
  border-radius: 18px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.08);
  padding: 16px;
  margin-bottom: 12px;
}
/* 兼容旧类名 .card */
.card {
  background: #FFFFFF;
  border-radius: 18px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.08);
  padding: 16px;
  margin-bottom: 12px;
}

/* 渐变按钮（暖橙） */
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

/* 次按钮：白底描边暖橙 */
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

/* 小标题（暖灰） */
.yh-section-title {
  font-size: 14px;
  color: #9C8C7A;
  margin: 16px 0 8px;
  display: flex;
  align-items: center;
}

/* 暖橙 tag */
.yh-tag {
  display: inline-block;
  background: rgba(232, 145, 80, 0.1);
  color: #E89150;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
}

/* 文字色 */
.yh-text-primary { color: #4A382A; }
.yh-text-secondary { color: #9C8C7A; }
.yh-text-hint { color: #9C8C7A; }

/* 原生 input 复位（统一奶油白填充 + 暖橙聚焦边） */
input {
  font-size: 14px;
  color: #4A382A;
}

/* 原生 button 复位 */
button {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  line-height: normal;
}
button::after { border: none; }
</style>
