import { useState } from 'react'
import QueryForm from './components/QueryForm'
import Chart from './components/Chart'
import { VERSION } from './version'
import './App.css'

function App() {
  const [readings, setReadings] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [count, setCount] = useState(0)
  const [metrics, setMetrics] = useState(['temperature_f'])
  const [hasQueried, setHasQueried] = useState(false)
  const [viewMode, setViewMode] = useState('chart')

  async function handleSubmit({ start, end, locations, metrics: selectedMetrics }) {
    setLoading(true)
    setError(null)
    setMetrics(selectedMetrics)
    setHasQueried(true)

    try {
      const params = new URLSearchParams()
      params.append('start', start)
      params.append('end', end)
      locations.forEach((loc) => params.append('locations', loc))

      const response = await fetch(`/readings?${params.toString()}`)

      if (response.ok) {
        const data = await response.json()
        setReadings(data.readings)
        setCount(data.readings.length)
      } else {
        let message
        try {
          const errorData = await response.json()
          message = `${response.status}: ${errorData.message || response.statusText}`
        } catch {
          message = `${response.status}: ${response.statusText || 'An error occurred'}`
        }
        setError(message)
        setReadings([])
        setCount(0)
      }
    } catch (err) {
      setError(err.message || 'Network error')
      setReadings([])
      setCount(0)
    } finally {
      setLoading(false)
    }
  }

  function formatTimestamp(isoString) {
    const date = new Date(isoString)
    return date.toLocaleString()
  }

  return (
    <div className="App">
      <h1>Temperature Monitor</h1>
      <p className="version">v{VERSION.toString()}</p>
      <QueryForm onSubmit={handleSubmit} disabled={loading} />

      {loading && (
        <div className="loading-indicator">
          <div className="spinner" aria-label="Loading"></div>
          <span>Loading readings...</span>
        </div>
      )}

      {error && (
        <div className="error-display" role="alert">
          <p>{error}</p>
        </div>
      )}

      {!loading && !error && count > 0 && (
        <div className="result-count">
          <p>Showing {count} reading{count !== 1 ? 's' : ''}</p>
        </div>
      )}

      {!loading && !error && hasQueried && (
        <div className="view-toggle">
          <button
            className={`toggle-button ${viewMode === 'chart' ? 'active' : ''}`}
            onClick={() => setViewMode('chart')}
          >
            Show Chart
          </button>
          <button
            className={`toggle-button ${viewMode === 'table' ? 'active' : ''}`}
            onClick={() => setViewMode('table')}
          >
            Show Table
          </button>
        </div>
      )}

      {!loading && !error && hasQueried && viewMode === 'chart' && readings.length > 0 && (
        <Chart readings={readings} metrics={metrics} />
      )}

      {!loading && !error && hasQueried && viewMode === 'chart' && readings.length === 0 && (
        <Chart readings={[]} metrics={metrics} />
      )}

      {!loading && !error && hasQueried && viewMode === 'table' && (
        <div className="table-container">
          {readings.length === 0 ? (
            <p>No data found</p>
          ) : (
            <table className="readings-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Location</th>
                  <th>Temperature (°F)</th>
                  <th>Humidity (%)</th>
                </tr>
              </thead>
              <tbody>
                {readings.map((r, idx) => (
                  <tr key={idx}>
                    <td>{formatTimestamp(r.timestamp)}</td>
                    <td>{r.location}</td>
                    <td>{r.temperature_f}</td>
                    <td>{r.humidity_pct}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}

export default App
