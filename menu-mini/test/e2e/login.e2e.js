/** 登录套件：登录页渲染 + 登录进入菜谱 Tab（admin/admin123）。 */
const { sleep } = require('./helpers')

module.exports = async function loginSuite(mini, r) {
  console.log('—— 登录 ——')
  let page = await mini.currentPage()

  if (!page.path.includes('login')) {
    // 已登录态（storage 残留）直接跳过登录步骤
    r.log('已有登录态（跳过登录）', true, page.path)
    return
  }

  const inputs = await page.$$('.ipt')
  r.log('登录表单渲染（2 个输入框）', inputs.length >= 2, `实际 ${inputs.length} 个`)
  if (inputs.length < 2) return

  await inputs[0].input('admin')
  await inputs[1].input('admin123')
  await (await page.$('.login-btn')).tap()
  await sleep(3000)

  page = await mini.currentPage()
  r.log('登录成功进入菜谱 Tab', page.path.includes('dish/List'), page.path)
}
