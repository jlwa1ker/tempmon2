@echo off
set "PATH=C:\Users\walke\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e100c20c\bin;%PATH%"
mvn test -Dtest=com.tempmon.filter.PayloadSizeFilterTest -Dsurefire.useFile=false -Dmaven.compiler.testExcludes=**/DuplicateFilterTest.java
