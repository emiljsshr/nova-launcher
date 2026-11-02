import { useEffect, useState } from "react";
import { heroSlides, type HeroSlide } from "../data/launcherData";
import { inTauri, launcher } from "../lib/launcher";

export type NewsSlide = HeroSlide & {
  // Wohin der zweite Banner-Button führt
  action?: "COSMETICS" | "FRIENDS" | "PARTNERS";
};

export type PatchNote = {
  version: string;
  date: string;
  title: string;
  sections: { title: string; items: string[] }[];
};

export type News = {
  slides: NewsSlide[];
  patchNotes: PatchNote[];
};

// Bis der Nova-Server antwortet (oder wenn er nicht erreichbar ist) gelten die eingebauten Banner
const fallback: News = { slides: heroSlides, patchNotes: [] };

/** News-Banner und Patchnotes vom Nova-Server (server/data/news.json). */
export function useNews() {
  const [news, setNews] = useState<News>(fallback);

  useEffect(() => {
    if (!inTauri) return;
    let cancelled = false;
    launcher
      .socialServerUrl()
      .then((url) => fetch(`${url}/api/news`))
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: Partial<News>) => {
        if (cancelled) return;
        setNews({
          slides: Array.isArray(data.slides) && data.slides.length > 0 ? data.slides : fallback.slides,
          patchNotes: Array.isArray(data.patchNotes) ? data.patchNotes : [],
        });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  return news;
}
