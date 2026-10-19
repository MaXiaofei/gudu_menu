/** 采购 + 我的 + 推荐占位套件。 */
const { sleep } = require('./helpers')

module.exports = async function miscSuite(mini, r) {
  console.log('—— 采购 / 我的 / 推荐 ——')

  // 采购页
  await mini.navigateTo({ url: '/pages/shopping/List' })
  await sleep(2500)
  let page = await mini.currentPage()
  const lists = await page.$$('.lcard')
  r.log('采购页（列表/空态）', page.path.includes('shopping/List'), `清单 ${lists.length} 张`)
  await mini.navigateBack()
  await sleep(1000)

  // 我的（导航改版后由右上角头像进入，不再是 Tab）
  await mini.navigateTo('/pages/profile/Profile')
  await sleep(2000)
  page = await mini.currentPage()
  const rows = await page.$$('.row')
  r.log('我的（用户卡+功能列表）', !!(await page.$('.user-card')) && rows.length >= 8, `功能行 ${rows.length}`)

  // 咕嘟 Tab（原智荐 Tab 位置）+ 全部推荐二级页
  await mini.switchTab('/pages/agent/Index')
  await sleep(1500)
  page = await mini.currentPage()
  r.log('咕嘟 Tab', page.path.includes('agent/Index'))
  await mini.navigateTo('/pages/recommend/Index')
  await sleep(1500)
  page = await mini.currentPage()
  r.log('全部推荐（二级页）', page.path.includes('recommend/Index'))
}
