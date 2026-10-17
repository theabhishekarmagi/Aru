import React, { useState } from "react";
import {
  ArrowUpRight,
  ArrowRight,
  Check,
  ChevronDown,
  Camera,
  Mic,
  Plus,
  Bookmark,
  SlidersHorizontal,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import "./landing.css";

const journal = "/?view=journal";
const examples = [
  {
    name: "A little of everything",
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
  {
    name: "A slow morning",
    meals: [
      ["Two eggs on buttered toast", "340"],
      ["A cappuccino", "90"],
      ["A banana on the way out", "105"],
    ],
    calories: "535",
    protein: "23",
    carbs: "61",
    fat: "22",
  },
];
function OpenJournal({ children = "Start your journal", className = "" }) {
  return (
    <a className={`landing-cta ${className}`} href={journal}>
      {children}
      <ArrowUpRight size={19} aria-hidden="true" />
    </a>
  );
}
function Notebook({ example }) {
  return (
    <div
      className="landing-notebook"
      aria-label="Illustrative Aru journal preview"
    >
      <div className="notebook-top">
        <img src="/aru-logo.png" alt="Aru" />
        <span>
          Today <ChevronDown size={13} />
        </span>
        <span className="notebook-count">3 meals</span>
      </div>
      <div className="notebook-lines">
        {example.meals.map(([meal, cal]) => (
          <div key={meal}>
            <span>{meal}</span>
            <span>
              {cal} <small>cal</small>
            </span>
          </div>
        ))}
        <div className="notebook-placeholder">
          Anything else? <span className="preview-caret" />
        </div>
      </div>
      <div className="notebook-totals">
        <strong>
          🔥 {example.calories} <small>cal</small>
        </strong>
        <div>
          <span>
            <i className="protein-dot" />
            {example.protein}g <small>protein</small>
          </span>
          <span>
            <i className="carbs-dot" />
            {example.carbs}g <small>carbs</small>
          </span>
          <span>
            <i className="fat-dot" />
            {example.fat}g <small>fat</small>
          </span>
        </div>
      </div>
      <div className="notebook-actions" aria-hidden="true">
        <Mic size={17} />
        <Plus size={20} />
        <Camera size={18} />
      </div>
    </div>
  );
}
export default function Landing() {
  const [sample, setSample] = useState(0);
  return (
    <div className="landing">
      <header className="landing-nav">
        <a className="landing-brand" href="/" aria-label="Aru home">
          <img src="/aru-logo.png" alt="Aru" />
          <span>food journal</span>
        </a>
        <nav aria-label="Main navigation">
          <a href="#how-it-works">How it works</a>
          <a href="#features">Features</a>
        </nav>
        <OpenJournal className="nav-cta">Open Aru</OpenJournal>
      </header>
      <main>
        <section className="landing-hero">
          <div className="hero-copy">
            <p className="landing-eyebrow">
              <span /> A LITTLE SPACE FOR YOUR EVERYDAY
            </p>
            <h1>
              Your food.
              <br />
              Your words.
              <br />
              <em>A little clarity.</em>
            </h1>
            <p className="hero-description">
              A food journal that feels like a notebook. Write what you ate, and
              let Aru help with the calories and macros.
            </p>
            <OpenJournal />
            <p className="hero-footnote">
              Open in your browser · Sign in with Google
            </p>
            <a className="hero-discover" href="#how-it-works">
              Meet your new daily habit <ArrowRight size={16} />
            </a>
          </div>
          <div className="hero-product">
            <div className="hero-orbit" />
            <div className="floating-note note-one">
              <Sparkles size={16} /> Just write. Aru does the maths.
            </div>
            <Notebook example={examples[sample]} />
            <div className="floating-note note-two">
              <Check size={16} /> Room for real life.
            </div>
            <div
              className="preview-selector"
              aria-label="Choose an example journal"
            >
              {examples.map((ex, index) => (
                <button
                  key={ex.name}
                  aria-pressed={index === sample}
                  onClick={() => setSample(index)}
                >
                  {ex.name}
                </button>
              ))}
            </div>
            <p className="preview-caption">
              Example entries · Nutrition values are illustrative
            </p>
          </div>
        </section>
        <div className="landing-principles">
          <span>Less tapping.</span>
          <span>More living.</span>
          <span>A journal that fits your day.</span>
        </div>
        <section id="how-it-works" className="landing-how landing-section">
          <div className="landing-section-heading">
            <p className="landing-eyebrow">A SMALL HABIT. A CLEARER PICTURE.</p>
            <h2>
              From “what did I eat?”
              <br />
              to <em>all written down.</em>
            </h2>
          </div>
          <div className="landing-steps">
            <article>
              <span className="step-number">01 / WRITE</span>
              <h3>Start with your words.</h3>
              <p>
                “Dal, rice and a little pickle.” Type your meal just as you’d
                describe it to a friend.
              </p>
              <div className="step-example">
                Dal, rice and a little pickle
                <span className="preview-caret" />
              </div>
            </article>
            <article>
              <span className="step-number">02 / UNDERSTAND</span>
              <h3>Let Aru fill in the details.</h3>
              <p>
                Get an estimate of calories, protein, carbs and fat. Open the
                breakdown to see each item.
              </p>
              <div className="step-example estimate-example">
                <Sparkles size={17} />
                <span>Nutrition, a little clearer.</span>
              </div>
            </article>
            <article>
              <span className="step-number">03 / KEEP GOING</span>
              <h3>See your day take shape.</h3>
              <p>
                Your totals come together as you log. Set your goals and return
                whenever you’re ready.
              </p>
              <div className="step-example progress-example">
                <span />
                <span />
                <span />
                <span />
                <span />
              </div>
            </article>
          </div>
        </section>
        <section id="features" className="landing-features landing-section">
          <div className="landing-section-heading">
            <p className="landing-eyebrow">THOUGHTFULLY SIMPLE</p>
            <h2>
              A little less effort.
              <br />
              <em>A lot more useful.</em>
            </h2>
            <p>Small details that make keeping a journal feel natural.</p>
          </div>
          <div className="landing-feature-grid">
            <article className="feature-saved">
              <div className="feature-art saved-art" aria-hidden="true">
                <div>
                  <span>YOUR USUALS</span>
                  <Bookmark size={17} />
                </div>
                <p>
                  My morning coffee <Plus size={17} />
                </p>
                <p>
                  Dal & rice <Plus size={17} />
                </p>
                <p>
                  Eggs on toast <Plus size={17} />
                </p>
              </div>
              <div className="feature-copy">
                <Bookmark size={21} />
                <h3>Your favourites, on repeat.</h3>
                <p>
                  Save the meals you come back to. Add them again without
                  writing everything from scratch.
                </p>
              </div>
            </article>
            <article className="feature-goals">
              <div className="feature-art goals-art" aria-hidden="true">
                <span>YOUR DAY, AT A GLANCE</span>
                <strong>
                  1,420 <small>/ 2,000 cal</small>
                </strong>
                <div className="goal-track">
                  <span />
                </div>
                <div className="goal-macros">
                  <span>
                    Protein <b>68g</b>
                  </span>
                  <span>
                    Carbs <b>174g</b>
                  </span>
                  <span>
                    Fat <b>49g</b>
                  </span>
                </div>
              </div>
              <div className="feature-copy">
                <SlidersHorizontal size={21} />
                <h3>A rhythm of your own.</h3>
                <p>
                  Choose your daily calorie and macro goals. See your running
                  totals in one quiet place.
                </p>
              </div>
            </article>
            <article className="feature-details">
              <div className="feature-art details-art" aria-hidden="true">
                <div>
                  <span>Oats with milk</span>
                  <span>240 cal</span>
                </div>
                <div>
                  <span>Portion</span>
                  <span className="portion-control">
                    − <b>1 bowl</b> +
                  </span>
                </div>
                <p>
                  <Check size={14} /> Your journal. Your corrections.
                </p>
              </div>
              <div className="feature-copy">
                <Check size={21} />
                <h3>Estimates you can edit.</h3>
                <p>
                  Portions aren’t always exact. Review the items and adjust
                  their nutrition when you know more.
                </p>
              </div>
            </article>
          </div>
          <div className="landing-extras">
            <article>
              <Camera />
              <h3>A photo can be a start.</h3>
              <p>
                Capture your meal for a nutrition estimate, then check the
                details.
              </p>
            </article>
            <article>
              <Mic />
              <h3>Say it in your own words.</h3>
              <p>
                Use dictation where your browser supports it, or simply type.
              </p>
            </article>
            <article>
              <ShieldCheck />
              <h3>A space of your own.</h3>
              <p>
                Sign in with Google to keep your cloud journal tied to your
                account.
              </p>
            </article>
          </div>
          <p className="landing-estimate-note">
            Aru provides nutrition estimates. Ingredients and portions vary, so
            review and adjust them as needed.
          </p>
        </section>
        <section className="landing-anywhere landing-section">
          <div>
            <p className="landing-eyebrow">
              SAME JOURNAL. YOUR FAVOURITE SCREEN.
            </p>
            <h2>
              A quiet moment.
              <br />
              <em>Wherever you are.</em>
            </h2>
            <p>
              At your desk or on the go, open Aru in your browser and pick up
              your web journal. On iPhone, add it to your Home Screen for a
              shortcut to your day.
            </p>
            <OpenJournal>Make a little space</OpenJournal>
          </div>
          <div className="anywhere-art" aria-hidden="true">
            <div className="mini-window">
              <div>
                <i />
                <i />
                <i />
              </div>
              <img src="/aru-logo.png" alt="" />
              <p>
                A little space for
                <br />
                everything you eat.
              </p>
              <span>Today, in your own words.</span>
            </div>
            <div className="mini-phone">
              <img src="/aru-logo.png" alt="" />
              <span>Today</span>
              <p>
                Breakfast with
                <br />a slow coffee.
              </p>
              <div>🔥 340 cal</div>
            </div>
          </div>
        </section>
        <section className="landing-faq landing-section">
          <div className="landing-section-heading">
            <p className="landing-eyebrow">GOOD TO KNOW</p>
            <h2>
              A few little <em>answers.</em>
            </h2>
          </div>
          {[
            [
              "Can I use Aru on my iPhone?",
              "Yes. Open the web app in Safari and sign in with Google. You can also use Safari’s Share menu to add Aru to your Home Screen. No APK is needed.",
            ],
            [
              "How does Aru work out nutrition?",
              "Aru estimates nutrition from your meal description or photo. Estimates can be wrong, especially when portions or ingredients are unclear. Review the breakdown and edit individual items when needed.",
            ],
            [
              "Will my journal be saved?",
              "When you’re signed in and online, your web journal is saved to your account. Sign in with the same Google account to return to it on another browser. Wait for pending saves before closing the page.",
            ],
            [
              "Does my Android journal appear here?",
              "The Android app currently stores its journal locally. Those existing entries do not automatically sync to the web journal.",
            ],
          ].map(([question, answer]) => (
            <details key={question}>
              <summary>
                {question}
                <Plus size={20} aria-hidden="true" />
              </summary>
              <p>{answer}</p>
            </details>
          ))}
        </section>
        <section className="landing-final">
          <img src="/aru-logo.png" alt="Aru" />
          <h2>
            Make room for
            <br />
            <em>your everyday.</em>
          </h2>
          <p>Your next meal is a good place to start.</p>
          <OpenJournal />
        </section>
      </main>
      <footer className="landing-footer">
        <a className="landing-brand" href="/" aria-label="Aru home">
          <img src="/aru-logo.png" alt="Aru" />
          <span>A little space for everything you eat.</span>
        </a>
        <div>
          <a href="#how-it-works">How it works</a>
          <a href="#features">Features</a>
          <a href={journal}>
            Open journal <ArrowUpRight size={14} />
          </a>
        </div>
        <p>© {new Date().getFullYear()} Aru</p>
      </footer>
    </div>
  );
}
