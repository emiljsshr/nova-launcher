
import { useState } from "react";
import novaLogo from "../assets/nova-logo.png";
import PixelButton from "./PixelButton";
import { PlayerHead } from "./PixelIcon";
import { PixelBackground, WindowControls } from "./WindowChrome";
import type { NavigationItem } from "../App";
import "./WelcomeScreen.css";

type WelcomeScreenProps = {
  navigationItems: readonly NavigationItem[];
  onFinish: (page: NavigationItem) => void;
};

// Wird nur beim allerersten Start angezeigt
export default function WelcomeScreen({
  navigationItems,
  onFinish,
}: WelcomeScreenProps) {
  const [showGameSelection, setShowGameSelection] = useState(false);

  return (
    <main className="welcome-screen">
      <PixelBackground />

      <header className="welcome-topbar" data-tauri-drag-region>
        <nav className="welcome-navigation" aria-label="Hauptnavigation">
          {navigationItems.map((item) => (
            <button
              key={item}
              type="button"
              className={`nav-link ${item === "HOME" ? "active" : ""}`}
              onClick={() => onFinish(item)}
            >
              {item === "PROFILE" && <PlayerHead size={14} />}
              {item}
            </button>
          ))}
        </nav>

        <WindowControls />
      </header>

      <section className="welcome-content">
        <h1 className="welcome-title">WELCOME TO,</h1>
        <img
          className="welcome-logo"
          src={novaLogo}
          alt="NOVA"
          draggable={false}
        />
      </section>

      {showGameSelection && (
        <div className="game-selection">
          <div className="game-selection-heading">SELECT YOUR GAME</div>

          <button
            type="button"
            className="game-option"
            onClick={() => onFinish("HOME")}
          >
            <span className="game-option-icon">N</span>
            <span className="game-option-copy">
              <strong>NOVA CLIENT</strong>
              <small>Minecraft Java Edition</small>
            </span>
            <span className="game-arrow">›</span>
          </button>
        </div>
      )}

      <footer className="welcome-bottom-bar" aria-hidden="true" />

      <PixelButton
        className="select-game-button"
        aria-expanded={showGameSelection}
        onClick={() => setShowGameSelection((current) => !current)}
      >
        {showGameSelection ? "BACK" : "SELECT GAME"}
      </PixelButton>
    </main>
  );
}
