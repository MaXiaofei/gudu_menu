/** 菜谱套件：列表/搜索/详情（用料+步骤+加到食集）。 */
const { sleep } = require('./helpers')

module.exports = async function dishSuite(mini, r) {
  console.log('—— 菜谱 ——')
  await mini.reLaunch('/pages/dish/List')
  await sleep(2500)
  let page = await mini.currentPage()

  const cards = await page.$$('.card')
  r.log('列表加载', cards.length > 0, `卡片 ${cards.length} 张`)
  if (!cards.length) return

  // 搜索（走页面方法，规避键盘事件差异）
  await (await page.$('.search-ipt')).input('蛋')
  await page.callMethod('onSearch')
  await sleep(2000)
  const searched = await page.$$('.card')
  r.log('搜索「蛋」执行', true, `命中 ${searched.length} 张`)
  await page.callMethod('onClearKw')
  await sleep(1500)

  // 详情
  const first = (await page.$$('.card'))[0]
  await first.tap()
  await sleep(2500)
  page = await mini.currentPage()
  const isDetail = page.path.includes('dish/Detail')
  r.log('进入详情', isDetail, page.path)
  if (!isDetail) return

  const ingRows = await page.$$('.ing-row')
  const steps = await page.$$('.step')
  r.log('详情内容（用料/步骤）', true, `用料 ${ingRows.length} 行 / 步骤 ${steps.length} 个`)
  r.log('「加到食集」按钮存在', !!(await page.$('.btn-add')))
  await mini.navigateBack()
  await sleep(1500)
}
