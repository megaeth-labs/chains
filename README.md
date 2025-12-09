# EVM-Based Chains

This repository hosts a community-maintained registry of EVM-compatible chains.  
Each network is defined using a standardized JSON schema, stored under `_data/chains`, and named according to its [CAIP-2](https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md) identifier.

These definitions are relied upon by wallets, explorers, SDKs, infrastructure providers, and a wide ecosystem of Ethereum-compatible tools.

---

## 📁 Chain File Structure

Each chain is represented by a dedicated JSON file containing metadata such as RPC endpoints, features, native currency details, and optional icon references.

### Example

```json
{
  "name": "Ethereum Mainnet",
  "chain": "ETH",
  "rpc": [
    "https://mainnet.infura.io/v3/${INFURA_API_KEY}",
    "https://api.mycryptoapi.com/eth"
  ],
  "faucets": [],
  "nativeCurrency": {
    "name": "Ether",
    "symbol": "ETH",
    "decimals": 18
  },
  "features": [{ "name": "EIP155" }, { "name": "EIP1559" }],
  "infoURL": "https://ethereum.org",
  "shortName": "eth",
  "chainId": 1,
  "networkId": 1,
  "icon": "ethereum",
  "explorers": [
    {
      "name": "etherscan",
      "url": "https://etherscan.io",
      "icon": "etherscan",
      "standard": "EIP3091"
    }
  ]
}
```

---

## 🖼 Icons (Stored in `_data/icons`)

If a chain or explorer defines an `icon`, a matching icon JSON file must exist.  
Example (`ethereum.json`):

```json
[
  {
    "url": "ipfs://QmdwQDr6vmBtXmK2TmknkEuZNoaDqTasFdZdu3DRw8b2wt",
    "width": 1000,
    "height": 1628,
    "format": "png"
  }
]
```

### Icon requirements

- URL **must** be publicly accessible through IPFS  
- `width` and `height` must be positive integers  
- `format` must be `png`, `jpg`, or `svg`  
- File size must be **< 250 KB**

---

## 🔗 Layered & Sharded Networks

Layer-2 networks or shard chains may reference a parent chain:

```json
{
  ...
  "parent": {
    "type": "L2",
    "chain": "eip155-1",
    "bridges": [{ "url": "https://bridge.arbitrum.io" }]
  }
}
```

- `type` can be `L2`, `shard`, etc.  
- `chain` must reference an existing entry  
- `bridges` is optional

---

## 🏷 Chain Status

Chains can specify a `status`:

| Status        | Meaning |
|---------------|---------|
| `active`      | Default; recommended for production use |
| `incubating`  | Early-stage or experimental chain |
| `deprecated`  | No longer maintained or replaced |

Chains **must never be deleted**, as that could enable replay attacks.

---

## 📦 Aggregated JSON Outputs

This repository automatically generates compiled chain lists:

- Full version → https://chainid.network/chains.json  
- Mini version → https://chainid.network/chains_mini.json  

These are widely consumed by tools and wallets.

---

## ⚠️ Validation Rules & Constraints

To maintain integrity:

- `name` and `shortName` **must be unique**  
- Parent chains must **exist** inside the repository  
- IPFS CIDs must be **retrievable via `ipfs get`**  
- Only **one** chain may use a given `chainId`  
  - Prevents replay attacks  
  - The first valid PR receives that chainId  
  - Attempts to override a claimed ID will be rejected  
- Deprecated chains may free up their ID, but will be marked with `reusedChainID`  

Additional constraints run automatically in CI.

---

## 🛠 Getting Your PR Merged

### 1. Validate before submission

Run the validation script:

```bash
./gradlew run
```

Run Prettier to format JSON:

```bash
npx prettier --write _data/*/*.json
```

---

### 2. After submitting your PR

- Ensure the **CI is green**  
- If you push any fixes, **re-request review**  
- Keep PRs focused and minimal  

---

## 🔧 Integrations & Ecosystem Usage

### Tools  
- MESC

### Explorers  
- Otterscan

### Wallets  
- WallETH  
- TREZOR  
- Minerva Wallet

### Relevant EIPs  
- EIP-155  
- EIP-3014  
- EIP-3770  
- EIP-4527  

### Chain Listing Sites  
- chainid.network / chainlist.wtf  
- chainlist.org  
- Chainlink docs  
- dRPC chainlist  
- eth-chains  
- EVM-BOX  
- evmchain.info  
- evmchainlist.org  
- networks.vercel.app  
- Wagmi compatible configs  
- chainlist.simplr.sh  

### Other tools  
- FaucETH  
- Sourcify Playground  
- Smart Contract UI  

Want your project listed? Open a PR!

---
