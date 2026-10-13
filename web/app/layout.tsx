import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "lenis/dist/lenis.css";
import "./globals.css";
import SmoothScroll from "./smooth-scroll";

const aro = localFont({
  src: "./fonts/aro-rounded-latin.woff2",
  variable: "--font-aro",
  weight: "100 900",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Aro Music — Find your next repeat",
  description:
    "Your local collection, YouTube Music discovery, Spotify playlist imports, and artwork-colored playback. Explore Aro Music for Android 11 and newer.",
  applicationName: "Aro Music",
  icons: { icon: "/aro-icon.png", apple: "/aro-icon.png" },
  openGraph: {
    title: "Aro Music — Find your next repeat",
    description:
      "The songs you keep. The ones you haven’t found yet. Together in Aro for Android.",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#121318",
  viewportFit: "cover",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="en"
      className={aro.variable}
      data-theme="dark"
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `try{var t=localStorage.getItem('aro-theme');if(t==='light')document.documentElement.dataset.theme='light'}catch(e){}`,
          }}
        />
      </head>
      <body>
        <SmoothScroll />
        {children}
      </body>
    </html>
  );
}
