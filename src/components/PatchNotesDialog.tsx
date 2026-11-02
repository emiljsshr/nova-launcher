import { useEffect } from "react";
import type { PatchNote } from "../hooks/useNews";
import "./PatchNotesDialog.css";

const dateFormat = new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "long", year: "numeric" });

export default function PatchNotesDialog({ notes, onClose }: { notes: PatchNote[]; onClose: () => void }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="patch-notes-backdrop" onClick={onClose}>
      <section
        className="patch-notes pixel-corners"
        role="dialog"
        aria-label="Patch Notes"
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <h2>PATCH NOTES</h2>
          <button type="button" aria-label="Schließen" onClick={onClose}>
            ✕
          </button>
        </header>

        <div className="patch-notes-scroll">
          {notes.length === 0 && <p className="patch-notes-empty">Patch Notes konnten nicht geladen werden.</p>}
          {notes.map((note) => (
            <article key={note.version}>
              <div className="patch-notes-head">
                <span className="patch-notes-version">v{note.version}</span>
                <time dateTime={note.date}>{dateFormat.format(new Date(note.date))}</time>
              </div>
              <h3>{note.title}</h3>
              {note.sections.map((section) => (
                <div key={section.title} className="patch-notes-section">
                  <h4>{section.title.toUpperCase()}</h4>
                  <ul>
                    {section.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
