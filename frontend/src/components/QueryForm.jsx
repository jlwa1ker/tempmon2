import { useState, useEffect } from 'react'
import './QueryForm.css'

/**
 * Converts a datetime-local input value (e.g. "2024-01-15T10:30") to RFC 3339 format.
 * Returns null if the value is not a valid date.
 */
function toRfc3339(datetimeLocalValue) {
  if (!datetimeLocalValue) return null
  const date = new Date(datetimeLocalValue)
  if (isNaN(date.getTime())) return null
  return date.toISOString()
}

/**
 * Validates that a string is a valid RFC 3339 / ISO 8601 datetime.
 */
function isValidRfc3339(value) {
  if (!value) return false
  const date = new Date(value)
  return !isNaN(date.getTime())
}

/**
 * Formats a Date to a datetime-local input value (YYYY-MM-DDTHH:mm).
 */
function toDatetimeLocal(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

const METRIC_OPTIONS = [
  { value: 'temperature_f', label: 'Temperature (°F)' },
  { value: 'humidity_pct', label: 'Humidity (%)' },
]

function QueryForm({ onSubmit, disabled = false }) {
  const now = new Date()
  const twentyFourHoursAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000)

  const [start, setStart] = useState(toDatetimeLocal(twentyFourHoursAgo))
  const [end, setEnd] = useState(toDatetimeLocal(now))
  const [locations, setLocations] = useState([])
  const [locationSelect, setLocationSelect] = useState('')
  const [availableLocations, setAvailableLocations] = useState([])
  const [metrics, setMetrics] = useState(['temperature_f'])
  const [metricSelect, setMetricSelect] = useState('')
  const [errors, setErrors] = useState({})

  // Fetch available locations on mount
  useEffect(() => {
    fetch('/locations')
      .then((res) => res.json())
      .then((data) => {
        if (data.locations && data.locations.length > 0) {
          setAvailableLocations(data.locations)
          setLocationSelect(data.locations[0])
        }
      })
      .catch(() => {
        // Silently fail — user can still type locations if needed
      })
  }, [])

  function validate() {
    const newErrors = {}

    // Validate start
    if (!start) {
      newErrors.start = 'Start datetime is required.'
    } else if (!isValidRfc3339(start)) {
      newErrors.start = 'Start datetime must be a valid date.'
    }

    // Validate end
    if (!end) {
      newErrors.end = 'End datetime is required.'
    } else if (!isValidRfc3339(end)) {
      newErrors.end = 'End datetime must be a valid date.'
    }

    // Validate end is after start
    if (!newErrors.start && !newErrors.end) {
      const startDate = new Date(start)
      const endDate = new Date(end)
      if (endDate <= startDate) {
        newErrors.end = 'End datetime must be after start datetime.'
      }
    }

    // Validate locations
    if (locations.length === 0) {
      newErrors.locations = 'At least one location is required.'
    }

    // Validate metrics
    if (metrics.length === 0) {
      newErrors.metrics = 'At least one metric is required.'
    }

    return newErrors
  }

  function handleAddLocation() {
    const selected = locationSelect.trim()
    if (!selected) return
    if (locations.includes(selected)) return
    setLocations([...locations, selected])
    // Clear locations error when a location is added
    if (errors.locations) {
      setErrors((prev) => {
        const next = { ...prev }
        delete next.locations
        return next
      })
    }
  }

  function handleRemoveLocation(index) {
    setLocations(locations.filter((_, i) => i !== index))
  }

  function handleAddMetric() {
    const selected = metricSelect.trim()
    if (!selected) return
    if (metrics.includes(selected)) return
    setMetrics([...metrics, selected])
    if (errors.metrics) {
      setErrors((prev) => {
        const next = { ...prev }
        delete next.metrics
        return next
      })
    }
  }

  function handleRemoveMetric(index) {
    setMetrics(metrics.filter((_, i) => i !== index))
  }

  // Determine which metric options are still available to add
  const availableMetricOptions = METRIC_OPTIONS.filter(
    (opt) => !metrics.includes(opt.value)
  )

  // Set default metricSelect to first available option
  useEffect(() => {
    if (availableMetricOptions.length > 0 && !metricSelect) {
      setMetricSelect(availableMetricOptions[0].value)
    } else if (availableMetricOptions.length > 0 && !availableMetricOptions.find(o => o.value === metricSelect)) {
      setMetricSelect(availableMetricOptions[0].value)
    }
  }, [metrics])

  function handleSubmit(e) {
    e.preventDefault()
    const validationErrors = validate()
    setErrors(validationErrors)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    const startRfc = toRfc3339(start)
    const endRfc = toRfc3339(end)

    onSubmit({
      start: startRfc,
      end: endRfc,
      locations,
      metrics
    })
  }

  return (
    <form className="query-form" onSubmit={handleSubmit} noValidate>
      <div className="form-group">
        <label htmlFor="query-start">Start Datetime</label>
        <input
          id="query-start"
          type="datetime-local"
          value={start}
          onChange={(e) => setStart(e.target.value)}
          disabled={disabled}
          aria-invalid={!!errors.start}
          aria-describedby={errors.start ? 'query-start-error' : undefined}
        />
        {errors.start && (
          <div id="query-start-error" className="validation-error" role="alert">
            {errors.start}
          </div>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="query-end">End Datetime</label>
        <input
          id="query-end"
          type="datetime-local"
          value={end}
          onChange={(e) => setEnd(e.target.value)}
          disabled={disabled}
          aria-invalid={!!errors.end}
          aria-describedby={errors.end ? 'query-end-error' : undefined}
        />
        {errors.end && (
          <div id="query-end-error" className="validation-error" role="alert">
            {errors.end}
          </div>
        )}
      </div>

      <div className="form-group locations-group">
        <label htmlFor="query-location-select">Locations</label>
        <div className="location-add-row">
          <select
            id="query-location-select"
            value={locationSelect}
            onChange={(e) => setLocationSelect(e.target.value)}
            disabled={disabled || availableLocations.length === 0}
            aria-describedby={errors.locations ? 'query-locations-error' : undefined}
          >
            {availableLocations.length === 0 && (
              <option value="">Loading...</option>
            )}
            {availableLocations.map((loc) => (
              <option key={loc} value={loc}>
                {loc}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={handleAddLocation}
            disabled={disabled || !locationSelect || locations.includes(locationSelect)}
            aria-label="Add location"
          >
            Add
          </button>
        </div>
        {locations.length > 0 && (
          <ul className="location-list" aria-label="Selected locations">
            {locations.map((loc, index) => (
              <li key={index} className="location-item">
                <span>{loc}</span>
                <button
                  type="button"
                  onClick={() => handleRemoveLocation(index)}
                  disabled={disabled}
                  aria-label={`Remove ${loc}`}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
        {errors.locations && (
          <div id="query-locations-error" className="validation-error" role="alert">
            {errors.locations}
          </div>
        )}
      </div>

      <div className="form-group metrics-group">
        <label htmlFor="query-metric-select">Metrics</label>
        <div className="location-add-row">
          <select
            id="query-metric-select"
            value={metricSelect}
            onChange={(e) => setMetricSelect(e.target.value)}
            disabled={disabled || availableMetricOptions.length === 0}
            aria-describedby={errors.metrics ? 'query-metrics-error' : undefined}
          >
            {availableMetricOptions.length === 0 && (
              <option value="">All metrics selected</option>
            )}
            {availableMetricOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={handleAddMetric}
            disabled={disabled || availableMetricOptions.length === 0}
            aria-label="Add metric"
          >
            Add
          </button>
        </div>
        {metrics.length > 0 && (
          <ul className="location-list" aria-label="Selected metrics">
            {metrics.map((m, index) => {
              const opt = METRIC_OPTIONS.find((o) => o.value === m)
              return (
                <li key={index} className="location-item">
                  <span>{opt ? opt.label : m}</span>
                  <button
                    type="button"
                    onClick={() => handleRemoveMetric(index)}
                    disabled={disabled}
                    aria-label={`Remove ${opt ? opt.label : m}`}
                  >
                    ×
                  </button>
                </li>
              )
            })}
          </ul>
        )}
        {errors.metrics && (
          <div id="query-metrics-error" className="validation-error" role="alert">
            {errors.metrics}
          </div>
        )}
      </div>

      <button
        type="submit"
        className="submit-button"
        disabled={disabled}
      >
        {disabled ? 'Loading...' : 'Query Readings'}
      </button>
    </form>
  )
}

export default QueryForm
