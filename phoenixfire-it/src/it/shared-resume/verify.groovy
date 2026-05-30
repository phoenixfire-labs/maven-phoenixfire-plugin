// Verifies the sharedForkPoolMaxPasses resume workflow: a test that crashes once in the shared pool
// is restarted in a FRESH shared-pool fork (not escalated to an isolated fork) and then passes.

import groovy.json.JsonSlurper

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

def slurper = new JsonSlurper()
def report = slurper.parse(json)

assert report.tests.size() == 1 : "Expected exactly one test record"
def t = report.tests[0]

// Recovered, ended PASSED, and - crucially - recovery happened at the SHARED level: the test was
// restarted in a fresh shared-pool fork rather than escalated to an isolated one.
assert t.finalState == 'PASSED' : "Expected the resuming test to end PASSED, was ${t.finalState}"
assert t.recovered == true : "Resuming test should be flagged recovered"
assert t.recoveryLevel == 'SHARED_FORK_POOL' : "Recovery should be at SHARED_FORK_POOL, was ${t.recoveryLevel}"
assert t.escalationPath == ['SHARED_FORK_POOL', 'SHARED_FORK_POOL'] :
        "Both attempts should be at the shared level, was ${t.escalationPath}"
assert t.attempts.every { it.isolationLevel == 'SHARED_FORK_POOL' } : "No attempt should have been isolated"

// Cross-check the JSONL fact table: exactly two attempts, both in the shared pool, crash then pass.
File facts = new File(reportsDir, "phoenixfire-facts.jsonl")
assert facts.isFile() : "JSON Lines fact table was not produced"
def attempts = facts.readLines().findAll { it.contains('"type":"test_attempt"') }.collect { slurper.parseText(it) }
assert attempts.size() == 2 : "Expected exactly two attempts (crash then shared resume)"
assert attempts.every { it.isolation == 'SHARED_FORK_POOL' } : "Both attempts must be in the shared pool"
assert attempts.any { it.outcome == 'CRASHED' } : "First attempt should be a crash"
assert attempts.any { it.outcome == 'PASSED' } : "Resumed attempt should pass"

return true
