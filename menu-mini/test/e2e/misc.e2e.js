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

  // 我的 Tab
  await mini.switchTab('/pages/profile/Profile')
  await sleep(2000)
  page = await mini.currentPage()
  const rows = await page.$$('.row')
  r.log('我的 Tab（用户卡+功能列表）', !!(await page.$('.user-card')) && rows.length >= 8, `功能行 ${rows.length}`)

  // 推荐占位
  await mini.switchTab('/pages/recommend/Index')
  await sleep(1500)
  page = await mini.currentPage()
  r.log('推荐 Tab（建设中占位）', page.path.includes('recommend/Index'))
}
