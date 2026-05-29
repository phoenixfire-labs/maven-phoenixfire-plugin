// Verifies Surefire-style file-based selection: the excludesFile must drop ExcludedTest entirely
// (it never appears in the report) while IncludedTest runs and passes.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text

assert content.contains('it.IncludedTest') : "IncludedTest should have been discovered and run"
assert !content.contains('it.ExcludedTest') : "ExcludedTest should have been excluded via excludesFile"
assert content.contains('"finalState":"PASSED"') : "IncludedTest should have passed"
assert !content.contains('"finalState":"FAILED"') : "No test should have failed"
assert !content.contains('"finalState":"CRASHED"') : "No test should have crashed"

return true
