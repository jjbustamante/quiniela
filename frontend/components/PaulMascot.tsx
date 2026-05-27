/**
 * PaulMascot — the octopus, our mascot. A simple stylized SVG.
 * Six tentacle ovals fanning beneath a round head with two big eyes and a
 * faint smile. Pure CSS-color inputs so each surrounding sticker frame can
 * recolor him without forking the shape.
 *
 * Sizes itself to fill its container — wrap in a fixed-size box (round,
 * starburst, ink border, whatever) and Paul flexes into it.
 */
export function PaulMascot({
  body = "var(--color-accent-red)",
  eye = "#fef9f1",
  ink = "#0a0a0a",
  accent,
  className,
  ariaLabel = "Paul el Pulpo",
}: {
  body?: string;
  eye?: string;
  ink?: string;
  /** Optional dot color painted on tentacles — suction cups. */
  accent?: string;
  className?: string;
  ariaLabel?: string;
}) {
  return (
    <svg
      viewBox="0 0 100 100"
      width="100%"
      height="100%"
      className={className}
      role="img"
      aria-label={ariaLabel}
      style={{ display: "block" }}
    >
      <g>
        <ellipse cx="22" cy="70" rx="6.5" ry="14" fill={body} transform="rotate(-24 22 70)" />
        <ellipse cx="34" cy="76" rx="6.5" ry="15" fill={body} transform="rotate(-12 34 76)" />
        <ellipse cx="44" cy="79" rx="6.5" ry="16" fill={body} transform="rotate(-4 44 79)" />
        <ellipse cx="56" cy="79" rx="6.5" ry="16" fill={body} transform="rotate(4 56 79)" />
        <ellipse cx="66" cy="76" rx="6.5" ry="15" fill={body} transform="rotate(12 66 76)" />
        <ellipse cx="78" cy="70" rx="6.5" ry="14" fill={body} transform="rotate(24 78 70)" />
        {accent && (
          <g fill={accent}>
            <circle cx="22" cy="68" r="1.6" />
            <circle cx="34" cy="74" r="1.6" />
            <circle cx="44" cy="78" r="1.6" />
            <circle cx="56" cy="78" r="1.6" />
            <circle cx="66" cy="74" r="1.6" />
            <circle cx="78" cy="68" r="1.6" />
          </g>
        )}
      </g>
      <circle cx="50" cy="40" r="28" fill={body} />
      <circle cx="40" cy="39" r="6" fill={eye} />
      <circle cx="60" cy="39" r="6" fill={eye} />
      <circle cx="41.5" cy="40" r="2.4" fill={ink} />
      <circle cx="61.5" cy="40" r="2.4" fill={ink} />
      <path
        d="M 44 52 Q 50 56.5 56 52"
        stroke={ink}
        strokeWidth="1.8"
        fill="none"
        strokeLinecap="round"
      />
    </svg>
  );
}

/**
 * PaulBadge — Paul on a red starburst disc, sized by `size` in px.
 * The canonical sticker form used by the masthead and Paul CTAs.
 */
export function PaulBadge({
  size = 32,
  className,
}: {
  size?: number;
  className?: string;
}) {
  // 16-point starburst plus a flat disc behind. Drawn at viewBox 100×100
  // and scaled to `size` via the wrapper's width/height.
  const bursts = Array.from({ length: 16 }).map((_, i) => {
    const a = (i / 16) * Math.PI * 2;
    const r1 = 38, r2 = 48;
    const x1 = 50 + Math.cos(a) * r1, y1 = 50 + Math.sin(a) * r1;
    const a2 = a + Math.PI / 16;
    const x2 = 50 + Math.cos(a2) * r2, y2 = 50 + Math.sin(a2) * r2;
    const a3 = a + Math.PI / 8;
    const x3 = 50 + Math.cos(a3) * r1, y3 = 50 + Math.sin(a3) * r1;
    return (
      <polygon
        key={i}
        points={`${x1},${y1} ${x2},${y2} ${x3},${y3}`}
        fill="var(--color-accent-red)"
      />
    );
  });

  return (
    <div
      className={className}
      style={{ width: size, height: size, position: "relative", flex: "0 0 auto" }}
    >
      <svg
        viewBox="0 0 100 100"
        width={size}
        height={size}
        style={{ position: "absolute", inset: 0 }}
        aria-hidden="true"
      >
        {bursts}
        <circle cx="50" cy="50" r="38" fill="#fef9f1" />
      </svg>
      <div
        style={{
          position: "absolute",
          inset: `${size * 0.1}px`,
        }}
      >
        <PaulMascot
          body="var(--color-accent-red)"
          eye="#fef9f1"
          ink="#0a0a0a"
          accent="var(--color-accent-gold)"
        />
      </div>
    </div>
  );
}
