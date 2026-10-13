export default function MusicArtwork({
  kind,
}: {
  kind: "record" | "discovery" | "playlist";
}) {
  if (kind === "record")
    return (
      <svg
        viewBox="0 0 600 320"
        fill="none"
        aria-hidden="true"
        className="music-artwork"
      >
        <path fill="#bccbfa" d="M0 0h600v320H0z" />
        <path
          d="M-40 270C70 270 35 80 160 80S250 265 365 265 470 90 640 90"
          stroke="#859dea"
          strokeWidth="85"
        />
        <circle cx="300" cy="160" r="132" fill="#263657" />
        <circle cx="300" cy="160" r="111" stroke="#53678f" strokeWidth="2" />
        <circle cx="300" cy="160" r="92" stroke="#53678f" strokeWidth="2" />
        <circle cx="300" cy="160" r="73" stroke="#53678f" strokeWidth="2" />
        <circle cx="300" cy="160" r="51" fill="#e8b6d0" />
        <circle cx="300" cy="160" r="8" fill="#263657" />
        <path
          d="M194 117a115 115 0 0 1 72-66m66 218a115 115 0 0 0 72-66"
          stroke="#8d9ab5"
          strokeWidth="7"
          strokeLinecap="round"
        />
      </svg>
    );
  if (kind === "discovery")
    return (
      <svg
        viewBox="0 0 600 320"
        fill="none"
        aria-hidden="true"
        className="music-artwork"
      >
        <path fill="#e8b6d0" d="M0 0h600v320H0z" />
        <path
          d="M-30 50c160 0 120 230 310 230S425 35 630 35"
          stroke="#cb80a4"
          strokeWidth="110"
        />
        {[
          { x: 100, h: 74 },
          { x: 160, h: 132 },
          { x: 220, h: 208 },
          { x: 280, h: 268 },
          { x: 340, h: 188 },
          { x: 400, h: 122 },
          { x: 460, h: 64 },
        ].map(({ x, h }) => (
          <rect
            key={x}
            x={x}
            y={(320 - h) / 2}
            width="40"
            height={h}
            rx="20"
            fill="#683854"
          />
        ))}
      </svg>
    );
  return (
    <svg
      viewBox="0 0 600 320"
      fill="none"
      aria-hidden="true"
      className="music-artwork"
    >
      <path fill="#c5dfd3" d="M0 0h600v320H0z" />
      <circle cx="540" cy="40" r="170" fill="#a5c8b9" />
      <circle cx="10" cy="340" r="190" fill="#a5c8b9" />
      <g transform="rotate(-9 300 160)">
        <rect x="143" y="37" width="312" height="247" rx="28" fill="#6c9584" />
      </g>
      <g transform="rotate(6 300 160)">
        <rect x="161" y="50" width="280" height="222" rx="25" fill="#254b40" />
        {[90, 150, 210].map((y, i) => (
          <g key={y}>
            <rect
              x="183"
              y={y - 15}
              width="42"
              height="42"
              rx="12"
              fill={i === 1 ? "#e8b6d0" : "#bccbfa"}
            />
            <path
              d={`M244 ${y}h143M244 ${y + 13}h${i === 1 ? 80 : 108}`}
              stroke="#c5dfd3"
              strokeWidth="8"
              strokeLinecap="round"
            />
          </g>
        ))}
      </g>
    </svg>
  );
}
