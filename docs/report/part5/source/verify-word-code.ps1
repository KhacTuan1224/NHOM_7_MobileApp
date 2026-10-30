$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$qaRoot = Join-Path $repoRoot 'build/chapter5-code-qa'
New-Item -ItemType Directory -Path $qaRoot -Force | Out-Null
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.Options.SaveNormalPrompt = $false
$word.Options.BackgroundSave = $false
try {
    foreach ($entry in @(
        @{ Path = 'docs/report/part5/Phan-5-Xay-dung-ung-dung.docx'; Pdf = 'chapter5.pdf' },
        @{ Path = 'docs/report/report.docx'; Pdf = 'report.pdf' }
    )) {
        $document = $null
        try {
            $document = $word.Documents.Open((Join-Path $repoRoot $entry.Path))
            $document.Fields.Update() | Out-Null
            foreach ($toc in $document.TablesOfContents) { $toc.Update() }
            $document.Repaginate()
            $document.Save()
            $document.ExportAsFixedFormat((Join-Path $qaRoot $entry.Pdf), 17)
            Write-Output ($entry.Path + ': PAGES=' + $document.ComputeStatistics(2))
        } finally {
            if ($null -ne $document) { $document.Close(0) }
        }
    }
} finally { $word.Quit() }
