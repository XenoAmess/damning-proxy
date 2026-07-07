<template>
  <div class="dashboard">
    <h2>Dashboard</h2>

    <div class="summary-cards">
      <el-card v-for="item in summaryCards" :key="item.label" class="summary-card">
        <div class="card-value">{{ item.value }}</div>
        <div class="card-label">{{ item.label }}</div>
      </el-card>
    </div>

    <div class="charts-row">
      <el-card class="chart-card">
        <v-chart class="chart" :option="requestsOption" autoresize />
      </el-card>
    </div>

    <div class="charts-row">
      <el-card class="chart-card half">
        <v-chart class="chart" :option="instancesOption" autoresize />
      </el-card>
      <el-card class="chart-card half">
        <v-chart class="chart" :option="statusOption" autoresize />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import {
  getMetricsSummary,
  getMetricsTimeSeries,
  getMetricsTopInstances,
  getMetricsStatusDistribution,
} from '../api/damning.js'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
])

const summary = ref({})
const timeSeries = ref([])
const topInstances = ref([])
const statusDistribution = ref([])

const summaryCards = computed(() => {
  return [
    { label: '总请求数', value: summary.value.totalRequests ?? '-' },
    { label: '错误请求', value: summary.value.errorRequests ?? '-' },
    {
      label: '平均延迟 (ms)',
      value: summary.value.avgLatencyMs ? summary.value.avgLatencyMs.toFixed(2) : '-',
    },
    { label: '总 Token 数', value: summary.value.totalTokens ?? '-' },
  ]
})

const requestsOption = computed(() => {
  const buckets = timeSeries.value.map((item) => item.bucket)
  const requests = timeSeries.value.map((item) => item.requests)
  const errors = timeSeries.value.map((item) => item.errors)
  return {
    title: { text: '请求与错误趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求数', '错误数'] },
    xAxis: { type: 'category', data: buckets },
    yAxis: { type: 'value' },
    series: [
      { name: '请求数', type: 'line', data: requests },
      { name: '错误数', type: 'line', data: errors },
    ],
  }
})

const instancesOption = computed(() => {
  return {
    title: { text: 'Top 实例请求量' },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: topInstances.value.map((item) => item.instanceSlug),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: topInstances.value.map((item) => item.requests) }],
  }
})

const statusOption = computed(() => {
  return {
    title: { text: '请求状态分布' },
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: '50%',
        data: statusDistribution.value.map((item) => ({
          name: item.status === 'error' ? '错误' : '成功',
          value: item.count,
        })),
      },
    ],
  }
})

async function loadData() {
  const end = new Date()
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  const startStr = start.toISOString().slice(0, 19)
  const endStr = end.toISOString().slice(0, 19)
  const params = { startTime: startStr, endTime: endStr }

  const [summaryRes, timeRes, instancesRes, statusRes] = await Promise.all([
    getMetricsSummary(params),
    getMetricsTimeSeries(params),
    getMetricsTopInstances(params),
    getMetricsStatusDistribution(params),
  ])

  summary.value = summaryRes.data
  timeSeries.value = timeRes.data
  topInstances.value = instancesRes.data
  statusDistribution.value = statusRes.data
}

onMounted(loadData)
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  text-align: center;
}

.card-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}

.card-label {
  margin-top: 8px;
  color: #606266;
}

.charts-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  flex: 1;
}

.chart-card.half {
  flex: 0 0 50%;
  min-width: 400px;
}

.chart {
  height: 300px;
}

@media (max-width: 900px) {
  .summary-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts-row {
    flex-direction: column;
  }
  .chart-card.half {
    flex: 1;
    min-width: auto;
  }
}
</style>
