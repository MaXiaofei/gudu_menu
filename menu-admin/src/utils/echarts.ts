// echarts 按需引入：只注册用到的图表/组件/渲染器，避免 `import * as echarts` 打入全量（~1MB）
import { use, init, type ComposeOption } from 'echarts/core'
import { LineChart, BarChart, type LineSeriesOption, type BarSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  type GridComponentOption,
  type TooltipComponentOption,
  type LegendComponentOption,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

export type ECOption = ComposeOption<
  LineSeriesOption | BarSeriesOption | GridComponentOption | TooltipComponentOption | LegendComponentOption
>
export type ECharts = ReturnType<typeof init>
export { init }
