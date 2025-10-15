import json
import os
import glob
import re
import requests
from urllib.parse import urlparse

CHAINS_DIR = "_data/chains"
WHITELIST_RPC_DOMAINS = ["infura.io", "alchemyapi.io", "ankr.com", "cloudflare-eth.com"]

def is_http_url_insecure(url):
    return url.startswith("http://")

def domain_from_url(url):
    try:
        return urlparse(url).netloc.lower()
    except Exception:
        return None

def is_untrusted_domain(url):
    domain = domain_from_url(url)
    if domain:
        return not any(d in domain for d in WHITELIST_RPC_DOMAINS)
    return True

def is_valid_ipfs(cid):
    return re.fullmatch(r"[A-Za-z0-9]{46}", cid) is not None

def audit_chain(chain, filename):
    findings = []
    cid = chain.get("chainId")
    name = chain.get("name")

    if cid == 1 and name != "Ethereum Mainnet":
        findings.append(f"[ERROR] {filename}: ChainId 1 must be Ethereum Mainnet")

    for url in chain.get("rpc", []):
        if is_http_url_insecure(url):
            findings.append(f"[ERROR] {filename}: Insecure RPC URL: {url}")
        if is_untrusted_domain(url):
            findings.append(f"[WARNING] {filename}: RPC domain not in trusted list: {url}")

    if "explorers" in chain:
        for exp in chain["explorers"]:
            url = exp.get("url")
            if url and is_http_url_insecure(url):
                findings.append(f"[WARNING] {filename}: Explorer URL not HTTPS: {url}")

    if "icon" in chain:
        icon_path = f"_data/icons/{chain['icon']}.json"
        if os.path.exists(icon_path):
            with open(icon_path, "r", encoding="utf-8") as f:
                icon_data = json.load(f)
                for icon_entry in icon_data:
                    url = icon_entry.get("url", "")
                    if url.startswith("ipfs://"):
                        cid = url.replace("ipfs://", "")
                        if not is_valid_ipfs(cid):
                            findings.append(f"[ERROR] {filename}: Invalid IPFS CID: {cid}")
                    elif is_http_url_insecure(url):
                        findings.append(f"[WARNING] {filename}: Insecure icon URL: {url}")
        else:
            findings.append(f"[ERROR] {filename}: Icon metadata not found: {icon_path}")

    return findings

def run_audit():
    print("🔍 Running metadata security audit...")
    files = glob.glob(os.path.join(CHAINS_DIR, "*.json"))
    total_findings = 0

    for filepath in files:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
        findings = audit_chain(data, filepath)
        for f in findings:
            print(f)
        total_findings += len(findings)

    if total_findings == 0:
        print("✅ No security issues found.")
    else:
        print(f"⚠️ Found {total_findings} security issues.")

if __name__ == "__main__":
    run_audit()
