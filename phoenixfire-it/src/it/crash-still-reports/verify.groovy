// Verifies that, despite a forked JVM hard-crash, Phoenixfire produced a complete report in which
// the well-behaved test is PASSED and the crashing test reached a terminal CRASHED state.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text
assert content.contains('"finalState":"PASSED"') : "Expected at least one PASSED test in the report"
assert content.contains('"finalState":"CRASHED"') : "Expected the crashing test to be recorded as CRASHED"

// Every discovered test must be accounted for: no test left NOT_RUN or RUNNING.
assert !content.contains('"finalState":"NOT_RUN"') : "A test was left NOT_RUN - accounting incomplete"
assert !content.contains('"finalState":"RUNNING"') : "A test was left RUNNING - accounting incomplete"

// The crashing test should show an escalation path (more than one isolation level attempted).
assert content.contains('"escalationPath"') : "Escalation path missing from report"

// Surefire-compatible XML must also exist for CI consumption.
File[] xml = reportsDir.listFiles({ d, name -> name.startsWith("TEST-") && name.endsWith(".xml") } as FilenameFilter)
assert xml != null && xml.length > 0 : "No Surefire-compatible TEST-*.xml reports were produced"

return true
