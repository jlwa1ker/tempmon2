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

const COLORS = ['#2563eb', '#dc2626', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#4f46e5', '#be185d']

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

export default function Chart({ readings, metric }) {
  const { chartData, locations } = useMemo(() => {
    if (!readings || readings.length === 0) {
      return { chartData: [], locations: [] }
    }

    // Collect distinct locations
    const locationSet = new Set()
    readings.forEach((r) => locationSet.add(r.location))
    const locs = Array.from(locationSet).sort()

    // Group readings by timestamp, with each location as a separate field
    const timeMap = new Map()
    readings.forEach((r) => {
      const key = r.timestamp
      if (!timeMap.has(key)) {
        timeMap.set(key, { timestamp: key })
      }
      const entry = timeMap.get(key)
      entry[r.location] = r[metric]
    })

    // Sort by timestamp
    const data = Array.from(timeMap.values()).sort(
      (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    )

    return { chartData: data, locations: locs }
  }, [readings, metric])

  if (!readings || readings.length === 0) {
    return (
      <div className="no-data-message" role="status">
        <p>No data found</p>
      </div>
    )
  }

  return (
    <div className="chart-container" aria-label="Readings line chart">
      <h2>{getMetricLabel(metric)}</h2>
      <ResponsiveContainer width="100%" height={400}>
        <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="timestamp"
            tickFormatter={formatTimestamp}
            label={{ value: 'Time', position: 'insideBottomRight', offset: -5 }}
          />
          <YAxis
            label={{ value: getMetricLabel(metric), angle: -90, position: 'insideLeft' }}
          />
          <Tooltip
            labelFormatter={formatTimestamp}
            formatter={(value, name) => [value, name]}
          />
          <Legend />
          {locations.map((loc, idx) => (
            <Line
              key={loc}
              type="monotone"
              dataKey={loc}
              name={loc}
              stroke={COLORS[idx % COLORS.length]}
              dot={false}
              connectNulls
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
