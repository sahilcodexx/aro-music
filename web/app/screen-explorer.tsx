"use client";

import Image from "next/image";
import { useState } from "react";
import { Icon } from "./icons";
import { LayoutGroup, motion, useReducedMotion } from "motion/react";

const screens = [
  {
    name: "Your home",
    title: "Pick up where you left off.",
    description:
      "Recently played, pinned favorites, and a daily mix. The music you come back to, with fresh discoveries alongside it.",
    src: "/screenshot-mix.png",
    alt: "Aro Home with Your Mix, artwork collage, and personalized music shelves",
    color: "home",
  },
  {
    name: "The player",
    title: "Let the artwork set the mood.",
    description:
      "Colors follow the song, from the player to the navigation. A wavy progress bar, your queue, and playback controls keep everything close.",
    src: "/screenshot-player.jpg",
    alt: "Aro’s plum-colored player with album artwork and playback controls",
    color: "player",
  },
  {
    name: "Your library",
    title: "Everything has its place.",
    description:
      "Songs, artists, albums, favorites, and playlists. Shuffle your collection or find exactly what you came for.",
    src: "/screenshot-library.jpg",
    alt: "Aro Library showing local songs, album artwork, and a shuffle control",
    color: "library",
  },
  {
    name: "The lyrics",
    title: "Know every word.",
    description:
      "Follow along with synced or static lyrics when available. Keep the words and playback together, without leaving the app.",
    src: "/screenshot-lyrics.jpg",
    alt: "Aro’s lyrics view showing song lines and playback progress",
    color: "lyrics",
  },
];

export default function ScreenExplorer() {
  const [selected, setSelected] = useState(0);
  const reducedMotion = useReducedMotion();
  const screen = screens[selected];
  const [announcement, setAnnouncement] = useState("");
  return (
    <div className={`screen-explorer screen-${screen.color}`}>
      <div
        className="explorer-preview"
        id="screen-preview"
        aria-label={`${screen.name} screenshot`}
      >
        <svg
          className="preview-flower"
          viewBox="0 0 440 440"
          aria-hidden="true"
        >
          <g>
            {Array.from({ length: 8 }, (_, i) => (
              <circle
                key={i}
                cx={220 + 130 * Math.cos((i * Math.PI) / 4)}
                cy={220 + 130 * Math.sin((i * Math.PI) / 4)}
                r="87"
              />
            ))}
            <circle cx="220" cy="220" r="150" />
          </g>
        </svg>
        <div className="phone explorer-phone">
          {screens.map((item, index) => (
            <div
              key={item.name}
              className="screen-layer t-panel-slide"
              data-open={selected === index}
              aria-hidden={selected !== index}
            >
              <Image
                src={item.src}
                width={931}
                height={2048}
                sizes="(max-width: 700px) 230px, 260px"
                alt={item.alt}
              />
            </div>
          ))}
        </div>
        <p className="preview-caption">An actual look inside Aro.</p>
      </div>
      <div className="explorer-content">
        <LayoutGroup id="aro-screen-picker">
          <div
            className="screen-picker"
            role="group"
            aria-label="Choose an app screen"
          >
            {screens.map((item, index) => (
              <button
                key={item.name}
                type="button"
                aria-pressed={selected === index}
                aria-controls="screen-preview"
                onClick={() => {
                  setSelected(index);
                  setAnnouncement(`${item.name}. ${item.title}`);
                }}
              >
                {selected === index && (
                  <motion.span
                    className="choice-indicator"
                    layoutId="active-screen"
                    aria-hidden="true"
                    transition={
                      reducedMotion
                        ? { duration: 0 }
                        : { type: "spring", stiffness: 420, damping: 38 }
                    }
                  />
                )}
                <span className="choice-label">
                  <span className="choice-check" aria-hidden="true">
                    <Icon name="check" />
                  </span>
                  {item.name}
                </span>
              </button>
            ))}
          </div>
        </LayoutGroup>
        <div className="screen-description">
          {screens.map((item, index) => (
            <div
              key={item.name}
              className="description-layer t-panel-slide"
              data-open={selected === index}
              aria-hidden={selected !== index}
            >
              <h3>{item.title}</h3>
              <p>{item.description}</p>
            </div>
          ))}
        </div>
        <p className="sr-only" role="status">
          {announcement}
        </p>
        <div className="explorer-footnote">
          <Icon name="music" />
          <span>Your music, with a little more personality.</span>
        </div>
      </div>
      <noscript>
        <p className="no-script-note">
          Enable JavaScript to switch previews. Aro also includes an
          artwork-colored player, an organized local library, and lyrics when
          available.
        </p>
      </noscript>
    </div>
  );
}
