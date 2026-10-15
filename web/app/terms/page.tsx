import Link from "next/link";
import type { Metadata } from "next";
import PolicyPage, { type PolicySection } from "../policy-page";
const REPO = "https://github.com/sahilcodexx/aro-music";
export const metadata: Metadata = {
  title: "Terms & conditions — Aro Music",
  description:
    "Aro’s development release, third-party service requirements, downloads, and applicable licenses.",
};
const sections: PolicySection[] = [
  {
    id: "scope",
    title: "Using Aro",
    body: (
      <p>
        These terms explain the current Aro website and development-stage
        Android app. Use Aro with music, accounts, and servers you are entitled
        to access, and follow the applicable service terms. This page does not
        replace the licenses included with the source or grant new
        redistribution rights.
      </p>
    ),
  },
  {
    id: "release",
    title: "Development release",
    body: (
      <>
        <p>
          The current phone release is v1.0.2 for Android devices running
          Android 11 or newer. It is published without the GitHub prerelease flag. Compatibility
          varies by device, setting, and connected service; broader testing
          remains incomplete.
        </p>
        <p>
          Read the{" "}
          <a href={`${REPO}/blob/main/RELEASE_CHECKLIST.md`}>
            release validation record
          </a>{" "}
          for tested behavior and known limitations. Keep separate copies of
          music and exported information you want to retain.
        </p>
      </>
    ),
  },
  {
    id: "services",
    title: "Third-party services",
    body: (
      <>
        <p>
          YouTube, YouTube Music, Spotify, and optional connected providers have
          their own terms, account requirements, availability, and privacy
          policies. Aro is not affiliated with those services and does not grant
          access rights to their content.
        </p>
        <p>
          Spotify playlist metadata is matched to YouTube for playback. Imports
          create local copies; changes to remote playlists are not automatically
          synchronized back. Removed, unmatched, or region-restricted tracks may
          be unavailable.
        </p>
      </>
    ),
  },
  {
    id: "downloads",
    title: "Downloads and updates",
    body: (
      <p>
        The website links to the project’s{" "}
        <a href={`${REPO}/releases/tag/v1.0.2`}>
          versioned GitHub release
        </a>
        . Review the release notes and installation requirements before
        installing. Future updates must use compatible signing credentials; an
        APK from another source may not be a compatible update.
      </p>
    ),
  },
  {
    id: "licenses",
    title: "Licenses and ownership",
    body: (
      <>
        <p>
          Original copyright and license notices remain applicable. The root
          license restricts redistribution and commercial publishing of
          derivatives; included InnerTubeX and Meld-derived code have GPLv3
          obligations. Compatible distribution rights for the combined app
          remain unresolved.
        </p>
        <p>
          Read{" "}
          <a href={`${REPO}/blob/main/LICENSE_STATUS.md`}>License status</a> and
          the actual included licenses. Aro’s branding, this website, and the
          maintainer’s development-prerelease authorization do not change those
          terms. Service names and artwork belong to their respective owners.
        </p>
      </>
    ),
  },
  {
    id: "privacy",
    title: "Privacy and accounts",
    body: (
      <p>
        Read the <Link href="/privacy">Privacy policy</Link> for on-device
        information, connected services, backup limitations, and website theme
        preferences. Use the provider’s authentication flow to connect an
        account and keep session information private.
      </p>
    ),
  },
  {
    id: "support",
    title: "Support and changes",
    body: (
      <p>
        Report reproducible problems through{" "}
        <a href={`${REPO}/issues`}>GitHub Issues</a> without posting personal
        data or credentials. Features and release requirements may change during
        development; consult the current documentation and release notes when
        updating.
      </p>
    ),
  },
];
export default function Terms() {
  return (
    <PolicyPage
      title="Terms & conditions"
      intro="A few clear expectations about the development release, the services you connect, and the licenses that apply."
      sections={sections}
    />
  );
}
