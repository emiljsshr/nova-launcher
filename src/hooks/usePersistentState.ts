
import { useCallback, useState } from "react";

// useState, der den Wert als JSON im localStorage ablegt.
// Fällt auf den Startwert zurück, wenn nichts (Gültiges) gespeichert ist.
export function usePersistentState<T>(key: string, initialValue: T) {
  const [value, setValue] = useState<T>(() => {
    try {
      const saved = localStorage.getItem(key);
      if (saved === null) return initialValue;

      const parsed = JSON.parse(saved);
      // Objekte mit neuen Feldern ergänzen, damit alte Speicherstände gültig bleiben
      if (
        typeof initialValue === "object" &&
        initialValue !== null &&
        !Array.isArray(initialValue)
      ) {
        return { ...initialValue, ...parsed };
      }
      return typeof parsed === typeof initialValue ? parsed : initialValue;
    } catch {
      return initialValue;
    }
  });

  const update = useCallback(
    (next: T | ((current: T) => T)) => {
      setValue((current) => {
        const resolved =
          typeof next === "function"
            ? (next as (current: T) => T)(current)
            : next;
        try {
          localStorage.setItem(key, JSON.stringify(resolved));
        } catch {
          // Ohne Speicher gilt der Wert nur für diese Sitzung
        }
        return resolved;
      });
    },
    [key],
  );

  return [value, update] as const;
}
