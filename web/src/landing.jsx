import React, { useState } from "react";
import {
  ArrowRight,
  Camera,
  Mic,
  Plus,
  Bookmark,
  SlidersHorizontal,
  ShieldCheck,
  Sparkles,
  ChevronDown,
  Settings,
  Check,
  Globe,
  Menu,
  X,
} from "lucide-react";
import "./landing.css";

const journal = "/?view=journal";
const examples = [
  {
    name: "Breakfast",
    meals: [
      ["Masala dosa & coconut chutney", "380"],
      ["Coffee with a splash of milk", "35"],
    ],
    calories: "415",
    protein: "10",
    carbs: "60",
    fat: "15",
  },
  {
    name: "Lunch",
    meals: [
      ["A bowl of dal, rice & cucumber", "520"],
      ["A glass of buttermilk", "65"],
    ],
    calories: "585",
    protein: "21",
    carbs: "89",
    fat: "17",
  },
  {
    name: "Your day",
    meals: [
      ["Masala dosa & coconut chutney", "380"],
      ["A bowl of dal, rice & cucumber", "520"],
      ["Coffee with a splash of milk", "35"],
    ],
    calories: "935",
    protein: "29",
    carbs: "148",
    fat: "25",
  },
];
function OpenJournal({ children = "Open Aru", className = "" }) {
  return (
    <a className={`landing-cta ${className}`} href={journal}>
      <Globe size={19} aria-hidden="true" />
      {children}
    </a>
  );
}
function Phone({ example = examples[2], className = "" }) {
  return (
    <div
      className={`aru-phone ${className}`}
      aria-label="Illustrative Aru journal preview"
    >
      <div className="phone-status" aria-hidden="true">
        <b>9:41</b>
        <span className="phone-island" />
        <span>▮▮▮ ▰</span>
      </div>
      <div className="phone-journal">
        <div className="phone-journal-header">
          <img src="/aru-logo.png" alt="Aru" />
          <span>
            Today <ChevronDown size={11} />
          </span>
          <span>
            {example.meals.length} <Settings size={12} />
          </span>
        </div>
        <div className="phone-meals">
          {example.meals.map(([meal, cal]) => (
            <div key={meal}>
              <span>{meal}</span>
              <span>
                {cal}
                <small> cal</small>
              </span>
            </div>
          ))}
          <p>
            Anything else?
            <i />
          </p>
        </div>
        <div className="phone-daily">
          <strong>🔥 {example.calories}</strong>
          <span>
            <i className="macro-protein" /> P {example.protein}
          </span>
          <span>
            <i className="macro-carb" /> C {example.carbs}
          </span>
          <span>
            <i className="macro-fat" /> F {example.fat}
          </span>
        </div>
        <div className="phone-tools" aria-hidden="true">
          <Mic size={18} />
          <Plus size={20} />
          <Camera size={19} />
        </div>
      </div>
      <div className="phone-home" aria-hidden="true" />
    </div>
  );
}
function FeatureArt({ type }) {
  if (type === "saved")
    return (
      <div className="feature-demo saved-demo">
        <header>
          Saved meals <Bookmark size={17} />
        </header>
        {["My morning coffee", "Dal & rice", "Eggs on toast"].map((m) => (
          <div key={m}>
            {m}
            <Plus size={17} />
          </div>
        ))}
      </div>
    );
  if (type === "goals")
    return (
      <div className="feature-demo goals-demo">
        <span>Daily goals</span>
        <strong>
          1,420 <small>/ 2,000 cal</small>
        </strong>
        <div className="demo-progress">
          <i />
        </div>
        <div className="demo-macros">
          <span>
            Protein<b>68g</b>
          </span>
          <span>
            Carbs<b>174g</b>
          </span>
          <span>
            Fat<b>49g</b>
          </span>
        </div>
        <p>Every meal adds to your picture.</p>
      </div>
    );
  if (type === "edit")
    return (
      <div className="feature-demo edit-demo">
        <header>
          Oats with milk <b>240 cal</b>
        </header>
        <div>
          Portion <span>− &nbsp; 1 bowl &nbsp; +</span>
        </div>
        <div>
          Protein <span>12 g</span>
        </div>
        <div>
          Carbs <span>36 g</span>
        </div>
        <footer>
          <Check size={14} /> Save your changes
        </footer>
      </div>
    );
  if (type === "photo")
    return (
      <div className="feature-demo photo-demo">
        <div className="camera-corners">
          <span>🍲</span>
        </div>
        <p>Start with a photo.</p>
        <Camera size={24} />
      </div>
    );
  if (type === "voice")
    return (
      <div className="feature-demo voice-demo">
        <Mic size={30} />
        <div className="voice-wave" aria-hidden="true">
          {[13, 25, 38, 18, 45, 29, 48, 22, 35, 16, 26, 12].map((h, i) => (
            <i key={i} style={{ height: h }} />
          ))}
        </div>
        <p>
          “Two eggs on toast
          <br />
          and my morning coffee.”
        </p>
      </div>
    );
  return (
    <div className="feature-demo privacy-demo">
      <img src="/aru-logo.png" alt="" />
      <ShieldCheck size={30} />
      <p>
        Your journal.
        <br />
        <strong>Your own space.</strong>
      </p>
      <span>
        <Check size={13} /> Connected to your account
      </span>
    </div>
  );
}
const features = [
  {
    type: "edit",
    title: "Easy editing",
    text: "Adjust portions and nutrition when you know more about your meal.",
  },
  {
    type: "goals",
    title: "Goals at a glance",
    text: "Choose calorie and macro goals that fit your day.",
  },
  {
    type: "saved",
    title: "Quick saved meals",
    text: "Keep your usuals close. Add favourite meals again in a tap.",
  },
  {
    type: "photo",
    title: "Photo estimates",
    text: "Capture a meal to get started, then review the details.",
  },
  {
    type: "voice",
    title: "Say what you ate",
    text: "Dictate in supported browsers, or simply type your meal.",
  },
  {
    type: "privacy",
    title: "A journal of your own",
    text: "Use Google sign-in to return to your cloud journal.",
  },
];
export default function Landing() {
  const [sample, setSample] = useState(2),
    [menu, setMenu] = useState(false);
  return (
    <div className="landing">
      <header className="landing-nav">
        <a className="landing-brand" href="/" aria-label="Aru home">
          <img src="/aru-logo.png" alt="Aru" />
        </a>
        <nav aria-label="Main navigation" className={menu ? "is-open" : ""}>
          <a href="#features" onClick={() => setMenu(false)}>
            Features
          </a>
          <a href="#accuracy" onClick={() => setMenu(false)}>
            Is it accurate?
          </a>
          <a href="#how-it-works" onClick={() => setMenu(false)}>
            How it works
          </a>
          <a href="#about" onClick={() => setMenu(false)}>
            About
          </a>
        </nav>
        <div className="nav-actions">
          <OpenJournal className="nav-cta" />
          <button
            className="landing-menu"
            onClick={() => setMenu(!menu)}
            aria-label={menu ? "Close navigation" : "Open navigation"}
            aria-expanded={menu}
          >
            {menu ? <X size={23} /> : <Menu size={23} />}
          </button>
        </div>
      </header>
      <main>
        <section className="landing-hero">
          <div className="hero-copy">
            <h1>
              Food journaling,
              <br />
              as simple as
              <br />
              writing a note.
            </h1>
            <p className="hero-description">
              Just write what you ate. Aru helps you find the calories and
              macros, so you can get on with your day.
            </p>
            <OpenJournal>Start your journal</OpenJournal>
            <p className="hero-footnote">
              In your browser. On your phone or desktop.
            </p>
          </div>
          <div className="hero-product">
            <Phone example={examples[sample]} />
            <div
              className="preview-selector"
              aria-label="Choose an example journal"
            >
              {examples.map((e, i) => (
                <button
                  key={e.name}
                  aria-pressed={sample === i}
                  onClick={() => setSample(i)}
                >
                  {e.name}
                </button>
              ))}
            </div>
            <p className="preview-caption">
              Example journal · Illustrative estimates
            </p>
          </div>
        </section>
        <section id="how-it-works" className="landing-how landing-section">
          <div className="how-product">
            <Phone />
            <span className="how-label">Your day, all written down.</span>
          </div>
          <div className="how-copy">
            <h2>
              A food journal
              <br />
              that feels like notes.
            </h2>
            {[
              [
                "01",
                "Write your meal",
                "Jot down what you ate in your own words. No searching through endless food lists.",
              ],
              [
                "02",
                "Let Aru help",
                "Get nutrition estimates for your meal, with an item-by-item breakdown to review.",
              ],
              [
                "03",
                "See your day add up",
                "Calories and macros come together as you log. Keep going at your own pace.",
              ],
            ].map(([n, title, text]) => (
              <article key={n}>
                <span>{n}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </div>
              </article>
            ))}
          </div>
        </section>
        <section id="features" className="landing-features landing-section">
          <div className="landing-section-heading">
            <h2>Small details. Big difference. 💪</h2>
            <p>
              Everything you need to keep a food journal,
              <br />
              without making it a chore.
            </p>
          </div>
          <div className="landing-feature-grid">
            {features.map((f) => (
              <article key={f.type}>
                <div
                  className={`feature-art feature-${f.type}`}
                  aria-hidden="true"
                >
                  <div className="feature-doodles">
                    ✧ &nbsp; + &nbsp; ◇ &nbsp; ✧ &nbsp; + &nbsp; ◦<br />+ &nbsp;
                    ◦ &nbsp; ✧ &nbsp; ◇ &nbsp; ✧<br />◇ &nbsp; ✧ &nbsp; + &nbsp;
                    ◦ &nbsp; ✧
                  </div>
                  <FeatureArt type={f.type} />
                </div>
                <h3>{f.title}</h3>
                <p>{f.text}</p>
              </article>
            ))}
          </div>
        </section>
        <section id="about" className="landing-about landing-section">
          <div>
            <h2>Meet Aru 👋</h2>
            <p>A little space for everything you eat.</p>
            <p>
              Aru brings food journaling back to something familiar: a blank
              page and your own words. From home-cooked dal to a quick coffee,
              it helps you keep track of everyday meals without getting in the
              way.
            </p>
            <p>
              Write, review, and make the journal your own. It’s built for real
              meals and real life.
            </p>
            <a className="landing-text-link" href={journal}>
              Get to know Aru <ArrowRight size={18} />
            </a>
          </div>
          <div className="about-mascot">
            <img
              src="/aru-logo.png"
              alt="Aru’s purple logo and food-loving mascot"
            />
            <span>Your everyday food journal.</span>
          </div>
        </section>
        <section id="accuracy" className="landing-faq landing-section">
          <div className="landing-section-heading">
            <h2>A few things to know.</h2>
            <p>Simple answers before you start.</p>
          </div>
          {[
            [
              "How accurate are the nutrition estimates?",
              "Aru provides estimates, not exact measurements. Ingredients and portions vary, especially for home-cooked meals. Check the breakdown and adjust individual items whenever you know more.",
            ],
            [
              "Can I use Aru on my iPhone?",
              "Yes. Open Aru in Safari and sign in with Google. Add it to your Home Screen from Safari’s Share menu if you’d like a shortcut. No APK is needed.",
            ],
            [
              "Will my journal be saved?",
              "Your web journal is saved to your account when you’re signed in and online. Wait for pending saves before closing the page, then sign in with the same Google account to return on another browser.",
            ],
            [
              "Does my Android journal appear here?",
              "The Android app currently stores its journal locally. Existing Android entries do not automatically sync to the web journal.",
            ],
          ].map(([q, a]) => (
            <details key={q}>
              <summary>
                {q}
                <Plus size={20} aria-hidden="true" />
              </summary>
              <p>{a}</p>
            </details>
          ))}
        </section>
        <section className="landing-final">
          <img src="/aru-logo.png" alt="Aru" />
          <h2>
            Your next meal.
            <br />
            Your first note.
          </h2>
          <p>A simpler way to keep track of your day.</p>
          <OpenJournal>Start your journal</OpenJournal>
          <span>Sign in with Google to get started.</span>
        </section>
      </main>
      <footer className="landing-footer">
        <div>
          <a className="landing-brand" href="/" aria-label="Aru home">
            <img src="/aru-logo.png" alt="Aru" />
          </a>
          <p>A little space for everything you eat.</p>
        </div>
        <nav aria-label="Footer navigation">
          <a href="#features">Features</a>
          <a href="#how-it-works">How it works</a>
          <a href="#accuracy">Accuracy</a>
          <a href={journal}>Open journal</a>
        </nav>
        <p>© {new Date().getFullYear()} Aru</p>
      </footer>
    </div>
  );
}
