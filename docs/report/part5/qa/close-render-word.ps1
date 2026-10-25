$ErrorActionPreference = 'Stop'
$probe = [Runtime.InteropServices.Marshal]::GetActiveObject('Word.Application')
$expected = 'D:\NHOM_7_MobileApp\docs\report\part5\Phan-5-Xay-dung-ung-dung.docx'
if ($probe.Documents.Count -ne 1 -or $probe.Documents.Item(1).FullName -ne $expected) { throw 'Refusing to close an unrelated Word session' }
$probe.Documents.Item(1).Close(0)
$probe.Quit()
Write-Output 'Closed only the generated chapter render session'
