$ErrorActionPreference = 'Stop'
$chapterRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $chapterRoot 'Phan-5-Xay-dung-ung-dung.docx'
$pdf = Join-Path $chapterRoot 'qa/Phan-5-Xay-dung-ung-dung.pdf'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.Options.SaveNormalPrompt = $false
$word.Options.BackgroundSave = $false
Write-Output 'WORD_READY'
$doc = $null
try {
    $doc = $word.Documents.Open($target)
    Write-Output 'DOCUMENT_OPEN'
    $doc.Fields.Update() | Out-Null
    Write-Output 'FIELDS_UPDATED'
    $doc.Repaginate()
    Write-Output 'REPAGINATED'
    $doc.Save()
    Write-Output 'SAVED'
    $doc.ExportAsFixedFormat($pdf,17)
    Write-Output 'PDF_EXPORTED'
    Write-Output ('PAGES=' + $doc.ComputeStatistics(2))
    $doc.Close(0)
    $doc = $null
} finally {
    if ($null -ne $doc) { $doc.Close(0) }
    $word.Quit()
}
