$ErrorActionPreference = 'Stop'
$chapterRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$qaRoot = Join-Path $repoRoot 'build/chapter5-preserved-qa'
New-Item -ItemType Directory -Path $qaRoot -Force | Out-Null
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.Options.SaveNormalPrompt = $false
$word.Options.BackgroundSave = $false
$document = $null
try {
    $document = $word.Documents.Open((Join-Path $PSScriptRoot 'chapter5-preserved.html'))
    $document.PageSetup.PaperSize = 7
    $document.PageSetup.TopMargin = 51
    $document.PageSetup.BottomMargin = 51
    $document.PageSetup.LeftMargin = 56.7
    $document.PageSetup.RightMargin = 42.5
    foreach ($paragraph in $document.Paragraphs) {
        $text = $paragraph.Range.Text.Trim()
        if ($text.StartsWith('Mã nguồn 5.')) { $paragraph.Format.KeepWithNext = -1 }
        if ($text.StartsWith('Nguồn:')) { $paragraph.Format.KeepWithNext = -1 }
        if ($paragraph.Range.Font.Name -eq 'Consolas') { $paragraph.Format.KeepTogether = -1 }
    }
    $document.SaveAs2((Join-Path $chapterRoot 'Phan-5-Xay-dung-ung-dung.docx'), 16)
    $document.Close(0)
    $document = $null
    $python = 'C:\Program Files\WindowsApps\OpenAI.CodexPrimaryRuntime.v26-1025-760-0_26.1025.760.0_x64__3k8sg7r9htsxt\dependencies\python\python.exe'
    & $python (Join-Path $PSScriptRoot 'format_preserved_docx.py')
    if ($LASTEXITCODE -ne 0) { throw 'Chapter image/code formatting failed.' }
    $document = $word.Documents.Open((Join-Path $chapterRoot 'Phan-5-Xay-dung-ung-dung.docx'))
    $document.Repaginate()
    $document.ExportAsFixedFormat((Join-Path $qaRoot 'chapter5.pdf'), 17)
    Write-Output ('CHAPTER5_PAGES=' + $document.ComputeStatistics(2))
    $document.Save()
} finally {
    if ($null -ne $document) { $document.Close(0) }
    $word.Quit()
}
