import Link from "next/link";
import Image from "next/image";
import { Icon } from "./icons";
import ThemeToggle from "./theme-toggle";
import MobileMenu from "./mobile-menu";
import { APK_URL } from "./project-links";
const REPO = "https://github.com/sahilcodexx/aro-music";
export function SiteHeader() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <nav className="container navigation" aria-label="Main navigation">
          <Link className="brand" href="/#top" aria-label="Aro Music home">
            <Image src="/aro-logo-white.png" width={40} height={40} alt="" />
            <span>
              aro<span className="brand-dot">.</span>
            </span>
          </Link>
          <div className="nav-links desktop-nav">
            <Link href="/#experience">Explore the app</Link>
            <Link href="/#questions">Questions</Link>
            <a href={REPO}>
              GitHub <Icon name="external" />
            </a>
          </div>
          <div className="nav-actions">
            <ThemeToggle />
            <a className="button button-small" href={APK_URL}>
              Get Aro <Icon name="download" />
            </a>
            <MobileMenu />
          </div>
        </nav>
      </header>
    </>
  );
}
export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="footer-top">
          <div className="footer-brand">
            <Link className="brand" href="/">
              <Image src="/aro-logo-white.png" width={36} height={36} alt="" />
              <span>
                aro<span className="brand-dot">.</span>
              </span>
            </Link>
            <p>A little closer to your music.</p>
          </div>
          <nav aria-label="Project links">
            <a href={REPO}>Source</a>
            <Link href="/privacy">Privacy</Link>
            <Link href="/terms">Terms</Link>
            <a href={`${REPO}/blob/main/LICENSE_STATUS.md`}>License status</a>
          </nav>
        </div>
        <div className="footer-bottom">
          <p>
            UI inspired by{" "}
            <a href="https://github.com/theovilardo/PixelPlay">PixelPlay</a>.
            {" "}Source adaptations and original notices are recorded in{" "}
            <a href={`${REPO}/blob/main/THIRD_PARTY_NOTICES.md`}>project credits</a>.
          </p>
          <p>
            Service names and artwork belong to their owners. Aro is not
            affiliated with Spotify or YouTube.
          </p>
        </div>
      </div>
    </footer>
  );
}
