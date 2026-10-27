import React, { useEffect, useId, useRef, useState } from "react";
import { X, LoaderCircle } from "lucide-react";
export function IconButton({ label, children, ...props }) {
  return (
    <button
      type="button"
      className="icon-button"
      aria-label={label}
      title={label}
      {...props}
    >
      {children}
    </button>
  );
}
export function Button({ children, secondary = false, ...props }) {
  return (
    <button className={`button ${secondary ? "secondary" : ""}`} {...props}>
      {children}
    </button>
  );
}
export function Sheet({ title, children, close, action, kind = "" }) {
  const ref = useRef(),
    id = useId();
  useEffect(() => {
    const previous = document.activeElement,
      dialog = ref.current;
    dialog.showModal();
    const cancel = (e) => {
      e.preventDefault();
      close();
    };
    dialog.addEventListener("cancel", cancel);
    return () => {
      dialog.removeEventListener("cancel", cancel);
      dialog.close();
      previous?.focus?.();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={`sheet ${kind}`}
      aria-labelledby={id}
      onClick={(e) => {
        if (e.target === ref.current) {
          const b = ref.current.getBoundingClientRect();
          if (
            e.clientX < b.left ||
            e.clientX > b.right ||
            e.clientY < b.top ||
            e.clientY > b.bottom
          )
            close();
        }
      }}
    >
      <div className="drag-handle" />
      <header className="sheet-header">
        <h2 id={id}>{title}</h2>
        {action}
        <IconButton label="Close" onClick={close}>
          <X size={24} />
        </IconButton>
      </header>
      <div className="sheet-content">{children}</div>
    </dialog>
  );
}
export function Field({ label, unit, as: Control = "input", ...props }) {
  return (
    <label className="outlined-field">
      <span>{label}</span>
      <Control aria-label={label} {...props} />
      {unit && <em>{unit}</em>}
    </label>
  );
}
export function Loading() {
  return (
    <div className="loading">
      <LoaderCircle size={30} className="spin" />
      <span>Opening Aru…</span>
    </div>
  );
}
export function ProfileArt({ step }) {
  const canvas = useRef(),
    start = useRef(performance.now());
  const names = [
    "Aru and the name tag",
    "Aru and the birthday cake",
    "Aru measuring height",
    "Aru on the weighing scale",
  ];
  useEffect(() => {
    let frame,
      alive = true;
    const image = new Image();
    image.src = `/profile-${["name", "birthday", "height", "weight"][step]}.png`;
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    image.onload = () => {
      const draw = (time) => {
        if (!alive) return;
        const node = canvas.current,
          ratio = devicePixelRatio || 1;
        node.width = 320 * ratio;
        node.height = 196 * ratio;
        const ctx = node.getContext("2d");
        ctx.scale(ratio, ratio);
        const progress = reduced ? 8 : ((time - start.current) % 6000) / 600,
          pose = Math.min(7, Math.floor(progress)),
          fraction = progress - pose,
          blend = pose === 0 ? 1 : Math.min(1, fraction / 0.22),
          fw = image.width / 4,
          fh = image.height / 2,
          gutter = fh * 0.025;
        const render = (index, opacity) => {
          ctx.globalAlpha = opacity;
          ctx.drawImage(
            image,
            (index % 4) * fw + gutter,
            Math.floor(index / 4) * fh + gutter,
            fw - gutter * 2,
            fh - gutter * 2,
            62,
            0,
            196,
            196,
          );
        };
        if (blend < 1) render(pose - 1, 1 - blend);
        render(pose, blend);
        if (!reduced) frame = requestAnimationFrame(draw);
      };
      draw(performance.now());
    };
    return () => {
      alive = false;
      cancelAnimationFrame(frame);
    };
  }, [step]);
  return (
    <button
      className="profile-art"
      aria-label={`${names[step]}. Replay animation`}
      type="button"
      onClick={() => (start.current = performance.now())}
    >
      <canvas ref={canvas} role="img" aria-label={names[step]} />
    </button>
  );
}
