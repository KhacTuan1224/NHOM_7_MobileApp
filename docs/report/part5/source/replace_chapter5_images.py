"""Apply scoped edits: retain 5.1 and 5.2, replace only figures 5.3.X."""
from pathlib import Path
import subprocess
from preserve_report_chapter5 import prepare, sync_full_report
if __name__ == '__main__':
    prepare()
    subprocess.run(['powershell.exe','-NoProfile','-File',str(Path(__file__).with_name('convert-preserved-chapter.ps1'))],check=True)
    sync_full_report()
