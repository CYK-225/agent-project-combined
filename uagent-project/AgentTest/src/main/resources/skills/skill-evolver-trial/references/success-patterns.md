# Success Patterns Catalog

## Pattern: Explore-First
- **Steps**: list_trial_files → read key files → understand structure → then act
- **Why it works**: Agent builds mental model before making changes, reduces blind errors
- **Expected trace**: First 2-3 entries are tool calls with `list_trial_files` and file reads

## Pattern: Incremental Execution
- **Steps**: Make small change → execute → verify → adjust → repeat
- **Why it works**: Errors caught early, easier to debug, less wasted work
- **Expected trace**: Alternating `write_trial_file` and `execute_trial_task` entries

## Pattern: Explicit Verification
- **Steps**: Execute task → capture output → compare against expected → call verify_trial_result
- **Why it works**: No assumptions about success, provides evidence for pass/fail decision
- **Expected trace**: Last entry before `verify_trial_result` shows output comparison

## Pattern: Error Recovery
- **Steps**: Execute → fail → read error → diagnose → fix → retry
- **Why it works**: Iterative debugging mirrors human problem-solving
- **Expected trace**: Multiple `execute_trial_task` entries with improving results

## Pattern: Idempotent Scripts
- **Steps**: Write script that produces same output regardless of starting state
- **Why it works**: Deterministic results, easy to verify, no hidden state dependencies
- **Expected trace**: Single execution with clean exitCode=0

## Common Task Templates

### Python Script Task
1. `list_trial_files("")` — see workspace
2. `write_trial_file("solve.py", code)` — create solution
3. `execute_trial_task("python solve.py")` — run it
4. `verify_trial_result()` — check output

### File Processing Task
1. `list_trial_files("")` — see input files
2. `read_trial_file("input.txt")` — understand input
3. `write_trial_file("process.py", code)` — create processor
4. `execute_trial_task("python process.py")` — process
5. `read_trial_file("output.txt")` — verify result
6. `verify_trial_result()` — formal check
