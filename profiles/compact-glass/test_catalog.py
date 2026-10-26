"""Offline catalogue safety checks: python -m unittest discover -s profiles/compact-glass -p test_catalog.py."""
import json
import tempfile
import unittest
from pathlib import Path

import build_catalog as catalog


class CatalogueTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'wallpapers').mkdir()
        self.manifest = {'wallpapers': {'offline_seed_files': []}}
        self.entries = {}
        for period, name in [('day','wallpapers.sources.json'),('night','night-wallpapers.sources.json')]:
            self.entries[name] = [dict(file=f'{period}-{i}.jpg',title=f'Photo {i}',location='Source location',
                author='Example photographer',license='CC BY-SA 4.0',license_url='https://creativecommons.org/licenses/by-sa/4.0/',
                page=f'https://commons.wikimedia.org/wiki/File:{period}-{i}.jpg',
                original_url=f'https://upload.wikimedia.org/wikipedia/commons/a/ab/{period}-{i}.jpg',
                download_url=f'https://upload.wikimedia.org/wikipedia/commons/a/ab/{period}-{i}.jpg',
                dimensions=[2560,1440],original_dimensions=[2560,1440],sha256=None,download_verified=False)
                for i in range(100)]
        self.save()

    def save(self):
        (self.root/'manifest.json').write_text(json.dumps(self.manifest),encoding='utf8')
        for name, images in self.entries.items():
            (self.root/name).write_text(json.dumps({'images':images}),encoding='utf8')

    def test_metadata_only_collection_needs_no_full_assets(self):
        self.assertEqual(200,len(catalog.collection(self.root)))

    def test_permitted_licences_only(self):
        for good in ['CC BY 4.0','CC BY-SA 2.0 fr','CC0','Public domain']:
            self.assertIsNotNone(catalog.LICENSE.fullmatch(good))
        for bad in ['CC BY-NC 4.0','CC BY-ND 4.0','CC BY-SA 4.0 or fair use','Public domain-ish']:
            self.assertIsNone(catalog.LICENSE.fullmatch(bad))

    def test_download_url_policy(self):
        self.assertTrue(catalog.safe_url('https://thumb.wikimedia.org:443/wikipedia/commons/x.jpg',True))
        for bad in ['http://upload.wikimedia.org/wikipedia/x.jpg',
                    'https://user:secret@upload.wikimedia.org/wikipedia/x.jpg',
                    'https://upload.wikimedia.org:8443/wikipedia/x.jpg',
                    'https://upload.wikimedia.org/wikipedia/x.jpg#fragment',
                    'https://upload.wikimedia.org.evil.example/wikipedia/x.jpg',
                    'https://upload.wikimedia.org/other/x.jpg',
                    'https://upload.wikimedia.org/wikipedia/x.jpg\tday',
                    'https://upload.wikimedia.org:invalid/wikipedia/x.jpg']:
            with self.subTest(url=bad):self.assertFalse(catalog.safe_url(bad,True))

    def test_story_source_rejects_non_http(self):
        self.entries['wallpapers.sources.json'][0]['story_source']='javascript:alert(1)'
        self.save()
        with self.assertRaisesRegex(ValueError,'story_source'):catalog.collection(self.root)

    def test_duplicates_rejected_across_periods(self):
        self.entries['night-wallpapers.sources.json'][0]['original_url']=self.entries['wallpapers.sources.json'][0]['original_url']
        self.save()
        with self.assertRaisesRegex(ValueError,'duplicate'):catalog.collection(self.root)

    def test_undersized_metadata_rejected(self):
        self.entries['wallpapers.sources.json'][0]['dimensions']=[2560,1439]
        self.save()
        with self.assertRaisesRegex(ValueError,'2560x1440'):catalog.collection(self.root)

    def test_seed_missing_or_unexpected_rejected(self):
        self.manifest['wallpapers']['offline_seed_files']=['seed.jpg'];self.save()
        with self.assertRaisesRegex(ValueError,'differs'):catalog.validate_seed_files(self.root)
        (self.root/'wallpapers'/'seed.jpg').write_bytes(b'fixture')
        self.assertEqual({'seed.jpg'},catalog.validate_seed_files(self.root))
        (self.root/'wallpapers'/'extra.JPG').write_bytes(b'fixture')
        with self.assertRaisesRegex(ValueError,'differs'):catalog.validate_seed_files(self.root)

    def test_seed_budget_checked_before_decode(self):
        self.manifest['wallpapers']['offline_seed_files']=['seed.jpg'];self.save()
        with (self.root/'wallpapers'/'seed.jpg').open('wb') as f:f.truncate(catalog.SEED_LIMIT+1)
        with self.assertRaisesRegex(ValueError,'20,000,000'):catalog.validate_seed_files(self.root)

    def test_uncatalogued_declared_seed_rejected(self):
        self.manifest['wallpapers']['offline_seed_files']=['unknown.jpg'];self.save()
        (self.root/'wallpapers'/'unknown.jpg').write_bytes(b'fixture')
        with self.assertRaisesRegex(ValueError,'Uncatalogued'):catalog.collection(self.root)

    def test_real_profile(self):
        images=catalog.collection()
        self.assertGreaterEqual(sum(i['period']=='day' for i in images),100)
        self.assertGreaterEqual(sum(i['period']=='night' for i in images),100)
        self.assertEqual(4,len(catalog.validate_seed_files(catalog.ROOT)))


if __name__=='__main__':unittest.main()
