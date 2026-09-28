# Kwatern

Read-only web viewer for [Gramps](https://gramps-project.org) genealogy exports. Point it at a
Gramps XML export (`.gramps`) or package (`.gpkg`, with the media) and it serves the tree from memory as a
single native binary.

*Kwatern* is the old spelling of the Czech *kvatern*, a volume of the Bohemian land registers. Sales of estates,
marriage contracts and wills were entered in them, and each volume was named after its colour, such as the white or
the green kwatern. The project is not affiliated with Gramps.

Status: early development. It serves person, family, place and source pages with photos, ancestor and
descendant charts, maps, media pages, a surname index and a search. See [docs/NOTES.md](docs/NOTES.md) for
progress, decisions and open items.

## Modules

- `gramps-xml` - parser and immutable model for Gramps XML (schema 1.7.x). No runtime dependencies.
- `gramps-core` - dates in all Gramps calendars, localized display and the privacy filter. Uses ICU4J.
- `image` - JPEG, PNG and GIF decoding and JPEG encoding for thumbnails, in plain Java. No runtime dependencies.
- `server` - the web application, built on the JDK's `jdk.httpserver`.

## Build

Requires JDK 25; native builds need GraalVM 25.

```sh
./gradlew test                                   # uses the Gramps example tree, downloaded on first run
./gradlew test -PgrampsFile=/path/to/tree.gramps # also checks your own export (prints counts only)
./gradlew :server:installDist                    # JVM build in server/build/install/kwatern
GRAALVM_HOME=/path/to/graalvm ./gradlew :server:nativeCompile
```

## Run

```sh
server/build/native/nativeCompile/kwatern tree.gramps --port 8080
server/build/native/nativeCompile/kwatern tree.gramps --media-dir /srv/media   # where the photos are
server/build/native/nativeCompile/kwatern tree.gpkg                          # package: media extracted
server/build/native/nativeCompile/kwatern tree.gramps --check   # load, print summary, exit
server/build/native/nativeCompile/kwatern --help
```

When the export file changes, the server loads it again and serves the new version; `--reload=off` turns this
off.

Memory levels off at a few hundred MB (about 220 MB for a tree of 2,000 people on a 1 GB machine). On a small
VPS a heap limit keeps it lower, at the cost of slightly more garbage collection: `-Xmx128m`, or `-Xmx96m`
for trees of a few thousand people, runs in about 80–100 MB:

```sh
server/build/native/nativeCompile/kwatern -Xmx128m tree.gramps
```

Maps load their images from OpenStreetMap in visitors' browsers; `--map=off` turns them off and
`--map-tiles` chooses another tile server.

By default people who may be alive appear only as "Living" and records marked private in Gramps are left
out. `--living=show` and `--private=show` publish them, for example on a site only family can reach;
the server then prints a warning at startup.

### Signing in

Family members can sign in to see more than the public, for example people who may be alive. With
`--access=members` everyone sees the public view and members who sign in see the members' view; with
`--access=private` only members who sign in see anything. `--members-living` and `--members-private` set what
the members' view shows (by default living people, but not records marked private).

```sh
kwatern passwd --users users.txt jana       # add a member or change their password
kwatern tree.gramps --access=members --users users.txt --secret-file session.key --behind-proxy
```

Removing a member's line from `users.txt` signs them out; the file is read again when it changes. Without
`--secret-file` members sign in again after each restart.

Passwords must not travel over plain HTTP. Put a reverse proxy with HTTPS in front of the server, such as
Caddy (`reverse_proxy 127.0.0.1:8080`) or nginx, keep the server on `127.0.0.1` and use `--behind-proxy`, so
that the visitor's address and HTTPS are taken from the proxy's `X-Forwarded-*` headers. The server refuses to
offer signing in on another address without `--behind-proxy`, unless `--allow-insecure-login` is given.

## License

AGPL-3.0-or-later, see [LICENSE](LICENSE). The program includes third-party software under its own licenses,
listed in [NOTICE.txt](server/src/main/resources/me/hejl/kwatern/licenses/NOTICE.txt);
`kwatern --licenses` prints them all, so every copy of the program carries them. If you serve a changed
version, the AGPL asks you to offer its source to visitors: `--source-url` links to it from every page.
