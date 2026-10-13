"use client";

import { useEffect } from "react";
import Lenis from "lenis";
import { usePathname } from "next/navigation";

export default function SmoothScroll() {
  const pathname = usePathname();
  useEffect(() => {
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let lenis: Lenis | undefined;
    function sync() {
      lenis?.destroy();
      lenis = undefined;
      if (!preference.matches)
        lenis = new Lenis({
          autoRaf: true,
          lerp: 0.09,
          smoothWheel: true,
          syncTouch: false,
          anchors: false,
          stopInertiaOnNavigate: true,
        });
    }
    function lenisOffset(offset: number) {
      // Lenis already subtracts CSS scroll-padding when measuring element targets.
      return (
        offset +
        (Number.parseFloat(
          getComputedStyle(document.documentElement).scrollPaddingTop,
        ) || 0)
      );
    }
    function onClick(event: MouseEvent) {
      if (
        !lenis ||
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
      )
        return;
      const link =
        event.target instanceof Element
          ? event.target.closest<HTMLAnchorElement>("a[href]")
          : null;
      if (!link || link.download || (link.target && link.target !== "_self"))
        return;
      const url = new URL(link.href, location.href);
      if (
        url.origin !== location.origin ||
        url.pathname !== location.pathname ||
        url.search !== location.search ||
        !url.hash
      )
        return;
      let target: HTMLElement | null;
      try {
        target = document.getElementById(decodeURIComponent(url.hash.slice(1)));
      } catch {
        return;
      }
      if (!target) return;
      event.preventDefault();
      const destination = target;
      lenis.resize();
      const offset =
        -(
          document.querySelector(".site-header")?.getBoundingClientRect()
            .height ?? 0
        ) - 16;
      lenis.scrollTo(destination, {
        offset: lenisOffset(offset),
        duration: 0.75,
        onComplete: () => {
          if (location.hash !== url.hash) history.pushState(null, "", url.hash);
          if (!destination.hasAttribute("tabindex"))
            destination.setAttribute("tabindex", "-1");
          destination.focus({ preventScroll: true });
        },
      });
    }
    sync();
    let disposed = false;
    const initialHash = location.hash;
    const initialY = window.scrollY;
    if (initialHash)
      void document.fonts.ready.then(() => {
        if (
          disposed ||
          location.hash !== initialHash ||
          Math.abs(window.scrollY - initialY) > 10
        )
          return;
        let target: HTMLElement | null;
        try {
          target = document.getElementById(
            decodeURIComponent(initialHash.slice(1)),
          );
        } catch {
          return;
        }
        if (!target) return;
        const offset =
          -(
            document.querySelector(".site-header")?.getBoundingClientRect()
              .height ?? 0
          ) - 16;
        if (lenis) {
          lenis.resize();
          lenis.scrollTo(target, {
            offset: lenisOffset(offset),
            immediate: true,
          });
        } else
          window.scrollTo({
            top: window.scrollY + target.getBoundingClientRect().top + offset,
            behavior: "instant",
          });
      });
    preference.addEventListener("change", sync);
    document.addEventListener("click", onClick, true);
    return () => {
      disposed = true;
      preference.removeEventListener("change", sync);
      document.removeEventListener("click", onClick, true);
      lenis?.destroy();
    };
  }, [pathname]);
  return null;
}
