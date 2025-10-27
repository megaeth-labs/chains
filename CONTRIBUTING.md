# Contributing Guide

Thanks for contributing to **megaeth-labs/chains** (fork of `ethereum-lists/chains`).  
This repo stores EVM chain metadata under `_data/chains` using CAIP-2 file names.

## ✅ Before you start
- Each chain lives in `_data/chains/<caip-2>.json`. Example format with required fields is shown in README (name, chain, rpc[], nativeCurrency, explorers[], etc.).
- If you reference an icon (network or explorer), provide a JSON in `_data/icons/<name>.json` with IPFS URLs, width/height, and format (png/jpg/svg). Files must be < 250 KB.  
- `shortName` and `name` must be **unique**. If it’s an L2/shard, add a `parent` section linking to an existing parent chain.

## 🔍 Constraints (summary)
- Unique `shortName` and `name`.
- If `parent` is used, the parent chain **must already exist**.
- Icon JSON uses **publicly retrievable IPFS CIDs** (not gateway-only).
- We do **not** delete historical chains; use `"status": "deprecated"` when needed.

## 🧪 Pre-flight checks (please run locally before PR)
```bash
# validate / aggregate
./gradlew run

# format JSON consistently
npx prettier --write _data/*/*.json
