# Vietnam address data

Source: https://github.com/daohoangson/dvhcvn
Snapshot: eaddcea82fc336d59e908989f04178c336a9db10 (2025-02-18).
Original: https://raw.githubusercontent.com/daohoangson/dvhcvn/eaddcea82fc336d59e908989f04178c336a9db10/data/dvhcvn.json

This is the repository's **three-level historical dataset**, as requested:
63 provinces, 696 districts, 10,047 wards. It is not a representation of the
subsequent two-level administrative system.

`dvhcvn.tsv` preserves the original IDs and Vietnamese names. Each UTF-8 row is
`id<TAB>parent_id<TAB>name`; provinces have an empty parent. There is no runtime
network request. Generate it with `python scripts/update_vietnam_addresses.py INPUT_JSON`.

Upstream license: GPL-3.0; the complete license is included as `LICENSE.dvhcvn`.
The dataset is third-party material and is not covered by the app's MIT license.
