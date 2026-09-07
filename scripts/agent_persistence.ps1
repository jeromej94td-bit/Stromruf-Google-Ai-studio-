param(
    [ValidateSet('Branch', 'Main')]
    [string]$Mode = 'Branch',
    [string]$CommitSha,
    [string]$Remote = 'origin',
    [string]$BaseBranch = 'main'
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
    $output = & git @GitArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed:`n$($output -join "`n")"
    }
    return $output
}

$root = (Invoke-Git rev-parse --show-toplevel | Select-Object -First 1).Trim()
Set-Location $root

$diffCheck = & git diff --check 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed:`n$($diffCheck -join "`n")"
}

if ($Mode -eq 'Branch') {
    $branch = (Invoke-Git branch --show-current | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($branch) -or $branch -eq $BaseBranch) {
        throw "Persistence verification requires a named feature/fix branch, not '$BaseBranch'."
    }

    $trackedDirty = & git status --porcelain --untracked-files=no
    if ($trackedDirty) {
        throw "Tracked changes remain uncommitted:`n$($trackedDirty -join "`n")"
    }

    Invoke-Git fetch $Remote $branch | Out-Null
    $localSha = (Invoke-Git rev-parse HEAD | Select-Object -First 1).Trim()
    $remoteSha = (Invoke-Git rev-parse "$Remote/$branch" | Select-Object -First 1).Trim()
    if ($localSha -ne $remoteSha) {
        throw "Local HEAD $localSha is not persisted at $Remote/$branch ($remoteSha)."
    }

    Write-Output "PERSISTED branch=$branch sha=$localSha"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($CommitSha)) {
    throw '-CommitSha is required in Main mode.'
}

Invoke-Git fetch $Remote $BaseBranch | Out-Null
& git merge-base --is-ancestor $CommitSha "$Remote/$BaseBranch"
if ($LASTEXITCODE -ne 0) {
    throw "Commit $CommitSha is not contained in $Remote/$BaseBranch."
}

$mainSha = (Invoke-Git rev-parse "$Remote/$BaseBranch" | Select-Object -First 1).Trim()
Write-Output "DURABLE main=$mainSha contains=$CommitSha"
