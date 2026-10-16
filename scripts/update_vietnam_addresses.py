"""Build the offline address catalog from the requested dvhcvn snapshot.

Download data/dvhcvn.json to the input path first; the build itself needs no network.
"""
import json
from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/address/dvhcvn.tsv'
data = json.loads(source.read_text(encoding='utf-8-sig'))
rows = []
for province in data['data']:
    rows.append((province['level1_id'], '', province['name']))
    for district in province['level2s']:
        rows.append((district['level2_id'], province['level1_id'], district['name']))
        for ward in district['level3s']:
            rows.append((ward['level3_id'], district['level2_id'], ward['name']))
assert len({r[0] for r in rows}) == len(rows), 'Duplicate administrative IDs'
assert all('\t' not in r[2] and '\n' not in r[2] for r in rows)
target.write_text(''.join('\t'.join(r) + '\n' for r in rows), encoding='utf-8')
print(f'Wrote {len(rows)} administrative units to {target}')
