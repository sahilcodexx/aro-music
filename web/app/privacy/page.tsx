import type { Metadata } from "next";
import PolicyPage, { type PolicySection } from "../policy-page";
const REPO = "https://github.com/sahilcodexx/aro-music";
export const metadata: Metadata = {
  title: "Privacy policy — Aro Music",
  description:
    "How Aro handles on-device data, connected services, backups, and website preferences.",
};
const sections: PolicySection[] = [
  {
    id: "overview",
    title: "About this policy",
    body: (
      <p>
        Aro is a development-stage Android music app. This policy describes the
        app’s current behavior and this website’s preferences. It is based on
        the project’s{" "}
        <a href={`${REPO}/blob/main/PRIVACY.md`}>data and privacy record</a>; it
        is not a store privacy certification.
      </p>
    ),
  },
  {
    id: "website",
    title: "This website",
    body: (
      <>
        <p>
          This website does not include account registration, contact forms, or
          an analytics integration. Choosing a light or dark theme saves that
          preference in your browser’s local storage under{" "}
          <code>aro-theme</code>. Clearing this site’s browser data removes it.
        </p>
        <p>
          App screenshots and font files are served with the website. Links to
          GitHub and other sites take you to services with their own privacy
          policies.
        </p>
      </>
    ),
  },
  {
    id: "device",
    title: "Data on your device",
    body: (
      <p>
        The app stores music metadata, playlists, favorites, listening history,
        settings, artwork caches, and connected-account session information on
        your device. This website does not read your app library or
        connected-account sessions.
      </p>
    ),
  },
  {
    id: "permissions",
    title: "Android permissions",
    body: (
      <p>
        Audio and media permissions let Aro discover songs stored on your phone.
        Notification and foreground-service permissions support playback
        controls and background playback. Optional device and provider features
        may use other permissions. You can manage granted permissions in
        Android’s app settings.
      </p>
    ),
  },
  {
    id: "services",
    title: "Connected services",
    body: (
      <>
        <p>
          YouTube and YouTube Music receive catalog and playback requests.
          Spotify receives sign-in, catalog, and playlist requests. Aro stores a
          Spotify session cookie and matches Spotify metadata to YouTube for
          audio playback; it does not stream Spotify audio.
        </p>
        <p>
          Optional server, cloud, and AI features send requests to the service
          or provider you configure. Those services operate under their own
          terms and privacy policies. Aro is not affiliated with them.
        </p>
      </>
    ),
  },
  {
    id: "backups",
    title: "Backups and exports",
    body: (
      <>
        <p>
          Android backup is enabled. The app’s rules exclude named provider
          preference stores, Spotify session DataStore, and settings DataStore
          because they can contain credentials.
        </p>
        <p>
          A complete account-storage audit remains a release task. Do not assume
          every cache or third-party WebView or TDLib file is excluded. Exported
          backups and logs can contain personal library information.
        </p>
      </>
    ),
  },
  {
    id: "manage",
    title: "Managing your data",
    body: (
      <p>
        Use disconnect or logout controls where available. Android’s app
        settings can clear Aro’s data or uninstall it. Clearing app data removes
        local app state and requires accounts to be reconnected. Exported copies
        and information held by external services are managed separately with
        those services.
      </p>
    ),
  },
  {
    id: "contact",
    title: "Questions and security",
    body: (
      <p>
        For general questions, use <a href={`${REPO}/issues`}>GitHub Issues</a>.
        Remove credentials, account identifiers, private playlist details, and
        sensitive filenames before posting. For security concerns, follow the
        project’s{" "}
        <a href={`${REPO}/blob/main/SECURITY.md`}>security reporting guide</a>.
      </p>
    ),
  },
];
export default function Privacy() {
  return (
    <PolicyPage
      title="Privacy policy"
      intro="What stays on your device, what connected services receive, and how to manage your data."
      sections={sections}
    />
  );
}
