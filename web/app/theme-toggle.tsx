"use client";

import { useEffect, useSyncExternalStore } from "react";
import { Icon } from "./icons";

function subscribe(listener: () => void) {
  window.addEventListener("aro-theme-change", listener);
  return () => window.removeEventListener("aro-theme-change", listener);
}
function getTheme() {
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

export default function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getTheme, () => "dark");
  useEffect(() => {
    const update = () => {
      const meta = document.querySelector('meta[name="theme-color"]');
      const color = theme === "light" ? "#f7f8fc" : "#121318";
      if (meta?.getAttribute("content") !== color)
        meta?.setAttribute("content", color);
    };
    // Next can replace viewport metadata during route navigation.
    const observer = new MutationObserver(update);
    observer.observe(document.head, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["content"],
    });
    update();
    return () => observer.disconnect();
  }, [theme]);
  function toggle() {
    const next = theme === "dark" ? "light" : "dark";
    const root = document.documentElement;
    root.classList.add("theme-switching");
    root.dataset.theme = next;
    try {
      localStorage.setItem("aro-theme", next);
    } catch {
      /* Theme works when browser storage is unavailable. */
    }
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", next === "light" ? "#f7f8fc" : "#121318");
    window.dispatchEvent(new Event("aro-theme-change"));
    void root.offsetHeight;
    requestAnimationFrame(() =>
      requestAnimationFrame(() => root.classList.remove("theme-switching")),
    );
  }
  const label = theme === "dark" ? "Use light theme" : "Use dark theme";
  return (
    <button
      type="button"
      className="theme-toggle"
      aria-label={label}
      title={label}
      onClick={toggle}
    >
      <span
        className="t-icon-swap"
        data-state={theme === "dark" ? "a" : "b"}
        aria-hidden="true"
      >
        <span className="t-icon" data-icon="a">
          <Icon name="sun" />
        </span>
        <span className="t-icon" data-icon="b">
          <Icon name="moon" />
        </span>
      </span>
    </button>
  );
}
