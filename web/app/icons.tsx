const paths: Record<string, React.ReactNode> = {
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="m6 6 12 12M18 6 6 18" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" />
    </>
  ),
  moon: <path d="M20 14.5A9 9 0 0 1 9.5 4a9 9 0 1 0 10.5 10.5Z" />,
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  check: <path d="m5 12 4 4 10-10" />,
  download: (
    <>
      <path d="M12 3v12m-5-5 5 5 5-5" />
      <path d="M4 16v4h16v-4" />
    </>
  ),
  external: (
    <>
      <path d="M14 4h6v6m0-6-9 9" />
      <path d="M10 4H4v16h16v-6" />
    </>
  ),
  back: <path d="M20 12H4m6-6-6 6 6 6" />,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  down: <path d="M12 4v16m-6-6 6 6 6-6" />,
  android: (
    <>
      <path d="m6 3 2 3m10-3-2 3M4 15v-4a8 8 0 0 1 16 0v4H4Zm0 0v5h16v-5" />
      <path d="M8 10h.01M16 10h.01" />
    </>
  ),
  music: (
    <>
      <path d="M9 18V5l11-2v13M9 9l11-2" />
      <ellipse cx="6" cy="18" rx="3" ry="3" />
      <ellipse cx="17" cy="16" rx="3" ry="3" />
    </>
  ),
  library: (
    <>
      <path d="M4 4v16M9 4v16m5-16 6 16" />
    </>
  ),
  play: <path d="m9 5 11 7-11 7V5Z" />,
  playlist: (
    <>
      <path d="M3 5h15M3 10h15M3 15h8m6-1v7m-3-3h6" />
    </>
  ),
};
export function Icon({ name }: { name: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
