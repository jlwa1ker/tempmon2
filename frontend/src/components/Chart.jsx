import { useMemo } from 'react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import './Chart.css'

const TEMP_COLORS = ['#2563eb', '#dc2626', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#4f46e5', '#be185d']
const HUMIDITY_COLORS = ['#06b6d4', '#f59e0b', '#84cc16', '#ec4899', '#8b5cf6', '#14b8a6', '#f97316', '#6366f1']

function formatTimestamp(isoString) {
  const date = new Date(isoString)
  const month = date.toLocaleString('en-US', { month: 'short' })
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month} ${day} ${hours}:${minutes}`
}

function getMetricLabel(metric) {
  if (metric === 'temperature_f') return 'Temperature (°F)'
  return 'Humidity (%)'
}

export default function Chart({ readings, metrics }) {
  const isDual = metrics.length === 2
  const hasTemp = metrics.includes('temperature_f')
  const hasHumidity = metrics.includes('humidity_pct')

  const { chartData, locations } = useMemo(() => {
    if (!readings || readings.length === 0) {
      return { chartData: [], locations: [] }
    }

    // Collect distinct locations
    const locationSet = new Set()
    readings.forEach((r) => locationSet.add(r.location))
    const locs = Array.from(locationSet).sort()

    // Group readings by timestamp, with each location+metric as a separate field
    const timeMap = new Map()
    readings.forEach((r) => {
      const key = r.timestamp
      if (!timeMap.has(key)) {
        timeMap.set(key, { timestamp: key })
      }
      const entry = timeMap.get(key)
      if (hasTemp) {
        entry[`${r.location}_temp`] = r.temperature_f
      }
      if (hasHumidity) {
        entry[`${r.location}_humidity`] = r.humidity_pct
      }
    })

    // Sort by timestamp
    const data = Array.from(timeMap.values()).sort(
      (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    )

    return { chartData: data, locations: locs }
  }, [readings, metrics])

  if (!readings || readings.length === 0) {
    return (
      <div className="no-data-message" role="status">
        <p>No data found</p>
      </div>
    )
  }

  const title = isDual
    ? 'Temperature (°F) & Humidity (%)'
    : getMetricLabel(metrics[0])

  return (
    <div className="chart-container" aria-label="Readings line chart">
      <h2>{title}</h2>
      <ResponsiveContainer width="100%" height={400}>
        <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="timestamp"
            tickFormatter={formatTimestamp}
            label={{ value: 'Time', position: 'insideBottomRight', offset: -5 }}
          />
          {isDual ? (
            <>
              <YAxis
                yAxisId="left"
                label={{ value: 'Temperature (°F)', angle: -90, position: 'insideLeft' }}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                label={{ value: 'Humidity (%)', angle: 90, position: 'insideRight' }}
              />
            </>
          ) : (
            <YAxis
              yAxisId="left"
              label={{ value: getMetricLabel(metrics[0]), angle: -90, position: 'insideLeft' }}
            />
          )}
          <Tooltip
            labelFormatter={formatTimestamp}
            formatter={(value, name) => [value, name]}
          />
          <Legend />
          {hasTemp &&
            locations.map((loc, idx) => (
              <Line
                key={`${loc}_temp`}
                type="monotone"
                dataKey={`${loc}_temp`}
                name={isDual ? `${loc} (°F)` : loc}
                stroke={TEMP_COLORS[idx % TEMP_COLORS.length]}
                yAxisId="left"
                dot={false}
                connectNulls
              />
            ))}
          {hasHumidity &&
            locations.map((loc, idx) => (
              <Line
                key={`${loc}_humidity`}
                type="monotone"
                dataKey={`${loc}_humidity`}
                name={isDual ? `${loc} (%)` : loc}
                stroke={isDual ? HUMIDITY_COLORS[idx % HUMIDITY_COLORS.length] : TEMP_COLORS[idx % TEMP_COLORS.length]}
                yAxisId={isDual ? 'right' : 'left'}
                dot={false}
                connectNulls
                strokeDasharray={isDual ? '5 5' : undefined}
              />
            ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
