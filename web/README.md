# Aro website

A responsive Next.js app showcase built around the real Aro player, its artwork colors, and its rounded typography. The site runs locally; no hosted deployment is configured.

## Development

From `web/`, with Bun installed:

```sh
bun install --frozen-lockfile
bun run dev
```

Open the printed local URL (normally `http://localhost:3000`).

## Checks and production preview

```sh
bun run lint
bunx tsc --noEmit
bun run build
bun run start
```

Stop the development server before building, because development and production builds share `.next/`. ESLint excludes installed agent skills and checks application source and configuration.

## Content and design

- The opening preserves Aro’s rounded heading face and its tonal heading panel. The paired real player/Home phones sit on the page without a colored backdrop, using shared relative proportions at every width. Original SVG record, waveform, and playlist illustrations explain the three music sources.
- Preview choices are native buttons with `aria-pressed`; Tab, Enter, and Space work without a custom keyboard mode. Changes announce through a polite live region.
- The page explains local playback, YouTube Music discovery, and Spotify playlist matching without implying Spotify audio streaming or automatic playlist synchronization.
- Header, hero, and download actions link directly to the maintainer-provided versioned APK. A separate link opens release notes and checksums. Android 11+ ARM64 requirements and installation steps remain visible. The URLs are shared in `app/project-links.ts`; update the APK and release links together when a new candidate is available.
- Questions use native `details` / `summary`. Navigation, downloads, and questions work without JavaScript. The initial preview remains visible, with a no-JavaScript explanation of the other screens.
- CSS includes visible focus, narrow-phone layouts, forced-color support, and reduced-motion behavior. The opening uses a short entrance. Preview changes use a 150ms panel slide/crossfade, selected choices include a checkmark, and question controls crossfade between plus/minus icons. Lenis smooths wheel and local anchor scrolling; touch remains native. Reduced motion destroys the Lenis instance and disables movement. Motion adds a shared selection indicator to the screen choices; there is no scroll pinning or autoplay.

`app/globals.css` defines the visual tokens and responsive rules. `app/transitions.css` preserves the tokens and panel/icon recipes from the installed [transitions-dev skill](.agents/skills/transitions-dev/SKILL.md); its MIT notice is in `TRANSITIONS_LICENSE.txt`. The skill is installed in this project and is available to later agent turns. `app/screen-explorer.tsx` owns preview selection; all four images stay mounted, with inactive panels hidden from assistive technology. Rapid changes retarget CSS transitions without delayed state updates. `app/questions.tsx` enhances native disclosure icons while preserving native open/close behavior. `public/` contains existing Aro screenshots and the app icon. `DESIGN.md` records the design direction.

`app/fonts/aro-rounded-latin.woff2` is a Latin subset derived from the repository’s Google Sans Flex font: roundness is fixed at 100, optical size at 18, width at 100, grade and slant at zero; weight remains variable. The derivative family is renamed Aro Rounded, embedded original notices are retained, and `app/fonts/OFL.txt` contains the SIL Open Font License. This font license does not resolve the app’s component licensing; see the repository’s `LICENSE_STATUS.md`. Artwork and service names belong to their respective owners.

The header includes a light/dark theme control, stored locally as `aro-theme`. A small head script restores the chosen theme before rendering, and the control uses the skill’s icon-swap hooks.

`/privacy` and `/terms` are real pages, with the shared header/footer and in-page navigation. Privacy follows `PRIVACY.md`; Terms summarize the current development release, third-party services, and existing licensing status without granting new redistribution rights.

Dependencies include [Lenis](https://github.com/darkroomengineering/lenis) and [Motion](https://motion.dev), installed through Bun and frozen by `bun.lock`.

Set canonical and social-image URLs only after a real hosting domain is chosen. Current metadata intentionally avoids treating the GitHub repository as the website’s domain.

At 1050px and below the header shows the brand, Get Aro, and a hamburger. The menu contains navigation, policy links, GitHub, and Appearance. Its Transitions.dev dropdown/icon recipes honor reduced motion. Closed menus are inert and hidden from assistive technology. Escape restores focus to the trigger; outside interaction, leaving the menu with Tab, link selection, route changes, and switching to desktop close it. Reopening cancels the close timer. Footer branding and supporting copy share matching column edges and stack on narrow phones.
