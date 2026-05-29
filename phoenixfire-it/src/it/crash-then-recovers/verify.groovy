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

// The native report must carry the run envelope and the fork-reuse diagnosis.
assert content.contains('"run"') : "Run envelope missing from native report"
assert content.contains('"runId"') : "runId missing from run envelope"
assert content.contains('"forkReuseSensitive":true') : "fork-reuse sensitivity not diagnosed"

// The vendor-agnostic JSON Lines fact table must be produced and well-formed.
File facts = new File(reportsDir, "phoenixfire-facts.jsonl")
assert facts.isFile() : "JSON Lines fact table was not produced"

List<String> lines = facts.readLines().findAll { !it.trim().isEmpty() }
assert lines.size() >= 1 : "Fact table is empty"

// First line is the run record; it must carry a runId.
assert lines[0].contains('"type":"run"') : "First fact line is not the run record"
assert lines[0].contains('"runId"') : "Run fact line missing runId"

// Every line must be valid one-line JSON (no embedded newlines breaking records).
def slurper = new groovy.json.JsonSlurper()
lines.each { line -> slurper.parseText(line) }

// There must be a test_result flagged fork-reuse-sensitive, and a crashed attempt under fork reuse.
assert lines.any { it.contains('"type":"test_result"') && it.contains('"forkReuseSensitive":true') } :
        "No fork-reuse-sensitive test_result emitted"
assert lines.any {
    it.contains('"type":"test_attempt"') && it.contains('"forkReuse":true') && it.contains('"outcome":"CRASHED"')
} : "No crashed-under-fork-reuse test_attempt emitted"
assert lines.any { it.contains('"exitCode":137') } : "Crash exit code not captured on an attempt"

return true
