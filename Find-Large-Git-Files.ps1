$ErrorActionPreference = 'Stop'
$thresholdGB = 0.1
$thresholdBytes = [int64]($thresholdGB * 1GB)

try {
    $root = (& git rev-parse --show-toplevel 2>$null | Select-Object -First 1)
    if (-not $root) { throw 'Folder ini bukan repository Git. Jalankan skrip dari dalam repository.' }
    $root = [System.IO.Path]::GetFullPath($root.Trim())
    Set-Location -LiteralPath $root
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host 'Tekan Enter untuk keluar'
    exit 1
}

$gitignore = Join-Path $root '.gitignore'
$report = Join-Path $root 'large-files-report.txt'
$scriptCmd = Join-Path $PSScriptRoot 'Find-Large-Git-Files.cmd'
$scriptPs1 = Join-Path $PSScriptRoot 'Find-Large-Git-Files.ps1'

Write-Host ''
Write-Host "Repository: $root" -ForegroundColor Cyan
Write-Host 'Mencari file lebih besar dari 100 MB...' -ForegroundColor Cyan
Write-Host 'Folder .git dikecualikan. Skrip tidak menghapus file.'

$found = @(
    Get-ChildItem -LiteralPath $root -Recurse -File -Force -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Length -gt $thresholdBytes -and
        $_.FullName -notmatch '[\\/]\.git([\\/]|$)' -and
        $_.FullName -ne $report -and
        $_.FullName -ne $scriptCmd -and
        $_.FullName -ne $scriptPs1
    } |
    ForEach-Object {
        $relative = $_.FullName.Substring($root.TrimEnd('\','/').Length).TrimStart('\','/') -replace '\\','/'
        [PSCustomObject]@{
            Relative = $relative
            FullName = $_.FullName
            Bytes = [int64]$_.Length
            SizeMB = [math]::Round($_.Length / 1MB, 2)
        }
    } | Sort-Object Bytes -Descending
)

$reportLines = @(
    'Laporan file lebih besar dari 100 MB'
    "Repository: $root"
    'Ambang batas: 100 MB'
    ''
)
if ($found.Count -eq 0) {
    Write-Host 'Tidak ditemukan file lebih besar dari 100 MB.' -ForegroundColor Yellow
    $reportLines += 'Tidak ditemukan file lebih besar dari 100 MB.'
} else {
    Write-Host "Ditemukan $($found.Count) file:" -ForegroundColor Green
    foreach ($item in $found) {
        Write-Host ('[{0,8} MB] {1}' -f $item.SizeMB, $item.Relative)
        $reportLines += ('[{0,8} MB] {1}' -f $item.SizeMB, $item.Relative)
    }

    $existing = @()
    if (Test-Path -LiteralPath $gitignore) {
        $existing = @(Get-Content -LiteralPath $gitignore -ErrorAction SilentlyContinue)
    }
    $added = 0
    foreach ($item in $found) {
        # Ignore this exact repository-relative path. Leading slash anchors it to repository root.
        $pattern = '/' + $item.Relative
        if ($existing -notcontains $pattern) {
            Add-Content -LiteralPath $gitignore -Value $pattern -Encoding UTF8
            $existing += $pattern
            $added++
        }
    }
    Write-Host ''
    Write-Host "$added aturan baru ditambahkan ke .gitignore." -ForegroundColor Green
    $reportLines += ''
    $reportLines += "Aturan .gitignore baru ditambahkan: $added"
    $reportLines += 'Catatan: .gitignore tidak menghentikan pelacakan file yang sudah pernah di-commit.'
}

Set-Content -LiteralPath $report -Value $reportLines -Encoding UTF8
Write-Host "Laporan disimpan: $report" -ForegroundColor Cyan
Write-Host ''
Write-Host 'Catatan: .gitignore hanya mencegah file yang belum dilacak masuk ke commit berikutnya.' -ForegroundColor Yellow
Read-Host 'Tekan Enter untuk keluar'
