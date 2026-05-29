// Happy path: all tests pass, the build succeeds, and every test is accounted for as PASSED.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text
assert content.contains('"total":3') : "Expected 3 discovered tests, report was: " + content
assert content.contains('"passed":3') : "Expected all 3 tests to pass"
assert !content.contains('"finalState":"FAILED"') : "Unexpected failure recorded"
assert !content.contains('"finalState":"CRASHED"') : "Unexpected crash recorded"

return true
