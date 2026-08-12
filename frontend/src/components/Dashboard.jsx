import { useState, useEffect } from 'react'
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

  useEffect(() => {
    fetchLatest()
    const interval = setInterval(fetchLatest, 60000)
    return () => clearInterval(interval)
  }, [])

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
        {readings.map((reading) => (
          <div key={reading.location} className="dashboard-card">
            <h2 className="card-location">{reading.location}</h2>
            <div className="card-values">
              <span className="card-temperature">{reading.temperature_f}°F</span>
              <span className="card-humidity">{reading.humidity_pct}%</span>
            </div>
            <p className="card-timestamp">{formatTimestamp(reading.timestamp)}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Dashboard
