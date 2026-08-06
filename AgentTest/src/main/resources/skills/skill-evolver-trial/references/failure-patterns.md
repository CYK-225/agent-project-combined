# Failure Patterns Catalog

## Pattern: Skip Exploration
- **Symptom**: Agent jumps directly to execution without understanding the workspace
- **Root Cause**: No systematic exploration step in the trial protocol
- **Fix**: Mandate `list_trial_files` as the first action in every trial
- **Detection**: First tool call is not `list_trial_files`

## Pattern: Silent Failure
- **Symptom**: Command returns exitCode=0 but output is wrong or empty
- **Root Cause**: Script error output goes to stderr, agent only checks stdout
- **Fix**: Capture both stdout and stderr, check stderr for warnings
- **Detection**: exitCode=0 but output length < expected minimum

## Pattern: Wrong Working Directory
- **Symptom**: FileNotFoundError or "No such file or directory"
- **Root Cause**: Agent assumes absolute paths or wrong CWD
- **Fix**: Always use `list_trial_files` to confirm paths before execution
- **Detection**: stderr contains "FileNotFoundError" or "No such file"

## Pattern: Encoding Error
- **Symptom**: UnicodeDecodeError or garbled output
- **Root Cause**: Default encoding mismatch (especially on Windows)
- **Fix**: Use `encoding='utf-8'` explicitly in Python scripts
- **Detection**: stderr contains "UnicodeDecodeError" or "UnicodeEncodeError"

## Pattern: Timeout
- **Symptom**: Trial exceeds time limit
- **Root Cause**: Infinite loop or waiting for input in script
- **Fix**: Add timeout to execute_trial_task, use non-interactive flags
- **Detection**: No result after 60 seconds

## Pattern: Partial Output
- **Symptom**: Output truncated, verification fails on missing content
- **Root Cause**: Large output exceeds buffer
- **Fix**: Write output to file instead of stdout, verify file content
- **Detection**: output ends mid-sentence or has unexpected truncation marker
