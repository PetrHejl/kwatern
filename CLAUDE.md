# Kwatern

Read-only web viewer for Gramps XML exports, built as a single GraalVM native binary. Decisions,
differences from Gramps and open items live in `docs/NOTES.md`; keep that file current when making
decisions or finding something worth remembering.

## Modules

- `gramps-xml` - StAX parser and immutable model. No dependencies beyond the JDK; keep it that way.
  The records and parser are hand-written. `SchemaCoverageTest` generates a document with every element and
  attribute of Gramps' `grampsxml.rng` (downloaded for the Gramps release set in the root `build.gradle`)
  and fails if the parser skips any of them. When it fails after a Gramps update, read the new element or
  attribute, or skip it on purpose with `ignore(...)` / `ignoreElement()` and a comment saying why.
- `gramps-core` - dates, names, places, i18n and the privacy filter. Uses ICU4J.
- `image` - JPEG, PNG and GIF decoders, a JPEG encoder and image scaling for thumbnails, written from the
  standards. No dependencies and no `java.awt`, so it works in the native binary; keep it that way. Tests
  compare with libjpeg-turbo's `djpeg` and ImageMagick when installed; test images come from the scripts in
  `image/src/test/fixtures`.
- `server` - HTTP server (`jdk.httpserver`) and command line (picocli). Pages are JTE templates in
  `server/src/main/jte`; they only lay out records from `view/Pages`, which `view/Views` fills with
  formatted, translated text. Page texts live in `server/src/main/resources/me/hejl/kwatern/messages*.properties`.
  Signing in lives in `server/.../auth`. With a login, each view is its own filtered `Site` (`Sites`), so pages
  never check who is viewing; keep it that way rather than hiding data in templates.

## Build and test

JDK 25 is required, GraalVM 25 for native builds:

```sh
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-graalce GRAALVM_HOME=$JAVA_HOME
./gradlew test                                   # downloads the Gramps example tree on first run
./gradlew test -PgrampsFile=/path/to/tree.gramps # also runs the checks against a real export
./gradlew nativeTest                             # the same tests compiled into native executables (slow)
./gradlew :server:nativeCompile
server/build/native/nativeCompile/kwatern --check /path/to/tree.gramps
```

After changing anything that may use reflection or resources (new library, ICU features, resource
files), run `./gradlew nativeTest`; the JVM tests do not catch missing Native Image configuration. Also
build the binary and run `--check`, and extend it when a new feature could fail that way. Test setup for
all modules (JUnit, the example tree, native tests) lives in the root `build.gradle`.

## Code style

- Java 25. Use records, sealed interfaces, switch expressions and pattern matching where they fit.
- Lines at most 120 characters. Indent with 4 spaces.
- Never use fully qualified class names in code; import the class. Only use a qualified name when
  two imported classes clash.
- Formatting is enforced by Spotless with Palantir Java Format (`./gradlew spotlessApply`); CI will run
  `spotlessCheck`. It also orders imports and forbids wildcard imports. It does not split long string
  literals; wrap those by hand.
- Model values: optional scalars are `null` when absent, lists are never `null` (copy with `List.copyOf`).
- Comments explain why, not what. When porting logic from Gramps, name the Gramps source, e.g.
  `// Gramps: gen/lib/date.py Date._calc_sort_value`.
- Text sizes only from the `--text-*` tokens in `style.css` (see "Type scale" in `docs/NOTES.md`), in CSS and in
  SVG charts alike. Choose the token by the role of the text, and add a token only for a new role.
  Likewise spaces (`--space-*`), corner radii (`--radius*`) and colours (theme variables, or `--overlay-*` and
  `--map-*` on photos and maps) come only from tokens (see "Visual tokens" in `docs/NOTES.md`). Reuse the
  shared components (`.badge-number`, `.card-note`, uppercase labels, "Show more") and put phone rules
  in the single `@media (max-width: 900px)` block at the end.
- No language-specific code: user-visible text goes into `messages*.properties` files, locale data
  comes from ICU.
- Gradle build scripts use the Groovy DSL.

## Dependencies

Keep them minimal. Ask before adding a library, and prefer the JDK or ICU4J where they suffice. A library or
file that ships in the program (including fonts and scripts) needs an entry in
`server/src/main/resources/me/hejl/kwatern/licenses/NOTICE.txt` and its license text in `Licenses.java`.

## Privacy and test data

- Genealogy exports contain personal data about living people. Never commit `.gramps` or `.gpkg`
  files, and never print names, dates or other personal details from a real export in test output or
  logs; print counts only.
- The privacy defaults must stay safe: without options nothing private and no details of living people
  are published.

## Git

- Commit only when asked. Commit messages: a short imperative subject, then a body explaining what and why.
- Always ask before pushing, every time, even right after a commit the user asked for. Never push the local
  `history` branch (the history before the repository was published).
