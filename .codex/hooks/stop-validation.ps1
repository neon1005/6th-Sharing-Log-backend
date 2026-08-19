[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

function Write-HookJson {
    param(
        [Parameter(Mandatory = $true)]
        $Value
    )

    $json = $Value | ConvertTo-Json -Depth 10 -Compress
    [Console]::Out.WriteLine($json)
}

function Get-ObjectProperty {
    param(
        [Parameter(Mandatory = $true)]
        $Object,

        [Parameter(Mandatory = $true)]
        [string] $Name,

        $DefaultValue
    )

    if ($null -eq $Object) {
        return $DefaultValue
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $DefaultValue
    }

    return $property.Value
}

function Get-TextHash {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Text
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()

    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hashBytes = $sha256.ComputeHash($bytes)

        return (
            [System.BitConverter]::ToString($hashBytes)
        ).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha256.Dispose()
    }
}

$eventData = $null
$stopHookActive = $false

try {
    $eventJson = [Console]::In.ReadToEnd()

    if ([string]::IsNullOrWhiteSpace($eventJson)) {
        $eventData = [pscustomobject]@{}
    }
    else {
        $eventData = $eventJson | ConvertFrom-Json
    }

    $stopHookActive = [bool](
        Get-ObjectProperty `
            -Object $eventData `
            -Name 'stop_hook_active' `
            -DefaultValue $false
    )

    $sessionId = [string](
        Get-ObjectProperty `
            -Object $eventData `
            -Name 'session_id' `
            -DefaultValue 'unknown-session'
    )

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $safeRepoRoot = $repoRoot.Replace('\', '/')

    function Invoke-RepoGit {
        param(
            [Parameter(Mandatory = $true)]
            [string[]] $Arguments
        )

        $output = @(
            & git `
                -c "safe.directory=$safeRepoRoot" `
                -c 'core.quotepath=false' `
                -C $repoRoot `
                @Arguments 2>&1
        )

        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            $message = @(
                $output |
                    ForEach-Object { $_.ToString() }
            ) -join [Environment]::NewLine

            throw (
                "git $($Arguments -join ' ') failed with " +
                "exit code $exitCode.`n$message"
            )
        }

        return @($output | ForEach-Object { $_.ToString() })
    }

    $trackedFiles = @(Invoke-RepoGit -Arguments @(
        'diff',
        '--name-only',
        '--diff-filter=ACMRTUXB',
        'HEAD'
    ))

    $untrackedFiles = @(Invoke-RepoGit -Arguments @(
        'ls-files',
        '--others',
        '--exclude-standard'
    ))

    $changedFiles = @(
        $trackedFiles + $untrackedFiles |
            ForEach-Object {
                if ($null -ne $_) {
                    $_.Trim().Replace('\', '/')
                }
            } |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace($_)
            } |
            Where-Object {
                $_ -notlike '.codex/*' -and
                $_ -notlike '.gradle/*' -and
                $_ -notlike 'build/*'
            } |
            Sort-Object -Unique
    )

    if ($changedFiles.Count -eq 0) {
        Write-HookJson -Value ([ordered]@{
            continue = $true
        })
        exit 0
    }

    $snapshotParts = [System.Collections.Generic.List[string]]::new()

    $head = @(
        Invoke-RepoGit -Arguments @('rev-parse', 'HEAD')
    ) -join ''

    $snapshotParts.Add("HEAD=$head")

    foreach ($file in $changedFiles) {
        $statusOutput = @(
            Invoke-RepoGit -Arguments @(
                'status',
                '--porcelain=v1',
                '--untracked-files=all',
                '--',
                $file
            )
        ) -join "`n"

        $absolutePath = Join-Path $repoRoot $file.Replace('/', '\')

        if (Test-Path -LiteralPath $absolutePath -PathType Leaf) {
            $contentHash = (
                Get-FileHash `
                    -LiteralPath $absolutePath `
                    -Algorithm SHA256
            ).Hash.ToLowerInvariant()
        }
        else {
            $contentHash = 'missing'
        }

        $snapshotParts.Add(
            "$statusOutput|$file|$contentHash"
        )
    }

    $snapshotHash = Get-TextHash -Text (
        $snapshotParts -join "`n"
    )

    $repositoryKey = (
        Get-TextHash -Text $repoRoot.ToLowerInvariant()
    ).Substring(0, 16)

    $sessionKey = (
        Get-TextHash -Text $sessionId
    ).Substring(0, 16)

    $stateDirectory = Join-Path `
        ([System.IO.Path]::GetTempPath()) `
        'sharinglog-codex-validation'

    New-Item `
        -ItemType Directory `
        -Path $stateDirectory `
        -Force |
        Out-Null

    $stampPath = Join-Path `
        $stateDirectory `
        "stamp-$repositoryKey-$sessionKey-$snapshotHash.json"

    if (Test-Path -LiteralPath $stampPath -PathType Leaf) {
        try {
            $existingStamp = Get-Content `
                -LiteralPath $stampPath `
                -Raw |
                ConvertFrom-Json

            if ($null -ne $existingStamp) {
                Write-HookJson -Value ([ordered]@{
                    continue = $true
                })
                exit 0
            }
        }
        catch {
            # Invalid cache files are ignored and overwritten below.
        }
    }

    $routerPath = Join-Path `
        $PSScriptRoot `
        'validation-router.ps1'

    if (-not (Test-Path -LiteralPath $routerPath -PathType Leaf)) {
        throw "Validation router not found: $routerPath"
    }

    $runToken = (
        "$repositoryKey-$sessionKey-" +
        "$($snapshotHash.Substring(0, 16))-" +
        "$([System.Guid]::NewGuid().ToString('N'))"
    )

    $resultPath = Join-Path `
        $stateDirectory `
        "result-$runToken.json"

    $standardOutputPath = Join-Path `
        $stateDirectory `
        "router-$runToken.stdout.log"

    $standardErrorPath = Join-Path `
        $stateDirectory `
        "router-$runToken.stderr.log"

    $powerShellExecutable = (Get-Process -Id $PID).Path

    if (
        [string]::IsNullOrWhiteSpace($powerShellExecutable) -or
        -not (Test-Path -LiteralPath $powerShellExecutable -PathType Leaf)
    ) {
        $powerShellExecutable = 'powershell.exe'
    }

    $argumentLine = (
        '-NoProfile -ExecutionPolicy Bypass ' +
        '-File "{0}" -ResultPath "{1}"'
    ) -f $routerPath, $resultPath

    $process = Start-Process `
        -FilePath $powerShellExecutable `
        -ArgumentList $argumentLine `
        -RedirectStandardOutput $standardOutputPath `
        -RedirectStandardError $standardErrorPath `
        -WindowStyle Hidden `
        -Wait `
        -PassThru

    $routerExitCode = $process.ExitCode
    $validationResult = $null

    if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
        try {
            $validationResult = Get-Content `
                -LiteralPath $resultPath `
                -Raw |
                ConvertFrom-Json
        }
        catch {
            $validationResult = $null
        }
    }

    if ($null -eq $validationResult) {
        $validationResult = [pscustomobject]@{
            status        = 'failed'
            testScope     = 'unknown'
            tests         = @()
            summary       = (
                'The validation router did not produce a valid result file.'
            )
            gradleCommand = $null
            logPath       = $null
        }
    }

    $status = [string](
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'status' `
            -DefaultValue 'failed'
    )

    $testScope = [string](
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'testScope' `
            -DefaultValue 'unknown'
    )

    $summary = [string](
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'summary' `
            -DefaultValue 'No validation summary was produced.'
    )

    $selectedTests = @(
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'tests' `
            -DefaultValue @()
    )

    $gradleCommand = [string](
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'gradleCommand' `
            -DefaultValue ''
    )

    $gradleLogPath = [string](
        Get-ObjectProperty `
            -Object $validationResult `
            -Name 'logPath' `
            -DefaultValue ''
    )

    if ($routerExitCode -ne 0 -and $status -eq 'passed') {
        $status = 'failed'
        $summary = (
            "Router exited with code $routerExitCode despite a passed result."
        )
    }

    $stamp = [ordered]@{
        schemaVersion      = 1
        timestamp          = (Get-Date).ToString('o')
        sessionId          = $sessionId
        repository         = $repoRoot
        snapshotHash       = $snapshotHash
        changedFiles       = $changedFiles
        routerExitCode     = $routerExitCode
        validationStatus   = $status
        validationScope    = $testScope
        resultPath         = $resultPath
        standardOutputPath = $standardOutputPath
        standardErrorPath  = $standardErrorPath
        gradleLogPath      = $gradleLogPath
    }

    $stampJson = $stamp | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText(
        $stampPath,
        $stampJson,
        $utf8NoBom
    )

    if ($testScope -eq 'full') {
        $testDescription = 'Full Gradle test suite'
    }
    elseif ($testScope -eq 'focused') {
        $testPreview = @(
            $selectedTests |
                Select-Object -First 8
        ) -join ', '

        if ($selectedTests.Count -gt 8) {
            $testPreview += ', ...'
        }

        $testDescription = "Focused tests: $testPreview"
    }
    elseif ($testScope -eq 'checks-only') {
        $testDescription = 'Repository checks only; no Gradle test selected'
    }
    else {
        $testDescription = 'Validation scope unknown'
    }

    $changedFilePreview = @(
        $changedFiles |
            Select-Object -First 8
    ) -join ', '

    if ($changedFiles.Count -gt 8) {
        $changedFilePreview += ', ...'
    }

    if ($status -eq 'passed') {
        $nextAction = (
            'Include the passed scope, untested scope, current branch, and ' +
            'deployment status in the final response.'
        )
    }
    elseif ($status -eq 'skipped') {
        $nextAction = (
            'State why validation was skipped in the final response.'
        )
    }
    else {
        $nextAction = (
            'Inspect the logs, fix the failure when it is in scope, rerun ' +
            'validation after edits, or report the exact unresolved failure.'
        )
    }

    $reasonLines = @(
        'Sharing Log validation router completed.',
        "Status: $status",
        "Summary: $summary",
        "Changed files: $changedFilePreview",
        "Scope: $testDescription"
    )

    if (-not [string]::IsNullOrWhiteSpace($gradleCommand)) {
        $reasonLines += "Command: $gradleCommand"
    }

    if (-not [string]::IsNullOrWhiteSpace($gradleLogPath)) {
        $reasonLines += "Gradle log: $gradleLogPath"
    }

    $reasonLines += "Router stdout: $standardOutputPath"
    $reasonLines += "Router stderr: $standardErrorPath"
    $reasonLines += $nextAction

    Write-HookJson -Value ([ordered]@{
        decision = 'block'
        reason   = ($reasonLines -join "`n")
    })

    exit 0
}
catch {
    $failureMessage = (
        "Sharing Log validation hook failed: $($_.Exception.Message)"
    )

    if ($stopHookActive) {
        # Avoid trapping the turn in an infinite Stop-hook loop.
        Write-HookJson -Value ([ordered]@{
            continue      = $true
            systemMessage = $failureMessage
        })
    }
    else {
        Write-HookJson -Value ([ordered]@{
            decision = 'block'
            reason   = (
                "$failureMessage`n" +
                'Inspect the hook configuration and report that automatic ' +
                'validation could not be completed.'
            )
        })
    }

    exit 0
}
