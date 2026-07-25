# Skill-Evolver-Trial Reference Index

This skill includes the following reference materials:

## Reference Documents

| Document | Description |
|----------|-------------|
| [failure-patterns.md](failure-patterns.md) | Common failure patterns in trial execution and how to avoid them |
| [success-patterns.md](success-patterns.md) | Proven success patterns and task templates for trial execution |

## Scripts

| Script | Description |
|--------|-------------|
| [task_template.py](../scripts/task_template.py) | Python task template with boilerplate for trial execution |

## Quick Reference

### Tool Summary
1. `list_trial_files(path)` — List workspace contents (always call first)
2. `read_trial_file(path)` — Read a file from workspace
3. `write_trial_file(path, content)` — Create or update a file
4. `execute_trial_task(command)` — Run a shell command
5. `verify_trial_result()` — Verify execution outcome

### Recommended Flow
```
list_trial_files → read context → write solution → execute → verify
```
