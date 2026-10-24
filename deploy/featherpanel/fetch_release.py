#!/usr/bin/env python3
"""Install verified public Kita releases without overwriting config/data/presets."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile

REPOSITORY = "Sumire-Labs/Kita"
MAX_ARCHIVE = 512 * 1024 * 1024
MAX_EXTRACTED = 1024 * 1024 * 1024


def request(url, limit):
    req = urllib.request.Request(url, headers={
        "User-Agent": "Kita-FeatherPanel-Updater/1.0",
        "Accept": "application/vnd.github+json" if "api.github.com" in url else "*/*",
    })
    with urllib.request.urlopen(req, timeout=60) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError("Download exceeds the configured size limit")
    return data


def valid_app(path):
    return (path / "bin/app").is_file() and (path / "lib").is_dir()


def checked_asset_url(asset):
    url = asset["browser_download_url"]
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "github.com":
        raise ValueError("Unexpected release asset host")
    if not parsed.path.startswith(f"/{REPOSITORY}/releases/download/"):
        raise ValueError("Release asset is outside the Kita repository")
    return url


def checksum_for(text, name):
    matches = []
    for line in text.splitlines():
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\*?(.+)", line.strip())
        if match and match[2] == name:
            matches.append(match[1].lower())
    if len(matches) != 1:
        raise ValueError("Release checksum is missing or ambiguous")
    return matches[0]


def extract_app(archive_path, destination):
    with zipfile.ZipFile(archive_path) as archive:
        entries = archive.infolist()
        if len(entries) > 15000 or sum(e.file_size for e in entries) > MAX_EXTRACTED:
            raise ValueError("Release archive is unexpectedly large")
        for entry in entries:
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.filename:
                raise ValueError("Unsafe archive path")
            if ":" in entry.filename or stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError("Links and drive paths are not allowed in release archives")
        archive.extractall(destination)
    candidates = [destination] + [p for p in destination.iterdir() if p.is_dir()]
    apps = [p for p in candidates if valid_app(p)]
    if len(apps) != 1:
        raise ValueError("Expected one Gradle application distribution containing bin/app and lib")
    (apps[0] / "bin/app").chmod(0o755)
    return apps[0]


def ensure_release(root, version, fetch=request):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", version):
        raise ValueError("Use latest or an exact GitHub release tag, such as v1.0.0")
    root = Path(root).resolve()
    cache = root / ".kita"
    releases = cache / "releases"
    # A symlink must never redirect updater writes outside the server's volume.
    for path in (cache, releases):
        if path.is_symlink():
            raise ValueError("Updater directories must not be symlinks")
    releases.mkdir(parents=True, exist_ok=True)
    marker = cache / "current.json"
    current = {}
    if marker.is_file() and not marker.is_symlink():
        current = json.loads(marker.read_text(encoding="utf-8"))
    if current.get("requested") == version:
        path = releases / current.get("directory", "")
        if path.parent == releases and not path.is_symlink() and valid_app(path):
            return path
    # Explicit tags can also be selected offline after a previous installation.
    if version != "latest":
        key = hashlib.sha256(f"{REPOSITORY}/{version}".encode()).hexdigest()[:24]
        cached = releases / key
        if not cached.is_symlink() and valid_app(cached):
            write_marker(marker, version, version, key)
            return cached
    endpoint = "latest" if version == "latest" else "tags/" + urllib.parse.quote(version)
    release = json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/releases/{endpoint}", 2**20))
    tag = release["tag_name"]
    if release.get("draft") or (version == "latest" and release.get("prerelease")):
        raise ValueError("The selected release is not a published stable release")
    assets = release.get("assets", [])
    bundles = [a for a in assets if re.fullmatch(r"kita-(?!audio-).+\.zip", a["name"])]
    sums = [a for a in assets if a["name"] == "SHA256SUMS"]
    if len(bundles) != 1 or len(sums) != 1:
        raise ValueError("Publish a release with one kita-*.zip and SHA256SUMS first")
    bundle = bundles[0]
    digest = checksum_for(fetch(checked_asset_url(sums[0]), 65536).decode(), bundle["name"])
    key = hashlib.sha256(f"{REPOSITORY}/{tag}".encode()).hexdigest()[:24]
    final = releases / key
    if final.is_symlink():
        raise ValueError("Release cache must not be a symlink")
    print(f"[Kita] Preparing release {tag}; config/data/presets are preserved", file=sys.stderr)
    with tempfile.TemporaryDirectory(prefix="download-", dir=cache) as temporary:
        temporary = Path(temporary)
        payload = fetch(checked_asset_url(bundle), MAX_ARCHIVE)
        if hashlib.sha256(payload).hexdigest() != digest:
            raise ValueError("SHA-256 verification failed; existing releases were not changed")
        archive = temporary / "release.zip"
        archive.write_bytes(payload)
        extracted = temporary / "unpacked"
        extracted.mkdir()
        application = extract_app(archive, extracted)
        if final.exists():
            if not valid_app(final):
                raise ValueError("An incomplete release cache exists; inspect it before retrying")
        else:
            shutil.move(str(application), str(final))
    write_marker(marker, version, tag, key)
    print(f"[Kita] Selected {tag}", file=sys.stderr)
    return final


def write_marker(path, requested, resolved, directory):
    temporary = path.with_suffix(".tmp")
    if path.is_symlink() or temporary.is_symlink():
        raise ValueError("Release marker must not be a symlink")
    temporary.write_text(json.dumps({"requested": requested, "resolved": resolved,
                                     "directory": directory}) + "\n", encoding="utf-8")
    os.replace(temporary, path)


if __name__ == "__main__":
    try:
        print(ensure_release(Path.cwd(), os.environ.get("KITA_VERSION", "latest")))
    except Exception as error:
        print(f"[Kita] Update stopped: {error}", file=sys.stderr)
        sys.exit(1)
