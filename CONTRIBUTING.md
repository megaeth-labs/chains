# Contributing Guide — MegaETH Chains

Welcome and thank you for contributing!  
This repository contains EVM-compatible chain metadata following the [ethereum-lists/chains](https://github.com/ethereum-lists/chains) schema.

## ✅ Before You Start
- Each chain lives in `_data/chains/<caip-2>.json`.  
- Icons go into `_data/icons/<name>.json` with valid IPFS URLs (max 250 KB).  
- `shortName` and `name` must be unique across the repo.  
- For L2/shard networks, define the `parent` section properly.

## 🧪 Local Validation
Run locally before submitting:
```bash
./gradlew run
npx prettier --write _data/*/*.json
