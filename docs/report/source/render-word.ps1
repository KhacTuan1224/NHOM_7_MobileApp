$ErrorActionPreference = 'Stop'
$reportRoot = Split-Path -Parent $PSScriptRoot
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open((Join-Path $reportRoot 'report.docx'))
    $doc.Fields.Update() | Out-Null
    foreach ($styleName in @('TOC 1','TOC 2','Table of Figures')) {
        try {
            $style = $doc.Styles.Item($styleName)
            $style.Font.Name = 'Times New Roman'
            $style.Font.Size = 10.5
            $style.ParagraphFormat.SpaceAfter = 1
            $style.ParagraphFormat.SpaceBefore = 0
            $style.ParagraphFormat.LineSpacingRule = 0
            $style.ParagraphFormat.Alignment = 0
        } catch { Write-Output "Optional generated style unavailable: $styleName" }
    }
    foreach ($toc in $doc.TablesOfContents) { $toc.Update() }
    foreach ($list in $doc.TablesOfFigures) { $list.Update() }
    $doc.Repaginate()
    $doc.Fields.Update() | Out-Null
    $doc.Save()
    $doc.ExportAsFixedFormat((Join-Path $reportRoot 'report.pdf'),17)
    Write-Output ('PAGES=' + $doc.ComputeStatistics(2))
    $doc.Close(0)
} finally { $word.Quit() }
