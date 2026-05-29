// Verifies Surefire -Dtest parity:
//  * a class outside the default *Test globs (Checks) is discovered because -Dtest overrides includes;
//  * the #fast* method filter keeps only fastOne/fastTwo and drops slowExcludedByMethodFilter;
//  * SlowTest (default-named, would fail) is never run because -Dtest replaced the includes.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text

assert content.contains('it.Checks') : "Checks should have been discovered via -Dtest override"
assert !content.contains('it.SlowTest') : "SlowTest should have been excluded by -Dtest"
assert content.contains('fastOne') : "fastOne should have run"
assert content.contains('fastTwo') : "fastTwo should have run"
assert !content.contains('slowExcludedByMethodFilter') : "method filter #fast* should have dropped it"

assert content.contains('"finalState":"PASSED"') : "selected methods should have passed"
assert !content.contains('"finalState":"FAILED"') : "no test should have failed"
assert !content.contains('"finalState":"CRASHED"') : "no test should have crashed"

return true
