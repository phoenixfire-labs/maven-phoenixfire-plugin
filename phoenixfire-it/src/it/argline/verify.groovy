// Verifies Surefire argLine parity: quote-aware tokenisation (a value with a space stays one
// argument) and @{property} late expansion (resolved from a build-time property). If either failed,
// ArgLineTest's assertions would fail and the build would not be SUCCESS.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text
assert content.contains('it.ArgLineTest') : "ArgLineTest should have run"
assert content.contains('"finalState":"PASSED"') : "argLine assertions should have passed"
assert !content.contains('"finalState":"FAILED"') : "no test should have failed"
assert !content.contains('"finalState":"CRASHED"') : "no test should have crashed"

return true
