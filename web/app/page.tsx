import Image from "next/image";
import ScreenExplorer from "./screen-explorer";
import { Icon } from "./icons";
import MusicArtwork from "./music-artwork";
import Questions from "./questions";
import { SiteHeader, SiteFooter } from "./site-shell";
import { APK_URL, RELEASES_URL } from "./project-links";

const REPO = "https://github.com/sahilcodexx/aro-music";

const questions = [
  [
    "What do I need to run Aro?",
    "An ARM64 Android phone running Android 11 or newer. Aro is currently a development release candidate; broader device compatibility is still being tested. There is no iPhone version.",
  ],
  [
    "Can I play music already on my phone?",
    "Yes. Give Aro audio access and your songs appear in Library. Browse by song, artist, or album, make playlists, and play your local collection without an internet connection.",
  ],
  [
    "How do Spotify playlists work?",
    "Connect Spotify to browse playlists and liked songs, or paste a playlist link into Library’s Import action. Aro matches Spotify metadata to YouTube for playback; it does not stream Spotify audio. Imports are local copies, and remote edits do not sync back. Unavailable tracks may be omitted.",
  ],
  [
    "Do I need to connect an account?",
    "No account is needed for your local library. Connect a supported service only if you want its features. Library metadata, preferences, listening history, and playlist caches are stored on your device; connected services receive requests when you use them.",
  ],
  [
    "Where can I report a problem?",
    "Use the project’s GitHub Issues page. Include your Android version, Aro version, and the steps that reproduce the problem. Keep passwords, session cookies, and personal account information out of reports.",
  ],
];

export default function Home() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <section id="top" className="hero" aria-labelledby="hero-title">
          <div className="container hero-grid">
            <div className="hero-copy">
              <p className="availability">
                <Icon name="android" /> Made for Android
              </p>
              <h1 id="hero-title">
                Find your
                <br />
                next repeat.
              </h1>
              <p className="hero-description">
                The songs you keep. The ones you haven’t found yet. Bring them
                together in a player that feels like you.
              </p>
              <div className="hero-actions">
                <a className="button" href={APK_URL}>
                  <Icon name="download" /> Get Aro for Android
                </a>
                <a className="text-link" href="#experience">
                  Take a look inside <Icon name="arrow" />
                </a>
              </div>
              <p className="release-note">
                Android 11+ <span aria-hidden="true">/</span> Development
                release candidate
              </p>
            </div>
            <div className="hero-visual-surface">
              <div className="hero-artwork">
                <div className="hero-art">
                  <div className="sound-disc" aria-hidden="true">
                    <span />
                    <span />
                    <span />
                  </div>
                  <div className="phone phone-back" aria-hidden="true">
                    <Image
                      src="/screenshot-mix.png"
                      width={931}
                      height={2048}
                      sizes="250px"
                      alt=""
                    />
                  </div>
                  <div className="phone phone-front">
                    <Image
                      src="/screenshot-player.jpg"
                      width={931}
                      height={2048}
                      sizes="(max-width: 700px) 230px, 270px"
                      preload
                      alt="Aro’s artwork-colored player playing The Emptiness Machine by Linkin Park"
                    />
                  </div>
                  <svg
                    className="hero-wave"
                    viewBox="0 0 440 100"
                    fill="none"
                    aria-hidden="true"
                  >
                    <path
                      d="M0 50C22 50 22 18 44 18S66 82 88 82s22-64 44-64 22 64 44 64 22-64 44-64 22 64 44 64 22-64 44-64 22 64 44 64 22-32 44-32h44"
                      stroke="currentColor"
                      strokeWidth="9"
                      strokeLinecap="round"
                    />
                  </svg>
                </div>
                <div className="art-note">
                  <span className="note-line" aria-hidden="true" />
                  <p>
                    A different song.
                    <br />A whole new color.
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div className="container hero-bottom">
            <p>Your files. Your favorites. Your next discovery.</p>
            <a href="#experience" aria-label="Explore the Aro app">
              <Icon name="down" />
            </a>
          </div>
        </section>

        <section
          id="experience"
          className="experience section-space"
          aria-labelledby="experience-title"
        >
          <div className="container">
            <div className="section-heading">
              <h2 id="experience-title">
                Good music.
                <br />
                Great company.
              </h2>
              <p>
                A familiar home for your collection,
                <br className="desktop-break" /> with room for something new.
              </p>
            </div>
            <ScreenExplorer />
          </div>
        </section>

        <section
          className="collection section-space"
          aria-labelledby="collection-title"
        >
          <div className="container collection-grid">
            <div className="section-heading collection-copy">
              <h2 id="collection-title">
                Your collection.
                <br />
                All together.
              </h2>
              <p>
                Keep the files on your phone close. Bring over the playlists
                you’ve spent years making. Then follow a song somewhere new.
              </p>
            </div>
            <div className="source-list">
              <article>
                <div className="source-art">
                  <MusicArtwork kind="record" />
                </div>
                <div className="source-body">
                  <div className="source-heading">
                    <Icon name="music" />
                    <h3>On your phone</h3>
                  </div>
                  <p>
                    Local songs, albums, and artists. Organized in one library,
                    ready when you are.
                  </p>
                  <span className="source-type">Local playback</span>
                </div>
              </article>
              <article>
                <div className="source-art">
                  <MusicArtwork kind="discovery" />
                </div>
                <div className="source-body">
                  <div className="source-heading">
                    <Icon name="play" />
                    <h3>Out there, waiting</h3>
                  </div>
                  <p>
                    Search YouTube Music for artist collections, new favorites,
                    and related-song queues.
                  </p>
                  <span className="source-type">Online discovery</span>
                </div>
              </article>
              <article>
                <div className="source-art">
                  <MusicArtwork kind="playlist" />
                </div>
                <div className="source-body">
                  <div className="source-heading">
                    <Icon name="playlist" />
                    <h3>The playlists you know</h3>
                  </div>
                  <p>
                    Import Spotify and YouTube playlists as local copies.
                    Spotify tracks use YouTube playback matching.
                  </p>
                  <span className="source-type">Playlist import</span>
                </div>
              </article>
            </div>
            <a
              className="text-link collection-link"
              href={`${REPO}/blob/main/README.md`}
            >
              See how Aro works <Icon name="external" />
            </a>
          </div>
        </section>

        <section
          id="download"
          className="download section-space"
          aria-labelledby="download-title"
        >
          <div className="container download-panel">
            <div className="download-copy">
              <Image src="/aro-icon.png" width={68} height={68} alt="" />
              <h2 id="download-title">
                Make yourself
                <br />
                at home.
              </h2>
              <p>Your next listening session starts here.</p>
              <a className="button button-dark" href={APK_URL}>
                <Icon name="download" /> Download APK
              </a>
              <a className="text-link release-link" href={RELEASES_URL}>
                Release notes and checksums <Icon name="external" />
              </a>
              <p className="download-meta">v1.0.1 · Android 11+ · Universal APK</p>
            </div>
            <div className="install-guide">
              <h3>A little help getting started</h3>
              <ol>
                <li>
                  <span>1</span>
                  <div>
                    <h4>Grab the APK</h4>
                    <p>
                      Use Download APK to save the app to your phone. Release
                      notes and checksums are available on GitHub.
                    </p>
                  </div>
                </li>
                <li>
                  <span>2</span>
                  <div>
                    <h4>Open it on your phone</h4>
                    <p>
                      If Android asks, allow your browser to install apps, then
                      finish installing Aro.
                    </p>
                  </div>
                </li>
                <li>
                  <span>3</span>
                  <div>
                    <h4>Put something on</h4>
                    <p>
                      Grant audio access for your local songs, or head to Search
                      to discover music.
                    </p>
                  </div>
                </li>
              </ol>
              <p className="candidate-note">
                This is a development release candidate.{" "}
                <a href={`${REPO}/blob/main/RELEASE_CHECKLIST.md`}>
                  See tested features and known limits.
                </a>
              </p>
            </div>
          </div>
        </section>

        <section
          id="questions"
          className="questions section-space"
          aria-labelledby="questions-title"
        >
          <div className="container faq-grid">
            <div>
              <h2 id="questions-title">
                Before you
                <br />
                press play.
              </h2>
              <p>A few things you might be wondering.</p>
              <a className="text-link" href={`${REPO}/issues`}>
                Ask on GitHub <Icon name="external" />
              </a>
            </div>
            <Questions questions={questions} />
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
