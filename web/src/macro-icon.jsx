import React from "react";
// Shared food symbols for macro labels; colours follow the nutrition reference.
export function MacroIcon({ kind, size = 16 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      className="macro-icon"
    >
      {kind === "proteinG" ? (
        <>
          <path
            fill="#f2bf18"
            d="M2 6 8 10C12 3 20 5 23 12 20 19 12 21 8 14L2 18 4 12Z"
          />
          <circle fill="#fff" cx="18" cy="11" r="1" />
        </>
      ) : kind === "carbsG" ? (
        <>
          <path
            fill="#ff3868"
            d="M12 7C3 2 1 9 3 15 5 23 9 23 12 21 15 23 19 23 21 15 23 9 21 2 12 7Z"
          />
          <path fill="#45ac70" d="M12 6C12 2 16 1 18 2 17 5 15 6 12 6Z" />
        </>
      ) : kind === "fatG" ? (
        <path
          fill="#d92ce6"
          d="M12 2C10 7 5 10 5 15 5 24 19 24 19 15 19 10 14 7 12 2Z"
        />
      ) : null}
    </svg>
  );
}
