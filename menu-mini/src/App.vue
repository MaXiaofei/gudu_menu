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

/* ============ 全局（口语化 + 极简工具感） ============ */
/* 背景 #FAFAFA，卡片白底 + 极轻阴影，圆角 8px，主色 #FF6B35 */
page {
  background: #FAFAFA;
  color: #222;
  font-size: 14px;
}

/* 卡片通用 */
.card {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  padding: 14px;
  margin-bottom: 12px;
}

/* 小标题（灰） */
.section-title {
  font-size: 14px;
  color: #999;
  margin: 16px 0 8px;
  display: flex;
  align-items: center;
}

/* 主色按钮（原生 button 复位） */
.btn-primary {
  background: #FF6B35;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 12px;
  font-size: 16px;
  line-height: 1.4;
}
.btn-primary::after {
  border: none;
}

/* 次按钮（白底描边） */
.btn-ghost {
  background: #fff;
  color: #FF6B35;
  border: 1px solid #FF6B35;
  border-radius: 8px;
  padding: 11px;
  font-size: 16px;
  line-height: 1.4;
}
.btn-ghost::after {
  border: none;
}

/* 原生 input 复位 */
input {
  font-size: 14px;
  color: #222;
}
</style>
