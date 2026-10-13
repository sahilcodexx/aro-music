"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "./icons";
import ThemeToggle from "./theme-toggle";
import { REPO_URL } from "./project-links";

function MobileMenuControls() {
  const [state, setState] = useState<"closed" | "open" | "closing">("closed");
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLElement>(null);
  const timer = useRef<number | undefined>(undefined);
  const frame = useRef<number | undefined>(undefined);
  const open = state === "open";

  const closeMenu = useCallback((restoreFocus = false) => {
    window.clearTimeout(timer.current);
    setState("closing");
    if (restoreFocus) trigger.current?.focus();
    const duration = window.matchMedia("(prefers-reduced-motion: reduce)")
      .matches
      ? 0
      : Number.parseFloat(
          getComputedStyle(document.documentElement).getPropertyValue(
            "--dropdown-close-dur",
          ),
        ) || 150;
    timer.current = window.setTimeout(() => setState("closed"), duration);
  }, []);

  function openMenu() {
    window.clearTimeout(timer.current);
    setState("open");
    frame.current = requestAnimationFrame(() => {
      if (trigger.current?.getAttribute("aria-expanded") === "true")
        panel.current?.querySelector<HTMLAnchorElement>("a")?.focus();
    });
  }

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target))
        closeMenu();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeMenu(true);
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open, closeMenu]);

  useEffect(() => {
    const desktop = window.matchMedia("(min-width: 1051px)");
    const change = () => {
      if (desktop.matches) {
        window.clearTimeout(timer.current);
        setState("closed");
      }
    };
    desktop.addEventListener("change", change);
    return () => {
      desktop.removeEventListener("change", change);
      window.clearTimeout(timer.current);
      if (frame.current !== undefined) cancelAnimationFrame(frame.current);
    };
  }, []);

  return (
    <div
      className="mobile-menu"
      ref={root}
      onBlurCapture={() => {
        // Check settled focus: relatedTarget can be retargeted while focus moves into the panel.
        requestAnimationFrame(() => {
          if (
            trigger.current?.getAttribute("aria-expanded") === "true" &&
            root.current &&
            !root.current.contains(document.activeElement)
          )
            closeMenu();
        });
      }}
    >
      <button
        ref={trigger}
        type="button"
        className="menu-trigger"
        aria-label={open ? "Close menu" : "Open menu"}
        aria-expanded={open}
        aria-controls="mobile-navigation"
        onClick={() => (open ? closeMenu(true) : openMenu())}
      >
        <span
          className="t-icon-swap"
          data-state={open ? "b" : "a"}
          aria-hidden="true"
        >
          <span className="t-icon" data-icon="a">
            <Icon name="menu" />
          </span>
          <span className="t-icon" data-icon="b">
            <Icon name="close" />
          </span>
        </span>
      </button>
      <nav
        ref={panel}
        id="mobile-navigation"
        aria-label="Mobile navigation"
        aria-hidden={!open}
        inert={!open}
        data-open={open}
        data-origin="top-right"
        className={`mobile-menu-panel t-dropdown${open ? " is-open" : state === "closing" ? " is-closing" : ""}`}
      >
        <Link href="/#experience" onClick={() => closeMenu()}>
          Explore the app <Icon name="arrow" />
        </Link>
        <Link href="/#questions" onClick={() => closeMenu()}>
          Questions <Icon name="arrow" />
        </Link>
        <Link href="/privacy" onClick={() => closeMenu()}>
          Privacy <Icon name="arrow" />
        </Link>
        <Link href="/terms" onClick={() => closeMenu()}>
          Terms <Icon name="arrow" />
        </Link>
        <a href={REPO_URL} onClick={() => closeMenu()}>
          GitHub <Icon name="external" />
        </a>
        <div className="menu-appearance">
          <span>Appearance</span>
          <ThemeToggle />
        </div>
      </nav>
    </div>
  );
}

export default function MobileMenu() {
  const pathname = usePathname();
  return <MobileMenuControls key={pathname} />;
}
