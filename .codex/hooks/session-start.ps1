$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

$eventJson = [Console]::In.ReadToEnd()
$eventData = $eventJson | ConvertFrom-Json

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$safeRepoRoot = $repoRoot.Replace('\', '/')

$branch = (
    & git -c "safe.directory=$safeRepoRoot" `
        -C $repoRoot branch --show-current
).Trim()

$changes = @(
    & git -c "safe.directory=$safeRepoRoot" `
        -C $repoRoot status --short
)

if ($changes.Count -eq 0) {
    $workingTreeState = "clean"
} else {
    $workingTreeState = "$($changes.Count) changed path(s)"
}

Write-Output @"
Sharing Log repository context:
- Git root: $repoRoot
- Current branch: $branch
- Working tree: $workingTreeState
- Preserve existing user changes before editing.
- Check the current branch and status before code work.
- Report the branch name after code work.
- This Gradle project requires Java 25. Use gradlew.bat and run focused tests for affected code.
"@
