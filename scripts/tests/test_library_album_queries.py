"""Exercise the actual Room SQL with SQLite, without a device or schema changes.

Run: python3 -m unittest discover -s scripts/tests -p 'test_library_album_queries.py'
"""
from pathlib import Path
import re
import sqlite3
import unittest

DAO = (Path(__file__).resolve().parents[2] /
       'app/src/main/java/com/aro/music/data/database/MusicDao.kt').read_text()
PREDICATE = re.search(r'private const val VALID_LIBRARY_ALBUM = """(.*?)"""', DAO, re.S).group(1)


def query(name):
    method = re.search(r'\bfun ' + name + r'\(', DAO).start()
    annotation = DAO.rfind('@Query(', 0, method)
    raw = DAO[annotation:method]
    raw = raw[raw.index('"""') + 3:raw.rfind('"""')]
    return raw.replace('""" + VALID_LIBRARY_ALBUM + """', PREDICATE)


class LibraryAlbumQueriesTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        self.db.executescript('''
          CREATE TABLE albums(id INTEGER PRIMARY KEY, title TEXT, artist_name TEXT,
            artist_id INTEGER, album_artist TEXT, album_art_uri_string TEXT,
            date_added INTEGER, year INTEGER);
          CREATE TABLE songs(id INTEGER PRIMARY KEY, album_id INTEGER, source_type INTEGER,
            parent_directory_path TEXT);
          CREATE TABLE favorites(songId INTEGER PRIMARY KEY, isFavorite INTEGER);
          CREATE TABLE playlists(id INTEGER PRIMARY KEY);
          CREATE TABLE playlist_songs(song_id INTEGER, playlist_id INTEGER);
        ''')
        # Same artist, two real releases: two tracks share the first release.
        for album_id, title in [(100, 'A real album'), (101, 'B single'),
                                (102, '22 crore plays'), (0, 'Unknown album'),
                                (103, ' Unknown Album '), (104, ''), (105, '1989')]:
            self.db.execute('INSERT INTO albums VALUES (?, ?, ?, 7, NULL, NULL, 0, 2026)',
                            (album_id, title, 'Same artist'))
        for song_id, album_id in enumerate([100, 100, 101, 102, 0, 103, 104, 105], 1):
            self.db.execute('INSERT INTO songs VALUES (?, ?, 9, ?)', (song_id, album_id, ''))
            self.db.execute('INSERT INTO favorites VALUES (?, 1)', (song_id,))
        self.params = dict(allowedParentDirs='', applyDirectoryFilter=0,
                           filterMode=0, minTracks=1, sortOrder='album_title_az', limit=50, offset=0)

    def test_real_releases_group_by_album_not_by_song_or_artist(self):
        for name in ['getAlbums', 'getAlbumsPaginated', 'getAlbumsPage', 'getAllAlbumsList']:
            with self.subTest(query=name):
                rows = self.db.execute(query(name), self.params).fetchall()
                self.assertEqual({100, 101, 105}, {row[0] for row in rows})
                self.assertEqual(2, next(row[6] for row in rows if row[0] == 100))
        self.assertEqual(8, self.db.execute('SELECT COUNT(*) FROM songs').fetchone()[0])
        self.assertEqual(8, self.db.execute('SELECT COUNT(*) FROM favorites').fetchone()[0])
        self.assertEqual(7, self.db.execute('SELECT COUNT(*) FROM albums').fetchone()[0])

    def test_hidden_metadata_does_not_consume_pagination_slots(self):
        rows = self.db.execute(query('getAlbumsPage'), self.params | dict(limit=1, offset=0)).fetchall()
        self.assertEqual([105], [row[0] for row in rows])  # real numeric album title
        rows = self.db.execute(query('getAlbumsPage'), self.params | dict(limit=1, offset=1)).fetchall()
        self.assertEqual([100], [row[0] for row in rows])

    def test_metadata_repair_preserves_valid_titles_and_saved_songs(self):
        annotation = DAO[DAO.index('@Query("UPDATE albums SET title'):]
        expression = annotation[:annotation.index(')\n')]
        parts = re.findall(r'"([^"\n]*)"|\b(VALID_LIBRARY_ALBUM)\b', expression)
        repair = ''.join(PREDICATE if constant else literal for literal, constant in parts)
        self.db.execute(repair, dict(id=102, title='Actual release'))
        self.db.execute(repair, dict(id=100, title='Do not overwrite a real title'))
        self.assertEqual('Actual release', self.db.execute(
            'SELECT title FROM albums WHERE id=102').fetchone()[0])
        self.assertEqual('A real album', self.db.execute(
            'SELECT title FROM albums WHERE id=100').fetchone()[0])
        self.assertEqual(8, self.db.execute('SELECT COUNT(*) FROM songs').fetchone()[0])
        self.assertEqual(8, self.db.execute('SELECT COUNT(*) FROM favorites').fetchone()[0])

    def test_offline_mode_does_not_show_remote_albums(self):
        self.assertEqual([], self.db.execute(query('getAlbumsPage'),
                                           self.params | dict(filterMode=1)).fetchall())


if __name__ == '__main__':
    unittest.main()
