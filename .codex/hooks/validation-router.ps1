[CmdletBinding()]
param(
    # Optional synthetic paths for testing the router without editing files.
    [string[]] $ChangedFiles,

    # Print the selected validation plan without running checks.
    [switch] $PlanOnly,

    # Optional JSON result file used by stop-validation.ps1.
    [string] $ResultPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$safeRepoRoot = $repoRoot.Replace('\', '/')
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$result = [ordered]@{
    schemaVersion  = 1
    timestamp      = (Get-Date).ToString('o')
    branch         = $null
    changedFiles   = @()
    testScope      = 'none'
    tests          = @()
    checks         = @()
    gradleCommand  = $null
    logPath        = $null
    status         = 'pending'
    exitCode       = 0
    summary        = $null
}

function Save-ValidationResult {
    if ([string]::IsNullOrWhiteSpace($ResultPath)) {
        return
    }

    $parentDirectory = Split-Path -Parent $ResultPath
    if (-not [string]::IsNullOrWhiteSpace($parentDirectory)) {
        New-Item -ItemType Directory -Path $parentDirectory -Force |
            Out-Null
    }

    $json = $result | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText(
        $ResultPath,
        $json,
        $utf8NoBom
    )
}

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
        $message = @($output | ForEach-Object { $_.ToString() }) `
            -join [Environment]::NewLine

        throw "git $($Arguments -join ' ') failed with exit code $exitCode.`n$message"
    }

    return @($output | ForEach-Object { $_.ToString() })
}

function Add-Tests {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Names
    )

    foreach ($name in $Names) {
        [void] $selectedTests.Add($name)
    }
}

function Get-FlywayInventory {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Vendor
    )

    $migrationDirectory = Join-Path `
        $repoRoot `
        "src\main\resources\db\migration\$Vendor"

    if (-not (Test-Path -LiteralPath $migrationDirectory -PathType Container)) {
        throw "Flyway migration directory not found: $migrationDirectory"
    }

    $inventory = @{}

    $migrationFiles = Get-ChildItem `
        -LiteralPath $migrationDirectory `
        -File `
        -Filter 'V*.sql'

    foreach ($migrationFile in $migrationFiles) {
        if ($migrationFile.Name -notmatch '^V(?<version>[^_]+)__(?<description>.+)\.sql$') {
            throw "Invalid Flyway migration filename: $($migrationFile.FullName)"
        }

        $version = $Matches['version']
        $description = $Matches['description']

        if ($inventory.ContainsKey($version)) {
            throw "Duplicate Flyway version V$version in $Vendor migrations."
        }

        $inventory[$version] = $description
    }

    return $inventory
}

function Test-FlywayMigrations {
    $h2Inventory = Get-FlywayInventory -Vendor 'h2'
    $mysqlInventory = Get-FlywayInventory -Vendor 'mysql'

    $versionDifference = @(
        Compare-Object `
            -ReferenceObject @($h2Inventory.Keys) `
            -DifferenceObject @($mysqlInventory.Keys)
    )

    if ($versionDifference.Count -gt 0) {
        $differenceText = @(
            $versionDifference |
                ForEach-Object {
                    "$($_.InputObject) $($_.SideIndicator)"
                }
        ) -join ', '

        throw "H2 and MySQL Flyway versions differ: $differenceText"
    }

    foreach ($version in $h2Inventory.Keys) {
        if ($h2Inventory[$version] -ne $mysqlInventory[$version]) {
            throw (
                "Flyway V$version filename descriptions differ. " +
                "H2='$($h2Inventory[$version])', " +
                "MySQL='$($mysqlInventory[$version])'."
            )
        }
    }
}

try {
    $branchOutput = @(Invoke-RepoGit -Arguments @('branch', '--show-current'))
    $branch = ($branchOutput -join '').Trim()

    if ([string]::IsNullOrWhiteSpace($branch)) {
        $headOutput = @(Invoke-RepoGit -Arguments @(
            'rev-parse',
            '--short',
            'HEAD'
        ))
        $branch = "detached@$($headOutput -join '')"
    }

    $result.branch = $branch

    if (-not $PSBoundParameters.ContainsKey('ChangedFiles')) {
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

        $ChangedFiles = @($trackedFiles + $untrackedFiles)
    }

    $changedFileList = @(
        $ChangedFiles |
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

    $result.changedFiles = $changedFileList

    if ($changedFileList.Count -eq 0) {
        $result.status = 'skipped'
        $result.summary = 'No relevant repository changes were found.'
        Save-ValidationResult

        Write-Output 'No relevant repository changes were found.'
        exit 0
    }

    $selectedTests = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )

    $runFullSuite = $false
    $runFlywayCheck = $false

    $routes = @(
        @{
            Pattern = '^src/main/java/gdg/sharinglog/rotation/engine/'
            Tests = @(
                'gdg.sharinglog.rotation.engine.RotationAssignmentEngineTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/rotation/recurrence/'
            Tests = @(
                'gdg.sharinglog.rotation.recurrence.RecurrencePeriodCalculatorTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/domain/rotation/'
            Tests = @(
                'gdg.sharinglog.domain.rotation.ChoreOccurrenceStateMachineTest',
                'gdg.sharinglog.domain.rotation.ChoreScheduleValidationTest',
                'gdg.sharinglog.domain.rotation.RotationDecisionLogTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/repository/rotation/'
            Tests = @(
                'gdg.sharinglog.rotation.persistence.RotationPersistenceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/access/'
            Tests = @(
                'gdg.sharinglog.service.rotation.access.RotationActorAccessServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/(api/chore|enrollment)/'
            Tests = @(
                'gdg.sharinglog.service.rotation.api.chore.ChoreApplicationServiceTest',
                'gdg.sharinglog.web.rotation.dto.UpdateChoreRequestTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/api/member/'
            Tests = @(
                'gdg.sharinglog.service.rotation.api.member.ChoreParticipationApplicationServiceTest',
                'gdg.sharinglog.service.rotation.api.member.MemberLeaveApplicationServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/api/notification/'
            Tests = @(
                'gdg.sharinglog.service.rotation.api.notification.NotificationSummaryServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/(api/occurrence|occurrence)/'
            Tests = @(
                'gdg.sharinglog.service.rotation.api.occurrence.OccurrenceQueryServiceTest',
                'gdg.sharinglog.service.rotation.occurrence.ChoreOccurrenceScheduleResolverTest',
                'gdg.sharinglog.service.rotation.occurrence.OccurrenceCommandServiceTest',
                'gdg.sharinglog.service.rotation.occurrence.OccurrenceGenerationServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/(api/substitute|substitute)/'
            Tests = @(
                'gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestApplicationServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/service/rotation/assignment/'
            Tests = @(
                'gdg.sharinglog.rotation.engine.RotationAssignmentEngineTest',
                'gdg.sharinglog.rotation.persistence.RotationPersistenceTest',
                'gdg.sharinglog.service.rotation.assignment.RotationConcurrencyTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/web/rotation/dto/'
            Tests = @(
                'gdg.sharinglog.web.rotation.dto.UpdateChoreRequestTest',
                'gdg.sharinglog.web.rotation.RotationViewMapperTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/web/rotation/error/'
            Tests = @(
                'gdg.sharinglog.web.rotation.error.RotationProblemAdviceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/web/rotation/http/'
            Tests = @(
                'gdg.sharinglog.web.rotation.http.ExpectedVersionTest',
                'gdg.sharinglog.web.rotation.http.IdempotencyKeyTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/web/rotation/RotationViewMapper\.java$'
            Tests = @(
                'gdg.sharinglog.web.rotation.RotationViewMapperTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/domain/booking/'
            Tests = @(
                'gdg.sharinglog.domain.booking.ReservationTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/(service|web|repository)/booking/'
            Tests = @(
                'gdg.sharinglog.domain.booking.ReservationTest',
                'gdg.sharinglog.service.booking.ReservationServiceTest',
                'gdg.sharinglog.service.booking.SpaceServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/(config/oauth/|config/WebOAuthSecurityConfig\.java$|web/(Auth|Login)|service/user/|domain/OAuthProvider\.java$)'
            Tests = @(
                'gdg.sharinglog.AuthProfileApiTest',
                'gdg.sharinglog.LoginFlowTest',
                'gdg.sharinglog.config.WebOAuthSecurityConfigTest',
                'gdg.sharinglog.config.oauth.OAuth2FailureHandlerTest',
                'gdg.sharinglog.config.oauth.OAuth2SuccessHandlerTest',
                'gdg.sharinglog.config.oauth.OAuth2UserCustomServiceTest',
                'gdg.sharinglog.config.oauth.OAuth2UserPersistenceServiceTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/(domain/GroupInvitation|repository/GroupInvitation|service/invitation/|web/(GroupInvitation|Invitation))'
            Tests = @(
                'gdg.sharinglog.GroupInvitationApiTest',
                'gdg.sharinglog.InvitationAcceptanceWebTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/(domain/(GroupMember|SharingGroup|MemberStatus)|repository/(GroupMember|SharingGroup)|service/group/|web/(GroupController|GroupMemberController|dto/))'
            Tests = @(
                'gdg.sharinglog.GroupCreationApiTest',
                'gdg.sharinglog.GroupMemberListApiTest',
                'gdg.sharinglog.MyGroupApiTest',
                'gdg.sharinglog.domain.GroupMemberLifecycleTest',
                'gdg.sharinglog.domain.SharingGroupSchedulePolicyTest'
            )
        },
        @{
            Pattern = '^src/main/java/gdg/sharinglog/config/RotationSchedulingConfig\.java$'
            Tests = @(
                'gdg.sharinglog.service.rotation.occurrence.OccurrenceGenerationServiceTest'
            )
        }
    )

    foreach ($file in $changedFileList) {
        if ($file -match '^src/test/java/(.+Test)\.java$') {
            $absoluteTestPath = Join-Path `
                $repoRoot `
                $file.Replace('/', '\')

            if (Test-Path -LiteralPath $absoluteTestPath -PathType Leaf) {
                $testClass = $Matches[1].Replace('/', '.')
                Add-Tests -Names @($testClass)
            }
            else {
                # Deleted or renamed test classes should compile the whole suite.
                $runFullSuite = $true
            }

            continue
        }

        if ($file -match '^src/test/') {
            $runFullSuite = $true
            continue
        }

        if (
            $file -match '^(build\.gradle(?:\.kts)?|settings\.gradle(?:\.kts)?|gradle\.properties)$' -or
            $file -match '^gradle/wrapper/' -or
            $file -match '^gradlew(?:\.bat)?$' -or
            $file -match '^src/main/resources/application.*\.(?:yml|yaml|properties)$'
        ) {
            $runFullSuite = $true
            continue
        }

        if (
            $file -match '^src/main/resources/db/migration/' -or
            $file -eq 'src/main/java/gdg/sharinglog/config/LegacySchemaFlywayConfiguration.java'
        ) {
            $runFlywayCheck = $true
            Add-Tests -Names @(
                'gdg.sharinglog.config.LegacySchemaFlywayConfigurationTest'
            )
            continue
        }

        $matchedRoute = $false

        foreach ($route in $routes) {
            if ($file -match $route.Pattern) {
                Add-Tests -Names @($route.Tests)
                $matchedRoute = $true
            }
        }

        if (
            $file -match '^src/main/java/.+\.java$' -and
            -not $matchedRoute
        ) {
            # Unknown production Java changes fall back to the full suite.
            $runFullSuite = $true
        }
    }

    if ($selectedTests.Count -gt 12) {
        # A large focused filter is harder to understand than one full suite.
        $runFullSuite = $true
    }

    $sortedTests = @($selectedTests | Sort-Object)

    if ($runFullSuite) {
        $result.testScope = 'full'
        $result.tests = @()
    }
    elseif ($sortedTests.Count -gt 0) {
        $result.testScope = 'focused'
        $result.tests = $sortedTests
    }
    else {
        $result.testScope = 'checks-only'
        $result.tests = @()
    }

    $plannedChecks = @('git diff --check')
    if ($runFlywayCheck) {
        $plannedChecks += 'Flyway H2/MySQL version and filename parity'
    }
    $result.checks = $plannedChecks

    $gradleArguments = @('test', '--console=plain')

    if (-not $runFullSuite) {
        foreach ($testClass in $sortedTests) {
            $gradleArguments += @('--tests', $testClass)
        }
    }

    if ($result.testScope -ne 'checks-only') {
        $result.gradleCommand = (
            '.\gradlew.bat ' + ($gradleArguments -join ' ')
        )
    }

    Write-Output "Branch: $branch"
    Write-Output 'Changed files:'
    $changedFileList | ForEach-Object {
        Write-Output "  - $_"
    }

    Write-Output "Validation scope: $($result.testScope)"
    Write-Output 'Checks:'
    $plannedChecks | ForEach-Object {
        Write-Output "  - $_"
    }

    if ($runFullSuite) {
        Write-Output 'Tests: full Gradle test suite'
    }
    elseif ($sortedTests.Count -gt 0) {
        Write-Output 'Tests:'
        $sortedTests | ForEach-Object {
            Write-Output "  - $_"
        }
    }
    else {
        Write-Output 'Tests: none'
    }

    if ($PlanOnly) {
        $result.status = 'planned'
        $result.summary = 'Validation plan generated; checks were not executed.'
        Save-ValidationResult
        exit 0
    }

    $diffCheckOutput = @(
        & git `
            -c "safe.directory=$safeRepoRoot" `
            -c 'core.quotepath=false' `
            -C $repoRoot `
            diff `
            --check `
            HEAD 2>&1
    )

    $diffCheckExitCode = $LASTEXITCODE

    if ($diffCheckExitCode -ne 0) {
        $diffCheckMessage = @(
            $diffCheckOutput |
                ForEach-Object { $_.ToString() }
        ) -join [Environment]::NewLine

        $result.status = 'failed'
        $result.exitCode = $diffCheckExitCode
        $result.summary = "git diff --check failed.`n$diffCheckMessage"
        Save-ValidationResult

        [Console]::Error.WriteLine($result.summary)
        exit $diffCheckExitCode
    }

    Write-Output 'git diff --check: PASS'

    if ($runFlywayCheck) {
        Test-FlywayMigrations
        Write-Output 'Flyway migration parity: PASS'
    }

    if ($result.testScope -eq 'checks-only') {
        $result.status = 'passed'
        $result.summary = (
            'Repository checks passed. No Gradle tests were selected.'
        )
        Save-ValidationResult

        Write-Output $result.summary
        exit 0
    }

    $javaHomeCandidates = @(
        $env:SHARINGLOG_JAVA_HOME,
        'C:\Users\SAMSUNG\.jdks\corretto-25.0.3'
    )

    $javaHome = $javaHomeCandidates |
        Where-Object {
            -not [string]::IsNullOrWhiteSpace($_) -and
            (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe'))
        } |
        Select-Object -First 1

    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        throw (
            'Java 25 was not found. Set SHARINGLOG_JAVA_HOME or install it at ' +
            'C:\Users\SAMSUNG\.jdks\corretto-25.0.3.'
        )
    }

    $env:JAVA_HOME = $javaHome
    $env:Path = "$(Join-Path $javaHome 'bin');$env:Path"

    $gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Gradle wrapper not found: $gradleWrapper"
    }

    $validationLogDirectory = Join-Path `
        ([System.IO.Path]::GetTempPath()) `
        'sharinglog-codex-validation'

    New-Item `
        -ItemType Directory `
        -Path $validationLogDirectory `
        -Force |
        Out-Null

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $gradleLogPath = Join-Path `
        $validationLogDirectory `
        "gradle-$timestamp.log"

    $result.logPath = $gradleLogPath

    Write-Output "JAVA_HOME: $javaHome"
    Write-Output "Running: $($result.gradleCommand)"

    $gradleOutput = @(
        & $gradleWrapper @gradleArguments 2>&1
    )
    $gradleExitCode = $LASTEXITCODE

    $gradleLogText = @(
        $gradleOutput |
            ForEach-Object { $_.ToString() }
    ) -join [Environment]::NewLine

    [System.IO.File]::WriteAllText(
        $gradleLogPath,
        $gradleLogText,
        $utf8NoBom
    )

    $gradleOutput | ForEach-Object {
        Write-Output $_
    }

    if ($gradleExitCode -ne 0) {
        $result.status = 'failed'
        $result.exitCode = $gradleExitCode
        $result.summary = (
            "Gradle validation failed with exit code $gradleExitCode. " +
            "Log: $gradleLogPath"
        )
        Save-ValidationResult

        [Console]::Error.WriteLine($result.summary)
        exit $gradleExitCode
    }

    $result.status = 'passed'
    $result.exitCode = 0

    if ($runFullSuite) {
        $result.summary = (
            'Full Gradle test suite and repository checks passed.'
        )
    }
    else {
        $result.summary = (
            "Focused Gradle validation passed with " +
            "$($sortedTests.Count) test filter(s)."
        )
    }

    Save-ValidationResult
    Write-Output $result.summary
    Write-Output "Gradle log: $gradleLogPath"
    exit 0
}
catch {
    $result.status = 'failed'
    $result.exitCode = 1
    $result.summary = "Validation router failed: $($_.Exception.Message)"

    Save-ValidationResult
    [Console]::Error.WriteLine($result.summary)
    exit 1
}