# Project notes

Decisions, known differences from Gramps, and open items. Keep this file current as work progresses.

## Status

| Milestone | Scope | State |
|---|---|---|
| 1 | Gramps XML parser and in-memory model | done |
| 2 | Reverse references, dates, privacy filter, place titles, name display, sorting | done |
| 3 | Home, surname index, surname, person and family pages in the chosen design | done |
| 3 | Place and source pages, links between them, search, bundled fonts | done |
| 3 | Places tree and sources list; coordinates read in all Gramps notations | done |
| 3 | Event and note pages: not planned; events and notes are shown in full where they belong | |
| 4 | Own JPEG decoder and encoder, thumbnail scaling (`image` module) | done |
| 4 | Thumbnails and portraits cropped to regions, served by the binary; portrait on person pages | done |
| 4 | Photos on person, family, place and source pages; media page with marked people; Photos listing; larger type | done |
| 4 | `.gpkg` input | done |
| 4 | PNG and GIF decoders | done |
| 5 | Ancestor tree and fan charts, descendant chart, family events and lifespans, maps | done |
| 6 | Hot reload | done |
| 6 | Optional login: members' view or private site | done |
| 6 | UI languages: German and Slovak besides English and Czech | done |

## Decisions

- **Scope:** read-only viewer. Editing stays in desktop Gramps; the site is rebuilt from a new export.
- **No export or download feature.**
- **Target:** a small VPS, so memory use matters. Single native binary built with GraalVM.
- **UI:** own server-rendered pages, not Gramps.js. Templates use JTE in "generate" mode: they become Java sources at
  build time, so only `jte-runtime` ships and JTE's extension writes the Native Image configuration.
  Templates only lay out page records (`server/.../view/Pages`) that `Views` fills with formatted,
  translated text. `Views` hands each page to one of its parts (`ListingViews`, `PersonViews`, `ChartViews`,
  `PlaceViews`, `MediaViews`), which share their helpers through `ViewPart`; it was one class of 1,800 lines
  until 2026-09-29.
- **Page language:** `?lang=` if given, else the browser's `Accept-Language`, else `--language`
  (default `en`). Any language works: ICU supplies dates and the alphabet (e.g. a "CH" heading in Czech);
  words fall back to English where no `messages_<lang>.properties` exists.
  - Only languages ICU has locale data for (about 250) are used; other codes are skipped as if not asked for.
    Each language's `Ui` (texts, formatters, collator) is kept for good, and every code that looked valid used
    to get one: all two- and three-letter codes took the native binary from 110 MB to 960 MB in 15 s.
- **URLs** use Gramps IDs (`/person/I0044`), falling back to the handle for objects without one.
- **Command line** (picocli, `Main`): the synopsis is written by hand (`kwatern [OPTIONS] EXPORT`), as the generated
  one listed every option. A wrong argument prints only the error and "Try 'kwatern --help'", like git and
  coreutils, and exits with 2. Enum options are read in any case and named in lower case in errors, as in the help.
  `kwatern help [COMMAND]` works too.
- **Security headers:** a strict Content Security Policy (no inline scripts or styles), `nosniff`,
  `same-origin` referrer. Only GET and HEAD are accepted.
- **Memory (no built-in heap limit):** the native image's defaults already bound the heap: at most 80% of
  physical memory (or of a container's limit), the young generation 10% of that. Measured 2026-09-28 on the
  example tree with `-XX:MaxRAM=1g` (a 1 GB VPS): 100 MB at start, levelling off at 218 MB after 30 reloads
  and 300 chart pages. A limit halves that (`-Xmx96m`: 82 MB over 31 reloads; a 1.7 GB package with
  `-Xmx64m`: 54 MB) at the cost of more frequent collections. It is left to the operator (the README says
  so), since a small built-in limit would break large trees and a large one would change nothing.
- **Dependencies:** as few as possible.
  - `gramps-xml` (parser and model) has none beyond the JDK.
  - `gramps-core` uses ICU4J for calendars and localized date display.
  - `server` uses the JDK's `jdk.httpserver`, and picocli for command-line options (its annotation
    processor generates the Native Image configuration).
  - `image` (JPEG decoding and encoding for thumbnails) has none; see "Images" below.
- **Build:** Gradle with the Groovy DSL. Formatting with Spotless and Palantir Java Format;
  `spotlessCheck` runs in CI. Tests run on the JVM (`test`) and compiled into native executables
  (`nativeTest`, about 3 minutes), which catches missing Native Image configuration.
- **CI and releases:** GitHub Actions. `build.yml` runs `spotlessCheck test` on every push and pull request,
  then (not for pull requests) `nativeTest`, the native binary and its `--check`. `release.yml` runs for a tag
  `v<version>` (the version lives in `gradle.properties`; the tag must match): Linux x64 and arm64 binaries built
  with `-Prelease` (`-march=compatibility`, since Native Image otherwise targets newer CPUs) on Ubuntu 22.04 for
  an older glibc, packaged with LICENSE and NOTICE.txt into a draft release that is published by hand.
- **License:** AGPL-3.0-or-later. This allows porting logic from Gramps (GPL-2.0-or-later) with attribution.
- **Languages:** no language-specific code. ICU provides month names and date order for any locale; texts
  such as "about" or "between ... and ..." come from `messages_<lang>.properties`. Adding a language means
  adding a file. English, Czech, German and Slovak are translated. Gramps type names (events, roles, place types
  and so on) follow Gramps' own translation of each language, so the site uses the words people know from
  Gramps; a term is changed where Gramps' reads badly on a page (Slovak "Manželstvo", the married state, is
  "Sobáš", the wedding). Slovak, like Czech, shows day and month as numbers and puts a comma before place
  names, avoiding case endings after prepositions. `MessagesTest` checks that each language has every text
  and only known keys. Translations need a native speaker's review.
- **Privacy is configurable, safe by default:** `--living=hide|show` and `--private=hide|show`, both
  `hide` unless given. Showing either prints a warning at startup. `--max-age` sets the maximum age
  used to decide who may be alive.
- **Living people:** shown only as "Living" with no name, gender, dates or other details. They keep their
  family links so the tree has no gaps. Families with a living parent lose their events and details.
- **Media nothing links to** are not published, unlike other unlinked objects such as a source without
  citations: a photo added but not yet linked may show someone alive, and nothing says who.
  A login for family members to see everything may come in milestone 6.
- **Name:** Kwatern (`kwatern` in commands, files and the `me.hejl.kwatern` package), so the name does not
  suggest an official Gramps project. It is the old spelling of the Czech *kvatern*, a volume of the Bohemian land
  registers, in which sales of estates, marriage contracts and wills were entered; each volume was named after its
  colour, such as the white or the green kwatern. The old spelling needs no accents. It was gramps-serve, then
  briefly Pamětník, until 2026-09-28. `gramps-xml` and `gramps-core` keep their descriptive names, as they are about
  Gramps data.
- **README screenshots** (`docs/screenshots`) show the Gramps example tree (v6.0.8, GPL-2.0-or-later, fictional
  people), person I0044, 1280 px wide, light theme, `--living=show --private=show`. Its photos are public domain on
  Wikimedia Commons except `Gunnlaugur_Larusson_-_Yawn.jpg` and `scanned_microfilm.png`, whose source is unclear;
  those two `<object>`s and their `<objref>`s are removed from the copy used for screenshots. Map tiles do not load
  in headless Firefox (`firefox --headless --screenshot`), so there is no map screenshot.

## Web design

Chosen from the mockups on the design canvas (a private claude.ai artifact) on 2026-09-26: the "Modern"
direction with these building blocks:

- **Heading:** given names, then the surname in bold, then the suffix. Below it a one-sentence life
  summary ("Born 21 June 1855 in Great Falls · died 28 June 1911 in Twin Falls, aged 56"; the age only when
  both dates are exact), then title, nickname, other names and a link to everyone with the surname.
  Without a photo the round portrait shows initials.
- **Person links:** in lists, whole rows with an initials avatar and years; inside sentences, plain text
  links. Names in links, boxes and titles read "Given Surname"; lists are sorted by surname.
- **Sources:** each event has a "N sources" button that opens the citations (source, page, date,
  confidence), plus "All sources on this page" at the bottom, each once with what it supports.
- **Family:** a diagram in the sidebar: parents, the person (dark box), and one block per family in
  marriage order with the partner and children. More than 8 children show "and N more", linking to the
  family page. Siblings are rows: the first 5, then "Show N more".
- **Gender:** the initials avatar is tinted, blue for men, terracotta for women, grey for unknown or
  other; the two colours also differ in lightness, and a hidden label names the gender for screen
  readers. `--gender-colours=off` turns the tint off. Future charts may use a coloured box edge instead.
- **Living people:** "Living" in italics with a dashed outline and no initials or gender colour. They are
  still links, to a page that shows only their place in the family.
- **Theme:** light and dark palettes; `--theme=auto` (default) follows each visitor's system setting,
  `--theme=light` or `dark` forces one. The dark variant is on the design canvas for review.
- **Type scale** (2026-09-28): every text size is one of eight tokens in `style.css`, chosen by role, in `rem`
  so that a larger font set in the browser enlarges the site too. An audit had found 15 different sizes for
  the same few roles (secondary text at 15, 16 and 17 px on different pages).

  | Token | Size | Role |
  |---|---|---|
  | `--text-badge` | 12 px | initials in small circles |
  | `--text-xs` | 14 px | uppercase labels, chart axes, number badges, map pins |
  | `--text-sm` | 16 px | secondary text: sub-lines, captions, chips, notes, family boxes, chart labels |
  | `--text-base` | 18 px | body text, header navigation, tabs |
  | `--text-md` | 20 px | summary lines, brand, letter index, small headings |
  | `--text-lg` | 22 px | card headings |
  | `--text-xl` | 28 px | stat numbers, large initials, headings on phones |
  | `--text-xxl` | 44 px | page heading |

  Charts follow the same roles: the lifespan chart like rows (name base, years sm), the tree chart like
  family boxes (sm), the fan chart base, sm and xs by ring. The only size outside the scale is the number
  on a photo's region marker, which grows with the image. On phones navigation and tabs take `sm` to fit.
  The sidebar is 420 px so names in the family diagram wrap less; years never break inside "1827–1916".
- **Visual tokens** (2026-09-28): spaces, corners and the colours that do not follow the theme are tokens in
  `style.css` too, after a second audit found about 20 spacing values, 11 corner radii, 15 hard-coded
  colours and three implementations of a number badge. The number badges on the media page were dark
  on dark in the dark theme.
  - Spaces sit on a 4 px grid: `--space-1` to `--space-8` are 4, 8, 12, 16, 24, 32, 48 and 64 px. The one
    exception is `--space-hair` (2 px), between tightly listed rows. Cards have 24 px padding (16 on phones).
  - Corners: `--radius-sm` 4 px for small parts, `--radius` 8 px for items within cards, `--radius-lg`
    12 px for cards, and `--radius-full` for pills, circles and bars.
  - Colours on photos and map tiles (`--overlay-*`, `--map-*`) are the same in both themes, since what is
    under them does not change.
  - Shared components each have one rule:
    - `.badge-number` numbers a list against a photo or a map; a map pin is the same badge with a white ring.
    - Uppercase group labels.
    - "Show N more".
    - `.card-note`: a remark under a card, or under its heading.
    - Avatars: 32 px in rows, 28 px in family boxes and charts.
  - One `@media (max-width: 900px)` block at the end holds all phone rules. The tree's column headings
    use gaps derived from the geometry in `Charts`, commented where they are set.
- **Stylesheet caching:** its URL carries a version from its content (`/static/style.css?v=...`), cached for
  good; any other request for it is revalidated, so a new build never shows with an old stylesheet. The other
  static files (Leaflet, `map.js`) are revalidated too, with an ETag from their content, so a map page does not
  fetch Leaflet's 150 KB again each time. They are read once and kept in memory.
- **Fonts:** Manrope and Source Sans 3 are bundled (Latin and Latin Extended, 217 KB, SIL Open Font
  License, from Fontsource). Other scripts use system fonts. Loading them from Google Fonts would break
  the Content Security Policy and tell Google who visits.
- **Search** (header on every page): names of people and places, ignoring case and accents; every word
  of the query must start a word of the name. People who may be alive are never found. The index is
  built once at startup.
- **Listings:** "Surnames", "Places" and "Sources" in the header. Places are a tree under the places that
  are within no other, with countries open and deeper levels collapsible; sources are grouped by letter.
  These listings and the tree's map are the same on every request, so they are made once per language and
  version of the tree (`Ui.page`). Pages about one place or surname use `TreeIndex`, built with the version:
  the events at each place, the places within each and the people under each surname. Before 2026-09-29 they
  went through the whole tree on each request; the USA page of the example tree took about 75 ms, now 13 ms.
- **Places that contain others** (countries, regions, parishes) are an overview of their area: a
  summary such as "State in USA · 68 events in 15 places within, 1628–1999"; the places within in the main
  column, busiest first, with a bar for their share; one timeline of everything within, naming the place
  (15 shown, up to 100 behind "Show more", the rest only counted); and "Surnames here", the most common
  surnames of people with events in the area. Towns without places inside keep the simple layout.
  Bars use `<progress>`, since the Content Security Policy forbids inline styles.
- **Coordinates** are read in every notation Gramps accepts (decimal with dot or comma, degrees-minutes-
  seconds, hemisphere letters) and shown as "39.0483° N, 95.6780° W", translated.
- **Places in the summary sentence** stay plain text; everywhere else places and sources are links.
- **No JavaScript:** opening sources and "Show more" use HTML `<details>`.
- **Layout:** header, a hero band, then a main column and a 420 px sidebar; below about 900 px the
  sidebar moves under the main column. Colours are CSS variables with a dark variant.
- **Language:** summary texts may have gender-specific variants (`summary.born.female`); Czech puts the
  place after a comma because a preposition would need the place name in another grammatical case.

## Images (milestone 4)

Thumbnails and portrait crops are made by our own JPEG decoder and encoder in the `image` module, written from
the standard (ITU-T T.81), not ported. Decided 2026-09-28 after trying the alternatives, because the target is a
single native binary:

- **ImageIO** works in a native image only with six shared libraries next to the binary (`libawt`,
  `libawt_headless`, `libjavajpeg`, `liblcms` and stubs, 1.7 MB) and JNI configuration. Linking them statically
  is no longer supported by GraalVM (oracle/graal#4921); forcing it builds, but fails at run time because
  `libawt` loads `libawt_headless.so` by path.
- **Libraries** that return `BufferedImage` need the same AWT libraries (Commons Imaging, ICAFE, ImageN).
  Pure-Java alternatives are too heavy (KorIM), immature, commercial (JDeli) or unmaintained (PNGJ).
- **GraalJS** with a JavaScript decoder: about 60 MB more, slower, and the decoders read whole images.

What the decoder does:

- Baseline and progressive JPEG with Huffman coding; greyscale, YCbCr, RGB, CMYK and YCCK (without colour
  profiles); all common chroma subsampling, restart markers, EXIF orientation, truncated files. Arithmetic
  coding, 12-bit, lossless and hierarchical JPEG are reported as unsupported.
- Decodes at 1/1, 1/2, 1/4 or 1/8 size, optionally only an area (for portraits), straight from the
  coefficients. A reduced size is exactly the full-size image averaged over blocks. libjpeg approximates the
  same, except for chroma subsampled in one direction only (4:2:2, 4:4:0, 4:1:1), which it enlarges by
  repeating pixels.
- Memory: baseline images stream one row of blocks at a time into the scaler, so even a 328-megapixel scan
  needs almost nothing, and decoding stops below the requested area. Progressive images keep coefficients:
  at 1/8 only the DC ones (the other scans are skipped), otherwise all of them up to 64 MB, beyond which
  they fall back to 1/8.
- The encoder writes baseline 4:2:0 JPEG with the standard tables, for the thumbnails.
- PNG (own decoder, `java.util.zip.Inflater` for the compressed data): all colour types and bit depths,
  Adam7 interlacing, transparency from alpha or `tRNS` drawn over white, EXIF orientation from `eXIf`; colour
  profiles and gamma are ignored. Rows are decoded straight into the scaler; interlaced images are held for
  the requested area only, up to 64 MB.
- GIF: the first frame, with its transparent colour over white.
- Formats are recognised by content, and "View full size" serves the type found (the GIF named `.jpg` in the
  real export is served as `image/gif`).
- PNG and GIF tests compare with ImageMagick where it is installed: every PNG colour type and bit depth,
  interlaced or not, with transparency, and GIF variants, generated at test time.
- Tests compare with libjpeg-turbo (`djpeg`) where it is installed, on test images made by
  `image/src/test/fixtures/make-jpeg-fixtures.sh` from a synthetic picture (committed), and with
  `-PgrampsFile` on all JPEG files of the export. They run on the JVM and as a native executable, without ImageIO.

Results on the real export (597 JPEG files, 5,049 megapixels, 32 progressive): all decode and match `djpeg`
to at least 57 dB PSNR at full size. Decoding all of them at 1/8 takes about 25 s on the JVM; libjpeg-turbo
takes 14 s. One file named `.jpg` is a GIF, so formats must be recognised by their content.

In the server (`web/MediaImages`):

- **Formats** are chosen by the first bytes of the file (`ImageDecoders`), through a small `ImageDecoder`
  interface, so another implementation can replace or join ours by passing a different list. JPEG, PNG and GIF
  (`ImageDecoders.DEFAULT`).
- **Photos on pages** (chosen from the mockups on 2026-09-28): a "Photos" card in the main column of person,
  family and place pages, on person and family pages as one compact row above the timeline (real timelines are long and
  pushed a card below them out of sight; chosen over a full card above it on 2026-09-28): four 120 px
  thumbnails with one-line titles (72 px, no titles, on phones), then "Show all N photos"; elsewhere two rows
  of larger tiles; titles in plain text, at most two lines, crops keeping
  the upper part where faces are; thumbnails with the date and, when the person is marked, "marked in this photo".
  A person's card also has the media of their events (captioned with the event), a family's those of its
  events. On source pages a citation's scans stand beside it; the source's own media get a card.
- **Media page** (`/media/{id}`): the image (longest side 1600 px) with the marked people outlined and
  numbered from left to right, "In this photo" with the same numbers below, and pointing at a name
  highlights its outline (CSS `:has()`, no JavaScript). The sidebar has date, kind, size and attributes, and
  "Also linked from" lists the other objects using it. "View full size" (`/media/{id}/original`) serves the
  file itself, read-only: images and PDF documents only, with a sandboxing Content Security Policy so a file
  can never run scripts on the site. Files that cannot be shown get the page without an image.
- **Photos listing** (`/photos`, "Photos" in the header when the tree has media): all media by decade of
  their date ("1890–1899", in every language), undated last, by date and title within.
- **Routes:** `/thumbnail/{media}` (longest side 320 px) and `/portrait/{person}` (240 px square). Both
  are twice the displayed size for high-DPI screens. Both are made only from media objects of the published
  database, never from a path in the request, so private media, media of living people and other files
  cannot be reached. Responses carry an ETag, and `Cache-Control` allows caching for an hour.
- **Portrait:** the first media reference of a person, as in Gramps, cropped to its region and widened to a
  square around it. Regions are taken to refer to the image as displayed (after EXIF rotation). Pages show
  the portrait only when the file exists and its header can be decoded, so they never show broken images;
  otherwise the initials remain.
- **Cache:** made on first request, at most two at a time in the whole process. Until 2026-09-29 the limit was per
  view, so with a login, and while a new version was being loaded, four or more large images could be decoded at
  once (up to 64 MB of coefficients each). The images are made once for both views: they depend only on the media
  object and its file, so the members' view and the public one share them (`MediaImages.forView`), each reaching
  only its own media. Originals have an ETag too. Thumbnails and portraits stay in memory for the
  life of the process; the larger images of media pages only up to 24 MB, the most recently used.
  - Requests for an image while it is being made wait for it rather than make it again. The larger images did
    not: 10 requests at once for a 144-megapixel photo decoded it 10 times, two at a time, the last answered
    after 12.8 s instead of 1.9 s, so anyone could keep two cores busy.
- **Files:** `--media-dir` sets where they are; by default the export's media path (with `~` expanded), else
  the export's directory. Absolute paths under the export's media path are also looked for under
  `--media-dir`, so the files can be copied to a server with the export unchanged.
- **Only files in the media directory** are served, since an export can name any file and may come from someone
  else: an absolute path or `../` in it served any file the server could read, e.g. under
  `/media/{id}/original` with a PDF type. Media elsewhere are skipped with a warning at start; your own tree
  with media all over the disk needs `--allow-media-anywhere`. Without `--media-dir`, the export's own media path
  is the media directory, so for an export from someone else set `--media-dir`.
- **Originals are checked by content:** a file is served as it is only if it decodes as an image, or begins as
  the type Gramps recorded does (`%PDF-` in the first kilobyte, the PNG, GIF, WebP, AVIF, BMP or TIFF
  signature). So even a file the export may name, e.g. with `--allow-media-anywhere` or a media path of `/`,
  is never served when it is a key, settings or other text.
- **Logs** name only the media ID when an image cannot be made, since file names often contain names.
  Likewise an error while serving a page logs only the kind of page (`/surname/...`) and the exceptions' types
  and stack traces, not the rest of the path or their messages, and a date the parser cannot read is counted in
  the warnings but not quoted. An export that cannot be loaded, at start or on reload, is reported with the
  parse error's line and column or else only the exception's type: the XML parser's messages quote the text, and
  those about files name media paths. Failed and refused sign-ins log the address (for tools such as fail2ban),
  never the name typed, which may be a password.

Native binary on the real export: all 582 readable media files (one is the GIF) give thumbnails in 36 s when
requested one after another, the same speed as on the JVM; from the cache they take 5 ms each. Memory went from
82 MB to 105 MB with everything cached (about 9 MB of thumbnails), peaking at 157 MB while two large images
were decoded at once.

## Gramps packages (`.gpkg`)

A package is read without Gramps: it is a gzip-compressed tar with the media files under their paths as
stored in the tree and the export as `data.gramps`, written last (Gramps: `plugins/export/exportpkg.py`).
`GrampsPackage` in `gramps-xml` reads it with its own small tar reader (ustar, PAX and GNU long names, as
Python's `tarfile` writes them; no dependency).

- It is recognised by its content, whatever the file is called.
- First pass: only `data.gramps` is read. After the privacy filter, a second pass extracts only the
  published media files, so private media and media of living people never reach the disk.
- They go into a new directory (mode 700) for every load, in the system's temporary directory or in
  `--extract-dir` (useful where `/tmp` is kept in memory), removed when a reload has replaced it and no request
  uses it any more, and on exit, including on SIGINT and SIGTERM (the binary is built with
  `--install-exit-handlers`). `--media-dir` is refused with a package, so an extraction cannot overwrite a media
  folder.
- Entries are only written inside that directory: names leading outside it, links and devices are skipped.
- Files that would leave less than 256 MB free are not extracted, with a warning: a small package from someone
  else can hold huge files of zeros, which would fill the disk, or the memory where `/tmp` is.
- Media are read only from that directory too, under their names in the package (`MediaImages.ofPackage`),
  whatever path the export gives. A package may come from someone else: before, an absolute path or `..` in its
  export served any file the server could read, e.g. under `/media/{id}/original` with a PDF type. A plain export
  keeps absolute paths, as Gramps allows media anywhere and the export is the operator's own.
- Real export as a package (1.7 GB, 598 media): the export is read, the 583 published files extracted in
  3.8 s, and the site serves them as with a plain export.

## Hot reload (milestone 6)

The server loads the export again when its file changes (`--reload=on`, the default) and serves the new
version from the next request on. Added 2026-09-28.

- The file is checked every `--reload-interval` seconds (default 10) by modification time and size, not with
  file system notifications, which fail on network mounts and in some containers. A change counts once
  both stayed the same for one more check, so a file still being copied is never read.
- The new version is loaded in the background (parsing, privacy filter, extracting a package) and then
  replaces the old one at once; each request uses the version it started with. If the file cannot be
  loaded, for example a truncated copy, the old version stays and the reason is printed.
- Thumbnails start afresh with each version, since a media file can change on disk without its Gramps
  object changing. A package is extracted into a new directory, and the previous one removed once the last request
  using the previous version has ended (`web/Version`). Until 2026-09-29 it was removed at once, while requests of
  the previous version might still be reading its files. `Loader` makes the versions, `Main` only the command line.
- While loading, both versions are in memory. Over 31 reloads of the example tree with `-Xmx96m` the
  process stayed at 82 MB; without a limit it levels off higher (see Memory under Decisions).
- The native image includes the XML parser's message bundle, so broken files give its real error instead
  of "Could not load any resource bundle".

## Login (milestone 6)

Optional signing in, added 2026-09-28. The design and the sign-in page are on the design canvas
("Milestone 6 · Sign-in"). The code is in `server/.../auth` and uses only the JDK.

- **Modes** (`--access`):
  - `open` (default): no login, as before.
  - `members`: a public view for everyone, plus a members' view after signing in.
  - `private`: nothing without signing in. Every address redirects to `/sign-in?next=...`, whether it exists
    or not, so nothing can be probed. The sign-in page shows only the brand, never the tree's name, counts or
    surnames.
- **Two filtered sites, not per-page checks.** The export is parsed once and filtered twice, into
  `Sites(everyone, members)`, each with its own search index; they share the images; the session picks one per
  request.
  - A mistake in a template cannot leak a living person into the public view, because it does not contain
    them.
  - What each view shows: `--living` and `--private` for the public view, `--members-living` (default show)
    and `--members-private` (default hide) for members. Members must see at least what the public sees.
  - Cost on the real export: 2 MB more heap and 3 MB more RSS.
- **Users file:** `name:hash` lines, written by `kwatern passwd --users FILE NAME`. It replaces the
  member's line in place and creates the file readable by its owner only.
  - Hashing is PBKDF2-HMAC-SHA256 with 600,000 iterations (OWASP 2023) and a 16-byte salt. Argon2 would need
    a library.
  - A check takes about 0.6 s in the native binary; two run at a time at most.
  - The file is re-read within 2 s of a change; a malformed new version keeps the old members.
  - A warning at startup when the group or everyone can read it or the `--secret-file`: hashes can be guessed
    offline, and the key signs sessions for anyone.
- **Sessions:** a stateless signed cookie, so the server stores nothing.
  - Contents: name, expiry and "keep" flag, signed with HMAC-SHA256 over them plus the member's current
    password hash. Changing the password or deleting the line ends the member's sessions at once.
  - "Keep me signed in": 30 days. Otherwise a browser-session cookie valid 12 hours. Renewed at most hourly
    while in use.
  - The key comes from `--secret-file` (created with a random key if missing), else it is random per start.
  - Signing out removes the cookie in that browser only; a copied cookie stays valid until it expires. There is
    no "sign out everywhere" button, which would need state on the server: a new password ends a member's
    sessions, and a new `--secret-file` everyone's (both in the README).
  - Flags: `HttpOnly`, `SameSite=Lax`, and `Secure` when the proxy reports HTTPS.
- **Forms:** sign-in and sign-out are POST forms without JavaScript.
  - Against cross-site forms: `Origin` must match `Host` (or `X-Forwarded-Host` behind a proxy). Without
    `Origin`, only `Sec-Fetch-Site: cross-site` is refused.
  - `next` must be a local path (not `//`, not `/\`, not `/sign-*`).
  - Forms over 4 KB are refused.
- **Guessing:** failures are counted per address and per name. After 5 within 15 minutes they wait 1 minute,
  doubling up to 15; the counters are in memory only.
  - Attempts still being checked count against the free ones: an address or name has at most 5 minus its
    failures in flight (one after a block), and further ones are refused with `Retry-After`. Otherwise many
    guesses sent at once all passed the check before the first failure was counted, and queued for the password
    check, which also kept members from signing in.
  - At most 32 attempts are in flight in all (the last waits some 10 s); further ones are refused at once. Many
    addresses (easy with IPv6) and names could otherwise queue without end. Such a flood still keeps members
    from signing in while it lasts, but costs no more memory and answers at once.
  - An unknown name takes as long as a wrong password and gets the same message.
  - Without `--behind-proxy` behind a proxy, every visitor has the proxy's address and shares one counter.
  - A member's name can be blocked by others guessing it, for at most 15 minutes at a time.
- **Caching:** members' pages are `private, no-store` with `X-Robots-Tag: noindex`, so they do not outlast
  signing out in the browser's cache. Members' images are `private, no-cache` with an ETag. With a login,
  every response varies by `Cookie`. A private site sends `noindex` everywhere.
- **HTTP:** the server has no TLS; a reverse proxy provides it. It refuses to start with a login on a
  non-loopback address unless `--behind-proxy` or `--allow-insecure-login` is given.
  - Trusted proxy headers: the last `X-Forwarded-For` entry, `X-Forwarded-Proto` and `X-Forwarded-Host`.
  - Only the proxy must be able to reach the server, or those headers can be forged.
  - A request must arrive within 30 s (`sun.net.httpserver.maxReqTime`, which the JDK leaves unlimited), so that
    clients sending slowly cannot hold connections open. Answers are not limited, as large files take long over
    slow links. Setting the property on the command line overrides it.
  - The JDK's server classes are initialized at run time in the native image (`native-image.properties`);
    initialized while building, they kept the build's unlimited value and the connection stayed open.
- **Header:** in members mode, "Sign in" at the end of the header returns to the current page.
  - Signed in, the header shows the name and a "Sign out" button, styled as a link, since signing out is a
    POST.
  - In members mode, a bar under the header says this is the members' view. A private site has no public
    view to confuse it with, so it has no bar.
- **Colours:** error messages use a new pair of colour tokens, `--danger-bg` and `--danger-fg`.
- **`--check`** hashes and verifies a password and signs a session, proving the JDK's crypto providers are
  in the native image. They are; no extra configuration was needed.

## Charts, timeline and maps (milestone 5)

Chosen from the mockups on the design canvas on 2026-09-28: both chart types, in tabs; all three maps, on
by default; both timeline features.

- **Person tabs:** Overview, Ancestors, Descendants and Map under the person's heading
  (`/person/{id}`, `/ancestors`, `/descendants`, `/map`). People who may be alive have no Map tab.
- **Ancestor chart** (`?generations=3..5`, default 4): a tree, the person on the left, drawn as SVG on the
  server (boxes and lines placed by attributes, as the Content Security Policy forbids inline styles). Five
  generations use narrower boxes without initials so the chart fits the page. Unknown ancestors are dashed
  boxes where the child is known; › on the last column continues the chart from that person. Long names
  shorten to initials and surname ("K. Holanová"), then with an ellipsis; the full name is the tooltip.
- **Fan chart** (`?view=fan`): the same ancestors in a half circle, father's side on the left, names along
  the rings where they fit and along the radius otherwise.
- **Small screens:** the tree chart becomes a list by generation, split into father's and mother's side.
- **Descendant chart** (`?generations=2..4`, default 3): an indented tree with marriages, children in the
  order of their family, and "N children ›" where the chart stops. A loop in damaged data is cut.
- **Family events in the timeline:** births of siblings and children and deaths of parents, spouses,
  siblings and children during the person's life, as grey rows linking the relative. Shown by default,
  `?family=hide` hides them. Births of parents and spouses are left out; so are people who may be alive.
- **Family lifespans:** bars on one time axis for parents, the person and spouses, siblings and children,
  the person's lifetime shaded, bars without a known end fading out; only when at least three are known.
  Hidden on phones, where it would be too small to read.
- **Maps** (Leaflet 1.9.4, BSD 2-Clause, bundled in `static/leaflet` from the npm package, checked against
  the registry's SHA-512; our `map.js` reads markers from `data-` attributes and sets text only as text):
  - the person's Map tab: their places numbered in timeline order, with a list; optionally the birth and
    death places of the family (`?family=show`);
  - place pages: a small map in the sidebar;
  - `/map`: all places with events as circles by number of events, filtered by kind (births, marriages,
    deaths and burials) and period.
  Events at places without coordinates are shown at the nearest enclosing place that has them (a house
  number in its village), and said so. Places with no coordinates up the hierarchy are listed as not on
  the map.
- **Tiles and privacy:** OpenStreetMap by default, `--map=off` turns maps off (no scripts, no tile server in
  the Content Security Policy), `--map-tiles` and `--map-attribution` set another tile server. Visitors'
  browsers load the tiles directly, which the server says at startup. OpenStreetMap refuses tiles without a
  `Referer`, so pages with a map send `Referrer-Policy: strict-origin`: the tile server learns the site's
  address, never the page or person viewed. All other pages keep `same-origin`.

## Design origins

The look of the site is our own: the "Modern" direction and its colours, fonts, boxes, tinted initials and row
links were designed for this project on the design canvas (2026-09-26 onwards), without copying or tracing
screenshots or code of other products.

The charts of milestone 5 follow standard genealogical forms that genealogy software and paper
charts have long shared: the ancestor tree (pedigree) chart, the fan chart, the descendant chart as an
indented list, the ⚭ sign for a marriage. Numbered map markers and lifespans as bars on a time axis are
generic ways of showing data. We keep our own styling for them and do not imitate the distinctive look of
any particular product.

Third-party material and what it requires:

- **Fonts** Manrope and Source Sans 3: SIL Open Font License, licence files bundled with the fonts. Fontsource's
  Source Sans 3 licence named "Google Inc." instead of Adobe's copyright; it is replaced with Google Fonts' own.
- **Leaflet** 1.9.4: BSD 2-Clause, licence bundled with it (`static/leaflet/LICENSE.txt`).
- **OpenStreetMap tiles**: "© OpenStreetMap contributors" shown on every map, and the
  OpenStreetMap tile usage policy (light use, identifying the application) followed, or another tile server
  configured.
- **Gramps**: logic ported from it (GPL-2.0-or-later) is marked with the Gramps source in comments, which the
  AGPL licence of this project allows.
- **Libraries in the program**: ICU4J (Unicode License v3), picocli and jte runtime (Apache 2.0). Their
  notices and licence texts are in `server/.../licenses` (`NOTICE.txt` lists everything bundled).
- **GraalVM Community Edition**: the native executable contains its runtime and the Java class library (GPLv2
  with the Classpath Exception, so the rest may be AGPL). The `graalvmLicenses` task copies GraalVM's licence
  and third-party notices into the executable and writes where their source is, from the GraalVM that builds
  it; it refuses other GraalVM editions, whose terms differ.
- **Every copy carries the licences:** `kwatern --licenses` prints them all from the executable (`Licenses`),
  so a copy of the single file is complete; `--check` fails if one is missing. The JVM distribution also has
  `LICENSE` and `NOTICE.txt` beside the program. A new library or bundled file needs its entry in `NOTICE.txt`
  and its licence text in `Licenses`.
- **Source code offer (AGPL section 13):** every page has a footer with the program and version; with
  `--source-url` it also links to the source code, which anyone serving a changed version must offer. It is on
  the private sign-in page too. There is no default link until the source is published.

## Differences from Gramps

- **Persian calendar:** ICU's arithmetic follows the official Iranian calendar. Gramps' 2820-year formula
  starts about 13% of months one day early (for example Nowruz 1404 on 2025-03-20 instead of 03-21), so
  Persian dates may sort one day differently than in Gramps.
- **Probably alive** (`gramps-core`, `privacy/AliveRules`):
  - "About", estimated and calculated dates count as up to 10 years later. Gramps uses 50, which would keep
    everyone born "about 1920" hidden as living.
  - A maximum parent age of 60 bounds a child's birth by the parents' births. Gramps has no such setting.
  - Otherwise uses Gramps' defaults: maximum age 110, sibling age difference 20, generation gap 20,
    minimum parent age 13. Doubt errs towards "alive".
- **Names:** a separator left at either end of a formatted name is removed, so someone with only a
  given name shows as "Jan"; Gramps shows ", Jan".
- **Custom name formats** from the export are read with English keywords (`surname`, `given`, ...) or
  `%` codes. Gramps also accepts keywords translated into its UI language; those are not recognised yet.
- **Place titles** use all levels of the hierarchy, like Gramps' default "Full" format. Gramps' other
  place formats (fewer levels, reversed order) are not supported yet. The preferred language of place
  names is the viewer's language; with no match the first name is used, as in Gramps.
- **Schema:** Gramps 6 writes `type="from"` and `type="to"` on dates, which the published 1.7.2 schema does
  not list. The parser accepts them.
- **Family dates:** the schema allows a date on a family, but Gramps does not write one and the model
  has no place for it, so the parser skips it on purpose. `<people default="...">` (Gramps' default
  person setting) is skipped too.
- **Schema sync:** the model and parser are hand-written, not generated; no generator produces records
  from the schema without a reflection-based runtime. `SchemaCoverageTest` checks instead that the parser
  reads every element and attribute of `grampsxml.rng` for the pinned Gramps release (6.0.8). The parser
  reports attributes it does not read, as it does unknown elements, also for real exports.

## Open items

- **Source URL:** once the source is published, make its address the default of `--source-url` and add it to
  `NOTICE.txt`.
- **Images:** other formats (TIFF, WebP, HEIC) are not decoded. The portrait is the
  first media reference, as in Gramps, even when its file is missing (then the initials remain). The peak memory while
  decoding large progressive images (up to 64 MB of coefficients each) could be lowered for small VPSs. CMYK is converted without a
  colour profile. Progressive images at 1/8 have colour at 1/16 resolution (DC coefficients only), which is
  fine for thumbnails. Speed in the native binary is not measured yet. Check whether Gramps image regions
  refer to the image before or after EXIF rotation.
- **Recheck user-entered dates** once pages show them: the real export has 36 free-text dates (`<datestr>`)
  and 3 `from` dates, all entered in the Gramps UI. Verify they parse and display as intended.
- **Check the living-people result:** 20 of 356 people in the real export are treated as possibly alive.
  Confirm this matches reality.
- **Rarer calendars:** Hebrew month numbering in non-leap years follows Gramps (Adar I and Adar II both
  mean Adar); only tested on a few dates.
- **Notes:** styles (bold, links) are not rendered yet.
- **Surname index:** surnames that do not start with a letter of the page language's alphabet go under
  ICU's "…" heading.
- **Native image:** ICU 78.3 needs its data included explicitly
  (`gramps-core/src/main/resources/META-INF/native-image/.../reachability-metadata.json`). Word breaking,
  units, currencies and transliteration data are left out; add them if a feature needs them.
  ICU creates its collator and number formats by reflection, so `CollatorServiceShim` and
  `NumberFormatServiceShim` are registered in the same file. JDK locale data (`java.text` formats) is only
  included for the default locale in a native image, so numbers are formatted with ICU (`i18n/Numbers`).
  This had broken Czech coordinates in the native binary until 2026-09-28.
  - Page texts are ICU `MessageFormat` patterns for the same reason: with the JDK's, counts in texts were grouped
    as in English in the native binary ("12,345 fotografií"). Until 2026-09-29.
  - Names of languages (of place names) come from ICU too (`Languages.name`); the JDK's gave English names in every
    language. ICU needs its `lang` and `region` data for them, and creates `LocaleDisplayNamesImpl`,
    `ICULangDataTables` and `ICURegionDataTables` by reflection, falling back to the bare code ("el") without
    them. The data adds about 5 MB to the binary.
  `--check` exercises date formatting, sorting, numbers in texts and language names to catch such gaps in a native
  build.

## Measurements

Memory, load time and size, compared with Gramps Web, are in the README ("Memory and size"). They were measured
with the native binary on the example tree: resident set when idle, after 1,500 pages for 300 people (their person,
ancestor, fan chart, descendant and map pages), and the same with `-Xmx96m`. Gramps Web ran from its documented
`docker-compose.yml` (image 3.22.1), with the tree imported through its API; memory is the sum of `docker stats`
after the person and timeline API requests of 300 people, with the default 8 web workers and 2 Celery
processes, and tuned to 2 and 1. Starting its stock compose file for the first time made both the web and the Celery
container create a tree named "Gramps Web"; starting the web container first avoids that. The binary is 45 MB
without the image decoders, 47 MB with them, 52 MB with ICU's names of languages and regions (2026-09-29).
