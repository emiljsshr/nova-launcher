
import "./PixelButton.css";


type PixelButtonProps = {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  type?: "button" | "submit" | "reset";
  "aria-expanded"?: boolean;
};


export default function PixelButton({
  children,
  onClick,
  disabled = false,
  className = "",
  type = "button",
  "aria-expanded": ariaExpanded,
}: PixelButtonProps) {
  return (
    <button
      type={type}
      className={`pixel-button ${className}`}
      onClick={onClick}
      disabled={disabled}
      aria-expanded={ariaExpanded}
    >
      <span className="pixel-button__text">{children}</span>
    </button>
  );
}