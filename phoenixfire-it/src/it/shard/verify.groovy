// Verifies indexable, deterministic shard-by-class partitioning. With four classes sorted by name
// (AlphaTest, BravoTest, CharlieTest, DeltaTest) assigned round-robin to 2 shards, shard 1 must run
// AlphaTest and CharlieTest, and must NOT run BravoTest or DeltaTest.

File reportsDir = new File(basedir, "target/phoenixfire-reports")
assert reportsDir.isDirectory() : "Phoenixfire reports directory was not created"

File json = new File(reportsDir, "phoenixfire-report.json")
assert json.isFile() : "Native JSON report was not produced"

String content = json.text

assert content.contains('it.AlphaTest') : "AlphaTest belongs to shard 1 and should have run"
assert content.contains('it.CharlieTest') : "CharlieTest belongs to shard 1 and should have run"
assert !content.contains('it.BravoTest') : "BravoTest belongs to shard 2 and must not run here"
assert !content.contains('it.DeltaTest') : "DeltaTest belongs to shard 2 and must not run here"

// The run envelope must record the shard identity for downstream per-node attribution.
assert content.contains('"shard"') : "report should record shard identity in the run envelope"
assert content.contains('"index":1') : "shard index should be 1"
assert content.contains('"count":2') : "shard count should be 2"

// The JSONL fact table should carry shard dimensions on its lines too.
File facts = new File(reportsDir, "phoenixfire-facts.jsonl")
assert facts.isFile() : "JSONL fact table was not produced"
assert facts.text.contains('"shardCount":2') : "facts should carry shardCount for slicing"

return true
