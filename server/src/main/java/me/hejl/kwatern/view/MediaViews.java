package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Region;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.image.ImageInfo;
import me.hejl.kwatern.view.Pages.Cited;
import me.hejl.kwatern.view.Pages.MarkedPerson;
import me.hejl.kwatern.view.Pages.MediaPage;
import me.hejl.kwatern.view.Pages.PhotoGroup;
import me.hejl.kwatern.view.Pages.PhotosPage;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** The page of a photo or document, and all of them by decade. */
final class MediaViews extends ViewPart {

    MediaViews(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        super(data, index, ui, images);
    }

    /** The media page: the image with the people marked in it, and everything that uses it. */
    public MediaPage media(Media media) {
        Optional<ImageInfo> info = images.info(media);
        int width = info.map(ImageInfo::displayWidth).orElse(0);
        int height = info.map(ImageInfo::displayHeight).orElse(0);
        List<MarkedPerson> marked = new ArrayList<>();
        List<Cited> linkedFrom = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(media.handle())) {
            boolean isMarked = false;
            if (referrer instanceof Person person && info.isPresent()) {
                for (MediaRef ref : person.media()) {
                    Region r = ref.region();
                    if (ref.media().equals(media.handle()) && r != null) {
                        int x1 = Math.clamp(Math.min(r.x1(), r.x2()), 0, 100);
                        int x2 = Math.clamp(Math.max(r.x1(), r.x2()), 0, 100);
                        int y1 = Math.clamp(Math.min(r.y1(), r.y2()), 0, 100);
                        int y2 = Math.clamp(Math.max(r.y1(), r.y2()), 0, 100);
                        marked.add(new MarkedPerson(
                                0,
                                link(person),
                                x1 * width / 100,
                                y1 * height / 100,
                                Math.max(1, (x2 - x1) * width / 100),
                                Math.max(1, (y2 - y1) * height / 100)));
                        isMarked = true;
                    }
                }
            }
            if (!isMarked) {
                describe(referrer).ifPresent(linkedFrom::add);
            }
        }
        // Numbered from left to right, as people read a group photo.
        marked.sort(Comparator.comparingInt(MarkedPerson::x).thenComparingInt(MarkedPerson::y));
        List<MarkedPerson> numbered = new ArrayList<>();
        for (MarkedPerson m : marked) {
            numbered.add(new MarkedPerson(numbered.size() + 1, m.person(), m.x(), m.y(), m.width(), m.height()));
        }
        List<Row> details = new ArrayList<>();
        addRow(details, "media.date", ui.date(media.date()));
        addRow(details, "media.kind", mediaKind(media));
        info.ifPresent(i -> addRow(details, "media.size", ui.t("media.pixels", i.displayWidth(), i.displayHeight())));
        details.addAll(attributes(media.attributes()));
        var sources = new SourceCollector();
        sources.add(media.citations(), ui.t("sources.media"));
        var group = photoGroup(media);
        return new MediaPage(
                mediaTitle(media),
                group.label(),
                Urls.photos() + "#" + group.id(),
                ui.join(
                        mediaKind(media),
                        ui.date(media.date()),
                        numbered.isEmpty() ? "" : ui.t("media.marked_count", numbered.size())),
                info.isPresent() ? Urls.mediaImage(media) : "",
                images.original(media).isPresent() ? Urls.original(media) : "",
                width,
                height,
                Math.max(8, Math.max(width, height) / 45),
                numbered,
                details,
                linkedFrom,
                notes(media.notes()),
                sources.uses());
    }

    /** All media, by decade of their date, undated last; the same on every request. */
    public PhotosPage photos() {
        return ui.page("photos", this::makePhotos);
    }

    private PhotosPage makePhotos() {
        Map<String, List<Media>> groups = new TreeMap<>();
        for (Media media : db.media().all()) {
            var group = photoGroup(media);
            groups.computeIfAbsent(group.id(), id -> new ArrayList<>()).add(media);
        }
        Comparator<Media> order = Comparator.comparingInt(
                        (Media m) -> m.date() == null ? Integer.MAX_VALUE : DateMath.sortValue(m.date()))
                .thenComparing(this::mediaTitle, ui.order().strings());
        List<PhotoGroup> result = new ArrayList<>();
        groups.forEach((id, media) -> {
            media.sort(order);
            result.add(new PhotoGroup(
                    id,
                    photoGroup(media.getFirst()).label(),
                    media.stream().map(m -> tile(m, "")).toList()));
        });
        return new PhotosPage(result, db.media().size());
    }

    /** The decade of a media object's date, such as "1890–1899", or undated; ids sort undated last. */
    private PhotoGroup photoGroup(Media media) {
        var year = media.date() == null ? OptionalInt.empty() : DateMath.gregorianYear(media.date());
        if (year.isEmpty()) {
            return new PhotoGroup("undated", ui.t("photos.undated"), List.of());
        }
        int decade = Math.floorDiv(year.getAsInt(), 10) * 10;
        // Offset so that ids sort by decade as text, also for years before 1000.
        return new PhotoGroup("d" + (decade + 100_000), decade + "–" + (decade + 9), List.of());
    }

    /** "Photo (JPEG)", "PDF document" or "File", from the type Gramps recorded. */
    private String mediaKind(Media media) {
        String mime = media.mime() == null ? "" : media.mime().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) {
            return ui.t("media.kind.image", mime.substring("image/".length()).toUpperCase(Locale.ROOT));
        }
        return ui.t(mime.equals("application/pdf") ? "media.kind.pdf" : "media.kind.file");
    }
}
