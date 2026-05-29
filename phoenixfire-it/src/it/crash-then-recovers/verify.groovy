// Verifies that a test which crashes on its first attempt but passes on an escalated retry is
// recorded as recovered (flaky), ends in a PASSED final state, and does NOT fail the build by
// default (failOnFlakyTests=false). The build succeeding is asserted via invoker.buildResult.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text

// The recovering test must end PASSED, not CRASHED - it was rescued by a retry.
assert content.contains('"finalState":"PASSED"') : "Expected the recovering test to end PASSED"
assert !content.contains('"finalState":"CRASHED"') : "No test should remain CRASHED after recovery"
assert !content.contains('"finalState":"NOT_RUN"') : "A test was left NOT_RUN - accounting incomplete"
assert !content.contains('"finalState":"RUNNING"') : "A test was left RUNNING - accounting incomplete"

// It must be flagged as recovered/flaky, and the summary flaky count must be at least 1.
assert content.contains('"recovered":true') : "Recovering test was not flagged as recovered"
assert (content =~ /"flaky":\s*([1-9])/).find() : "Summary flaky count should be >= 1"

return true
