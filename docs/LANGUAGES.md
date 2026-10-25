# Input and display languages

English is the default. The landing page, including its navigation, upload controls and errors, stays English even while another language is entered. Language detection runs on submission (Enter or the submit button), never while typing; Shift+Enter only inserts a newline. The detected language is sent to the report analysis endpoint; report UI labels and subsequent chatbot responses follow input language.

Supported codes: `en`, `ko`, `zh` (Simplified Chinese UI), `ja`, `es`, `fr`, `de`, `pt`, `ru`, `hi`. This is the product's supported set, not a global language-popularity ranking.

Detection runs locally in `frontend/src/language.js`: course codes are removed, distinctive scripts are checked, short common words are scored, and Latin-script phrases use [franc-min](https://github.com/wooorm/franc). Short or ambiguous inputs preserve the previous language. Detection is heuristic; short phrases and mixed-language inputs can be misidentified. No input is sent to an external translation service merely by typing.

`i18n.js` contains Korean UI translations and `locales/translations.js` contains the other eight translated dictionaries. These are static, machine-assisted translations. A missing key falls back to English. Official names, course codes, exact report quotations and already-saved AI text remain original; changing input language does not rewrite old report descriptions or conversation messages. New analysis explanations and chat answers are requested in the selected language. Demo responses are explicitly illustrative.

The backend accepts only the ten codes. `SupportedLanguages` maps codes to the language names used in the analysis prompt; the shared Gemini/Luminal chat prompt follows the latest question and uses the code for neutral input. No database schema changes or new environment variables.

## Verification

`npm --prefix frontend test` checks all ten language examples, neutral inputs, fixed-English home rendering, full dictionary key coverage and placeholders. Backend `SupportedLanguagesTest` checks accepted/rejected chat codes. Optional `LIONPATHWAY_LANGUAGE_LIVE_TEST=1` with Maven `-Dtest=LiveLanguagesTest` exercises French and Japanese Luminal replies using a synthetic earned-credit question. This does not certify translation accuracy across all ten languages. A more ambiguous Japanese remaining-credit question produced an after-enrollment number; the language smoke test uses an explicit earned-credit question to isolate language behavior. Academic answers still require checking the report's numerical summary.
