# Diagnostics

## CloudWatch Logs

The tempmon2 service logs to CloudWatch under the log group `/ecs/tempmon2`.

### Query for recent errors

```powershell
$startTime = [DateTimeOffset]::UtcNow.AddHours(-2).ToUnixTimeMilliseconds()
aws logs filter-log-events --log-group-name "/ecs/tempmon2" --filter-pattern "ERROR" --start-time $startTime --limit 20 --query "events[*].message" --output json
```

### Query for stack traces (follows ERROR lines)

```powershell
$startTime = [DateTimeOffset]::UtcNow.AddHours(-1).ToUnixTimeMilliseconds()
aws logs filter-log-events --log-group-name "/ecs/tempmon2" --filter-pattern "Exception" --start-time $startTime --limit 20 --query "events[*].message" --output json
```

### Query all recent logs (unfiltered)

```powershell
$startTime = [DateTimeOffset]::UtcNow.AddMinutes(-10).ToUnixTimeMilliseconds()
aws logs filter-log-events --log-group-name "/ecs/tempmon2" --start-time $startTime --limit 50 --query "events[*].message" --output json
```

## Diagnosing Ingest Failures

Test the `/ingest` endpoint locally:

```powershell
Invoke-RestMethod -Method POST -Uri "http://tempmon.walkerweb.us/ingest" -ContentType "application/json" -Body '{"readings":[{"timestamp":"2025-06-01T12:00:00+00:00","temperature_f":72.5,"humidity_pct":45.0,"location":"feather-m0-01"}]}'
```

To capture error response bodies from non-2xx responses:

```powershell
try {
    Invoke-WebRequest -Method POST -Uri "http://tempmon.walkerweb.us/ingest" -ContentType "application/json" -Body '{"readings":[...]}'
} catch {
    $_.Exception.Response.StatusCode
    $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $reader.ReadToEnd()
}
```

## ECS Service

The service runs on AWS ECS (Fargate). Check service status:

```powershell
aws ecs describe-services --cluster default --services tempmon2 --query "services[0].{status:status,running:runningCount,desired:desiredCount}" --output table
```
