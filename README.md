# Dictée FR — French spelling practice

A small Android app for practising French spelling words, with two games.

## Home screen

Opens to a menu:

- **🔤 Lettres mélangées** — the scrambled-letters game
- **✏️ Dictée sur papier** — listen → write on paper → type it in → get corrected
- **📚 Gérer les mots** — enter / paste / delete the words
- **📷 Scanner une liste** — photograph a word list and add it automatically

Both games draw from the same word list and can be replayed (words are reshuffled).

## Games

### Lettres mélangées
The word is spoken in French. Its letters appear scrambled as tiles; she **drags**
each tile into its slot. Every letter **plays its sound as it lands** (see
*Letter sounds* below). Correct ones lock in with a chime + "Bravo !"; wrong
letters get a tone + "Essaie encore" and slide back. **💡 Indice** drops the next
correct letter into place. **🔊 Épeler** spells the word out slowly. A space in
the target (e.g. *un siècle*) shows as a gap between slot groups — no tile for it.

### Dictée sur papier
Tap **🔊 Écouter le mot**, she writes it on paper, then types what she wrote with
the on-screen letter keys (accents, `-`, `'`, **espace**). **✅ Vérifier**:
- **Right** → a chime + "Bravo !", the word shown in green.
- **Wrong** → a soft tone + "Essaie encore"; the correct spelling is **not**
  shown. Her answer stays visible with the wrong/missing letters in red so she
  can fix them and check again. **💡 Indice** reveals one more letter each tap;
  **🔊 Épeler lentement** spells it out. After 3 wrong tries a **👁 Réponse**
  button appears to reveal the answer.

Using a hint or revealing the answer = solved "avec aide" (no score point;
counted separately).

## Letter sounds

By default each letter is spoken by name using the French text-to-speech voice
("a", "em", "esse"…). To use **real phonics recordings** instead, drop `.mp3`
files into `app/src/main/assets/lettersounds/` and rebuild — see the
`README.txt` in that folder for the exact filenames and links to openly-licensed
sources (Wikimedia Commons "French pronunciation", Lingua Libre, IPA Handbook
audio). Any letter without a clip falls back to the spoken name automatically.

## Scan a word list

**📷 Scanner une liste** → take a photo of the list → on-device OCR (Google ML Kit,
runs offline) extracts one candidate word per line into an editable box → fix any
mistakes → **Ajouter à la liste**. Works best with a printed or clearly written
list, one word per line, flat and well lit. Asks for the camera permission the
first time.

## Requirements on the device

- Android 8.0 (API 26) or newer.
- **French text-to-speech voice** — most devices have it; if not, the app says so.
  Install under *Settings → System → Languages → Text-to-speech output*.
- Works fully offline (games, TTS, OCR). First use of the camera prompts for
  permission.
- The app always uses a light theme, even if the phone is in dark mode.

## Building

Self-contained toolchain lives under `~/android-sdk` (JDK 17 + Android SDK 34).

```bash
./build-apk.sh
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~15–20 MB — the OCR model is
bundled).

## Installing on the device

**Copy the file:** move `app-debug.apk` to the device, tap it in a file manager,
allow "install unknown apps".

**Or over USB:**
```bash
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug build, signed with the standard Android debug key — fine for personal use,
not for the Play Store.
