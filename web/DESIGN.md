# Aro website design

The audience is an Android listener deciding whether Aro fits their collection and how to install it. The primary task is to understand the app, see its real interface, and reach the existing candidate release.

The user's Material reference is [Google's Material website](https://m3.material.io/styles). The requested refinement keeps two defining elements: Aro's rounded heading face and the paired, angled player/Home phone mockups. The surrounding site uses neutral dark tonal surfaces, large rounded containers, generous spacing, and original music illustrations. The documentation site's sidebar is not needed for this landing page's navigation.

## Tokens

| Role | Value |
| --- | --- |
| Page | `#121318` |
| Hero copy surface | `#1d1e24` |
| Cards and content | `#202127` |
| Hero phones / ink on blue | `#13213b` |
| Primary text | `#eeeaf2` |
| Secondary text | `#c3c0cb` |
| App blue / action | `#bdd0ff` |
| Artwork rose | `#e5b5cb` |
| Illustrated blue / rose / mint | `#bccbfa` / `#e8b6d0` / `#c5dfd3` |

Major sections use 32px radii, source cards 28px, question panels 20px, and controls pill shapes. These roles distinguish structural levels rather than applying one radius everywhere. Font loading and the existing rounded family are unchanged. Hero heading size adapts to the inset surface while retaining the two-line desktop treatment.

## Page progression

```text
Brand           Explore / Questions / GitHub           Get Aro

Tonal headline surface                    Original paired phones

Color-changing real screen preview        Segmented choices + explanation

Collection introduction
Local-record illustration / discovery waveform / imported playlist

Download release                          Tonal installation panel

Questions introduction                    Native disclosures with icon swaps

Project, privacy, licensing, and upstream credits
```

On phones the main sections stack. The brand and download stay visible; the compact header moves other actions into a hamburger menu. All controls and text sit within the surface padding. Hero phone artwork and its annotation are measured independently to avoid clipped phone edges or text.

The three source cards are parallel explanations of actual behaviors, with different original SVG illustrations that mean record, discovery waveform, and imported playlist. They are static content rather than fake interactive controls. The site omits invented user counts, testimonials, compatibility promises, and automatic playlist synchronization claims. Numbering appears only in the installation sequence.

## Motion

The installed `transitions-dev` skill supplies its semantic tokens and verbatim panel-reveal/icon-swap CSS in `app/transitions.css`, with the upstream MIT notice retained. Preview-specific tokens reduce movement to 8px and duration to 150ms, keeping frequent choices responsive. Color changes use 300ms; no delayed callback controls selection. All four screen and description layers stay mounted so transitions can reverse or retarget when choices change rapidly. Inactive layers have `aria-hidden`; the selected button has `aria-pressed` plus a visible checkmark. A stable polite status region announces the choice.

Question controls use native `details` and `summary`, with the skill's icon-swap hooks for plus/minus. Native CSS also shows the expanded icon without JavaScript. Reduced-motion mode disables panel and icon transitions, background movement, and the opening entrance. Buttons use brief press feedback; there is no autoplay, continuously moving artwork, or scroll pinning.

## Latest refinement

The phone surface is now transparent; the heading surface stays. Both phones, their rotations, and the waveform use proportions of one frame rather than separate phone/mobile coordinates. The annotation follows the frame in normal flow, so it cannot be cropped by the artwork stage. The scrollbar remains visible with a thin thumb.

Light mode uses `#f7f8fc` for the page, `#e9ebf3` for surfaces, `#20232c` for text, `#565b6b` for secondary text, and `#435d93` for textual accents. Image colors and the app screenshots remain unchanged. The chosen theme persists in local storage; color transitions are suppressed during switching.

Lenis smooths wheel and anchor scrolling, leaves touch native, updates hash/focus on anchor completion, and cleans up on route changes or reduced-motion changes. Motion adds the segmented control’s shared indicator; its movement is disabled for reduced motion. The skill’s CSS still owns image/description swaps and disclosure/theme icons.

Privacy and Terms use the same font, theme, and shell, with responsive in-page navigation and restrained reading widths. Their content comes from the current repository behavior and licenses rather than invented business terms.

The compact menu is a nonmodal navigation disclosure, not an application menu with custom arrow-key rules. It uses native links, moves focus to its first link on open, restores trigger focus on Escape, and removes closed content from keyboard navigation with `inert`. A shared two-column grid aligns footer links with their supporting copy; it stacks at 700px.
