
import type { ReactNode } from "react";
import "./ui.css";

// Gemeinsame Bausteine für alle Launcher-Seiten

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <span className="page-eyebrow">{eyebrow}</span>
        <h1 className="page-title">{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </header>
  );
}

export function Card({
  title,
  meta,
  children,
  className = "",
  dark = false,
}: {
  title?: string;
  meta?: ReactNode;
  children: ReactNode;
  className?: string;
  dark?: boolean;
}) {
  return (
    <section className={`nova-card ${dark ? "is-dark" : ""} ${className}`}>
      {(title || meta) && (
        <header className="nova-card-header">
          {title && <h2>{title}</h2>}
          {meta && <div className="nova-card-meta">{meta}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

export function Tabs<T extends string>({
  items,
  value,
  onChange,
  label,
}: {
  items: readonly { value: T; label: string; count?: number }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div className="nova-tabs" role="tablist" aria-label={label}>
      {items.map((item) => (
        <button
          key={item.value}
          type="button"
          role="tab"
          aria-selected={item.value === value}
          className={item.value === value ? "is-active" : ""}
          onClick={() => onChange(item.value)}
        >
          {item.label}
          {item.count !== undefined && (
            <span className="nova-tab-count">{item.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={`nova-toggle ${checked ? "is-on" : ""}`}
      onClick={() => onChange(!checked)}
    >
      <span />
    </button>
  );
}

export function SettingRow({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <div className="setting-row">
      <div className="setting-row-copy">
        <strong>{title}</strong>
        {description && <span>{description}</span>}
      </div>
      <div className="setting-row-control">{children}</div>
    </div>
  );
}

export function SearchField({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <label className="nova-search">
      <svg viewBox="0 0 16 16" shapeRendering="crispEdges" aria-hidden="true">
        <path
          fill="currentColor"
          d="M5 1h4v1h1v1h1v4h-1v1h1v1h1v1h1v1h1v2h-2v-1h-1v-1h-1v-1h-1V9h-1v1H5V9H4V8H3V3h1V2h1zm0 2v1H4v3h1v1h4V7h1V4H9V3z"
        />
      </svg>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
      />
    </label>
  );
}

export function CoinIcon({ size = 16 }: { size?: number }) {
  return (
    <svg
      className="coin-icon"
      width={size}
      height={size}
      viewBox="0 0 8 8"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <path fill="#1d1d23" d="M2 0h4v1h1v1h1v4H7v1H6v1H2V7H1V6H0V2h1V1h1z" />
      <path fill="#bebec7" d="M2 1h4v1h1v4H6v1H2V6H1V2h1z" />
      <path fill="#ececf5" d="M2 2h1v1H2zm1-1h2v1H3z" />
      <path fill="#47474f" d="M3 3h2v2H3z" />
    </svg>
  );
}

export function formatNumber(value: number) {
  return value.toLocaleString("de-DE");
}
