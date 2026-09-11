# show-folder-tree.ps1
# Displays a tree-like structure of all files and folders in a given directory
# and saves it as plain text to -Out (kept out of git via .git/info/exclude).
#
# Usage: .\scripts\show-folder-tree.ps1 [-Path "C:\Path\To\Directory"] [-Out <file.txt>] [-ShowHidden]
#        -Path defaults to this project's root, whatever the current directory is.
#
# Run:
#   .\scripts\show-folder-tree.ps1

param (
    [Parameter(Mandatory = $false)]
    [string]$Path = (Split-Path $PSScriptRoot -Parent),

    [Parameter(Mandatory = $false)]
    [string]$Out = "folder-tree.txt",

    [Parameter(Mandatory = $false)]
    [switch]$ShowHidden
)

# Define box-drawing characters via Unicode code points
# so the script file stays pure ASCII and encoding never matters
$BRANCH_MID  = [char]0x251C + [char]0x2500 + [char]0x2500 + " "  # ├──
$BRANCH_LAST = [char]0x2514 + [char]0x2500 + [char]0x2500 + " "  # └──
$PIPE        = [char]0x2502 + "   "                               # │
$BLANK       = "    "

# --- Ignore lists ------------------------------------------------------------
$IGNORE = @{
    Dirs  = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            'node_modules', '.git', '.svn', '.hg',
            'dist', 'build', 'out', 'bin', 'obj',
            '.next', '.nuxt', '.vite', '.turbo',
            '__pycache__', '.pytest_cache', '.mypy_cache',
            'venv', '.venv', 'env', '.env',
            'coverage', '.nyc_output',
            '.idea', '.vscode', '.vs',
            'vendor', 'packages', 'bower_components',
            'Migrations', 'migrations',
            'logs', 'tmp', 'temp', '.cache', '.agents', '.claude', '.expo',
            '.gradle', '.kotlin', 'release', '.cxx', 'androidTest', 'test', '.oldgit'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
    Exts  = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            '.user'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
    Files = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            'Cargo.lock'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
}

# Plain-text copy of every rendered line, written to -Out once the tree is done
$treeLines = [System.Collections.Generic.List[string]]::new()

function Show-Tree {
    param (
        [string]$FolderPath,
        [string]$Indent = ""
    )

    $getChildParams = @{
        LiteralPath = $FolderPath
        Force       = $ShowHidden.IsPresent
        ErrorAction = "SilentlyContinue"
    }

    # Folders first, then files — both sorted alphabetically
    $items = Get-ChildItem @getChildParams |
            Sort-Object @{ Expression = { $_.PSIsContainer }; Descending = $true }, Name

    # Filter ignored items before rendering — avoids wasted index/branch logic
    $items = @($items | Where-Object {
        if ($_.PSIsContainer) {
            -not $IGNORE.Dirs.Contains($_.Name)
        } else {
            if ($IGNORE.Files.Contains($_.Name)) { return $false }
            # Never list this script's own output file
            if ($_.FullName -eq $outPath) { return $false }
            $ext = [IO.Path]::GetExtension($_.Name)
            -not ($ext -and $IGNORE.Exts.Contains($ext))
        }
    })

    for ($i = 0; $i -lt $items.Count; $i++) {
        $item   = $items[$i]
        $isLast = ($i -eq $items.Count - 1)

        $branch      = if ($isLast) { $BRANCH_LAST } else { $BRANCH_MID }
        $childIndent = if ($isLast) { "$Indent$BLANK" } else { "$Indent$PIPE" }

        if ($item.PSIsContainer) {
            Write-Host "$Indent$branch" -NoNewline
            Write-Host "$($item.Name)/" -ForegroundColor Cyan
            $treeLines.Add("$Indent$branch$($item.Name)/")
            Show-Tree -FolderPath $item.FullName -Indent $childIndent
        } else {
            Write-Host "$Indent$branch" -NoNewline
            Write-Host $item.Name -ForegroundColor White
            $treeLines.Add("$Indent$branch$($item.Name)")
        }
    }
}

# --- Git exclude -------------------------------------------------------------
# Lists a generated file in the repo's .git/info/exclude -- git's local,
# never-committed ignore list -- so it never shows up as an untracked change
# and no project's .gitignore has to be edited. No-op outside a git repo or
# when the file is already ignored.
function Add-GitExclude {
    param([string]$FilePath)

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return }
    $dir    = Split-Path $FilePath -Parent
    $name   = Split-Path $FilePath -Leaf
    $prefix = git -C $dir rev-parse --show-prefix 2>$null   # $dir relative to the repo root
    # Not a git repo: nothing to exclude, and git's failure code must not outlive this no-op
    if ($LASTEXITCODE -ne 0) { $global:LASTEXITCODE = 0; return }
    git -C $dir check-ignore -q -- $name 2>$null
    if ($LASTEXITCODE -eq 0) { return }

    $exclude   = git -C $dir rev-parse --path-format=absolute --git-path info/exclude
    $existing  = if (Test-Path -LiteralPath $exclude) { [IO.File]::ReadAllText($exclude) } else { '' }
    $separator = if ($existing -and -not $existing.EndsWith("`n")) { "`n" } else { '' }
    New-Item -ItemType Directory -Force (Split-Path $exclude) | Out-Null
    [IO.File]::AppendAllText($exclude, "$separator/$prefix$name`n")
}

# --- Entry point -------------------------------------------------------------

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue

if (-not $resolvedPath) {
    Write-Error "Path not found: $Path"
    exit 1
}

$outPath = if ([IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path (Get-Location) $Out }

$rootName = Split-Path -Leaf $resolvedPath.Path
Write-Host "$rootName/" -ForegroundColor Yellow
$treeLines.Add("$rootName/")

Show-Tree -FolderPath $resolvedPath.Path

[IO.File]::WriteAllText($outPath, ($treeLines -join [Environment]::NewLine), [Text.Encoding]::UTF8)
Add-GitExclude $outPath
Write-Host "`nSaved to: $outPath" -ForegroundColor DarkGray
