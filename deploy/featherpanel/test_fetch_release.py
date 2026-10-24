import hashlib, importlib.util, io, json, os, stat, tempfile, unittest, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('updater',str(ROOT/'fetch_release.py'))
u=importlib.util.module_from_spec(spec);spec.loader.exec_module(u)

def bundle(extra=None):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as z:
        z.writestr('kita-1.0.0/bin/app','#!/bin/sh\necho test\n')
        z.writestr('kita-1.0.0/lib/app.jar','test fixture')
        if extra:z.writestr(*extra)
    return stream.getvalue()

def remote(tag='v1.0.0',payload=None,corrupt=False):
    payload=payload if payload is not None else bundle()
    base=f'https://github.com/{u.REPOSITORY}/releases/download/{tag}/'
    release={'tag_name':tag,'assets':[
        {'name':'kita-1.0.0.zip','browser_download_url':base+'kita-1.0.0.zip'},
        {'name':'SHA256SUMS','browser_download_url':base+'SHA256SUMS'}]}
    checksum=('0'*64 if corrupt else hashlib.sha256(payload).hexdigest())+'  kita-1.0.0.zip\n'
    calls=[]
    def fetch(url,limit):
        calls.append(url)
        if 'api.github.com' in url:return json.dumps(release).encode()
        if url.endswith('SHA256SUMS'):return checksum.encode()
        return payload
    return fetch,calls

class UpdaterTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
    def tearDown(self):self.temp.cleanup()
    def test_installs_verified_bundle_and_preserves_user_data(self):
        for folder in ('config','data','presets'):
            (self.root/folder).mkdir();(self.root/folder/'keep.txt').write_text('keep')
        fetch,_=remote();app=u.ensure_release(self.root,'v1.0.0',fetch)
        self.assertTrue(u.valid_app(app))
        for folder in ('config','data','presets'):
            self.assertEqual((self.root/folder/'keep.txt').read_text(),'keep')
    def test_pinned_release_restarts_without_network(self):
        fetch,calls=remote();first=u.ensure_release(self.root,'v1.0.0',fetch)
        second=u.ensure_release(self.root,'v1.0.0',lambda *_:self.fail('unexpected network'))
        self.assertEqual(first,second);self.assertEqual(len(calls),3)
    def test_latest_is_pinned_after_first_download(self):
        fetch,_=remote();first=u.ensure_release(self.root,'latest',fetch)
        self.assertEqual(first,u.ensure_release(self.root,'latest',lambda *_:self.fail('latest unexpectedly updated')))
    def test_cached_tag_can_be_selected_for_rollback(self):
        fetch,_=remote();first=u.ensure_release(self.root,'v1.0.0',fetch)
        fetch,_=remote('v2.0.0');u.ensure_release(self.root,'v2.0.0',fetch)
        self.assertEqual(first,u.ensure_release(self.root,'v1.0.0',lambda *_:self.fail('rollback used network')))
    def test_checksum_failure_preserves_previous_selection(self):
        fetch,_=remote();u.ensure_release(self.root,'v1.0.0',fetch)
        before=(self.root/'.kita/current.json').read_bytes()
        fetch,_=remote('v2.0.0',corrupt=True)
        with self.assertRaisesRegex(ValueError,'SHA-256'):u.ensure_release(self.root,'v2.0.0',fetch)
        self.assertEqual(before,(self.root/'.kita/current.json').read_bytes())
    def test_path_traversal_archive_is_rejected(self):
        fetch,_=remote(payload=bundle(('../../outside.txt','bad')))
        with self.assertRaisesRegex(ValueError,'Unsafe archive path'):u.ensure_release(self.root,'v1.0.0',fetch)
        self.assertFalse((self.root.parent/'outside.txt').exists())
    def test_drive_path_is_rejected(self):
        fetch,_=remote(payload=bundle(('C:/outside','bad')))
        with self.assertRaises(ValueError):u.ensure_release(self.root,'v1.0.0',fetch)
    def test_missing_distribution_fails_before_selection(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as archive:archive.writestr('README','not a distribution')
        fetch,_=remote(payload=stream.getvalue())
        with self.assertRaisesRegex(ValueError,'Expected one'):u.ensure_release(self.root,'v1.0.0',fetch)
        self.assertFalse((self.root/'.kita/current.json').exists())
    def test_unsafe_version_is_rejected(self):
        for version in ('../../data','v1;echo bad','', 'a'*81):
            with self.subTest(version=version),self.assertRaises(ValueError):
                u.ensure_release(self.root,version,lambda *_:self.fail('invalid input used network'))
    def test_asset_host_restriction(self):
        for url in ('http://github.com/Sumire-Labs/Kita/releases/download/v1/a',
                    'https://example.com/a','https://github.com/another/repo/releases/download/v1/a'):
            with self.subTest(url=url),self.assertRaises(ValueError):u.checked_asset_url({'browser_download_url':url})
    def test_missing_and_duplicate_checksums_are_rejected(self):
        for text in ('', 'not a checksum',('a'*64+'  app.zip\n')*2):
            with self.subTest(text=text),self.assertRaises(ValueError):u.checksum_for(text,'app.zip')
    def test_config_template_only_uses_supported_spell_variables(self):
        import re
        spell=json.loads((ROOT/'spell-kita.json').read_text(encoding='utf-8'))
        keys={x['env_variable'] for x in spell['variables']}
        template=(ROOT/'kita.example.yml').read_text(encoding='utf-8')
        used=set(re.findall(r'\$\{([A-Z_]+)',template))
        self.assertFalse(used-keys-{'YT_DLP'})
        self.assertEqual(spell['meta']['version'],'PTDL_v2')
        for key in ('files','startup','logs'):json.loads(spell['config'][key])
        self.assertEqual(spell['config']['stop'],'^C')
    def test_shell_files_use_unix_line_endings(self):
        for filename in ('start.sh','entrypoint.sh'):
            self.assertNotIn(b'\r',(ROOT/filename).read_bytes())

if __name__=='__main__':unittest.main(verbosity=2)
