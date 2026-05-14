import { useState } from 'react'
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

function QueryForm({ onSubmit, disabled = false }) {
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [locations, setLocations] = useState([])
  const [locationInput, setLocationInput] = useState('')
  const [metric, setMetric] = useState('temperature_f')
  const [errors, setErrors] = useState({})

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
    } else {
      for (let i = 0; i < locations.length; i++) {
        if (locations[i].length < 1 || locations[i].length > 255) {
          newErrors.locations = 'Each location must be between 1 and 255 characters.'
          break
        }
      }
    }

    return newErrors
  }

  function handleAddLocation() {
    const trimmed = locationInput.trim()
    if (!trimmed) return
    if (trimmed.length > 255) return
    setLocations([...locations, trimmed])
    setLocationInput('')
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

  function handleLocationKeyDown(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleAddLocation()
    }
  }

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
      metric
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
        <label htmlFor="query-location-input">Locations</label>
        <div className="location-add-row">
          <input
            id="query-location-input"
            type="text"
            value={locationInput}
            onChange={(e) => setLocationInput(e.target.value)}
            onKeyDown={handleLocationKeyDown}
            placeholder="Enter location name"
            maxLength={255}
            disabled={disabled}
            aria-describedby={errors.locations ? 'query-locations-error' : undefined}
          />
          <button
            type="button"
            onClick={handleAddLocation}
            disabled={disabled || !locationInput.trim()}
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

      <div className="form-group">
        <label htmlFor="query-metric">Metric</label>
        <select
          id="query-metric"
          value={metric}
          onChange={(e) => setMetric(e.target.value)}
          disabled={disabled}
        >
          <option value="temperature_f">Temperature (°F)</option>
          <option value="humidity_pct">Humidity (%)</option>
        </select>
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
