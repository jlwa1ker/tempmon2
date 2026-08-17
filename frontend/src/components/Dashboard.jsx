import { useState, useEffect } from 'react'
import { LineChart, Line, XAxis, YAxis, ResponsiveContainer } from 'recharts'
import './Dashboard.css'

function formatTimestamp(isoString) {
  const date = new Date(isoString)
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function Dashboard() {
  const [readings, setReadings] = useState([])
  const [history, setHistory] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  async function fetchLatest() {
    try {
      const response = await fetch('/latest')
      if (response.ok) {
        const data = await response.json()
        setReadings(data.readings || [])
        setError(null)
      } else {
        setError(`Failed to fetch latest readings (${response.status})`)
      }
    } catch (err) {
      setError(err.message || 'Network error')
    } finally {
      setLoading(false)
    }
  }

  async function fetchHistory(locations) {
    const now = new Date()
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000)
    const params = new URLSearchParams()
    params.append('start', oneHourAgo.toISOString())
    params.append('end', now.toISOString())
    locations.forEach((loc) => params.append('locations', loc))

    try {
      const response = await fetch(`/readings?${params.toString()}`)
      if (response.ok) {
        const data = await response.json()
        // Group by location
        const grouped = {}
        for (const r of data.readings) {
          if (!grouped[r.location]) grouped[r.location] = []
          grouped[r.location].push(r)
        }
        setHistory(grouped)
      }
    } catch {
      // Silently fail — sparklines are a nice-to-have
    }
  }

  useEffect(() => {
    fetchLatest()
    const interval = setInterval(fetchLatest, 60000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (readings.length > 0) {
      const locations = readings.map((r) => r.location)
      fetchHistory(locations)
    }
  }, [readings])

  if (loading) {
    return (
      <div className="dashboard">
        <div className="loading-indicator">
          <div className="spinner" aria-label="Loading"></div>
          <span>Loading latest readings...</span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="dashboard">
        <div className="error-display" role="alert">
          <p>{error}</p>
        </div>
      </div>
    )
  }

  if (readings.length === 0) {
    return (
      <div className="dashboard">
        <p>No readings available.</p>
      </div>
    )
  }

  return (
    <div className="dashboard">
      <div className="dashboard-cards">
        {readings.map((reading) => {
          const locationHistory = history[reading.location] || []
          return (
            <div key={reading.location} className="dashboard-card">
              <h2 className="card-location">{reading.location}</h2>
              <div className="card-body">
                <div className="card-info">
                  <div className="card-values">
                    <span className="card-temperature">{reading.temperature_f}°F</span>
                    <span className="card-humidity">
                      {Number(reading.humidity_pct).toFixed(1)}%
                    </span>
                  </div>
                  <p className="card-timestamp">{formatTimestamp(reading.timestamp)}</p>
                </div>
                {locationHistory.length > 1 && (
                  <div className="card-chart">
                    <ResponsiveContainer width="100%" height={80}>
                      <LineChart data={locationHistory} margin={{ top: 5, right: 5, bottom: 0, left: 5 }}>
                        <XAxis
                          dataKey="timestamp"
                          tickFormatter={(ts) => {
                            const d = new Date(ts)
                            return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
                          }}
                          tick={{ fontSize: 10 }}
                          interval="preserveStartEnd"
                        />
                        <YAxis domain={['auto', 'auto']} hide />
                        <Line
                          type="monotone"
                          dataKey="temperature_f"
                          stroke="#2563eb"
                          dot={false}
                          strokeWidth={1.5}
                        />
                        <Line
                          type="monotone"
                          dataKey="humidity_pct"
                          stroke="#dc2626"
                          dot={false}
                          strokeWidth={1.5}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default Dashboard
