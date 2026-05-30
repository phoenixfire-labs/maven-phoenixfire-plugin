// When a shared fork crashes, tests in that fork (including failures) are retried in isolation.

import groovy.json.JsonSlurper

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

def slurper = new JsonSlurper()
def report = slurper.parse(json)

assert report.tests.size() == 2 : "Expected two test records"

def pollution = report.tests.find { it.displayName == 'aFailsFromPollution()' }
assert pollution != null : "Pollution test record missing"

assert pollution.finalState == 'PASSED' : "Pollution test should recover, was ${pollution.finalState}"
assert pollution.recovered == true
assert pollution.firstFailLevel == 'SHARED_FORK_POOL'
assert pollution.recoveryLevel == 'FRESH_FORK'
assert pollution.forkReuseSensitive == true
assert pollution.escalationPath == ['SHARED_FORK_POOL', 'FRESH_FORK']

def crasher = report.tests.find { it.displayName == 'zCrashesTheFork()' }
assert crasher != null
assert crasher.finalState == 'PASSED'
assert crasher.recovered == true

return true
