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
  const [metric, setMetric] = useState('temperature_f')
  const [hasQueried, setHasQueried] = useState(false)

  async function handleSubmit({ start, end, locations, metric: selectedMetric }) {
    setLoading(true)
    setError(null)
    setMetric(selectedMetric)
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

      {!loading && !error && readings.length > 0 && (
        <Chart readings={readings} metric={metric} />
      )}

      {!loading && !error && hasQueried && readings.length === 0 && (
        <Chart readings={[]} metric={metric} />
      )}
    </div>
  )
}

export default App
