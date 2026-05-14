$mvnPath = (Get-ChildItem "C:\Users\walke\.m2\wrapper\dists" -Recurse -Filter "mvn.cmd" | Select-Object -First 1).FullName
& $mvnPath test "-Dtest=DuplicateFilterCompletenessPropertyTest" "-Dsurefire.failIfNoSpecifiedTests=false" "--no-transfer-progress"
