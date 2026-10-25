$ErrorActionPreference = 'Stop'
$probe = [Runtime.InteropServices.Marshal]::GetActiveObject('Word.Application')
Write-Output ('VERSION=' + $probe.Version)
Write-Output ('OPEN_DOCUMENTS=' + $probe.Documents.Count)
foreach ($entry in $probe.Documents) { Write-Output $entry.FullName }
Write-Output ('CACHED_PAGES=' + $probe.ActiveDocument.BuiltInDocumentProperties.Item('Number of Pages').Value)
Write-Output ('BACKGROUND_SAVE=' + $probe.BackgroundSavingStatus)
Write-Output ('BACKGROUND_PRINT=' + $probe.BackgroundPrintingStatus)
