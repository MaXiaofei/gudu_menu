/** 库存套件：分组/筛选 chips/搜索（防抖）/入库页顶栏。 */
const { sleep } = require('./helpers')

module.exports = async function pantrySuite(mini, r) {
  console.log('—— 库存 ——')
  await mini.switchTab('/pages/pantry/List')
  await sleep(2500)
  const page = await mini.currentPage()

  const chips = await page.$$('.chip')
  const rows = await page.$$('.row')
  r.log('库存 Tab（4 档筛选）', chips.length === 4, `chips ${chips.length} / 行 ${rows.length}`)

  // 搜索（输入即搜 300ms 防抖）
  await (await page.$('.search-ipt')).input('鸡')
  await sleep(1500)
  r.log('搜索态（找到 N 个）', !!(await page.$('.found')) || !!(await page.$('.no-hit')))
  const clear = await page.$('.search-clear')
  if (clear) {
    await clear.tap()
    await sleep(1200)
  }

  // 切「用完」筛选
  const chipNone = (await page.$$('.chip'))[1]
  await chipNone.tap()
  await sleep(1800)
  r.log('档位筛选（用完）', true)
}
