/** 食集套件：Tab 列表 / 新建弹窗 / 详情三 Tab（菜+备菜+聚餐）。 */
const { sleep } = require('./helpers')

module.exports = async function menuSuite(mini, r) {
  console.log('—— 食集 ——')
  await mini.switchTab('/pages/menu/List')
  await sleep(2500)
  let page = await mini.currentPage()

  const cards = await page.$$('.card')
  r.log('食集列表', page.path.includes('menu/List'), `食集卡 ${cards.length} 张`)

  // 新建弹窗（不真的创建）
  const newBtn = await page.$('.new-btn')
  await newBtn.tap()
  await sleep(800)
  r.log('新建食集弹窗', !!(await page.$('.dialog')))
  await (await page.$('.dialog-btn')).tap() // 取消
  await sleep(600)

  if (!cards.length) {
    r.log('食集为空（跳过详情走查）', true)
    return
  }

  // 详情 + 三 Tab
  await (await page.$$('.card'))[0].tap()
  await sleep(2500)
  page = await mini.currentPage()
  r.log('进入食集详情', page.path.includes('menu/Detail'), page.path)
  if (!page.path.includes('menu/Detail')) return

  const tabs = await page.$$('.tab')
  r.log('三 Tab（菜/备菜/聚餐）', tabs.length === 3)

  // 备菜 Tab
  await tabs[1].tap()
  await sleep(2500)
  const prepRows = await page.$$('.prep-row')
  const progress = await page.$('.prep-progress')
  r.log('备菜 Tab（进度+清单）', !!progress || prepRows.length > 0, `备菜行 ${prepRows.length}`)

  // 聚餐 Tab
  const tabs2 = await page.$$('.tab')
  await tabs2[2].tap()
  await sleep(2500)
  r.log('聚餐 Tab（邀请卡/错误态渲染）', !!(await page.$('.invite-card')) || !!(await page.$('ui-state')))

  await mini.navigateBack()
  await sleep(1200)
}
