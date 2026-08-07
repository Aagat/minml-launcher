(() => {
  const root = document.documentElement;
  root.classList.add("js");

  const updateStageScales = () => {
    const width = window.innerWidth;
    const gutter = width <= 420 ? 40 : width <= 720 ? 48 : width <= 1199 ? 96 : 144;
    const available = Math.max(280, width - gutter);

    root.style.setProperty("--hero-stage-scale", Math.min(1, available / 574));
    root.style.setProperty("--device-stage-scale", Math.min(1, available / 520));
    root.style.setProperty("--widget-stage-scale", Math.min(1, available / 650));
  };

  updateStageScales();
  window.addEventListener("resize", updateStageScales, { passive: true });
})();
